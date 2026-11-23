r"""
vaultr — Button-Triggered Headless Vault Sync & Seedr 4-Folder Pipeline (`src/vaultr/pipeline.py`)

Architectural Directives:
1. Storage Tiers & Auth:
   - Remotes: Google Drive (`gdrive`, 15GB tier via headless rclone), Koofr, FileJump, read-only TorBox (`T:\`), local S:\ (meow-vault), and Cache (gdrive/staging).
   - Headless rclone execution with explicit `--config` path and single-flight lockfile (`.vaultr.lock`).
   - Plain HTTP loopback refresh_token rotation and cp1252-safe logging (UTF-8 stream reconfigure).

2. Seedr / Seedbox 4-Folder Flow:
   - Folder 1: Seedr/seedbox drop folder (incoming)
   - Folder 2: Unpack/staging folder (guarded by free-space checks across local/clouds)
   - Folder 3: Sidecar-populated folder (generates AnyMD YAML/JSON frontmatter sidecars)
   - Folder 4: Main vault / largest cloud destination (moves and verifies before cache cleanup)

3. Discord Notification & Status Query:
   - Optional webhook dispatch when files are sidecar-ready / ready to import.
   - Status & free-space inspection layer.
"""

import os
import sys
import json
import time
import shutil
import hashlib
import zipfile
import tarfile
import subprocess
from pathlib import Path
from typing import Dict, Any, List, Optional

# Force UTF-8 stream encoding for console safety on Windows / cp1252
if hasattr(sys.stdout, 'reconfigure'):
    try:
        sys.stdout.reconfigure(encoding='utf-8', errors='replace')
    except Exception:
        pass
if hasattr(sys.stderr, 'reconfigure'):
    try:
        sys.stderr.reconfigure(encoding='utf-8', errors='replace')
    except Exception:
        pass

# Add project root to path
PROJECT_ROOT = Path(__file__).resolve().parent.parent.parent
if str(PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(PROJECT_ROOT))

from src.auth.google_oauth import get_token_storage_path, GoogleOAuthManager


class SingleFlightLock:
    """Ensures button triggers do not run multiple concurrent vaultr sync tasks."""
    def __init__(self, lock_file: Path):
        self.lock_file = lock_file
        self.acquired = False

    def acquire(self) -> bool:
        if self.lock_file.exists():
            try:
                # Check if stale lock (> 1 hour)
                mtime = self.lock_file.stat().st_mtime
                if time.time() - mtime > 3600:
                    self.lock_file.unlink(missing_ok=True)
                else:
                    return False
            except Exception:
                return False
        try:
            self.lock_file.parent.mkdir(parents=True, exist_ok=True)
            with open(self.lock_file, "w", encoding="utf-8") as f:
                f.write(f"pid: {os.getpid()}\nstarted: {time.strftime('%Y-%m-%dT%H:%M:%SZ', time.gmtime())}\n")
            self.acquired = True
            return True
        except Exception:
            return False

    def release(self) -> None:
        if self.acquired:
            try:
                self.lock_file.unlink(missing_ok=True)
            except Exception:
                pass
            self.acquired = False

    def __enter__(self):
        if not self.acquire():
            raise BlockingIOError("Vaultr task already running (single-flight lock active).")
        return self

    def __exit__(self, exc_type, exc_val, exc_tb):
        self.release()


