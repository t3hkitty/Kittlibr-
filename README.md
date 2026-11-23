# Kittlibr 📚✨

Kittlibr is a cross-platform reading, library management, and story discovery suite designed for web and Android.

## Features

- **Immersive Reading Engine**: Customizable typography (Google Fonts), adjustable font sizes, and eye-health reading themes (*OLED True Black, Sepia, Reverse Sepia, Mocha, Grass, Galaxy, Sci-Fi, Kawaii*).
- **Advanced Acquisition & Storage**: EPUB/novel downloading, secure WebDAV & Rclone storage sync, and cloud synchronization.
- **AI-Powered Discovery**: Tailored recommendation feeds and genre discovery.
- **Calibre Template & Multi-Select Manager**: Automated file renaming templates (`{category}/{genre}/{title} - {author}.{ext}`) and batch AI metadata cleanup.
- **Modular Extension Marketplace**: Install or create extensions for TTS, audiobooks, metadata parsers, and external readers.

---

## Extension Suggestion List

Kittlibr supports a modular extension architecture. Below is the recommended suggestion list of built-in and community extensions you can enable or develop:

1. **Advanced TTS & Audiobook Sync (`tts_audiobook`)**:
   - High-fidelity text-to-speech rendering with synchronized audio playback positions and bookmark saving across devices.
2. **NovelUpdates & Google Searcher (`novelupdates`)**:
   - Scrapes web novel chapters and metadata with Cloudflare WebView cookie bypass.
3. **Librera Reader Stats Sync (`librera`)**:
   - Direct synchronization of reading time, page statistics, and bookmarks with external reader apps like Librera.
4. **Goodreads Reading List & Review Sync (`goodreads`)**:
   - Keeps your reading lists, ratings, and reviews synchronized with Goodreads and external catalog stores.
5. **Rclone Cloud Storage Provider (`rclone`)**:
   - Connects remote cloud backends (Google Drive, Mega, Dropbox, OneDrive) for decentralized library storage and backup.
6. **Calibre AI Batch Metadata Enchanter (`calibre_ai`)**:
   - Advanced AI metadata cleanup and automated series sorting.

---

## 🛠️ Installation & Setup (DIY)

Kittlibr supports zero-setup local offline reading as well as one-command Docker and native server deployments.

See the complete guide in [**`INSTALL.md`**](INSTALL.md):
- **Local-First Zero-Setup Mode**: Read local folders on Web/APK with no server running.
- **Docker & Docker Compose**: Complete OOB stack deployment via `./docker-run.sh`.
- **Native Host & Termux (Android)**: Run loopback daemons on any machine or mobile device.
- **Speed-Tuned Rclone Config**: Non-FUSE JSON-RPC settings for maximum cloud transfer speed.

---

## License

MIT License. See `LICENSE` for details.
