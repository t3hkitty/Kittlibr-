# 📖 Kittlibr Blueprint: Koodo-Based EPUB + `.anymd` Sidecar Loader

> **Status:** Active Core Blueprint  
> **Target:** Kittlibr (`kitlib`) Reader Core & Universal Sidecar Engine  
> **Source Base:** Koodo Reader architecture + AnyMD flat-file YAML/JSON sidecar databasing  

---

## 1. Reliable Book Loading First (No Binary Stubs)

* **Real Archive & Chapter Extraction:**
  - Load `.epub` files by unpacking and parsing archive contents (`META-INF/container.xml`, OPF package manifest/spine, XHTML chapters, TOC, and cover image) using client/daemon EPUB extraction.
  - Reject and eliminate binary placeholder stubs (`[Binary EPUB Book ...]`).
  - Render actual HTML/XHTML chapters with resource resolving (CSS, images, SVG) through blob/object URLs.
* **Additional Formats & Fallbacks:**
  - Support `.md` / serial text files cleanly alongside `.epub` (parsing markdown headings `# Chapter ...` into real TOC chapters).
  - Provide an **Open With** fallback (system handler, external reader, `pandoc`, or Calibre CLI) if a file cannot be parsed natively in-app.
* **Reading State Persistence:**
  - Persist chapter, CFI / structural paragraph offset, theme, font size, and percentage progress immediately.
  - Never reset to `Page 1 / 1` on reload or reopen.

---

## 2. AnyMD Flat-File Sidecar Databasing

* **Sidecar-First Metadata:**
  - Store book metadata, shelf/virtual-folder tags, `added_at` timestamps, content warnings (`#does-the-dog-die`, `#warnings`), custom tags, and reading progress in clean `.anymd` / YAML/JSON frontmatter sidecars.
  - Eliminate SQLite-only lock-in and bloated GUI database dependencies.
* **Configurable Sidecar Storage Modes:**
  - Support per-root / per-remote storage modes:
    1. `.anymd/` hidden folder (centralized vault rollup).
    2. `.filename.anymd` or `filename.anymd` per-file sidecars (side-by-side).
    3. `.anymdz` compressed 7z archives for low-inode or remote vaults.
  - Explicit `md5` / `xxhash` deduplication tracking and Zettelkasten Pacific Time serial IDs (`YYYYMMDD-HHMM`).

---

## 3. Honest Paths & `:3050` / `rclone` Integration

* **No Hardcoded Path Resets:**
  - Preserve user-entered paths and subpaths (such as `Z:\kittlib\epub` or `davjump` + `meow-vault/inbox`) across navigation and reloads.
  - Never silently reset to a non-existent `library/` default.
  - Never inject fake directory chips (`library`, `backups`, `downloads`, `staging`, `novels`, `epubs`, `incoming`).
* **Honest Daemon & Remote Status:**
  - Expose `GET /api/status` (`200 OK`) on the `:3050` daemon.
  - Echo the exact remote + subpath being queried or tested.
  - Clearly label `daemon offline` / `simulated` states instead of masking `404` or offline scans as live results.

---

## 4. Test Specifications

1. **Real `.epub` Chapter Extraction & TOC:**
   - Verify parsing of valid EPUB archives.
   - Assert all spine chapters are extracted with non-empty content and valid titles.
   - Assert binary stub string `[Binary EPUB Book` is never generated or accepted.
2. **`.anymd` Sidecar Read/Write Round-Trips:**
   - Atomic write and read of YAML frontmatter sidecars.
   - Verify persistence of reading progress, tags, content warnings, and timestamps.
3. **Subpath & State Persistence:**
   - Verify user-entered subpaths and reading locations are preserved across sessions.
