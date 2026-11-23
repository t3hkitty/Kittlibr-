"""
Google OAuth Loopback & Token Manager (`src/auth/google_oauth.py`)
Centralized token lifecycle & rclone.conf integration for AnyMD, MBB, Kittlibr, and Vault Sync.

Design Specifications:
1. Always uses plain HTTP loopback (http://127.0.0.1:<port>) with Desktop app client (project `anymd-515619`)
2. Manages offline refresh_tokens and generates both:
   - Unified token bundle: ~/.config/anymd/google_oauth_token.json (or APPDATA/anymd on Windows)
   - rclone.conf-compatible JSON block: token = {"access_token": "...", "token_type": "Bearer", "refresh_token": "...", "expiry": "..."}
3. Automatic proactive and on-demand token refresh using grant_type=refresh_token rotation.
4. Modular, opt-in scopes with graceful degradation:
   - Base Drive / Vault Sync (drive, drive.file)
   - MBB Fitness / Telemetry (Google Fit / Health Connect equivalent scopes)
   - MBB Location / Timeline (User location & geofence telemetry)
   - MBB Contacts / CRM (CardDAV & Google Contacts context)
5. Robust cross-platform console encoding (UTF-8 stream reconfigure, clean ASCII tags).
"""

import os
import sys
import json
import time
from pathlib import Path
from typing import Dict, Any, List, Optional
import urllib.request
import urllib.parse
import urllib.error
from http.server import HTTPServer, BaseHTTPRequestHandler
import threading

# Reconfigure streams to avoid Windows cp1252 / charmap encoding errors
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

# Canonical Defaults
DEFAULT_CLIENT_ID = os.environ.get("GOOGLE_OAUTH_CLIENT_ID", "515619-desktop.apps.googleusercontent.com")
DEFAULT_CLIENT_SECRET = os.environ.get("GOOGLE_OAUTH_CLIENT_SECRET", "")
TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token"
AUTH_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth"

# Modular Scope Presets
SCOPE_PRESETS: Dict[str, Dict[str, Any]] = {
    "base_vault": {
        "name": "Base Vault & Drive Sync",
        "description": "Read/write access to Google Drive vaults and AnyMD flat-file sidecars",
        "scopes": [
            "https://www.googleapis.com/auth/drive.file",
            "https://www.googleapis.com/auth/drive"
        ]
    },
    "mbb_fitness": {
        "name": "MBB Fitness & Somatic Telemetry",
        "description": "Google Fit & somatic activity data (steps, heart rate, workouts)",
        "scopes": [
            "https://www.googleapis.com/auth/fitness.activity.read",
            "https://www.googleapis.com/auth/fitness.body.read",
            "https://www.googleapis.com/auth/fitness.location.read"
        ]
    },
    "mbb_timeline": {
        "name": "MBB Location & Timeline Telemetry",
        "description": "Location & geofence daily logs feeding MacroDroid/Tasker AnyMD entries",
        "scopes": [
            "https://www.googleapis.com/auth/userlocation.reporting.read"
        ]
    },
    "mbb_contacts": {
        "name": "MBB Contacts & Relationship CRM",
        "description": "CardDAV / Google Contacts context for relationship logs and notes",
        "scopes": [
            "https://www.googleapis.com/auth/contacts.readonly"
        ]
    }
}


def get_token_storage_path() -> Path:
    """Returns canonical cross-platform path for google_oauth_token.json."""
    custom_path = os.environ.get("ANYMD_TOKEN_PATH")
    if custom_path:
        p = Path(custom_path)
        p.parent.mkdir(parents=True, exist_ok=True)
        return p

    if sys.platform.startswith("win"):
        base = Path(os.environ.get("APPDATA", Path.home() / "AppData" / "Roaming")) / "anymd"
    else:
        base = Path.home() / ".config" / "anymd"
    base.mkdir(parents=True, exist_ok=True)
    return base / "google_oauth_token.json"


def resolve_scopes(presets: List[str], custom_scopes: Optional[List[str]] = None) -> List[str]:
    """Combines preset keys and custom scopes into a deduplicated list."""
    out = []
    for p in presets:
        if p in SCOPE_PRESETS:
            for s in SCOPE_PRESETS[p]["scopes"]:
                if s not in out:
                    out.append(s)
    if custom_scopes:
        for s in custom_scopes:
            if s not in out:
                out.append(s)
    return out


