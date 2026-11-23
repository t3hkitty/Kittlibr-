"""
CLI utility for Google OAuth loopback authorization (`src/auth/google_oauth_cli.py`)
Run on Windows (`C:\\Scripts\\google_oauth_loopback.py` or `python -m src.auth.google_oauth_cli`)
Captures offline refresh_token on http://127.0.0.1:<port> and outputs rclone.conf token string.
"""

import os
import sys
import argparse
import webbrowser
from pathlib import Path

# Add project root to sys.path
sys.path.insert(0, str(Path(__file__).parent.parent.parent))

from src.auth.google_oauth import (
    GoogleOAuthManager,
    SCOPE_PRESETS,
    resolve_scopes,
    get_token_storage_path
)


def main():
    parser = argparse.ArgumentParser(description="AnyMD & Kittlibr Google OAuth Loopback Manager")
    parser.add_argument("--client-id", default=os.environ.get("GOOGLE_OAUTH_CLIENT_ID", "515619-desktop.apps.googleusercontent.com"))
    parser.add_argument("--client-secret", default=os.environ.get("GOOGLE_OAUTH_CLIENT_SECRET", ""))
    parser.add_argument("--port", type=int, default=8085, help="Loopback port on 127.0.0.1")
    parser.add_argument("--modules", nargs="+", default=["base_vault"], choices=list(SCOPE_PRESETS.keys()) + ["all"], help="Scope presets to include")
    parser.add_argument("--status", action="store_true", help="Print current token status and scopes")
    parser.add_argument("--refresh", action="store_true", help="Force refresh access token using stored refresh_token")
    parser.add_argument("--rclone-token", action="store_true", help="Print rclone.conf token JSON string")
    parser.add_argument("--no-browser", action="store_true", help="Do not automatically launch web browser")

    args = parser.parse_args()

    manager = GoogleOAuthManager(
        client_id=args.client_id,
        client_secret=args.client_secret,
        port=args.port
    )

    if args.status:
        st = manager.get_scope_status()
        print("\n--- AnyMD / Kittlibr Google OAuth Status ---")
        print(f"Token Path: {st['token_path']}")
        print(f"Token Present: {st['has_token']}")
        print(f"Expiry: {st['expiry']}")
        print("\nScope Modules:")
        for k, v in st["presets"].items():
            badge = "[AUTHORIZED]" if v["authorized"] else "[OPT-IN / NOT GRANTED]"
            print(f"  * {v['name']} ({k}): {badge}")
            if not v["authorized"]:
                print(f"    Missing: {', '.join(v['missing_scopes'])}")
        return

    if args.refresh:
        try:
            print("[INFO] Refreshing access token...")
            new_bundle = manager.refresh_access_token(force=True)
            print(f"[OK] Token refreshed successfully. Expiry: {new_bundle.get('expiry')}")
        except Exception as e:
            print(f"[ERROR] Failed to refresh token: {e}")
            sys.exit(1)
        return

    if args.rclone_token:
        print(manager.get_rclone_token_json())
        return

    # Run OAuth loopback authorization
    presets = list(SCOPE_PRESETS.keys()) if "all" in args.modules else args.modules
    scopes = resolve_scopes(presets)

    auth_url = manager.get_auth_url(scopes)
    print("\n=======================================================")
    print("AnyMD / Kittlibr Google OAuth Loopback Authorization")
    print("=======================================================")
    print(f"Loopback Listener: http://127.0.0.1:{args.port}/")
    print(f"Active Scope Modules: {', '.join(presets)}")
    print(f"Token Output: {manager.token_path}")
    print("\nIf browser does not open automatically, visit this URL:")
    print(auth_url)
    print("=======================================================\n")

    if not args.no_browser:
        try:
            webbrowser.open(auth_url)
        except Exception:
            pass

    print("[INFO] Waiting for authorization redirect on 127.0.0.1...")
    try:
        code = manager.start_loopback_listener(timeout_sec=120)
        print("[OK] Authorization code received. Exchanging for tokens...")
        bundle = manager.exchange_code(code)
        print(f"[OK] Tokens saved successfully to: {manager.token_path}")
        print(f"[OK] Expiry: {bundle.get('expiry')}")
        print("\nrclone.conf token snippet:")
        print(f"token = {manager.get_rclone_token_json()}\n")
    except Exception as e:
        print(f"[ERROR] Authorization failed: {e}")
        sys.exit(1)


if __name__ == "__main__":
    main()
