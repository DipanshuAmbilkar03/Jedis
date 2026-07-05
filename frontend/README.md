#  Jedis Developer Console & Admin Panel

A modern, single-page web administration dashboard for the Jedis Database. Styled with a premium dark theme, glassmorphism containers, responsive layouts, and interactive micro-animations.

---

##  What Is In This Directory?

This directory contains the React + Vite frontend source code. The dashboard operates in two modes:
1. **Public Landing Page Mode**: A detailed product landing page detailing the database architecture, features, and an **embedded interactive playground shell** to try out RESP2 commands locally or against a live local server.
2. **Developer Admin Console Mode** (accessed via `/admin`, login: `admin`/`admin`): A private, live dashboard showing system health charts, key storage tables, and shell terminals.

---

##  Main Console Features

### 1. Interactive Landing Page
- **Hero & Installation Mockups**: Modern intro page highlighting the database specifications, with a visual console terminal mock showing download and start commands.
- **Embedded Database Playground**: A responsive CLI console directly on the home page. Runs commands using an offline mock database sandbox when the server is offline, or forwards them to the real backend when connected.

### 2. Private Admin Console Subsections
*  **Database Overview**: Shows active connection status, database uptime, number of stored keys, active clients connected, memory usage gauges, and real-time persistence stats.
*  **Key Explorer**: A searchable data table listing all active database keys, their data types (STRING, LIST, SET, HASH), TTL, and value previews. Allows creating new keys via a visual forms editor, viewing item values in popup dialogues, or deleting keys with a single click.
*  **Interactive Live CLI Shell**: A fully featured terminal shell console. Supports command history mapping (Up/Down arrow key recall), clear screen commands, and syntax-colored output logs.
*  **Persistence & Configuration**: Displays active configurations, eviction policies, data directories, RDB snapshot schedules, and cluster status.

---

##  Tech Stack

* **Core**: React 19, Vite 8
* **Styling**: Vanilla CSS (Tailwind-free for maximum layout control, featuring blur filters, custom glow meshes, and smooth flex/grid panels)
* **Linter**: Oxlint (fast, rust-based JS/JSX compiler and linter)
* **Single-File Bundler**: `vite-plugin-singlefile` (inlines all CSS, JS, and HTML markup into a single self-contained output file)

---

##  Compilation & Deployment Pipeline

The dashboard is served by the built-in HTTP server of the Java backend. To avoid cross-origin configurations or path resolution errors across different Operating Systems, the frontend is compiled into a single file and embedded as a source code text block inside the backend JAR:

```
┌──────────────┐          ┌───────────────────┐          ┌───────────────────────────┐
│ React Source │──(Vite)─▶│  dist/index.html  │──(node)─▶│    DashboardHtml.java     │
│ (src/*.jsx)  │          │ (Self-contained)  │          │ (Embedded Java text block)│
└──────────────┘          └───────────────────┘          └───────────────────────────┘
```

### Build Commands:

1. **Run Development Server** (with local backend proxy):
   ```bash
   npm run dev
   ```
   *Note: Automatically proxies API requests to `http://localhost:8080` where the Java backend serves the REST endpoints.*

2. **Run Linter**:
   ```bash
   npm run lint
   ```

3. **Build Frontend Bundle**:
   ```bash
   npm run build
   ```
   *Compiles all assets and outputs a single 250KB file at `dist/index.html`.*

4. **Inject and Embed in Java Backend**:
   ```bash
   node update_dashboard_html.js
   ```
   *Reads `dist/index.html`, escapes backslashes and triple quotes, splits the content into chunks, and writes it directly into the Java class `DashboardHtml.java` in the `mini-redis` project.*