class OAuthLoopbackHandler(BaseHTTPRequestHandler):
    auth_code: Optional[str] = None
    error: Optional[str] = None

    def log_message(self, format, *args):
        pass  # Suppress default noisy access logs

    def do_GET(self):
        parsed = urllib.parse.urlparse(self.path)
        query = urllib.parse.parse_qs(parsed.query)

        if "code" in query:
            OAuthLoopbackHandler.auth_code = query["code"][0]
            self.send_response(200)
            self.send_header("Content-Type", "text/html; charset=utf-8")
            self.end_headers()
            self.wfile.write(b"""
                <!DOCTYPE html>
                <html>
                <head><title>AnyMD / Kittlibr Auth Successful</title></head>
                <body style="font-family: sans-serif; background: #121212; color: #e0e0e0; text-align: center; padding: 3rem;">
                    <h2 style="color: #00e676;">[OK] Authorization Received!</h2>
                    <p>Google OAuth token captured successfully for AnyMD, MBB, Kittlibr, and rclone.</p>
                    <p style="color: #a0a0a0;">You may close this browser window and return to your application.</p>
                </body>
                </html>
            """)
        elif "error" in query:
            OAuthLoopbackHandler.error = query["error"][0]
            self.send_response(400)
            self.send_header("Content-Type", "text/html; charset=utf-8")
            self.end_headers()
            self.wfile.write(f"<h3>Auth Failed: {OAuthLoopbackHandler.error}</h3>".encode("utf-8"))
        else:
            self.send_response(404)
            self.end_headers()


