"""
librmanage_service.py — Localhost HTTP/JSON-RPC Service (127.0.0.1:5820)
Decouples Calibre Python engine from AGPL-3.0 koodo-reader UI shell.
Delegates canonical record persistence and queries to AnyMD (:3050) with direct-disk YAML fallback.
Exposes hooks for librget (OpenlibExtended) and librscrape (WebToEpub/lncrawl).
"""

import json
import os
import sys
import tempfile
from pathlib import Path
from http.server import HTTPServer, BaseHTTPRequestHandler
import urllib.request
import urllib.error

# Ensure src path is available
SRC_PATH = Path(__file__).resolve().parent.parent.parent.parent
if str(SRC_PATH) not in sys.path:
    sys.path.insert(0, str(SRC_PATH))
ENGINE_DIR = Path(__file__).parent
if str(ENGINE_DIR) not in sys.path:
    sys.path.insert(0, str(ENGINE_DIR))

from metadata_bridge import MetadataBridge
from convert_worker import ConvertWorker
from query_engine import QueryEngine
from plugin_registry import REGISTRY
from extensions.librget.librget_plugin import LibrgetPlugin
from extensions.librscrape.librscrape_plugin import LibrscrapePlugin

# Register sub-plugins
librget = LibrgetPlugin()
librscrape = LibrscrapePlugin()
REGISTRY.register_acquisition_plugin("librget", librget)
REGISTRY.register_scraper_plugin("librscrape", librscrape)

STAGING_DIR = os.environ.get("KITTLIBR_STAGING_DIR", "/home/antigravity/kittlibr_host/staging_cache")
ANYMD_URL = os.environ.get("ANYMD_DAEMON_URL", "http://127.0.0.1:3050")

bridge = MetadataBridge(cache_staging_dir=STAGING_DIR)
converter = ConvertWorker(cache_staging_dir=STAGING_DIR)


class LibrmanageHandler(BaseHTTPRequestHandler):
    def _send_json(self, data, status=200):
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Headers", "Content-Type")
        self.end_headers()
        self.wfile.write(json.dumps(data).encode("utf-8"))

    def do_OPTIONS(self):
        self.send_response(200)
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "POST, GET, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Content-Type")
        self.end_headers()

    def do_GET(self):
        if self.path == "/" or self.path == "/api/status":
            self._send_json({
                "status": "online",
                "service": "Kittlibr librmanage Calibre Engine",
                "port": 5820,
                "anymd_daemon": ANYMD_URL,
                "registered_subplugins": {
                    "acquisition": list(REGISTRY.acquisition_plugins.keys()),
                    "scrapers": list(REGISTRY.scraper_plugins.keys())
                }
            })
        elif self.path == "/api/catalog":
            # Delegate to AnyMD daemon with disk fallback
            catalog = self._fetch_catalog_from_anymd_or_disk()
            self._send_json({"success": True, "catalog": catalog})
        else:
            self._send_json({"error": "Not Found"}, status=404)

    def do_POST(self):
        length = int(self.headers.get("Content-Length", 0))
        body = json.loads(self.rfile.read(length).decode("utf-8")) if length > 0 else {}

        if self.path == "/api/metadata/extract":
            file_path = body.get("filePath")
            meta = bridge.extract_embedded_metadata(file_path)
            self._send_json({"success": True, "metadata": meta})

        elif self.path == "/api/metadata/save":
            book_data = body.get("book", {})
            sidecar_path = body.get("sidecarPath")
            embed = body.get("embedBinary", False)

            # 1. Forward to AnyMD daemon (:3050)
            saved_via_anymd = False
            rel_path = book_data.get("relative_path") or os.path.basename(sidecar_path or "book.epub")
            try:
                post_data = json.dumps({
                    "file_path": rel_path,
                    "metadata": book_data,
                    "mode": "subfolder"
                }).encode("utf-8")
                req = urllib.request.Request(
                    f"{ANYMD_URL}/api/sidecar/write",
                    data=post_data,
                    headers={"Content-Type": "application/json"}
                )
                with urllib.request.urlopen(req, timeout=3) as resp:
                    if resp.status == 200:
                        saved_via_anymd = True
            except Exception as e:
                print(f"[librmanage] AnyMD daemon unreachable ({e}), using direct-disk fallback")

            # 2. Direct-disk atomic write fallback if daemon offline or if sidecar_path explicitly supplied
            if not saved_via_anymd and sidecar_path:
                ok = bridge.write_metadata_to_yaml_sidecar(book_data, sidecar_path)
            else:
                ok = saved_via_anymd

            if embed and book_data.get("filePath"):
                bridge.embed_metadata_into_book(book_data["filePath"], book_data)

            self._send_json({"success": ok, "persisted_via": "anymd_daemon" if saved_via_anymd else "disk_fallback"})

        elif self.path == "/api/convert":
            in_file = body.get("inputFile")
            res = converter.convert_to_epub3(in_file)
            self._send_json(res)

        elif self.path == "/api/query/filter":
            query_str = body.get("query", "")
            catalog = body.get("catalog") or self._fetch_catalog_from_anymd_or_disk()

            # First try AnyMD query engine
            filtered = [b for b in catalog if QueryEngine.evaluate_expression(b, query_str)]
            self._send_json({"success": True, "results": filtered, "total": len(filtered)})

        elif self.path == "/api/plugins/librget/search":
            q = body.get("query", "")
            res = REGISTRY.acquisition_plugins["librget"].search(q)
            self._send_json({"success": True, "results": res})

        elif self.path == "/api/plugins/librget/download":
            item_id = body.get("itemId", "openlib_1")
            res = REGISTRY.acquisition_plugins["librget"].enqueue_download(item_id, STAGING_DIR)
            self._send_json({"success": True, "download": res})

        elif self.path == "/api/plugins/librscrape/scrape":
            url = body.get("novelUrl", "")
            res = REGISTRY.scraper_plugins["librscrape"].scrape_and_package(url, STAGING_DIR)
            self._send_json({"success": True, "scraped": res})

        else:
            self._send_json({"error": "Endpoint not found"}, status=404)

    def _fetch_catalog_from_anymd_or_disk(self):
        try:
            with urllib.request.urlopen(f"{ANYMD_URL}/api/catalog/all", timeout=2) as resp:
                data = json.loads(resp.read().decode("utf-8"))
                return data.get("records", [])
        except Exception:
            # Fallback: scan local staging folder
            catalog = []
            anymd_dir = Path(STAGING_DIR) / ".anymd"
            if anymd_dir.exists():
                for p in anymd_dir.glob("*.md"):
                    if not p.name.startswith(".tmp-"):
                        try:
                            lines = p.read_text(encoding="utf-8").splitlines()
                            # Basic extraction
                            catalog.append({"relative_path": p.stem, "title": p.stem})
                        except Exception:
                            pass
            return catalog


def run_server(port=None):
    host = os.environ.get("LIBRMANAGE_HOST", "127.0.0.1")
    port = int(os.environ.get("LIBRMANAGE_PORT", port or 5820))
    server = HTTPServer((host, port), LibrmanageHandler)
    print(f"🐾 Kittlibr librmanage Calibre Engine running at http://{host}:{port}")
    server.serve_forever()


if __name__ == "__main__":
    run_server()
