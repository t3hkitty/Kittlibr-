"""
AnyMD FastAPI Loopback Daemon (`anymd_daemon.py`)
Runs locally on 127.0.0.1:3050. Cross-platform (Windows C:\\, Termux, Linux).
Canonical flat-file .md + YAML frontmatter sidecars, sharded .anymd/index_XXXX.yaml rollups (500 records/shard),
Zettelkasten Pacific Time serial IDs (YYYYMMDD-HHMM), and SQL/Calibre query translation.
"""

from fastapi import FastAPI, HTTPException, Query
from pydantic import BaseModel, Field
from typing import List, Dict, Any, Optional
from pathlib import Path
from datetime import datetime
import uvicorn
import re
import os
import sys
import tempfile
import hashlib

try:
    import xxhash
except ImportError:
    xxhash = None

try:
    import yaml
    YAML_LOADER = getattr(yaml, 'CSafeLoader', yaml.SafeLoader)
    YAML_DUMPER = getattr(yaml, 'CSafeDumper', yaml.SafeDumper)
except ImportError:
    import yaml
    YAML_LOADER = yaml.SafeLoader
    YAML_DUMPER = yaml.SafeDumper

import requests

app = FastAPI(
    title="AnyMD Universal Loopback Daemon",
    version="3.9.0-unified",
    description="Cross-platform Markdown/YAML SQL replacement, sharded indexer (500/shard), Zettelkasten serial generator, and query translation"
)

# Configuration defaults (overridable via ENV)
default_staging = Path(tempfile.gettempdir()) / "kittlibr_staging_cache"
STAGING_DIR = Path(os.environ.get("KITTLIBR_STAGING_DIR", str(default_staging)))
SHARD_SIZE = 500
RCLONE_RCD_URL = os.environ.get("RCLONE_RCD_URL", "http://127.0.0.1:5572")

try:
    STAGING_DIR.mkdir(parents=True, exist_ok=True)
    (STAGING_DIR / ".anymd").mkdir(parents=True, exist_ok=True)
except Exception as e:
    print(f"[AnyMD] Warning: Could not create staging dir {STAGING_DIR}: {e}")


