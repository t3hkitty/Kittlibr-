# 🐾 UNIFIED_MERGE_MANIFEST.md — Master Merge & Legacy Feature Replacement
**Workspace:** `kittlibr_host` (Local Cache Staging)  
**License Decoupling:** AGPL-3.0 Frontend UI Chassis <-> GPL-3.0 Calibre/Python Services <-> MIT Rclone Loopback HTTP/JSON Contracts  
**Status:** ✅ Fully Audited, Deduplicated & Verified via Automated Smoke Test (`smoke_test.py`)

---

## 1. Service Topology & Loopback Port Map

All services communicate strictly via authenticated localhost HTTP/JSON-RPC loopback (`127.0.0.1`), eliminating FUSE drive mounts, direct disk walks, and monolithic database locks:

| Port | Service | Implementation Path | Superseded Legacy Feature | Credentials / Auth |
| :--- | :--- | :--- | :--- | :--- |
| **`127.0.0.1:3050`** | **`AnyMD` Loopback Daemon** | `src/daemon/anymd_daemon.py` | Koodo `IndexedDB`, standalone SQLite `metadata.db` ownership, and raw `Z:\`/`K:\` directory walks | Localhost loopback HTTP/JSON |
| **`127.0.0.1:5820`** | **`librmanage` Engine** | `src/extensions/librmanage/engine/librmanage_service.py` | Koodo default bookshelf handlers & standalone Calibre CLI wrappers | Localhost loopback HTTP/JSON |
| **`127.0.0.1:5572`** | **`rclone rcd` Daemon** | Existing host binary on `PATH` (`rclone rcd`) | WinFsp / FUSE drive letters (`K:\`, `Z:\`, `T:\`) | Authenticated via `--rc-user` & `--rc-pass` (never `--rc-no-auth` or `--rc-serve`) |

---

## 2. Comprehensive Inventory of Severed Legacy Code Paths

The following legacy code paths, conflicting registries, and external database locks have been **completely excised or decoupled**:

### A. Storage, Database, & Sync Severed Paths
- ❌ **Koodo `IndexedDB` Catalog:** Excised browser-internal `IndexedDB` file/metadata blob storage.
- ❌ **Direct FUSE/WinFsp Drive Mounts:** Excised all hardcoded OS drive-letter mounts (`K:\`, `Z:\`, `T:\`) and direct filesystem walks.
- ❌ **Monolithic SQLite `metadata.db` Ownership:** Decoupled SQLite locking; canonical metadata is owned by flat-file `.md` YAML sidecars with sharded `.anymd/index_XXXX.yaml` rollups (500 records/shard).
- ❌ **Stock Third-Party Cloud Sync Adapters:** Gutted hardcoded sync adapters:
  - `OneDrive`, `MEGA`, `Dropbox`, `Yandex Disk`, `Baidu Wangpan`, `Aliyun Drive`, `WebDAV` lock-ins.
  - `Readwise`, `Notion`, `Obsidian`, `Anki`, `Eudic` connectors.

### B. Reader Engine & Resiliency Severed Paths
- ❌ **Raw FBReader Source Tree:** Completely excluded raw `FBReader` C++ and Java source trees (`zlibrary/ui/`, `fbreader/src/formats/`, `fbreader/src/network/`, `fbreader/src/bookmodel/`).
- ❌ **Malformed Mojibake Fallbacks:** Replaced with clean-room `EncodingFallbackSniffer.ts` (BOM, strict UTF-8, GBK, Shift-JIS, Windows-1252 heuristics).
- ❌ **Zip-Slip & Corrupt Archive Exploits:** Replaced with `DefensiveArchiveUnpacker.ts`.
- ❌ **Brittle Line-Based Bookmarks:** Replaced with `StructuralLocationAnchor.ts` paragraph/element/offset anchors that survive font reflow and window resizing.
- ❌ **Unprotected Viewport Loader:** All three hardening modules are directly hooked into `kookit.min.js` prior to view dispatch.

### C. Translation & Continuity Severed Paths
- ❌ **Bundled Stock Translation & Dictionary JSONs:** Deleted all default dictionary/translation plugins (Google Translate stubs, Bing, Baidu, DeepL stubs).
- ❌ **Proprietary Cloud TTS Voices:** Excised closed-source cloud TTS voice engines.
- ❌ **Robotic Zero-Anaphora Translation Over-Naming:** Replaced by `nuance-brick.ts` (`active_subject` + `tension_vector` with `fading_line_handoff` decay).
- ❌ **Unregulated Entity Translation:** Replaced by deterministic `⟦ENT_xxx⟧` entity masking + pronoun locks from `<series_slug>.glossary.yaml` and dialect switching (`en-US` / `en-GB`).

### D. Acquisition, Scraping & Plugin Duplication Severed Paths
- ❌ **Disparate Unindexed Scrapers:** Replaced with clean-room `librget` and `librscrape` sub-plugins.
- ❌ **Duplicate Plugin Trees:** Removed shadow copies of `librget_plugin.py` and `librscrape_plugin.py` previously sitting inside `librmanage/engine/`. Each plugin now lives strictly in its canonical extension package:
  - Canonical `librget`: `src/extensions/librget/librget_plugin.py`
  - Canonical `librscrape`: `src/extensions/librscrape/librscrape_plugin.py`
  - Imported cleanly into `src/extensions/librmanage/engine/librmanage_service.py` via `extensions.librget.librget_plugin` and `extensions.librscrape.librscrape_plugin`.

---

## 3. Verified Replacement Architecture in `kittlibr_host`

```
kittlibr_host/
├── start_kittlibr.py                  # Master launcher & loopback orchestrator
├── smoke_test.py                      # 9-point end-to-end integration test
├── kittlibr-ext.json                  # 6-slot extension registry manifest
├── UNIFIED_MERGE_MANIFEST.md          # This audit manifest
├── staging_cache/                     # Local C:\ cache staging folder
│   ├── .anymd/
│   │   ├── manifest.yaml              # Sharded rollup manifest
│   │   ├── index_0000.yaml            # Sharded catalog index (<= 500 records)
│   │   └── <book>.epub.md             # Canonical YAML frontmatter sidecars
│   └── <book>.epub                    # Normalized EPUB 3 binaries
└── src/
    ├── daemon/
    │   └── anymd_daemon.py            # Port 3050: Sharded indexer, Zettelkasten IDs, queries
    ├── extensions/
    │   ├── librmanage/
    │   │   ├── LibrmanageController.ts
    │   │   ├── components/            # FacetSidebar, BookCatalogCard, MoreInfoModal, TagFilterBar
    │   │   ├── ui/                    # QueryBar
    │   │   └── engine/
    │   │       ├── librmanage_service.py  # Port 5820: Calibre headless & plugin dispatcher
    │   │       ├── metadata_bridge.py     # Calibre metadata read/embed
    │   │       ├── convert_worker.py      # ebook-convert EPUB 3 normalizer
    │   │       ├── query_engine.py        # Boolean tag/warning expression parser
    │   │       └── plugin_registry.py     # SubPluginRegistry lifecycle harness
    │   ├── librget/
    │   │   └── librget_plugin.py      # Canonical OpenlibExtended acquisition slot
    │   ├── librscrape/
    │   │   └── librscrape_plugin.py   # Canonical Web serial & EPUB 3 assembly slot
    │   ├── librtrans/
    │   │   ├── librtrans_pipeline.ts  # Multi-pass translation engine
    │   │   ├── nuance-brick.ts        # Pass 1.5 zero-anaphora continuity middleware
    │   │   ├── BilingualViewport.tsx  # Side-by-side reading viewport
    │   │   └── GlossaryDrawer.tsx     # Pronoun lock & entity mask editor
    │   └── librsync/                  # Rclone RC client & staging vault service
    ├── utils/
    │   └── hardening/                 # DefensiveArchiveUnpacker, EncodingFallbackSniffer, StructuralLocationAnchor
    └── assets/
        └── lib/
            └── kookit.min.js          # Hardened viewport engine shim
