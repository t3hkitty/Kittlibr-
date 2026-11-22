---
name: github-pages-builder
description: Configures, builds, and deploys static web frontends and interactive consoles for GitHub Pages (*.github.io and custom domains) with static fallback modes, relative asset paths, .nojekyll guards, and GitHub Actions deployment workflows.
---

# GitHub Pages Static Deployment Skill

When building or adapting a web app, console, or frontend (such as Kittlibr UI, AnyMD browser viewer, or interactive tools) for **GitHub Pages**:

## 1. Static Host & Dual-Mode Backend Guardrails
- **No Server-Side Runtime Assumption:** GitHub Pages serves static assets only (`HTML`, `CSS`, `JS`, `JSON`, `YAML`, `MD`). Never assume Python (`FastAPI`, `uvicorn`), `rclone rcd`, or SQLite binaries run on the GitHub Pages host.
- **Graceful Localhost Fallback (`Static / Bring-Your-Own-Vault Mode`):**
  - If the frontend connects to local loopback services (`127.0.0.1:3050`, `127.0.0.1:5820`, `127.0.0.1:5572`), wrap health checks in a fast timeout (`<= 800ms`).
  - When localhost daemons are unreachable (e.g., viewing on `*.github.io`), automatically switch to **Static Client Mode**: load bundled sample `.anymd/index_0000.yaml` + `.md` files or let the user open local `.md` / `.epub` / `.yaml` files directly via the browser File System / drag-and-drop API with zero console errors.

## 2. Asset Pathing & Jekyll Bypass
- **Subpath-Safe URLs:** Configure Vite/Webpack/static HTML to use relative paths (`base: './'`) or repo-prefixed base paths so assets, fonts, and `kookit.min.js` resolve properly under `https://<user>.github.io/<repo>/`.
- **`.nojekyll` & SPA Routing:** Always emit an empty `.nojekyll` file in the publish root (`dist/` or `docs/`) so underscore-prefixed or dot-prefixed sample directories are preserved, and include a `404.html` redirect shim if using client-side history routing.

## 3. Security & Privacy Hygiene
- **Zero Hardcoded Credentials or Local Machine Paths:** Strip all local credentials (`--rc-pass`, API keys, tokens) and personal filesystem paths (`C:\Users\...`, `/home/antigravity/...`) from static builds before committing.

## 4. Automated Deployment Artifact
- Generate `.github/workflows/deploy-pages.yml` using `actions/checkout@v4`, `actions/configure-pages@v5`, `actions/upload-pages-artifact@v3`, and `actions/deploy-pages@v4` triggered on pushes to `main` (or `workflow_dispatch`).
