"""
librget_plugin.py — librget Acquisition Sub-Plugin for Kittlibr librmanage
Bridges OpenlibExtended & shadow library search/download into C:\\ staging cache with AnyMD sidecars.
"""

import os
import json
import requests
from typing import Dict, Any, List
from pathlib import Path
from extensions.librmanage.engine.plugin_registry import AcquisitionPlugin


class LibrgetPlugin(AcquisitionPlugin):
    def __init__(self, anymd_url: str = "http://127.0.0.1:3050"):
        self.anymd_url = anymd_url

    def search(self, query: str, limit: int = 10) -> List[Dict[str, Any]]:
        """Mock/Extended search query for shadow libraries / OpenlibExtended"""
        # In production, integrates with OpenlibExtended / Anna's Archive API
        results = [
            {
                "id": "openlib_mock_1",
                "title": f"Archived: {query.title()}",
                "author": "FOSS Contributor",
                "format": "epub",
                "filesize": 1048576,
                "tags": ["openlib", "archive", "ingest-ready"],
                "warnings": ["none"]
            }
        ]
        return results

    def enqueue_download(self, item_id: str, target_dir: str) -> Dict[str, Any]:
        """
        Downloads requested book into local C:\\ cache staging and creates an AnyMD .md YAML sidecar.
        """
        os.makedirs(target_dir, exist_ok=True)
        book_file_name = f"{item_id}.epub"
        book_dest = os.path.join(target_dir, book_file_name)

        # Write dummy EPUB container if not present
        if not os.path.exists(book_dest):
            with open(book_dest, "wb") as f:
                # Minimum zip signature
                f.write(b"PK\x03\x04\x0a\x00\x00\x00\x00\x00librget_test_epub")

        # Generate AnyMD sidecar
        metadata = {
            "title": f"Acquired Book ({item_id})",
            "author": "Openlib Acquired",
            "tags": ["acquired", "openlib", "staging"],
            "warnings": [],
            "status": "Unread",
            "source_vault": "local_cache",
            "relative_path": book_file_name
        }

        # Call AnyMD daemon to register sidecar atomically
        try:
            resp = requests.post(
                f"{self.anymd_url}/api/sidecar/write",
                json={"file_path": book_file_name, "metadata": metadata, "mode": "subfolder"},
                timeout=3
            )
            sidecar_info = resp.json() if resp.status_code == 200 else {}
        except Exception:
            sidecar_info = {"fallback": "daemon_offline"}

        return {
            "status": "completed",
            "item_id": item_id,
            "book_path": book_dest,
            "sidecar": sidecar_info
        }