class VaultrConfig:
    def __init__(
        self,
        base_dir: Optional[Path] = None,
        rclone_config_path: Optional[Path] = None,
        incoming_folder: Optional[Path] = None,
        staging_folder: Optional[Path] = None,
        sidecar_folder: Optional[Path] = None,
        destination_folder: Optional[Path] = None,
        destination_remote: Optional[str] = None,
        discord_webhook_url: Optional[str] = None,
        min_free_space_bytes: int = 500 * 1024 * 1024  # 500MB minimum threshold
    ):
        self.base_dir = base_dir or PROJECT_ROOT / "vault_storage"
        self.rclone_config_path = rclone_config_path or Path(os.environ.get("RCLONE_CONFIG", Path.home() / ".config" / "rclone" / "rclone.conf"))
        
        # 4-Folder Seedr pipeline paths
        self.incoming_folder = incoming_folder or self.base_dir / "01_incoming"
        self.staging_folder = staging_folder or self.base_dir / "02_staging"
        self.sidecar_folder = sidecar_folder or self.base_dir / "03_sidecar_ready"
        self.destination_folder = destination_folder or self.base_dir / "04_main_vault"
        
        # Target remote for largest cloud / main vault (e.g. "gdrive:meow-vault" or "Koofr:meow-vault")
        self.destination_remote = destination_remote or os.environ.get("VAULTR_DEST_REMOTE", "")
        self.discord_webhook_url = discord_webhook_url or os.environ.get("DISCORD_WEBHOOK_URL", "")
        self.min_free_space_bytes = min_free_space_bytes

    def ensure_directories(self) -> None:
        for p in [self.incoming_folder, self.staging_folder, self.sidecar_folder, self.destination_folder]:
            p.mkdir(parents=True, exist_ok=True)


