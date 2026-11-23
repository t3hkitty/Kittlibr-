# 📚 Kittlibr Sources & Blueprints for NotebookLM / Vaultr

## Active Source List

1. **KOODO_ANYMD_READER_BLUEPRINT.md**
   - **Path:** `docs/blueprints/KOODO_ANYMD_READER_BLUEPRINT.md`
   - **Description:** Foundational blueprint for Kittlibr reader core. Specifies real EPUB unpacker (container.xml, OPF package manifest/spine, chapter extraction), `.md` serial text parsing, `.anymd` YAML frontmatter sidecars, reading progress persistence, and honest loopback `:3050` daemon status without binary stubs or fake path resets.

2. **UNIFIED_MERGE_MANIFEST.md**
   - **Path:** `UNIFIED_MERGE_MANIFEST.md`
   - **Description:** Port topology (`:3050` AnyMD, `:5820` librmanage, `:5572` rclone rcd) and decoupled service architecture.

3. **kittlibr-ext.json**
   - **Path:** `kittlibr-ext.json`
   - **Description:** 6-slot extension registry and service contract specification.
