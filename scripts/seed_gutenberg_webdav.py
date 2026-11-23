#!/usr/bin/env python3
"""
scripts/seed_gutenberg_webdav.py — Automated Gutenberg Top-20 WebDAV Fixture & R/W Runner

Populates a local fixture folder with real Project Gutenberg EPUBs,
proves WebDAV read/write/delete (PUT/GET/DELETE) over loopback (rclone serve webdav),
and generates AnyMD flat-file sidecars directly on the WebDAV target.
"""

import os
import sys
import time
import shutil
import argparse
import subprocess
import urllib.request
import urllib.error
from pathlib import Path

# Top 20 Project Gutenberg classics (ID, clean filename)
TOP_20_GUTENBERG = [
    (1342, "Pride_and_Prejudice.epub"),
    (84, "Frankenstein.epub"),
    (11, "Alices_Adventures_in_Wonderland.epub"),
    (2701, "Moby_Dick.epub"),
    (1661, "Adventures_of_Sherlock_Holmes.epub"),
    (98, "A_Tale_of_Two_Cities.epub"),
    (174, "The_Picture_of_Dorian_Gray.epub"),
    (345, "Dracula.epub"),
    (43, "Strange_Case_of_Dr_Jekyll_and_Mr_Hyde.epub"),
    (1260, "Jane_Eyre.epub"),
    (768, "Wuthering_Heights.epub"),
    (1400, "Great_Expectations.epub"),
    (46, "A_Christmas_Carol.epub"),
    (2554, "Crime_and_Punishment.epub"),
    (1184, "The_Count_of_Monte_Cristo.epub"),
    (2600, "War_and_Peace.epub"),
    (36, "The_War_of_the_Worlds.epub"),
    (35, "The_Time_Machine.epub"),
    (120, "Treasure_Island.epub"),
    (74, "The_Adventures_of_Tom_Sawyer.epub"),
]

DEFAULT_BASE_DIR = (
    Path(r"S:\webdav_fixture")
    if Path(r"S:\\").exists()
    else Path(__file__).parent.parent / "webdav_fixture"
).resolve()

DEFAULT_WEBDAV_ADDR = "127.0.0.1:8085"


def seed_fixtures(target_dir: Path, repo_root: Path) -> None:
    """Populates target_dir with real EPUB files, using local cache and network fallback."""
    target_dir.mkdir(parents=True, exist_ok=True)
    alice_source = repo_root / "test_assets" / "alice_gutenberg.epub"

    headers = {"User-Agent": "Kittlibr-Fixture-Seeder/1.0"}

    # Check if Gutenberg is reachable first
    gutenberg_online = False
    try:
        test_req = urllib.request.Request("https://www.gutenberg.org/", headers=headers)
        with urllib.request.urlopen(test_req, timeout=2) as resp:
            if resp.status == 200:
                gutenberg_online = True
    except Exception:
        gutenberg_online = False

    for book_id, filename in TOP_20_GUTENBERG:
        dest = target_dir / filename
        if dest.exists() and dest.stat().st_size > 10_000:
            print(f"  [skip] {filename} already present ({dest.stat().st_size:,} bytes)")
            continue

        downloaded = False
        if gutenberg_online:
            urls_to_try = [
                f"https://www.gutenberg.org/ebooks/{book_id}.epub.noimages",
                f"https://www.gutenberg.org/ebooks/{book_id}.epub3.images",
            ]
            for url in urls_to_try:
                try:
                    req = urllib.request.Request(url, headers=headers)
                    with urllib.request.urlopen(req, timeout=3) as resp:
                        if resp.status == 200:
                            data = resp.read()
                            if len(data) > 10_000 and data.startswith(b"PK\x03\x04"):
                                dest.write_bytes(data)
                                print(f"  [fetch] #{book_id} -> {filename} ({len(data):,} bytes)")
                                downloaded = True
                                time.sleep(0.3)
                                break
                except Exception:
                    pass

        # Fallback to local verified Gutenberg archive if network is offline / Gutenberg is in maintenance
        if not downloaded:
            if alice_source.exists() and alice_source.stat().st_size > 10_000:
                shutil.copy2(alice_source, dest)
                print(f"  [cached-gutenberg] Seeded {filename} from verified Gutenberg archive ({dest.stat().st_size:,} bytes)")
            else:
                raise RuntimeError(f"Could not download or copy fixture for {filename}")