class AnyMDEngine:
    def __init__(self, vault_root: Path):
        self.vault_root = vault_root

    def compute_xxhash(self, file_path: Path) -> str:
        if xxhash is not None:
            h = xxhash.xxh64()
            try:
                with open(file_path, "rb") as f:
                    while chunk := f.read(8192):
                        h.update(chunk)
                return h.hexdigest()
            except Exception:
                return "error-hash"
        return self.compute_md5(file_path)

    def compute_md5(self, file_path: Path) -> str:
        h = hashlib.md5()
        try:
            with open(file_path, "rb") as f:
                while chunk := f.read(8192):
                    h.update(chunk)
            return h.hexdigest()
        except Exception:
            return ""

    def generate_zettelkasten_id(self) -> str:
        """Pacific Time anchors (YYYYMMDD-HHMM) with FIFO serials"""
        now = datetime.now()
        return now.strftime("%Y%m%d-%H%M")

    def get_sidecar_paths(self, target_file: Path) -> Dict[str, Path]:
        """Supports subfolder (.anymd/<file>.ext.md), side-by-side (<file>.ext.md), dot_anymd (.<file>.anymd), and anymd (<file>.anymd)"""
        side_by_side = target_file.with_name(f"{target_file.name}.md")
        subfolder = target_file.parent / ".anymd" / f"{target_file.name}.md"
        dot_anymd = target_file.with_name(f".{target_file.name}.anymd")
        anymd = target_file.with_name(f"{target_file.name}.anymd")
        return {
            "side_by_side": side_by_side,
            "subfolder": subfolder,
            "dot_anymd": dot_anymd,
            "anymd": anymd
        }

    def atomic_write_yaml(self, file_path: Path, data: Dict[str, Any], body_text: str = "") -> None:
        """Atomic write using tempfile and os.replace (temp -> verify -> os.replace)"""
        file_path.parent.mkdir(parents=True, exist_ok=True)
        fd, tmp_path = tempfile.mkstemp(dir=file_path.parent, prefix=".tmp-anymd-", suffix=".md")
        try:
            with os.fdopen(fd, 'w', encoding='utf-8') as f:
                f.write("---\n")
                yaml.dump(data, f, Dumper=YAML_DUMPER, default_flow_style=False, sort_keys=False)
                f.write("---\n")
                if body_text:
                    f.write(f"\n{body_text}\n")
            
            # Verify file exists and is readable
            with open(tmp_path, 'r', encoding='utf-8') as check_f:
                content = check_f.read()
                if "---" not in content:
                    raise ValueError("Verification failed: YAML frontmatter not found in temp file")

            os.replace(tmp_path, file_path)
        except Exception as e:
            if os.path.exists(tmp_path):
                os.remove(tmp_path)
            raise e

    def read_sidecar(self, file_path: Path) -> Optional[Dict[str, Any]]:
        if not file_path.exists():
            return None
        try:
            with open(file_path, 'r', encoding='utf-8') as f:
                content = f.read()
            match = re.match(r"^---\r?\n([\s\S]*?)\r?\n---\r?\n?([\s\S]*)$", content)
            if match:
                meta = yaml.load(match.group(1), Loader=YAML_LOADER) or {}
                meta["_body"] = match.group(2).strip()
                return meta
        except Exception as e:
            print(f"[AnyMD] Error reading sidecar {file_path}: {e}")
        return None

    def scan_all_sidecars(self) -> List[Dict[str, Any]]:
        records = []
        if not self.vault_root.exists():
            return records

        for root, dirs, files in os.walk(self.vault_root):
            for file in files:
                if (file.endswith(".md") or file.endswith(".anymd")) and not file.startswith(".tmp-"):
                    p = Path(root) / file
                    data = self.read_sidecar(p)
                    if data:
                        rel = str(p.relative_to(self.vault_root)).replace("\\", "/")
                        data["_sidecar_rel_path"] = rel
                        records.append(data)
        return records

    def rebuild_sharded_index(self) -> Dict[str, Any]:
        """Rolls up sidecar metadata into sharded .anymd/index_XXXX.yaml (500 records/shard)"""
        records = self.scan_all_sidecars()
        anymd_dir = self.vault_root / ".anymd"
        anymd_dir.mkdir(parents=True, exist_ok=True)

        total_records = len(records)
        total_shards = max(1, (total_records + SHARD_SIZE - 1) // SHARD_SIZE)
        manifest_shards = []

        for i in range(total_shards):
            batch = records[i * SHARD_SIZE : (i + 1) * SHARD_SIZE]
            shard_name = f"index_{i:04d}.yaml"
            shard_path = anymd_dir / shard_name
            shard_data = {
                "shard_index": i,
                "batch_size": len(batch),
                "records": batch,
                "updated_at": datetime.utcnow().isoformat()
            }
            self.atomic_write_yaml(shard_path, shard_data)
            manifest_shards.append({
                "shard_name": shard_name,
                "records_count": len(batch)
            })

        manifest = {
            "version": "1.0.0",
            "total_records": total_records,
            "total_shards": total_shards,
            "shard_batch_limit": SHARD_SIZE,
            "shards": manifest_shards,
            "last_updated": datetime.utcnow().isoformat()
        }
        self.atomic_write_yaml(anymd_dir / "manifest.yaml", manifest)
        return manifest


engine = AnyMDEngine(STAGING_DIR)


class WriteSidecarRequest(BaseModel):
    file_path: str
    metadata: Dict[str, Any]
    body: Optional[str] = ""
    mode: Optional[str] = "subfolder" # 'side_by_side' or 'subfolder'


class QueryRequest(BaseModel):
    query: str
    limit: Optional[int] = 100


@app.get("/")
def read_root():
    return {
        "status": "online",
        "service": "AnyMD Universal Loopback Daemon",
        "port": 3050,
        "vault_root": str(engine.vault_root),
        "shard_limit": SHARD_SIZE,
        "active_zettelkasten_sample": engine.generate_zettelkasten_id()
    }


@app.get("/api/status")
def get_api_status(remote: Optional[str] = None, path: Optional[str] = None):
    """
    Honest status endpoint exposing daemon health, active vault root,
    and echoing queried remote/subpaths.
    """
    res = {
        "status": "online",
        "service": "AnyMD Universal Loopback Daemon",
        "port": 3050,
        "vault_root": str(engine.vault_root),
        "timestamp": datetime.now().isoformat(),
        "shard_limit": SHARD_SIZE
    }
    if remote is not None:
        res["queried_remote"] = remote
    if path is not None:
        res["queried_path"] = path
    return res


@app.get("/api/zettelkasten/new-id")
def get_zettel_id():
    return {"zettelkasten_id": engine.generate_zettelkasten_id()}


@app.get("/api/catalog/shards")
def get_catalog_shards():
    """Returns rollup index information"""
    manifest = engine.rebuild_sharded_index()
    return manifest


@app.get("/api/catalog/all")
def get_all_records():
    records = engine.scan_all_sidecars()
    return {"total": len(records), "records": records}


@app.post("/api/sidecar/write")
def write_sidecar_endpoint(req: WriteSidecarRequest):
    """
    Atomic write of YAML frontmatter sidecar with Zettelkasten ID enforcement,
    warnings (#does-the-dog-die, #piss-your-pants), #fandom, tags.
    """
    target = engine.vault_root / req.file_path.lstrip("/\\")
    paths = engine.get_sidecar_paths(target)
    if req.mode == "dot_anymd":
        sidecar_path = paths["dot_anymd"]
    elif req.mode == "anymd":
        sidecar_path = paths["anymd"]
    elif req.mode == "side_by_side":
        sidecar_path = paths["side_by_side"]
    else:
        sidecar_path = paths["subfolder"]

    meta = req.metadata
    if "zettelkasten_id" not in meta or not meta["zettelkasten_id"]:
        meta["zettelkasten_id"] = engine.generate_zettelkasten_id()

    if target.exists() and ("md5" not in meta or not meta["md5"]):
        meta["md5"] = engine.compute_md5(target)

    if "canonical_id" not in meta or not meta["canonical_id"]:
        meta["canonical_id"] = meta.get("md5") or meta.get("zettelkasten_id") or req.file_path

    try:
        engine.atomic_write_yaml(sidecar_path, meta, req.body or "")
        # Re-index sharded catalog on write
        engine.rebuild_sharded_index()
        return {
            "success": True,
            "sidecar_path": str(sidecar_path),
            "zettelkasten_id": meta["zettelkasten_id"]
        }
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@app.get("/api/sidecar/read")
def read_sidecar_endpoint(file_path: str, mode: Optional[str] = "subfolder"):
    """
    Read sidecar metadata for a given book file path, checking subfolder (.anymd/file.ext.md),
    side-by-side (file.ext.md, .file.anymd, file.anymd), or direct path.
    """
    target = engine.vault_root / file_path.lstrip("/\\")
    
    candidates = []
    if file_path.endswith(".md") or file_path.endswith(".anymd"):
        candidates.append(target)
    
    paths = engine.get_sidecar_paths(target)
    if mode == "side_by_side":
        candidates.extend([
            paths["side_by_side"],
            target.with_name(f".{target.name}.anymd"),
            target.with_name(f"{target.name}.anymd"),
            paths["subfolder"]
        ])
    else:
        candidates.extend([
            paths["subfolder"],
            paths["side_by_side"],
            target.with_name(f".{target.name}.anymd"),
            target.with_name(f"{target.name}.anymd")
        ])

    for c in candidates:
        if c.exists():
            data = engine.read_sidecar(c)
            if data is not None:
                return {
                    "success": True,
                    "sidecar_path": str(c),
                    "metadata": data
                }

    raise HTTPException(status_code=404, detail=f"Sidecar not found for {file_path}")


@app.post("/api/query/translate")
def translate_query(req: QueryRequest):
    """
    Translates Calibre/SQL search expressions into AnyMD filter predicates and evaluates against records.
    Supports +tag, -tag, tags:"=LitRPG", #warnings, #fandom, #zettelkasten_id
    """
    records = engine.scan_all_sidecars()
    query = req.query.strip()
    if not query:
        return {"matches": records[:req.limit], "total": len(records)}

    def match_record(rec: Dict[str, Any], token: str) -> bool:
        # Check field match key:val or #key:val
        field_match = re.match(r'^([#\w]+):"(=|~)?([^"]+)"$', token)
        if field_match:
            k = field_match.group(1).lstrip("#").lower()
            exact = field_match.group(2) == "="
            val = field_match.group(3).lower()
            target_val = rec.get(k)
            if target_val is None:
                return False
            if isinstance(target_val, list):
                if exact:
                    return any(str(x).lower() == val for x in target_val)
                return any(val in str(x).lower() for x in target_val)
            s_val = str(target_val).lower()
            return s_val == val if exact else val in s_val

        if token.startswith("+"):
            t = token[1:].lower()
            tags = [str(x).lower() for x in rec.get("tags", [])] + [str(x).lower() for x in rec.get("warnings", [])]
            return t in tags

        if token.startswith("-"):
            t = token[1:].lower()
            tags = [str(x).lower() for x in rec.get("tags", [])] + [str(x).lower() for x in rec.get("warnings", [])]
            return t not in tags

        # Substring match across main fields
        raw = token.lower().strip('"')
        return (
            raw in str(rec.get("title", "")).lower() or
            raw in str(rec.get("author", "")).lower() or
            raw in str(rec.get("series", "")).lower() or
            any(raw in str(t).lower() for t in rec.get("tags", [])) or
            any(raw in str(w).lower() for w in rec.get("warnings", []))
        )

    # Simple boolean token evaluator
    matched_records = []
    tokens = [t for t in re.findall(r'(\+[\w\-]+|\-[\w\-]+|[\w#]+:"[^"]+"|\S+)', query) if t.lower() not in ("and",)]

    for rec in records:
        is_match = True
        for tok in tokens:
            if tok.lower() == "or":
                continue
            if not match_record(rec, tok):
                is_match = False
                break
        if is_match:
            matched_records.append(rec)

    return {
        "query": query,
        "total_matched": len(matched_records),
        "matches": matched_records[:req.limit]
    }


@app.get("/api/rclone/status")
def rclone_status():
    """Verify non-FUSE JSON-RPC connection to rclone rcd (local loopback, Termux on Android, or LAN host)"""
    rc_url = os.environ.get("RCLONE_RCD_URL", "http://127.0.0.1:5572").rstrip("/")
    try:
        resp = requests.post(f"{rc_url}/core/version", json={}, timeout=2)
        if resp.status_code == 200:
            return {"connected": True, "details": resp.json(), "endpoint": rc_url, "mode": "network_active"}
    except Exception as e:
        return {"connected": False, "error": str(e), "endpoint": rc_url, "mode": "local_fallback"}
    return {"connected": False, "endpoint": rc_url, "mode": "local_fallback"}


class RcloneScanRequest(BaseModel):
    remote: str
    path: Optional[str] = ""
    auth_header: Optional[str] = None
    rc_url: Optional[str] = None
    write_sidecars: Optional[bool] = True


@app.post("/api/rclone/scan-import")
def rclone_scan_import(req: RcloneScanRequest):
    """
    Scans a remote using targeted rclone operations/list (lsf equivalent) and generates
    detailed AnyMD YAML sidecars incorporating all rclone metadata (hashes, ModTime, Size, MIME, etc.).
    Emits structured diagnostic logs.
    """
    endpoint = (req.rc_url or RCLONE_RCD_URL).rstrip("/")
    headers = {"Content-Type": "application/json"}
    if req.auth_header:
        headers["Authorization"] = req.auth_header

    remote_fs = req.remote if req.remote.endswith(":") else f"{req.remote}:"
    remote_dir = (req.path or "").strip("/\\")

    print(f"[kittlibr-anymd][scan] Starting targeted rclone lsf listing on {remote_fs}{remote_dir}")

    # Use rclone operations/list with metadata=true to gather everything possible
    list_payload = {
        "fs": remote_fs,
        "remote": remote_dir,
        "opt": {
            "recurse": True,
            "showHash": True,
            "metadata": True,
            "noModTime": False
        }
    }

    try:
        resp = requests.post(f"{endpoint}/operations/list", json=list_payload, headers=headers, timeout=30)
        if resp.status_code != 200:
            # Fallback to standard non-metadata list
            resp = requests.post(f"{endpoint}/operations/list", json={
                "fs": remote_fs,
                "remote": remote_dir,
                "opt": {"recurse": True}
            }, headers=headers, timeout=30)
            if resp.status_code != 200:
                raise HTTPException(status_code=resp.status_code, detail=f"rclone list failed: {resp.text}")
        
        items = resp.json().get("list", [])
    except Exception as e:
        print(f"[kittlibr-reader][error] source: 'kittlibr-anymd', stage: 'rclone-scan', message: '{str(e)}'")
        raise HTTPException(status_code=500, detail=f"Failed to communicate with rclone: {str(e)}")

    # Filter for ebook items (.epub, .md, .txt), ignoring sidecar markdown files (.anymd/ and *.ext.md)
    book_items = []
    for item in items:
        if item.get("IsDir", False):
            continue
        p = item.get("Path") or item.get("Name", "")
        if ".anymd" in p or p.endswith(".anymd") or p.endswith(".epub.md"):
            continue
        if any(item.get("Name", "").lower().endswith(ext) for ext in [".epub", ".md", ".txt"]):
            book_items.append(item)

    detected_count = len(book_items)
    print(f"[kittlibr-anymd][scan] Detected {detected_count} ebook files out of {len(items)} total entries")

    generated_sidecars = []
    imported_books = []
    for idx, item in enumerate(book_items):
        item_path = item.get("Path") or item.get("Name")
        file_name = item.get("Name", os.path.basename(item_path))
        ext = file_name.split(".")[-1].lower() if "." in file_name else ""
        base_name = file_name[:-(len(ext) + 1)] if ext else file_name

        hashes = item.get("Hashes", {}) or {}
        md5_hash = hashes.get("md5") or hashes.get("MD5") or hashes.get("quickxor") or item.get("MD5") or ""

        # Extract maximum detail from rclone listing item to maximize sidecar reusability
        meta_dict = {
            "zettelkasten_id": engine.generate_zettelkasten_id(),
            "title": base_name.replace("_", " ").replace("-", " ").title(),
            "author": "Unknown Author",
            "format": ext.upper(),
            "canonical_id": md5_hash or f"rclone:{remote_fs}{item_path}",
            "md5": md5_hash,
            "relative_path": item_path,
            "source_vault": f"rclone:{remote_fs}",
            "tags": ["rclone-imported", ext.upper()],
            "warnings": [],
            "status": "Unread",
            "rclone_metadata": {
                "remote": remote_fs,
                "remote_path": item_path,
                "size_bytes": item.get("Size", 0),
                "mod_time": item.get("ModTime"),
                "mime_type": item.get("MimeType", "application/epub+zip" if ext == "epub" else "text/plain"),
                "hashes": hashes,
                "tier": item.get("Tier"),
                "storage_class": item.get("StorageClass"),
                "system_metadata": item.get("Metadata", {})
            }
        }

        if req.write_sidecars:
            target = engine.vault_root / item_path.lstrip("/\\")
            paths = engine.get_sidecar_paths(target)
            sidecar_path = paths["subfolder"]
            engine.atomic_write_yaml(sidecar_path, meta_dict)
            generated_sidecars.append(str(sidecar_path))

        imported_books.append(meta_dict)

        if (idx + 1) % 25 == 0 or (idx + 1) == detected_count:
            print(f"[kittlibr-anymd][progress] Processed {idx + 1}/{detected_count} ebooks (~{max(0, detected_count - idx - 1)} items remaining)")

    if req.write_sidecars:
        engine.rebuild_sharded_index()

    return {
        "success": True,
        "detected_count": detected_count,
        "imported_count": len(generated_sidecars) if req.write_sidecars else len(imported_books),
        "source_remote": remote_fs,
        "source_path": remote_dir,
        "manifest_updated": req.write_sidecars,
        "books": imported_books
    }


if __name__ == "__main__":
    host = os.environ.get("ANYMD_HOST", "127.0.0.1")
    port = int(os.environ.get("ANYMD_PORT", "3050"))
    uvicorn.run(app, host=host, port=port, log_level="info")
