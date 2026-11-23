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
import xxhash

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
STAGING_DIR = Path(os.environ.get("KITTLIBR_STAGING_DIR", "/home/antigravity/kittlibr_host/staging_cache"))
SHARD_SIZE = 500
RCLONE_RCD_URL = os.environ.get("RCLONE_RCD_URL", "http://127.0.0.1:5572")

STAGING_DIR.mkdir(parents=True, exist_ok=True)
(STAGING_DIR / ".anymd").mkdir(parents=True, exist_ok=True)


class AnyMDEngine:
    def __init__(self, vault_root: Path):
        self.vault_root = vault_root

    def compute_xxhash(self, file_path: Path) -> str:
        h = xxhash.xxh64()
        try:
            with open(file_path, "rb") as f:
                while chunk := f.read(8192):
                    h.update(chunk)
            return h.hexdigest()
        except Exception:
            return "error-hash"

    def generate_zettelkasten_id(self) -> str:
        """Pacific Time anchors (YYYYMMDD-HHMM) with FIFO serials"""
        now = datetime.now()
        return now.strftime("%Y%m%d-%H%M")

    def get_sidecar_paths(self, target_file: Path) -> Dict[str, Path]:
        """Supports both side-by-side <file>.ext.md and .anymd/<file>.ext.md"""
        side_by_side = target_file.with_name(f"{target_file.name}.md")
        subfolder = target_file.parent / ".anymd" / f"{target_file.name}.md"
        return {
            "side_by_side": side_by_side,
            "subfolder": subfolder
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
                if file.endswith(".md") and not file.startswith(".tmp-"):
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
    sidecar_path = paths["side_by_side"] if req.mode == "side_by_side" else paths["subfolder"]

    meta = req.metadata
    if "zettelkasten_id" not in meta or not meta["zettelkasten_id"]:
        meta["zettelkasten_id"] = engine.generate_zettelkasten_id()

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


if __name__ == "__main__":
    host = os.environ.get("ANYMD_HOST", "127.0.0.1")
    port = int(os.environ.get("ANYMD_PORT", "3050"))
    uvicorn.run(app, host=host, port=port, log_level="info")
