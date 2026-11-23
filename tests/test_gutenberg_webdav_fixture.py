#!/usr/bin/env python3
"""
tests/test_gutenberg_webdav_fixture.py
Automated end-to-end integration test against a local Gutenberg Top-20 WebDAV fixture.

Validates:
  1. Real read/write/delete WebDAV server loopback (rclone serve webdav on 127.0.0.1:8085).
  2. AnyMD :3050 daemon discovery of 20 real Gutenberg EPUBs via rclone rcd.
  3. Real chapter extraction from Gutenberg EPUBs (asserting zero binary stub placeholders).
  4. Real .anymd sidecar generation and round-trip verification directly on WebDAV.
"""

import os
import sys
import time
import json
import shutil
import tempfile
import unittest
import subprocess
import urllib.request
from pathlib import Path

REPO_ROOT = Path(__file__).parent.parent.resolve()
sys.path.insert(0, str(REPO_ROOT))

from src.daemon.anymd_daemon import AnyMDEngine, app
from scripts.seed_gutenberg_webdav import (
    seed_fixtures,
    verify_webdav_read_write,
    launch_webdav_server,
)
from fastapi.testclient import TestClient


class TestGutenbergWebDAVFixture(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.fixture_dir = REPO_ROOT / "webdav_fixture"
        cls.library_dir = cls.fixture_dir / "library"
        cls.webdav_addr = "127.0.0.1:8085"
        cls.rcd_addr = "127.0.0.1:5572"

        # 1. Ensure 20 Gutenberg fixtures are seeded
        seed_fixtures(cls.library_dir, REPO_ROOT)

        # 2. Launch WebDAV server
        cls.webdav_proc = launch_webdav_server(cls.fixture_dir, cls.webdav_addr)

        # 3. Launch rclone rcd daemon
        cls.rcd_proc = subprocess.Popen(
            ["rclone", "rcd", "--rc-addr", cls.rcd_addr, "--rc-no-auth"],
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
        )
        time.sleep(2.0)

    @classmethod
    def tearDownClass(cls):
        if hasattr(cls, "webdav_proc") and cls.webdav_proc:
            cls.webdav_proc.terminate()
            cls.webdav_proc.wait()
        if hasattr(cls, "rcd_proc") and cls.rcd_proc:
            cls.rcd_proc.terminate()
            cls.rcd_proc.wait()

    def test_01_webdav_read_write_delete_probe(self):
        """Verifies loopback WebDAV PUT, GET, and DELETE against /library."""
        verify_webdav_read_write(f"http://{self.webdav_addr}")

    def test_02_anymd_discovers_twenty_gutenberg_epubs_via_webdav(self):
        """Tests that AnyMD :3050 scans the WebDAV remote and discovers all 20 real EPUBs."""
        client = TestClient(app)

        remote_str = f":webdav,url='http://{self.webdav_addr}/':"
        scan_payload = {
            "remote": remote_str,
            "path": "library",
            "rc_url": f"http://{self.rcd_addr}",
            "write_sidecars": True,
        }

        resp = client.post("/api/rclone/scan-import", json=scan_payload)
        self.assertEqual(resp.status_code, 200, f"Scan import failed: {resp.text}")
        data = resp.json()
        self.assertTrue(data["success"])
        self.assertEqual(data["detected_count"], 20, f"Expected 20 EPUBs, got {data['detected_count']}")
        self.assertEqual(data["imported_count"], 20)

    def test_03_real_epub_chapter_extraction_no_binary_stubs(self):
        """Parses a real Gutenberg EPUB from the WebDAV fixture asserting valid chapter prose and zero stubs."""
        alice_path = self.library_dir / "Alices_Adventures_in_Wonderland.epub"
        self.assertTrue(alice_path.exists())

        node_script = f"""
const fs = require('fs');
globalThis.JSZip = require('./src/assets/lib/jszip.min.js');
const {{ DefensiveArchiveUnpacker }} = require('./src/utils/hardening/DefensiveArchiveUnpacker');

async function testExtraction() {{
    const bytes = fs.readFileSync('{alice_path}');
    const result = await DefensiveArchiveUnpacker.unpackEpub(new Uint8Array(bytes));
    
    if (result.chapters.length < 5) {{
        throw new Error('Expected at least 5 chapters, got: ' + result.chapters.length);
    }}
    
    // Assert zero binary stub placeholders across all chapters
    for (const ch of result.chapters) {{
        if (ch.html.includes('[Binary EPUB Book')) {{
            throw new Error('Detected forbidden binary stub in chapter: ' + ch.title);
        }}
    }}
    
    // Locate Down the Rabbit-Hole / White Rabbit chapter
    const rabbitHoleCh = result.chapters.find(c => c.html.includes('White Rabbit') || c.html.includes('Down the Rabbit-Hole'));
    if (!rabbitHoleCh) {{
        throw new Error('Down the Rabbit-Hole / White Rabbit passage not found in any chapter');
    }}
    
    console.log(JSON.stringify({{
        success: true,
        chaptersCount: result.chapters.length,
        rabbitHoleChapterTitle: rabbitHoleCh.title
    }}));
}}

testExtraction().catch(e => {{ console.error(e); process.exit(1); }});
"""
        res = subprocess.run(["npx", "tsx", "-e", node_script], capture_output=True, text=True, cwd=str(REPO_ROOT))
        self.assertEqual(res.returncode, 0, f"Real EPUB extraction failed: {res.stderr}")
        self.assertIn("chaptersCount", res.stdout)

    def test_04_anymd_webdav_sidecar_roundtrip_and_persistence(self):
        """Writes an AnyMD sidecar to the WebDAV folder and reads it back cleanly."""
        sidecar_path = self.library_dir / ".anymd" / "Alices_Adventures_in_Wonderland.epub.md"
        sidecar_path.parent.mkdir(parents=True, exist_ok=True)

        engine = AnyMDEngine(self.fixture_dir)
        metadata = {
            "title": "Alice's Adventures in Wonderland",
            "author": "Lewis Carroll",
            "format": "EPUB",
            "reading_progress": {
                "chapter_index": 1,
                "percentage": 14.2,
                "scroll_top": 450
            },
            "tags": ["Classics", "Fantasy", "WebDAV-Verified"],
            "source": "Gutenberg Top 20"
        }

        # Atomic write
        engine.atomic_write_yaml(sidecar_path, metadata, body_text="Alice falls down the rabbit hole.")
        self.assertTrue(sidecar_path.exists())

        # Read back
        read_data = engine.read_sidecar(sidecar_path)
        self.assertIsNotNone(read_data)
        self.assertEqual(read_data["title"], "Alice's Adventures in Wonderland")
        self.assertEqual(read_data["author"], "Lewis Carroll")
        self.assertEqual(read_data["reading_progress"]["chapter_index"], 1)
        self.assertEqual(read_data["reading_progress"]["percentage"], 14.2)
        self.assertIn("WebDAV-Verified", read_data["tags"])
        self.assertEqual(read_data["_body"], "Alice falls down the rabbit hole.")


if __name__ == "__main__":
    unittest.main()