```

---

## 4. End-to-End Automated Verification Evidence

Ran `python3 start_kittlibr.py --smoke-test` with the following clean output:

```text
🐾 RUNNING COMPLETE KITTLIBR END-TO-END SMOKE TEST...
  [PASS] Rclone Daemon (:5572) - Version v1.60.1-DEV (Authenticated via --rc-user / --rc-pass)
  [PASS] AnyMD Daemon (:3050) - AnyMD Universal Loopback Daemon
  [PASS] librmanage Engine (:5820) - Subplugins: {'acquisition': ['librget'], 'scrapers': ['librscrape']}
  [PASS] librscrape Ingest & Packaging - Created staging_cache/dungeon-crawler-carl-vol1.epub
  [PASS] AnyMD Sharded Rollup Index & Sidecar Files - Sidecar: dungeon-crawler-carl-vol1.epub.md, Shard: index_0000.yaml, Manifest: manifest.yaml (Records: 1)
  [PASS] Boolean & Faceted Query Engine - Matched 1 books
  [PASS] MoreInfo Modal Atomic YAML Save - Persisted via: anymd_daemon (temp -> verify -> os.replace)
  [PASS] Hardened Reader Pipeline - EncodingFallbackSniffer, DefensiveArchiveUnpacker & StructuralLocationAnchor verified
  [PASS] librtrans + nuance-brick Translation - Masking, zero-anaphora continuity & dialect audit passed

===================================================================
SMOKE TEST SUMMARY: 9 PASSED, 0 FAILED
===================================================================
```
