"""
Integration tests for Vaultr 4-Folder Seedr flow, single-flight locking, free-space checks,
and AnyMD daemon /api/vaultr endpoints.
"""

import unittest
import tempfile
import json
import zipfile
from pathlib import Path
from fastapi.testclient import TestClient

from src.vaultr.pipeline import VaultrPipeline, VaultrConfig, SingleFlightLock
from src.daemon.anymd_daemon import app, vaultr_pipeline


class TestVaultrPipeline(unittest.TestCase):
    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory()
        self.base_path = Path(self.temp_dir.name)
        
        self.config = VaultrConfig(
            base_dir=self.base_path,
            incoming_folder=self.base_path / "incoming",
            staging_folder=self.base_path / "staging",
            sidecar_folder=self.base_path / "sidecars",
            destination_folder=self.base_path / "main_vault",
            min_free_space_bytes=1024  # Small threshold for tests
        )
        self.pipeline = VaultrPipeline(self.config)
        self.client = TestClient(app)

    def tearDown(self):
        self.temp_dir.cleanup()

    def test_01_single_flight_lock(self):
        """Verify single-flight lock prevents double execution."""
        lock_file = self.base_path / ".test.lock"
        lock1 = SingleFlightLock(lock_file)
        lock2 = SingleFlightLock(lock_file)

        self.assertTrue(lock1.acquire())
        self.assertFalse(lock2.acquire(), "Concurrent lock acquisition should fail")

        lock1.release()
        self.assertTrue(lock2.acquire(), "Lock should be re-acquirable once released")
        lock2.release()

    def test_02_seedr_4folder_pipeline_execution(self):
        """Verify 4-Folder Seedr pipeline: incoming -> unpack -> sidecar -> destination."""
        self.config.ensure_directories()

        # Create simulated incoming zip file containing an epub
        zip_path = self.config.incoming_folder / "seedr_pack_vol1.zip"
        book_content = b"Simulated EPUB book content for Vaultr"
        with zipfile.ZipFile(zip_path, "w") as zf:
            zf.writestr("book_alpha.epub", book_content)

        # Also create a loose .txt file
        txt_path = self.config.incoming_folder / "readme_notes.txt"
        with open(txt_path, "w", encoding="utf-8") as f:
            f.write("Important seedbox release notes")

        # Run pipeline
        res = self.pipeline.run_pipeline()
        self.assertTrue(res["success"])
        self.assertEqual(res["processed_count"], 2)

        # Verify incoming is cleared
        self.assertEqual(len(list(self.config.incoming_folder.iterdir())), 0)

        # Verify destination has both binary and AnyMD sidecar
        dest_files = [p.name for p in self.config.destination_folder.iterdir()]
        self.assertIn("book_alpha.epub", dest_files)
        self.assertIn("book_alpha.epub.md", dest_files)
        self.assertIn("readme_notes.txt", dest_files)
        self.assertIn("readme_notes.txt.md", dest_files)

        # Verify sidecar contains frontmatter and Zettelkasten ID
        sidecar_path = self.config.destination_folder / "book_alpha.epub.md"
        with open(sidecar_path, "r", encoding="utf-8") as f:
            sidecar_text = f.read()
        self.assertIn("---", sidecar_text)
        self.assertIn("zettelkasten_id", sidecar_text)
        self.assertIn("seedr-imported", sidecar_text)

        # Verify status endpoint query
        status = self.pipeline.get_tier_status()
        self.assertEqual(status["destination_count"], 4)
        self.assertEqual(status["last_processed_count"], 2)
        self.assertEqual(status["status"], "completed")

    def test_03_daemon_vaultr_endpoints(self):
        """Verify AnyMD daemon /api/vaultr/status and /api/status include vaultr info."""
        resp = self.client.get("/api/vaultr/status")
        self.assertEqual(resp.status_code, 200)
        data = resp.json()
        self.assertIn("status", data)
        self.assertIn("staging_space", data)
        self.assertIn("destination_space", data)

        status_resp = self.client.get("/api/status")
        self.assertEqual(status_resp.status_code, 200)
        status_data = status_resp.json()
        self.assertIn("vaultr_status", status_data)