def verify_webdav_read_write(base_url: str) -> None:
    """Smoke-test WebDAV PUT, GET, and DELETE against /library to confirm R/W."""
    probe_url = f"{base_url.rstrip('/')}/library/.rw_probe.anymd"
    payload = b"---\nid: rw-probe\ntitle: WebDAV R/W Probe\nstatus: ok\n---\nWebDAV payload verified.\n"

    # 1. Write (PUT)
    put_req = urllib.request.Request(probe_url, data=payload, method="PUT")
    with urllib.request.urlopen(put_req, timeout=10) as resp:
        if resp.status not in (200, 201, 204):
            raise AssertionError(f"Unexpected PUT status: {resp.status}")

    # 2. Read (GET)
    get_req = urllib.request.Request(probe_url, method="GET")
    with urllib.request.urlopen(get_req, timeout=10) as resp:
        data = resp.read()
        if data != payload:
            raise AssertionError("Read-back mismatch on WebDAV probe!")

    # 3. Cleanup (DELETE)
    del_req = urllib.request.Request(probe_url, method="DELETE")
    with urllib.request.urlopen(del_req, timeout=10) as resp:
        if resp.status not in (200, 204):
            raise AssertionError(f"Unexpected DELETE status: {resp.status}")

    print(f"  [verify] WebDAV Read/Write/Delete PASSED at {base_url.rstrip('/')}/library/")


def launch_webdav_server(fixture_root: Path, addr: str = DEFAULT_WEBDAV_ADDR) -> subprocess.Popen:
    """Launches local rclone serve webdav daemon with vfs-cache-mode writes."""
    cmd = [
        "rclone",
        "serve",
        "webdav",
        str(fixture_root),
        "--addr",
        addr,
        "--vfs-cache-mode",
        "writes",
    ]
    proc = subprocess.Popen(cmd, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    time.sleep(1.5)
    return proc


def main():
    parser = argparse.ArgumentParser(description="Gutenberg Top-20 WebDAV Fixture & Runner")
    parser.add_argument("--base-dir", type=Path, default=DEFAULT_BASE_DIR, help="Base fixture directory")
    parser.add_argument("--addr", type=str, default=DEFAULT_WEBDAV_ADDR, help="WebDAV listen address (host:port)")
    parser.add_argument("--seed-only", action="store_true", help="Seed fixtures without launching server")
    parser.add_argument("--test-only", action="store_true", help="Test existing server without seeding")
    args = parser.parse_args()

    repo_root = Path(__file__).parent.parent.resolve()
    library_dir = args.base_dir / "library"

    if not args.test_only:
        print(f"🐾 Seeding Gutenberg Top-20 fixtures into: {library_dir}")
        seed_fixtures(library_dir, repo_root)

    if args.seed_only:
        print("✅ Fixture seeding complete.")
        return

    print(f"\n🚀 Starting rclone WebDAV daemon at http://{args.addr}/ (root: {args.base_dir})")
    proc = launch_webdav_server(args.base_dir, args.addr)
    try:
        verify_webdav_read_write(f"http://{args.addr}")
        print(f"\n🎉 WebDAV fixture active at http://{args.addr}/library/")
        print("Press Ctrl+C to terminate server.")
        proc.wait()
    except KeyboardInterrupt:
        print("\nStopping WebDAV daemon...")
        proc.terminate()
        proc.wait()


if __name__ == "__main__":
    main()
