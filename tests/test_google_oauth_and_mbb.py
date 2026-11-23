"""
Unit and integration tests for Google OAuth loopback manager and MBB telemetry ingestion.
"""

import unittest
import json
import tempfile
import os
import time
from pathlib import Path
from fastapi.testclient import TestClient

from src.auth.google_oauth import (
    GoogleOAuthManager,
    SCOPE_PRESETS,
    resolve_scopes
)
from src.daemon.anymd_daemon import app, engine


class TestGoogleOAuthAndMBB(unittest.TestCase):
    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory()
        self.token_file = Path(self.temp_dir.name) / "test_oauth_token.json"
        self.manager = GoogleOAuthManager(
            client_id="test-client-id",
            client_secret="test-client-secret",
            token_path=self.token_file,
            port=8099
        )
        self.client = TestClient(app)

    def tearDown(self):
        self.temp_dir.cleanup()

    def test_01_scope_presets_resolution(self):
        """Verify scope presets resolve correctly and without duplicates."""
        base_scopes = resolve_scopes(["base_vault"])
        self.assertIn("https://www.googleapis.com/auth/drive.file", base_scopes)
        self.assertIn("https://www.googleapis.com/auth/drive", base_scopes)

        all_scopes = resolve_scopes(["base_vault", "mbb_fitness", "mbb_timeline", "mbb_contacts"])
        self.assertIn("https://www.googleapis.com/auth/fitness.activity.read", all_scopes)
        self.assertIn("https://www.googleapis.com/auth/userlocation.reporting.read", all_scopes)
        self.assertIn("https://www.googleapis.com/auth/contacts.readonly", all_scopes)
        self.assertEqual(len(all_scopes), len(set(all_scopes)), "Scopes must be deduplicated")

    def test_02_auth_url_construction(self):
        """Verify auth URL constructs desktop app 127.0.0.1 redirect."""
        scopes = resolve_scopes(["base_vault", "mbb_fitness"])
        url = self.manager.get_auth_url(scopes)
        self.assertIn("accounts.google.com/o/oauth2/v2/auth", url)
        self.assertIn("redirect_uri=http%3A%2F%2F127.0.0.1%3A8099%2F", url)
        self.assertIn("access_type=offline", url)
        self.assertIn("prompt=consent", url)

    def test_03_token_bundle_save_and_rclone_format(self):
        """Test token bundle saving, status audit, and rclone.conf single-line JSON formatting."""
        raw_token_data = {
            "access_token": "ya29.test_access_token_12345",
            "token_type": "Bearer",
            "refresh_token": "1//test_refresh_token_67890",
            "expires_in": 3600,
            "scope": "https://www.googleapis.com/auth/drive.file https://www.googleapis.com/auth/fitness.activity.read https://www.googleapis.com/auth/fitness.body.read https://www.googleapis.com/auth/fitness.location.read"
        }

        self.manager.save_token_bundle(raw_token_data)
        self.assertTrue(self.token_file.exists())

        loaded = self.manager.load_token_bundle()
        self.assertEqual(loaded["access_token"], raw_token_data["access_token"])
        self.assertEqual(loaded["refresh_token"], raw_token_data["refresh_token"])

        # Check rclone config string
        rclone_json_str = self.manager.get_rclone_token_json()
        parsed_rclone = json.loads(rclone_json_str)
        self.assertEqual(parsed_rclone["access_token"], raw_token_data["access_token"])
        self.assertEqual(parsed_rclone["refresh_token"], raw_token_data["refresh_token"])
        self.assertEqual(parsed_rclone["token_type"], "Bearer")

        # Check scope status audit
        status = self.manager.get_scope_status()
        self.assertTrue(status["has_token"])
        self.assertTrue(status["presets"]["base_vault"]["authorized"] or status["presets"]["mbb_fitness"]["authorized"])

    def test_04_daemon_oauth_status_endpoints(self):
        """Verify daemon /api/status and /api/auth/google/status endpoints."""
        resp = self.client.get("/api/status")
        self.assertEqual(resp.status_code, 200)
        data = resp.json()
        self.assertIn("oauth_status", data)

        auth_resp = self.client.get("/api/auth/google/status")
        self.assertEqual(auth_resp.status_code, 200)
        auth_data = auth_resp.json()
        self.assertIn("presets", auth_data)
        self.assertIn("base_vault", auth_data["presets"])
        self.assertIn("mbb_fitness", auth_data["presets"])

    def test_05_mbb_telemetry_ingestion_direct(self):
        """Verify /api/mbb/telemetry/ingest writes canonical AnyMD Markdown and YAML without webhooks."""
        payload = {
            "module": "fitness",
            "subpath": "telemetry_test",
            "data": {
                "daily_steps": 8420,
                "active_minutes": 45,
                "resting_heart_rate": 58,
                "notes": "Evening walk around neighborhood"
            }
        }

        resp = self.client.post("/api/mbb/telemetry/ingest", json=payload)
        self.assertEqual(resp.status_code, 200)
        res_data = resp.json()
        self.assertTrue(res_data["success"])
        self.assertEqual(res_data["module"], "fitness")

        saved_path = Path(res_data["saved_path"])
        self.assertTrue(saved_path.exists())

        # Verify Markdown body and YAML frontmatter
        content = saved_path.read_text(encoding="utf-8")
        self.assertIn("---", content)
        self.assertIn("daily_steps: 8420", content)
        self.assertIn("MBB Telemetry Entry (FITNESS)", content)


if __name__ == "__main__":
    unittest.main()