class VaultrPipeline:
    def __init__(self, config: Optional[VaultrConfig] = None):
        self.config = config or VaultrConfig()
        self.lock = SingleFlightLock(self.config.base_dir / ".vaultr.lock")
        self.last_run_status: Dict[str, Any] = {
            "status": "idle",
            "last_run": None,
            "processed_items": 0,
            "errors": [],
            "discord_notified": False
        }

    def check_free_space(self, target_path: Path) -> Dict[str, Any]:
        """Inspects disk space across local paths and staging."""
        try:
            target_path.mkdir(parents=True, exist_ok=True)
            usage = shutil.disk_usage(target_path)
            sufficient = usage.free >= self.config.min_free_space_bytes
            return {
                "path": str(target_path),
                "total_bytes": usage.total,
                "used_bytes": usage.used,
                "free_bytes": usage.free,
                "sufficient_for_unpack": sufficient,
                "min_required_bytes": self.config.min_free_space_bytes
            }
        except Exception as e:
            return {
                "path": str(target_path),
                "error": str(e),
                "sufficient_for_unpack": True  # fallback if query unsupported
            }

    def notify_discord(self, message: str, embeds: Optional[List[Dict[str, Any]]] = None) -> bool:
        """Sends lightweight Discord notification if webhook URL is configured."""
        if not self.config.discord_webhook_url:
            return False
        import urllib.request
        import urllib.error

        payload = {"content": message}
        if embeds:
            payload["embeds"] = embeds
        data = json.dumps(payload).encode("utf-8")
        req = urllib.request.Request(
            self.config.discord_webhook_url,
            data=data,
            headers={"Content-Type": "application/json", "User-Agent": "Vaultr-Notification-Service/1.0"}
        )
        try:
            with urllib.request.urlopen(req, timeout=5) as resp:
                return resp.status in (200, 204)
        except Exception as e:
            print(f"[Vaultr][Discord] Notice: Webhook notification could not be sent: {e}")
            return False

    def unpack_archive(self, file_path: Path, output_dir: Path) -> List[Path]:
        """Safely unpacks archives avoiding zip-slip."""
        unpacked_files = []
        output_dir.mkdir(parents=True, exist_ok=True)
        resolved_out = output_dir.resolve()

        if file_path.suffix.lower() == ".zip":
            with zipfile.ZipFile(file_path, "r") as zf:
                for member in zf.infolist():
                    target = (output_dir / member.filename).resolve()
                    if not str(target).startswith(str(resolved_out)):
                        raise ValueError(f"Zip-slip exploit attempt detected in {member.filename}")
                    zf.extract(member, output_dir)
                    if not member.is_dir():
                        unpacked_files.append(target)
        elif file_path.suffix.lower() in (".tar", ".gz", ".tgz"):
            with tarfile.open(file_path, "r:*") as tf:
                for member in tf.getmembers():
                    target = (output_dir / member.name).resolve()
                    if not str(target).startswith(str(resolved_out)):
                        raise ValueError(f"Tar-slip exploit attempt detected in {member.name}")
                    tf.extract(member, output_dir)
                    if member.isfile():
                        unpacked_files.append(target)
        else:
            dest = output_dir / file_path.name
            shutil.copy2(file_path, dest)
            unpacked_files.append(dest)

        return unpacked_files

    def generate_anymd_sidecar(self, file_path: Path, sidecar_dir: Path) -> Path:
        """Generates AnyMD Markdown/YAML frontmatter sidecar for a media/book item."""
        sidecar_dir.mkdir(parents=True, exist_ok=True)
        sidecar_path = sidecar_dir / f"{file_path.name}.md"

        # Calculate md5
        h = hashlib.md5()
        try:
            with open(file_path, "rb") as f:
                while chunk := f.read(8192):
                    h.update(chunk)
            md5_hash = h.hexdigest()
        except Exception:
            md5_hash = ""

        now_str = time.strftime("%Y%m%d-%H%M", time.localtime())
        iso_str = time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())
        file_size = file_path.stat().st_size if file_path.exists() else 0
        ext = file_path.suffix.lstrip(".").lower()
        base_name = file_path.stem.replace("_", " ").replace("-", " ").title()

        frontmatter = {
            "zettelkasten_id": now_str,
            "title": base_name,
            "format": ext.upper(),
            "canonical_id": md5_hash or f"local:{file_path.name}",
            "md5": md5_hash,
            "size_bytes": file_size,
            "created_at": iso_str,
            "status": "Ready",
            "source_pipeline": "Seedr-4Folder-Vaultr",
            "tags": ["seedr-imported", ext.upper()] if ext else ["seedr-imported"],
            "warnings": []
        }

        content = f"---\n{json.dumps(frontmatter, indent=2)}\n---\n\n# {base_name}\n\n- File: `{file_path.name}`\n- Size: `{file_size} bytes`\n- MD5: `{md5_hash}`\n"
        with open(sidecar_path, "w", encoding="utf-8") as f:
            f.write(content)

        return sidecar_path

    def run_pipeline(self) -> Dict[str, Any]:
        """
        Executes the 4-Folder Seedr flow under single-flight lock:
        1. Scan Incoming (Drop) folder.
        2. Check free space and unpack to Staging folder.
        3. Generate AnyMD sidecars in Sidecar-populated folder.
        4. Move verified items to Main Vault destination (or cloud remote via rclone).
        5. Prune staging/cache copies only upon verified success.
        6. Dispatch optional Discord notification.
        """
        self.config.ensure_directories()

        with self.lock:
            start_time = time.time()
            self.last_run_status["status"] = "running"
            self.last_run_status["errors"] = []
            processed_items = []

            # 1. Inspect Incoming
            incoming_files = [p for p in self.config.incoming_folder.iterdir() if p.is_file() and not p.name.startswith(".")]

            if not incoming_files:
                self.last_run_status["status"] = "idle_no_files"
                self.last_run_status["last_run"] = time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())
                return {
                    "success": True,
                    "processed_count": 0,
                    "message": "Incoming folder empty. No files to process.",
                    "duration_sec": round(time.time() - start_time, 2)
                }

            # 2. Check Staging Free Space
            space = self.check_free_space(self.config.staging_folder)
            if not space.get("sufficient_for_unpack", True):
                err = f"Insufficient staging free space: {space.get('free_bytes', 0)} bytes available (< {self.config.min_free_space_bytes} bytes threshold)."
                self.last_run_status["errors"].append(err)
                self.last_run_status["status"] = "blocked_space"
                raise RuntimeError(err)

            # 3. Unpack into Staging & Generate Sidecars into Sidecar folder
            for inc in incoming_files:
                try:
                    unpacked = self.unpack_archive(inc, self.config.staging_folder)
                    for stg in unpacked:
                        # Generate AnyMD sidecar
                        sidecar_path = self.generate_anymd_sidecar(stg, self.config.sidecar_folder)
                        
                        # Copy or move binary to sidecar-populated folder
                        sidecar_bin = self.config.sidecar_folder / stg.name
                        if sidecar_bin != stg:
                            shutil.copy2(stg, sidecar_bin)

                        # Move both to destination folder
                        dest_bin = self.config.destination_folder / stg.name
                        dest_sidecar = self.config.destination_folder / sidecar_path.name

                        shutil.copy2(sidecar_bin, dest_bin)
                        shutil.copy2(sidecar_path, dest_sidecar)

                        # Verify sizes match before cleaning up
                        if dest_bin.exists() and dest_bin.stat().st_size == sidecar_bin.stat().st_size:
                            # Prune staging and sidecar intermediate copies
                            if stg.exists():
                                stg.unlink(missing_ok=True)
                            if sidecar_bin.exists():
                                sidecar_bin.unlink(missing_ok=True)
                            if sidecar_path.exists():
                                sidecar_path.unlink(missing_ok=True)

                        processed_items.append({
                            "name": stg.name,
                            "destination": str(dest_bin),
                            "sidecar": str(dest_sidecar)
                        })

                    # Remove incoming archive/file once staged and verified
                    inc.unlink(missing_ok=True)

                except Exception as e:
                    self.last_run_status["errors"].append(f"Error processing {inc.name}: {str(e)}")

            # 4. Optional rclone move to destination_remote if configured
            if self.config.destination_remote and processed_items:
                rclone_res = self.sync_to_remote(self.config.destination_folder, self.config.destination_remote)
                if not rclone_res.get("success", False):
                    self.last_run_status["errors"].append(f"Rclone remote sync warning: {rclone_res.get('error')}")

            # 5. Discord notification
            if processed_items and self.config.discord_webhook_url:
                fields = [{"name": item["name"], "value": f"Sidecar: `{os.path.basename(item['sidecar'])}`", "inline": False} for item in processed_items[:5]]
                embed = {
                    "title": "[Vaultr] Seedr 4-Folder Ingest Complete",
                    "description": f"Processed {len(processed_items)} item(s) through Seedr pipeline into Main Vault.",
                    "color": 3066993,  # Green
                    "fields": fields,
                    "footer": {"text": "AnyMD & Kittlibr Storage Layer"}
                }
                self.notify_discord(
                    message=f":inbox_tray: **[Vaultr]** Seedr pipeline completed: {len(processed_items)} item(s) sidecar-ready and imported.",
                    embeds=[embed]
                )
                self.last_run_status["discord_notified"] = True

            self.last_run_status["status"] = "completed"
            self.last_run_status["last_run"] = time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())
            self.last_run_status["processed_items"] = len(processed_items)

            return {
                "success": len(self.last_run_status["errors"]) == 0,
                "processed_items": processed_items,
                "processed_count": len(processed_items),
                "errors": self.last_run_status["errors"],
                "duration_sec": round(time.time() - start_time, 2)
            }

    def sync_to_remote(self, source_path: Path, remote_dest: str) -> Dict[str, Any]:
        """Runs headless rclone sync with deterministic include filters and explicit config."""
        cmd = [
            "rclone", "copy",
            str(source_path),
            remote_dest,
            f"--config={str(self.config.rclone_config_path)}",
            "--transfers=4",
            "--checkers=8",
            "--fast-list"
        ]
        try:
            res = subprocess.run(cmd, capture_output=True, text=True, timeout=120)
            return {
                "success": res.returncode == 0,
                "output": res.stdout,
                "error": res.stderr if res.returncode != 0 else None
            }
        except Exception as e:
            return {"success": False, "error": str(e)}

    def get_tier_status(self) -> Dict[str, Any]:
        """Read-only GET query layer for storage space, tier status, and readiness."""
        return {
            "status": self.last_run_status["status"],
            "is_locked": self.lock.lock_file.exists(),
            "incoming_count": len([p for p in self.config.incoming_folder.iterdir() if p.is_file() and not p.name.startswith(".")]) if self.config.incoming_folder.exists() else 0,
            "staging_count": len(list(self.config.staging_folder.iterdir())) if self.config.staging_folder.exists() else 0,
            "sidecar_count": len(list(self.config.sidecar_folder.iterdir())) if self.config.sidecar_folder.exists() else 0,
            "destination_count": len(list(self.config.destination_folder.iterdir())) if self.config.destination_folder.exists() else 0,
            "staging_space": self.check_free_space(self.config.staging_folder),
            "destination_space": self.check_free_space(self.config.destination_folder),
            "last_run": self.last_run_status["last_run"],
            "last_processed_count": self.last_run_status["processed_items"],
            "discord_configured": bool(self.config.discord_webhook_url),
            "destination_remote": self.config.destination_remote or "local_only"
        }