class GoogleOAuthManager:
    def __init__(
        self,
        client_id: str = DEFAULT_CLIENT_ID,
        client_secret: str = DEFAULT_CLIENT_SECRET,
        token_path: Optional[Path] = None,
        port: int = 8085
    ):
        self.client_id = client_id
        self.client_secret = client_secret
        self.token_path = token_path or get_token_storage_path()
        self.port = port
        self.redirect_uri = f"http://127.0.0.1:{self.port}/"

    def get_auth_url(self, scopes: List[str], state: str = "anymd_auth") -> str:
        """Constructs Google Desktop App OAuth consent URL."""
        params = {
            "client_id": self.client_id,
            "redirect_uri": self.redirect_uri,
            "response_type": "code",
            "scope": " ".join(scopes),
            "access_type": "offline",
            "prompt": "consent",
            "state": state
        }
        return f"{AUTH_ENDPOINT}?{urllib.parse.urlencode(params)}"

    def load_token_bundle(self) -> Optional[Dict[str, Any]]:
        """Reads stored token bundle from disk."""
        if not self.token_path.exists():
            return None
        try:
            with open(self.token_path, "r", encoding="utf-8") as f:
                return json.load(f)
        except Exception as e:
            print(f"[OAuthManager] Warning reading {self.token_path}: {e}")
            return None

    def save_token_bundle(self, token_data: Dict[str, Any]) -> None:
        """Atomically saves token bundle and rclone-compatible config block."""
        self.token_path.parent.mkdir(parents=True, exist_ok=True)
        # Calculate absolute expiry time
        expires_in = token_data.get("expires_in", 3600)
        expiry_epoch = time.time() + expires_in
        expiry_iso = time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime(expiry_epoch))

        bundle = {
            "client_id": self.client_id,
            "token_type": token_data.get("token_type", "Bearer"),
            "access_token": token_data.get("access_token"),
            "refresh_token": token_data.get("refresh_token") or self._get_existing_refresh_token(),
            "scope": token_data.get("scope", ""),
            "expires_in": expires_in,
            "expiry_epoch": expiry_epoch,
            "expiry": expiry_iso,
            "updated_at": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())
        }

        tmp_path = self.token_path.with_suffix(".tmp")
        with open(tmp_path, "w", encoding="utf-8") as f:
            json.dump(bundle, f, indent=2)
        tmp_path.replace(self.token_path)

    def _get_existing_refresh_token(self) -> Optional[str]:
        existing = self.load_token_bundle()
        return existing.get("refresh_token") if existing else None

    def refresh_access_token(self, force: bool = False) -> Dict[str, Any]:
        """Refreshes access_token using stored refresh_token if expired or forced."""
        bundle = self.load_token_bundle()
        if not bundle or not bundle.get("refresh_token"):
            raise ValueError("No refresh_token found in token bundle. Re-run loopback authorization.")

        # Check expiry (refresh 5 minutes before expiration)
        expiry_epoch = bundle.get("expiry_epoch", 0)
        if not force and (expiry_epoch - time.time()) > 300:
            return bundle

        refresh_token = bundle["refresh_token"]
        payload = urllib.parse.urlencode({
            "client_id": self.client_id,
            "client_secret": self.client_secret,
            "refresh_token": refresh_token,
            "grant_type": "refresh_token"
        }).encode("utf-8")

        req = urllib.request.Request(
            TOKEN_ENDPOINT,
            data=payload,
            headers={"Content-Type": "application/x-www-form-urlencoded"}
        )

        try:
            with urllib.request.urlopen(req, timeout=15) as resp:
                new_data = json.loads(resp.read().decode("utf-8"))
            new_data["refresh_token"] = refresh_token  # Preserve refresh_token
            self.save_token_bundle(new_data)
            return self.load_token_bundle() or new_data
        except urllib.error.HTTPError as e:
            err_body = e.read().decode("utf-8", errors="replace")
            raise RuntimeError(f"Token refresh failed ({e.code}): {err_body}")

    def exchange_code(self, auth_code: str) -> Dict[str, Any]:
        """Exchanges authorization code for access and refresh tokens."""
        payload = urllib.parse.urlencode({
            "code": auth_code,
            "client_id": self.client_id,
            "client_secret": self.client_secret,
            "redirect_uri": self.redirect_uri,
            "grant_type": "authorization_code"
        }).encode("utf-8")

        req = urllib.request.Request(
            TOKEN_ENDPOINT,
            data=payload,
            headers={"Content-Type": "application/x-www-form-urlencoded"}
        )

        with urllib.request.urlopen(req, timeout=20) as resp:
            token_data = json.loads(resp.read().decode("utf-8"))

        self.save_token_bundle(token_data)
        return self.load_token_bundle() or token_data

    def start_loopback_listener(self, timeout_sec: int = 120) -> str:
        """Starts local HTTP listener on 127.0.0.1:<port> to capture redirect code."""
        OAuthLoopbackHandler.auth_code = None
        OAuthLoopbackHandler.error = None

        server = HTTPServer(("127.0.0.1", self.port), OAuthLoopbackHandler)
        server_thread = threading.Thread(target=server.serve_forever, daemon=True)
        server_thread.start()

        start_time = time.time()
        try:
            while time.time() - start_time < timeout_sec:
                if OAuthLoopbackHandler.auth_code:
                    return OAuthLoopbackHandler.auth_code
                if OAuthLoopbackHandler.error:
                    raise RuntimeError(f"OAuth loopback returned error: {OAuthLoopbackHandler.error}")
                time.sleep(0.5)
            raise TimeoutError(f"OAuth listener timed out after {timeout_sec}s waiting for redirect.")
        finally:
            server.shutdown()
            server.server_close()

    def get_rclone_token_json(self) -> str:
        """Generates the token = {...} single-line JSON string expected by rclone.conf."""
        bundle = self.load_token_bundle()
        if not bundle:
            return "{}"
        rclone_dict = {
            "access_token": bundle.get("access_token"),
            "token_type": bundle.get("token_type", "Bearer"),
            "refresh_token": bundle.get("refresh_token"),
            "expiry": bundle.get("expiry")
        }
        return json.dumps(rclone_dict)

    def get_scope_status(self) -> Dict[str, Any]:
        """Audits active scopes in token bundle against available presets."""
        bundle = self.load_token_bundle()
        granted_scopes = []
        if bundle and bundle.get("scope"):
            granted_scopes = bundle["scope"].split()

        status = {}
        for key, preset in SCOPE_PRESETS.items():
            required = preset["scopes"]
            has_all = all(s in granted_scopes for s in required)
            status[key] = {
                "name": preset["name"],
                "description": preset["description"],
                "authorized": has_all,
                "missing_scopes": [s for s in required if s not in granted_scopes]
            }

        return {
            "has_token": bundle is not None,
            "token_path": str(self.token_path),
            "expiry": bundle.get("expiry") if bundle else None,
            "presets": status
        }
