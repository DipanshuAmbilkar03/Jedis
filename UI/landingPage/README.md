# Jedis Landing Page - Standalone Scroll-Scrub Module

This directory is a **self-contained** marketing landing page for **Jedis**, a high-performance in-memory key-value database built in pure Java.

It is intentionally decoupled from the main repository modules (`mini-redis`, `frontend`). It owns its own dependencies, Vite config, scripts, and static assets. Changes here do not affect the dashboard console or the Java engine.

## Features

1. **Scroll-scrub canvas animation** - viewport-pinned HTML5 canvas scrubs through 192 WebP frames based on scroll position.
2. **Preloader** - progressive image cache with load status before the page unlocks.
3. **Compressed WebP frames** - ~6.5 MB total (down from ~22.8 MB JPEG sources).
4. **Performance tuned** - `requestAnimationFrame` scroll throttling, redraw only on frame change, debounced resize.
5. **Interactive CLI playground** - browser-side mock terminal for commands like `PING`, `HELP`, `SET`, `GET`, `KEYS *`, and `DEL`.
6. **Reduced-motion fallback** - respects `prefers-reduced-motion: reduce` and shows a static layout.
7. **Standalone styling** - pure CSS with dark theme / glassmorphism (no shared styles from `/frontend`).

## Requirements

- Node.js 18+ (recommended: current LTS)

## Commands

Run these from this directory (`UI/landingPage`):

```bash
# Install dependencies
npm install

# Local development server
npm run dev

# Production build -> dist/
npm run build

# Preview production build
npm run preview

# Lint
npm run lint
```

## Layout

```text
UI/landingPage/
├── public/
│   └── frames/          # frame_001.webp ... frame_192.webp
├── src/
│   ├── App.jsx          # Scroll canvas, overlays, CLI, sections
│   ├── App.css          # Page styles
│   ├── index.css        # Global reset
│   └── main.jsx         # React entry
├── index.html
├── package.json
├── vite.config.js
└── README.md
```

## Isolation notes

- Package name: `jedis-landing-page` (not `frontend`)
- Does **not** use `vite-plugin-singlefile` or embed into the Java JAR
- Does **not** share code, CSS, or build scripts with `/frontend`
- Safe to develop, build, and deploy independently

## Optional static hosting

```bash
npm run build
# serve the contents of dist/ from any static host
```

If hosting under a subpath, set Vite `base` in `vite.config.js` accordingly.
