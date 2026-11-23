#!/usr/bin/env python3
"""
tests/test_koodo_reader_and_sidecars.py
Automated tests for Kittlibr reader core:
  1. Real EPUB Archive & Chapter Extraction (DefensiveArchiveUnpacker) rejecting binary stubs.
  2. AnyMD Sidecar Read/Write round-trip (atomic YAML frontmatter, Zettelkasten ID, md5 dedupe, warnings).
  3. Multiple sidecar storage modes:
     - Subfolder (.anymd/file.ext.md)
     - Side-by-side (file.ext.md)
     - Dot AnyMD (.file.anymd)
     - AnyMD extension (file.anymd)
  4. Honest Daemon status (/api/status returning 200 OK echoing remote/subpath).
  5. Structural Location Anchoring and subpath persistence verification.
"""

import os
import sys
import tempfile
import zipfile
import subprocess
import unittest
from pathlib import Path

# Add repo root to sys.path
REPO_ROOT = Path(__file__).parent.parent.resolve()
sys.path.insert(0, str(REPO_ROOT))

from src.daemon.anymd_daemon import AnyMDEngine, app
from fastapi.testclient import TestClient


class TestKoodoReaderAndSidecars(unittest.TestCase):
    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory()
        self.vault_root = Path(self.temp_dir.name)
        self.engine = AnyMDEngine(self.vault_root)

    def tearDown(self):
        self.temp_dir.cleanup()

    def create_mock_epub(self, epub_path: Path, title: str, author: str, chapters: list) -> Path:
        """Constructs a standards-compliant EPUB3 archive in memory/disk"""
        with zipfile.ZipFile(epub_path, "w") as z:
            z.writestr("mimetype", "application/epub+zip", compress_type=zipfile.ZIP_STORED)
            z.writestr(
                "META-INF/container.xml",
                """<?xml version="1.0"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
  <rootfiles>
    <rootfile full-path="OEBPS/package.opf" media-type="application/oebps-package+xml"/>
  </rootfiles>
</container>""",
            )

            manifest_items = []
            spine_itemrefs = []
            for idx, ch in enumerate(chapters):
                ch_id = f"chapter_{idx + 1}"
                ch_file = f"ch_{idx + 1}.xhtml"
                manifest_items.append(f'<item id="{ch_id}" href="{ch_file}" media-type="application/xhtml+xml"/>')
                spine_itemrefs.append(f'<itemref idref="{ch_id}"/>')

                z.writestr(
                    f"OEBPS/{ch_file}",
                    f"""<html xmlns="http://www.w3.org/1999/xhtml">
<head><title>{ch['title']}</title></head>
<body>
  <h2>{ch['title']}</h2>
  <div>{ch['body']}</div>
</body>
</html>""",
                )

            opf_xml = f"""<?xml version="1.0" encoding="utf-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="BookId">
  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
    <dc:title>{title}</dc:title>
    <dc:creator>{author}</dc:creator>
    <dc:identifier id="BookId">urn:uuid:test-book-{title.lower().replace(' ', '-')}</dc:identifier>
  </metadata>
  <manifest>
    {chr(10).join(manifest_items)}
  </manifest>
  <spine>
    {chr(10).join(spine_itemrefs)}
  </spine>
</package>"""
            z.writestr("OEBPS/package.opf", opf_xml)

        return epub_path

    def test_01_real_epub_chapter_extraction_rejects_binary_stubs(self):
        """Test real EPUB unpacking via DefensiveArchiveUnpacker and assert no binary stub placeholders."""
        epub_file = self.vault_root / "the_lost_citadel.epub"
        sample_chapters = [
            {"title": "Chapter 1: The Gate of Whispers", "body": "<p>Wind howled through the granite arches.</p>"},
            {"title": "Chapter 2: The Silent Library", "body": "<p>Rows of ancient codices lined the vault.</p>"},
            {"title": "Chapter 3: The Star Core", "body": "<p>A crystalline luminescence pulsed from the pedestal.</p>"}
        ]
        self.create_mock_epub(epub_file, "The Lost Citadel", "C. S. Sterling", sample_chapters)

        # Run extraction using the hardening unpacker
        node_script = f"""
const fs = require('fs');
globalThis.JSZip = require('./src/assets/lib/jszip.min.js');
const {{ DefensiveArchiveUnpacker }} = require('./src/utils/hardening/DefensiveArchiveUnpacker');

async function run() {{
    const bytes = fs.readFileSync('{epub_file}');
    const result = await DefensiveArchiveUnpacker.unpackEpub(new Uint8Array(bytes));
    
    // Check extraction
    if (result.title !== 'The Lost Citadel') {{
        throw new Error('Title mismatch: ' + result.title);
    }}
    if (result.author !== 'C. S. Sterling') {{
        throw new Error('Author mismatch: ' + result.author);
    }}
    if (result.chapters.length !== 3) {{
        throw new Error('Chapter count mismatch: ' + result.chapters.length);
    }}
    
    // Assert no binary stubs in chapter HTML
    for (const ch of result.chapters) {{
        if (ch.html.includes('[Binary EPUB Book')) {{
            throw new Error('Detected forbidden binary stub in chapter: ' + ch.title);
        }}
    }}
    
    if (!result.chapters[0].html.includes('The Gate of Whispers')) {{
        throw new Error('Chapter 1 content missing');
    }}
    if (!result.chapters[2].html.includes('The Star Core')) {{
        throw new Error('Chapter 3 content missing');
    }}
    
    console.log(JSON.stringify({{
        success: true,
        chaptersCount: result.chapters.length,
        titles: result.chapters.map(c => c.title)
    }}));
}}
run().catch(e => {{ console.error(e); process.exit(1); }});
"""
        res = subprocess.run(["npx", "tsx", "-e", node_script], capture_output=True, text=True, cwd=str(REPO_ROOT))
        self.assertEqual(res.returncode, 0, f"DefensiveArchiveUnpacker failed: {res.stderr}")
        self.assertIn("Gate of Whispers", res.stdout)
        self.assertNotIn("[Binary EPUB Book", res.stdout)

    def test_02_anymd_sidecar_atomic_roundtrip(self):
        """Test atomic YAML frontmatter sidecar creation, verification, and reading."""
        book_file = self.vault_root / "tales_of_the_crypt.epub"
        book_file.write_bytes(b"dummy epub binary content for md5 check")
        
        computed_md5 = self.engine.compute_md5(book_file)
        self.assertTrue(len(computed_md5) > 0)

        metadata = {
            "title": "Tales of the Crypt",
            "author": "Archivist Poe",
            "tags": ["Grimdark", "Fantasy", "Classic"],
            "warnings": ["#does-the-dog-die", "#piss-your-pants"],
            "reading_progress": {
                "chapter_index": 4,
                "percentage": 42.5,
                "anchor": "ch4_p12"
            },
            "added_at": "2026-11-23T10:00:00Z"
        }

        # 1. Write in subfolder mode (.anymd/tales_of_the_crypt.epub.md)
        paths = self.engine.get_sidecar_paths(book_file)
        subfolder_sidecar = paths["subfolder"]
        self.engine.atomic_write_yaml(subfolder_sidecar, metadata, body_text="Personal reader notes on chapter 4.")

        self.assertTrue(subfolder_sidecar.exists())
        
        # Read back
        read_meta = self.engine.read_sidecar(subfolder_sidecar)
        self.assertIsNotNone(read_meta)
        self.assertEqual(read_meta["title"], "Tales of the Crypt")
        self.assertEqual(read_meta["reading_progress"]["chapter_index"], 4)
        self.assertEqual(read_meta["reading_progress"]["percentage"], 42.5)
        self.assertIn("#does-the-dog-die", read_meta["warnings"])
        self.assertEqual(read_meta["_body"], "Personal reader notes on chapter 4.")

    def test_03_configurable_sidecar_storage_modes(self):
        """Test sidecar resolution across all supported storage modes."""
        sample_book = self.vault_root / "fantasy_odyssey.epub"
        sample_book.write_bytes(b"sample fantasy odyssey book data")

        paths = self.engine.get_sidecar_paths(sample_book)
        
        # Check all mode targets
        self.assertEqual(paths["subfolder"].name, "fantasy_odyssey.epub.md")
        self.assertEqual(paths["side_by_side"].name, "fantasy_odyssey.epub.md")
        self.assertEqual(paths["dot_anymd"].name, ".fantasy_odyssey.epub.anymd")
        self.assertEqual(paths["anymd"].name, "fantasy_odyssey.epub.anymd")

        # Test writing dot_anymd
        meta_dot = {"title": "Fantasy Odyssey (Dot AnyMD)", "mode": "dot_anymd"}
        self.engine.atomic_write_yaml(paths["dot_anymd"], meta_dot)
        self.assertTrue(paths["dot_anymd"].exists())
        self.assertEqual(self.engine.read_sidecar(paths["dot_anymd"])["title"], "Fantasy Odyssey (Dot AnyMD)")

        # Test writing anymd
        meta_anymd = {"title": "Fantasy Odyssey (AnyMD)", "mode": "anymd"}
        self.engine.atomic_write_yaml(paths["anymd"], meta_anymd)
        self.assertTrue(paths["anymd"].exists())
        self.assertEqual(self.engine.read_sidecar(paths["anymd"])["title"], "Fantasy Odyssey (AnyMD)")

    def test_04_daemon_api_status_and_honest_subpaths(self):
        """Test GET /api/status returns 200 OK, echoes remote/subpaths, and prevents silent resets."""
        client = TestClient(app)
        
        # 1. Base status check
        resp = client.get("/api/status")
        self.assertEqual(resp.status_code, 200)
        data = resp.json()
        self.assertEqual(data["status"], "online")
        self.assertEqual(data["port"], 3050)
        self.assertIn("vault_root", data)

        # 2. Echo queried remote & subpath check (e.g. davjump + meow-vault/inbox)
        custom_remote = "davjump"
        custom_subpath = "meow-vault/inbox"
        resp_echo = client.get(f"/api/status?remote={custom_remote}&path={custom_subpath}")
        self.assertEqual(resp_echo.status_code, 200)
        echo_data = resp_echo.json()
        self.assertEqual(echo_data["queried_remote"], "davjump")
        self.assertEqual(echo_data["queried_path"], "meow-vault/inbox")
        # Ensure no fake defaults are returned
        self.assertNotEqual(echo_data["queried_path"], "library/")

    def test_05_structural_location_anchor_resilience(self):
        """Verify StructuralLocationAnchor generates persistent anchors that survive viewport reflow."""
        node_script = """
const { StructuralLocationAnchor } = require('./src/utils/hardening/StructuralLocationAnchor');

// 1. Create anchor at paragraph 3 out of 10
const anchor = StructuralLocationAnchor.createAnchor(3, 0, 15, 10, 'The shadows began to lengthen across the marble courtyard.');
if (anchor.paragraphIndex !== 3 || anchor.percentage !== 30) {
    throw new Error('Anchor calculation failed: ' + JSON.stringify(anchor));
}

// 2. Mock DOM paragraphs
const paragraphs = [
    { innerText: 'Introductory text.' },
    { innerText: 'First chapter begins.' },
    { innerText: 'Second chapter text.' },
    { innerText: 'The shadows began to lengthen across the marble courtyard.' },
    { innerText: 'Subsequent events.' }
];

const resolved = StructuralLocationAnchor.resolveAnchor(anchor, paragraphs);
if (!resolved || !resolved.innerText.includes('shadows began to lengthen')) {
    throw new Error('Failed to resolve anchor in DOM: ' + JSON.stringify(resolved));
}

console.log(JSON.stringify({ success: true, resolvedText: resolved.innerText }));
"""
        res = subprocess.run(["npx", "tsx", "-e", node_script], capture_output=True, text=True, cwd=str(REPO_ROOT))
        self.assertEqual(res.returncode, 0, f"StructuralLocationAnchor test failed: {res.stderr}")
        self.assertIn("shadows began to lengthen", res.stdout)


    def test_06_real_gutenberg_alice_in_wonderland_comparison(self):
        """
        Tests network retrieval / real EPUB parsing of Gutenberg's 'Alice's Adventures in Wonderland'
        and explicitly asserts that the loader extracts real literary prose rather than code,
        scripts, JSON strings, or binary stubs.
        """
        alice_epub_path = REPO_ROOT / "test_assets" / "alice_gutenberg.epub"
        
        # If not already cached locally in test_assets, fetch live over network
        if not alice_epub_path.exists():
            alice_epub_path.parent.mkdir(parents=True, exist_ok=True)
            url = "https://media.githubusercontent.com/media/AndresSweeneyRios/bookstore-demo/24999548645ad44936db470b4874699982a9755f/books/alices-adventures-in-wonderland.epub"
            import urllib.request
            req = urllib.request.Request(url, headers={"User-Agent": "Kittlibr-Test/1.0"})
            with urllib.request.urlopen(req, timeout=15) as resp, open(alice_epub_path, "wb") as f:
                f.write(resp.read())

        self.assertTrue(alice_epub_path.exists(), "Alice in Wonderland EPUB file must exist")
        self.assertGreater(alice_epub_path.stat().st_size, 50000, "EPUB size must be > 50KB")

        node_script = f"""
const fs = require('fs');
globalThis.JSZip = require('./src/assets/lib/jszip.min.js');
const {{ DefensiveArchiveUnpacker }} = require('./src/utils/hardening/DefensiveArchiveUnpacker');

async function verifyAlice() {{
    const bytes = fs.readFileSync('{alice_epub_path}');
    const result = await DefensiveArchiveUnpacker.unpackEpub(new Uint8Array(bytes));

    // 1. Author and Title verification
    if (!result.title.includes('Alice')) {{
        throw new Error('Title does not contain Alice: ' + result.title);
    }}
    if (!result.author.includes('Lewis Carroll')) {{
        throw new Error('Author mismatch: ' + result.author);
    }}
    if (result.chapters.length < 10) {{
        throw new Error('Insufficient chapters unpacked: ' + result.chapters.length);
    }}

    // 2. Locate Chapter 1 / Down the Rabbit-Hole
    let rabbitHoleChapter = null;
    for (const ch of result.chapters) {{
        if (ch.html.includes('Down the Rabbit-Hole') || ch.html.includes('White Rabbit')) {{
            rabbitHoleChapter = ch;
            break;
        }}
    }}

    if (!rabbitHoleChapter) {{
        throw new Error('Could not find Down the Rabbit-Hole chapter in unpacked chapters');
    }}

    const cleanText = rabbitHoleChapter.html.replace(/\\s+/g, ' ');

    // 3. Strict literary text comparison: verify actual story paragraphs exist
    const expectedProse1 = "LICE was beginning to get very";
    const expectedProse2 = "tired of sitting by her sister on";
    const expectedProse3 = "the bank, and of having nothing";
    const expectedProse4 = "White Rabbit with pink eyes ran close by her";

    if (!cleanText.includes(expectedProse1) || !cleanText.includes(expectedProse2) || !cleanText.includes(expectedProse3)) {{
        throw new Error('Real book text missing from chapter HTML: ' + cleanText.slice(0, 300));
    }}
    if (!cleanText.includes(expectedProse4)) {{
        throw new Error('White Rabbit passage missing from chapter HTML: ' + cleanText.slice(0, 300));
    }}

    // 4. Verify it is loading actual book prose and NOT code/scripts/stubs
    if (rabbitHoleChapter.html.includes('[Binary EPUB Book')) {{
        throw new Error('Corrupt/mock binary stub placeholder detected in Alice chapter');
    }}
    if (rabbitHoleChapter.html.includes('<script') || rabbitHoleChapter.html.includes('function(') || rabbitHoleChapter.html.includes('console.log')) {{
        throw new Error('Script or executable code leaked into unpacked chapter output');
    }}

    console.log(JSON.stringify({{
        success: true,
        title: result.title,
        author: result.author,
        chaptersCount: result.chapters.length,
        verifiedProsePassage: expectedProse4
    }}));
}}

verifyAlice().catch(e => {{ console.error(e); process.exit(1); }});
"""
        res = subprocess.run(["npx", "tsx", "-e", node_script], capture_output=True, text=True, cwd=str(REPO_ROOT))
        self.assertEqual(res.returncode, 0, f"Alice in Wonderland verification failed: {res.stderr}")
        self.assertIn("White Rabbit with pink eyes ran close by her", res.stdout)
        self.assertIn("Lewis Carroll", res.stdout)


if __name__ == "__main__":
    unittest.main()
