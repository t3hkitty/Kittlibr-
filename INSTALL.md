# Kittlibr Setup & Installation Guide (DIY) 🛠️📖

This guide walks you through setting up and running **Kittlibr** on any system:
- **Mode 1: Zero-Setup Local-First Baseline** (No laptop daemon, no cloud required — runs right on your phone/tablet/desktop browser or APK)
- **Mode 2: Out-of-the-Box (OOB) Docker Deployment** (Single-command stack for Home Servers, NAS, Raspberry Pi, or VPS)
- **Mode 3: Native Host / Python Setup** (Running directly on Linux, macOS, or Windows)
- **Mode 4: Android / Termux Setup** (Running rclone / loopback on device without a computer)
- **Mode 5: LAN / Multi-Device Setup** (Accessing your home library daemon from your phone or tablet on Wi-Fi)

---

## 🚀 Quick Navigation

- [Architecture Overview](#-architecture-overview)
- [Mode 1: Local-First Baseline (Zero-Setup)](#-mode-1-local-first-baseline-zero-setup)
- [Mode 2: Docker Deployment (Recommended for Servers/NAS)](#-mode-2-docker-deployment-recommended-for-serversnas)
- [Mode 3: Native Host Setup (Linux / macOS / Windows)](#-mode-3-native-host-setup-linux--macos--windows)
- [Mode 4: Android On-Device Setup (Termux)](#-mode-4-android-on-device-setup-termux)
- [Mode 5: Multi-Device / LAN Configuration](#-mode-5-multi-device--lan-configuration)
- [Mode 6: Background Seeker Service & Device Pairing](#-mode-6-background-seeker-service--device-pairing-android-windows-wsl-linux)
- [⚡ Speed-Tuned Rclone Remote Control (Optional Cloud Sync)](#-speed-tuned-rclone-remote-control-optional-cloud-sync)
- [🛠️ Troubleshooting & Service Health Checks](#️-troubleshooting--service-health-checks)

---

## 🏛 Architecture Overview

Kittlibr is designed to be **modular and resilient**:
1. **Frontend Reader UI** (`index.html` / Cordova / APK):
   - Fully standalone client-side reading engine.
   - Works 100% offline using the HTML5 File System Access API or Android Storage Access Framework (SAF).
2. **AnyMD Loopback Daemon** (Port `3050`):
   - Python microservice for sharded YAML rollups, Markdown sidecars, and book index caching.
3. **librmanage Calibre Engine** (Port `5820`):
   - Headless book cataloging, acquisition (librget), and metadata scrapers (librscrape).
4. **Rclone JSON-RPC Daemon** (Port `5572`):
   - Connects remote WebDAV/cloud storage without OS drive mounts or FUSE root permissions.
5. **Static Web Server** (Port `8080`):
   - Embedded launcher serving the web app across your local network.

---

## 📱 Mode 1: Local-First Baseline (Zero-Setup)

> **No server, no laptop daemon, no internet connection required.**

You can turn off your laptop or server completely without losing access to your books or breaking the reader.

1. **Open Kittlibr**:
   - Web: Open `index.html` directly in any modern browser (Chrome, Edge, Firefox, Brave, Safari) or visit your deployed GitHub Pages URL.
   - Android: Install and open the standalone APK (`Kittlibr.apk`).
2. **Pick Your Local Library Folder**:
   - In the sidebar or top bar, click **"Pick Local Folder"** (or use the native Android Storage Access Framework folder picker).
   - Select any folder on your device containing `.epub`, `.md`, or `.txt` files.
3. **Read & Organize**:
   - Your library will index and load immediately into memory and local browser cache (`IndexedDB`/`localStorage`).
   - If any optional background daemon (`:3050` or `:5572`) is offline, Kittlibr smoothly falls back to 100% client-side local operation.

---

## 🐳 Mode 2: Docker Deployment (Recommended for Servers/NAS)

Run the entire Kittlibr topology in a single, isolated container with persistent storage.

### Prerequisites
- Docker (and Docker Compose v2) installed.

### Fast Deploy: Using `docker-run.sh`
```bash
# Clone the repository
git clone https://github.com/t3hkitty/Kittlibr-.git
cd Kittlibr-

# Make executable and run
chmod +x docker-run.sh
./docker-run.sh
```

### Deploy: Using `docker compose`
```bash
# Start all services in the background
docker compose up -d

# Check service status
docker compose ps

# Stream logs
docker compose logs -f
```

### Manual `docker run` Command
```bash
# 1. Create persistent storage volume for books and metadata
docker volume create kittlibr_vault_data

# 2. Build image
docker build -t t3hkitty/kittlibr:latest .

# 3. Run container
docker run -d \
  --name kittlibr \
  --restart unless-stopped \
  -p 8080:8080 \
  -p 3050:3050 \
  -p 5572:5572 \
  -p 5820:5820 \
  -v kittlibr_vault_data:/app/staging_cache \
  t3hkitty/kittlibr:latest
```

Once started, open **`http://<SERVER_IP>:8080`** in your browser.

---

## 💻 Mode 3: Native Host Setup (Linux / macOS / Windows)

### 1. Prerequisites
- **Python**: Version 3.10, 3.11, or 3.12 installed.
- **Rclone** (Optional, for cloud storage/WebDAV): [Download rclone](https://rclone.org/downloads/) and ensure it is on your system `PATH`.

### 2. Install Python Dependencies
```bash
git clone https://github.com/t3hkitty/Kittlibr-.git
cd Kittlibr-

pip install -r requirements.txt
# Or manually install:
pip install fastapi uvicorn requests pyyaml xxhash pydantic httpx
```

### 3. Launch Kittlibr
```bash
# Start all unified daemons and web server
python3 start_kittlibr.py
```

### 4. Custom Configuration via Environment Variables
You can customize the ports and interfaces:
```bash
export SERVE_WEB=1           # 1 to launch static web server on port 8080
export WEB_PORT=8080         # Web UI port
export ANYMD_PORT=3050       # AnyMD daemon port
export LIBRMANAGE_PORT=5820  # Calibre engine port
export RCLONE_BIND_ADDR=0.0.0.0:5572
export RCLONE_RC_USER=kittlibr
export RCLONE_RC_PASS=wordsaremagic

python3 start_kittlibr.py
```

---

## 🤖 Mode 4: Android On-Device Setup (Termux)

If you want to run the full daemon stack or Rclone directly on your Android phone/tablet without keeping a computer running:

1. **Install Termux** from F-Droid (not Google Play).
2. **Install Packages in Termux**:
   ```bash
   pkg update && pkg upgrade
   pkg install python git rclone curl
   ```
3. **Clone & Setup Kittlibr**:
   ```bash
   git clone https://github.com/t3hkitty/Kittlibr-.git
   cd Kittlibr-
   pip install fastapi uvicorn requests pyyaml xxhash pydantic httpx
   ```
4. **Run Rclone rcd on Localhost**:
   ```bash
   rclone rcd \
     --rc-addr=127.0.0.1:5572 \
     --rc-user=kittlibr \
     --rc-pass=wordsaremagic \
     --transfers=4 \
     --checkers=8 \
     --buffer-size=32M \
     --timeout=30s \
     --contimeout=10s
   ```
5. **Open Kittlibr on Android**:
   - Open Kittlibr APK or browse to `http://127.0.0.1:8080`.
   - In Settings, set your Rclone endpoint to `http://127.0.0.1:5572`.

---

## 📶 Mode 5: Multi-Device / LAN Configuration

To host Kittlibr on a home server (or desktop) and access it from your phone, e-reader, or laptop:

1. **Find Host LAN IP**:
   - Linux/macOS: `ip a` or `ifconfig` (e.g. `192.168.1.150`).
   - Windows: `ipconfig` (e.g. `192.168.1.150`).
2. **Start Kittlibr Binding to All Interfaces (`0.0.0.0`)**:
   - If using Docker, this is configured automatically!
   - If using native Python:
     ```bash
     WEB_HOST=0.0.0.0 ANYMD_HOST=0.0.0.0 LIBRMANAGE_HOST=0.0.0.0 RCLONE_BIND_ADDR=0.0.0.0:5572 python3 start_kittlibr.py
     ```
3. **Access from Client Devices**:
   - Open browser on phone/tablet: `http://192.168.1.150:8080`
   - In Kittlibr Client Settings:
     - AnyMD URL: `http://192.168.1.150:3050`
     - Rclone URL: `http://192.168.1.150:5572`

---

## 🛰️ Mode 6: Background Seeker Service & Device Pairing (Android, Windows, WSL, Linux)

The **LibrManage Metadata Seeker & Scraper Service** runs as a persistent headless background daemon (`src/daemon/anymd_daemon.py` and `librmanage_service.py` on port `5820` / `3050`). It actively watches incoming staging directories, normalizes titles, queries online book databases (Goodreads, Open Library, Internet Archive), downloads cover artwork, generates AI synopsis cards, and enriches sidecar YAML metadata shards (`.anymd`).

Because the Seeker Service is designed as an asynchronous background worker, client devices (Android APK, Windows desktop viewer, or web browsers) can pair with it directly over loopback or LAN.

### 1. Platform Installation Requirements

| Platform | Requirements | Installation Command |
| :--- | :--- | :--- |
| **Android (APK)** | Android 7.0+ (Nougat) or higher, `POST_NOTIFICATIONS` permission enabled | Install `Kittlibr.apk` from GitHub Actions Releases. If running headless seeker daemon locally on device, install Termux + Python. |
| **Windows 10/11** | Python 3.10+ (or standalone `Kittlibr.exe`), PowerShell or CMD | `pip install fastapi uvicorn requests pyyaml xxhash pydantic httpx` or run `Kittlibr.exe`. |
| **WSL 2 (Ubuntu/Debian)** | WSL 2 Kernel, Python 3.10+ | `sudo apt update && sudo apt install -y python3 python3-pip python3-venv git rclone` |
| **Linux (Server/Desktop)** | Systemd / Docker, Python 3.10+ | `sudo apt install python3-pip && pip install -r requirements.txt` |
| **macOS** | macOS 12+ (Monterey+), Homebrew | `brew install python rclone && pip3 install -r requirements.txt` |

---

### 2. Launching the Headless Background Seeker Service

#### On Linux / WSL / macOS:
```bash
# Start background seeker daemon binding to your preferred interface
export LIBRMANAGE_HOST=0.0.0.0
export LIBRMANAGE_PORT=5820
export ANYMD_HOST=0.0.0.0
export ANYMD_PORT=3050

python3 start_kittlibr.py --headless &
```

Or set it up as a **systemd user service** (`~/.config/systemd/user/kittlibr-seeker.service`):
```ini
[Unit]
Description=Kittlibr Background Seeker & AnyMD Daemon
After=network.target

[Service]
Type=simple
WorkingDirectory=/home/username/Kittlibr-
ExecStart=/usr/bin/python3 start_kittlibr.py
Restart=always
RestartSec=5

[Install]
WantedBy=default.target
```
Enable and start:
```bash
systemctl --user enable --now kittlibr-seeker.service
```

#### On Windows / WSL:
- Run directly inside Windows PowerShell:
  ```powershell
  python start_kittlibr.py
  # Or run the prebuilt single-file executable:
  .\Kittlibr.exe
  ```
- If running under **WSL 2** with Windows frontend pairing, ensure your `.wslconfig` includes `networkingMode=mirrored` or configure port forwarding:
  ```powershell
  netsh interface portproxy add v4tov4 listenport=5820 listenaddress=0.0.0.0 connectport=5820 connectaddress=127.0.0.1
  ```

#### On Android (Termux):
```bash
# Keep Termux running in the background with wake lock
termux-wake-lock
cd Kittlibr-
python3 start_kittlibr.py &
```

---

### 3. Pairing Client Apps with the Background Seeker Service

Once the service is active, link your client app (Android APK, Windows UI, or Web Edition):

1. **Open Kittlibr Client** (Web App, Windows Viewer, or Android APK).
2. **Navigate to `LibrManage` > `Preferences`** (or `Settings` > `Service Endpoints`):
   - **Local Device**: If running on the same machine/device, set endpoint to:
     ```text
     http://127.0.0.1:5820
     ```
   - **Remote / LAN (Phone to PC/Server)**: If your seeker service is hosted on your PC/NAS, enter your host LAN IP:
     ```text
     http://192.168.1.xxx:5820
     ```
3. **Verify Pairing**:
   - In the **LibrManage** tab, click **"⚡ Run Seeker on All Books"** or toggle **"Run Metadata Seeker on Import automatically"**.
   - Newly dropped EPUB, Markdown, or web novel chapters in your staging folder will be automatically scanned, cleaned, and updated with AI summaries and genre/trope tags.

---

## ⚡ Speed-Tuned Rclone Remote Control (Optional Cloud Sync)

Kittlibr communicates with `rclone` via HTTP JSON-RPC (`rcd`), avoiding the overhead and instability of operating system FUSE drive letters.

When starting rclone standalone or in custom scripts, use these recommended transfer speed tuning parameters:

```bash
rclone rcd \
  --rc-addr=0.0.0.0:5572 \
  --rc-user=kittlibr \
  --rc-pass=wordsaremagic \
  --transfers=4 \
  --checkers=8 \
  --buffer-size=32M \
  --timeout=30s \
  --contimeout=10s
```

### Parameter Explanations:
- `--transfers=4`: Downloads up to 4 book chapters or media files concurrently.
- `--checkers=8`: Parallel hash and existence checkers for fast remote folder diffs.
- `--buffer-size=32M`: In-memory read buffer per transfer stream to eliminate disk I/O bottlenecks.
- `--timeout=30s` & `--contimeout=10s`: Quick failover on dropped connections without locking UI threads.

---

## 🛠️ Troubleshooting & Service Health Checks

| Test | Command | Expected Output |
| :--- | :--- | :--- |
| **Web UI** | `curl -I http://localhost:8080/` | `HTTP/1.0 200 OK` |
| **AnyMD Engine** | `curl -s http://localhost:3050/` | `{"status":"online","service":"AnyMD Universal Loopback Daemon",...}` |
| **librmanage** | `curl -s http://localhost:5820/api/status` | `{"status":"online","service":"Kittlibr librmanage Calibre Engine",...}` |
| **Rclone rcd** | `curl -s -u kittlibr:wordsaremagic -X POST http://localhost:5572/core/version` | `{"version":"v1.x.x",...}` |

### Common Issues

1. **"Failed to fetch / Connection reset by peer"**:
   - Ensure the daemon is bound to `0.0.0.0` if accessing outside `localhost`.
   - Verify host firewall allows incoming connections on ports `8080`, `3050`, `5572`, and `5820`.

2. **"Unknown flag --vfs-cache-mode or --dir-cache-time"**:
   - These flags apply to `rclone mount`, not `rclone rcd`. Kittlibr uses non-FUSE JSON-RPC transfer flags (`--transfers`, `--checkers`, `--buffer-size`).

3. **"Can I turn off my computer?"**:
   - **Yes!** Use **Mode 1 (Local Folder)** on your device, or deploy Kittlibr via **Mode 2 (Docker)** to a low-power home NAS or VPS that stays on.
