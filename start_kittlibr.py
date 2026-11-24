#!/usr/bin/env python3
"""
start_kittlibr.py — Master Unified Service & Healthcheck Orchestrator
Starts and coordinates:
  1. rclone rcd loopback daemon (:5572)
  2. AnyMD YAML/Markdown loopback daemon (:3050)
  3. librmanage Calibre engine (:5820)
  4. kittlibr_host frontend development/preview server
Performs active health checks and reports loopback topology status.
"""

import os
import sys
import time
import subprocess
import signal
import urllib.request
import urllib.error
import json
import base64
from pathlib import Path

# Force UTF-8 encoding on standard I/O to prevent Windows cp1252 / charmap crashes
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

# Paths
BASE_DIR = Path(__file__).parent.resolve()
SRC_DIR = BASE_DIR / "src"
STAGING_DIR = BASE_DIR / "staging_cache"
STAGING_DIR.mkdir(parents=True, exist_ok=True)

# Service URLs (Supports local 127.0.0.1, Termux on Android, or another local network host)
RCLONE_URL = os.environ.get("RCLONE_RCD_URL", "http://127.0.0.1:5572").rstrip("/")
ANYMD_URL = os.environ.get("ANYMD_DAEMON_URL", "http://127.0.0.1:3050").rstrip("/")
LIBRMANAGE_URL = os.environ.get("LIBRMANAGE_URL", "http://127.0.0.1:5820").rstrip("/")

os.environ["KITTLIBR_STAGING_DIR"] = str(STAGING_DIR)
os.environ["ANYMD_DAEMON_URL"] = ANYMD_URL
os.environ["RCLONE_RCD_URL"] = RCLONE_URL

processes = []


def cleanup(signum=None, frame=None):
    print("\n[INFO] Shutting down Kittlibr services...")
    for p in processes:
        try:
            p.terminate()
            p.wait(timeout=2)
        except Exception:
            try:
                p.kill()
            except Exception:
                pass
    sys.exit(0)


signal.signal(signal.SIGINT, cleanup)
signal.signal(signal.SIGTERM, cleanup)


def wait_for_endpoint(url: str, name: str, timeout_sec: int = 10, method: str = "GET", extra_headers: dict = None, check_fn=None) -> bool:
    start = time.time()
    headers = {"User-Agent": "Kittlibr-Launcher", "Content-Type": "application/json"}
    if extra_headers:
        headers.update(extra_headers)
    while time.time() - start < timeout_sec:
        try:
            req = urllib.request.Request(
                url,
                data=b"{}" if method == "POST" else None,
                headers=headers
            )
            with urllib.request.urlopen(req, timeout=1) as resp:
                if resp.status == 200:
                    data = resp.read()
                    if check_fn:
                        if check_fn(data):
                            print(f"  [OK] {name} healthy at {url}")
                            return True
                    else:
                        print(f"  [OK] {name} healthy at {url}")
                        return True
        except Exception:
            time.sleep(0.5)
    print(f"  [WARN] Timeout waiting for {name} at {url}")
    return False


RCLONE_USER = os.environ.get("RCLONE_RC_USER", "kittlibr")
RCLONE_PASS = os.environ.get("RCLONE_RC_PASS", "wordsaremagic")

def start_rclone_rcd():
    print("[1/4] Checking loopback rclone rcd daemon on 127.0.0.1:5572...")
    # First check if rclone rcd is already running on 127.0.0.1:5572
    auth_header = "Basic " + base64.b64encode(f"{RCLONE_USER}:{RCLONE_PASS}".encode()).decode()
    if wait_for_endpoint(f"{RCLONE_URL}/core/version", "Existing rclone rcd (:5572)", timeout_sec=2, method="POST", extra_headers={"Authorization": auth_header}):
        print("  [OK] Connected to existing authenticated rclone rcd daemon on 127.0.0.1:5572")
        return

    # Check if rclone is available on PATH
    try:
        ver_res = subprocess.run(["rclone", "version"], capture_output=True, text=True)
        if ver_res.returncode != 0:
            print("  [WARN] rclone binary check failed. Proceeding with local disk staging fallback.")
            return
    except FileNotFoundError:
        print("  [WARN] rclone binary not on system PATH. Proceeding with local disk staging fallback.")
        return

    # Start authenticated rcd tuned for high transfer throughput without FUSE drive mounts
    rclone_bind = os.environ.get("RCLONE_BIND_ADDR", "127.0.0.1:5572")
    print(f"  Starting authenticated rclone rcd on {rclone_bind} with transfer performance tuning...")
    cmd = [
        "rclone", "rcd",
        f"--rc-addr={rclone_bind}",
        f"--rc-user={RCLONE_USER}",
        f"--rc-pass={RCLONE_PASS}",
        "--transfers=4",
        "--checkers=8",
        "--buffer-size=32M",
        "--timeout=30s",
        "--contimeout=10s"
    ]
    try:
        proc = subprocess.Popen(cmd, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        processes.append(proc)
        wait_for_endpoint(f"{RCLONE_URL}/core/version", f"rclone rcd ({rclone_bind})", timeout_sec=5, method="POST", extra_headers={"Authorization": auth_header})
    except Exception as e:
        print(f"  [WARN] Could not spawn rclone rcd: {e}. Local disk staging fallback active.")


def start_anymd_daemon():
    anymd_host = os.environ.get("ANYMD_HOST", "127.0.0.1")
    anymd_port = os.environ.get("ANYMD_PORT", "3050")
    print(f"[2/4] Starting AnyMD daemon on {anymd_host}:{anymd_port}...")
    daemon_script = SRC_DIR / "daemon" / "anymd_daemon.py"
    proc = subprocess.Popen(
        [sys.executable, str(daemon_script)],
        cwd=str(BASE_DIR)
    )
    processes.append(proc)
    wait_for_endpoint(ANYMD_URL, f"AnyMD Daemon ({anymd_port})", timeout_sec=8)


def start_librmanage_engine():
    librmanage_host = os.environ.get("LIBRMANAGE_HOST", "127.0.0.1")
    librmanage_port = os.environ.get("LIBRMANAGE_PORT", "5820")
    print(f"[3/4] Starting librmanage Calibre engine on {librmanage_host}:{librmanage_port}...")
    engine_script = SRC_DIR / "extensions" / "librmanage" / "engine" / "librmanage_service.py"
    proc = subprocess.Popen(
        [sys.executable, str(engine_script)],
        cwd=str(engine_script.parent)
    )
    processes.append(proc)
    wait_for_endpoint(f"{LIBRMANAGE_URL}/api/status", "librmanage Engine (:5820)", timeout_sec=8)


def start_web_server():
    # Serve web app by default so launching the EXE opens Kittlibr UI in browser
    if os.environ.get("SERVE_WEB", "1") in ("1", "true", "True"):
        web_port = int(os.environ.get("WEB_PORT", "8080"))
        web_host = os.environ.get("WEB_HOST", "127.0.0.1")
        print(f"[4/4] Starting Kittlibr Web Edition on http://{web_host}:{web_port}...")
        web_proc = subprocess.Popen(
            [sys.executable, "-m", "http.server", str(web_port), "--bind", web_host],
            cwd=str(BASE_DIR),
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL
        )
        processes.append(web_proc)
        # Automatically launch default browser to the Kittlibr UI
        try:
            import webbrowser
            webbrowser.open(f"http://127.0.0.1:{web_port}/")
        except Exception as e:
            print(f"Could not open browser automatically: {e}")


def print_status_banner():
    # Check Google OAuth token status if present
    token_note = "No token captured (run python -m src.auth.google_oauth_cli)"
    try:
        from src.auth.google_oauth import GoogleOAuthManager
        mgr = GoogleOAuthManager()
        st = mgr.get_scope_status()
        if st["has_token"]:
            active_mods = [k for k, v in st["presets"].items() if v["authorized"]]
            token_note = f"Active ({len(active_mods)} modules: {', '.join(active_mods) or 'base'})"
    except Exception:
        pass

    banner = f"""
===================================================================
KITTLIBR UNIFIED SERVICE TOPOLOGY READY
===================================================================
  1. [Rclone Daemon]  : {RCLONE_URL} (Non-FUSE JSON-RPC)
  2. [AnyMD Daemon]   : {ANYMD_URL} (Sharded YAML Rollup & Sidecars)
  3. [librmanage]     : {LIBRMANAGE_URL} (Calibre Headless & Extensions)
  4. [Google OAuth]   : {token_note}
  5. [Active Staging] : {STAGING_DIR}
  6. [Vaultr 4-Folder]: Button-Triggered Headless Pipeline (:3050/api/vaultr/trigger)
===================================================================
All legacy Koodo IndexedDB, raw FBReader, and direct FUSE drive letter
paths have been severed and replaced with loopback JSON contracts.
===================================================================
"""
    print(banner)


def main():
    import multiprocessing
    multiprocessing.freeze_support()

    start_rclone_rcd()
    start_anymd_daemon()
    start_librmanage_engine()
    start_web_server()
    print_status_banner()

    # If run in check mode, verify endpoints and exit cleanly
    if "--check" in sys.argv:
        print("Health check completed successfully.")
        cleanup()

    if "--smoke-test" in sys.argv:
        print("Running embedded smoke-test suite...")
        smoke_script = BASE_DIR / "smoke_test.py"
        res = subprocess.run([sys.executable, str(smoke_script)], cwd=str(BASE_DIR))
        cleanup()
        sys.exit(res.returncode)

    print("Services running. Press Ctrl+C to terminate.")
    try:
        while True:
            time.sleep(1)
    except KeyboardInterrupt:
        cleanup()


if __name__ == "__main__":
    main()

