# Jedis Web Admin Dashboard — Implementation Plan

## Overview

Build a **built-in Web Admin Dashboard** so that anyone can manage Jedis by opening a browser tab at `http://localhost:8080`. No installation of Redis desktop clients needed — everything works out of the box.

The dashboard uses **zero external dependencies** — it is built entirely on JDK's built-in `com.sun.net.httpserver.HttpServer` for the backend and vanilla HTML/CSS/JS for the frontend.

---

## Architecture

```
┌──────────────────────────────────────────────────────────┐
│                    User's Browser                        │
│   http://localhost:8080                                   │
│                                                          │
│   ┌────────────┐  ┌────────────┐  ┌──────────────────┐  │
│   │  Metrics   │  │   Key      │  │    Web CLI        │  │
│   │  Cards     │  │  Explorer  │  │    Terminal       │  │
│   └────────────┘  └────────────┘  └──────────────────┘  │
└──────────────┬───────────────────────────────────────────┘
               │  REST API (JSON)
               ▼
┌──────────────────────────────────────────────────────────┐
│            DashboardServer (port 8080)                    │
│  ┌────────────────────────────────────────────────────┐  │
│  │  ApiHandler                                         │  │
│  │  GET  /              → serves dashboard HTML        │  │
│  │  GET  /api/info      → server stats & metrics       │  │
│  │  GET  /api/keys      → list all keys + types + TTL  │  │
│  │  POST /api/command   → execute any Redis command    │  │
│  │  POST /api/key       → set a key-value pair         │  │
│  │  DELETE /api/key     → delete a key                 │  │
│  └────────────────────────────────────────────────────┘  │
│                          │                                │
│                          ▼                                │
│   CommandRouter  ←→  DataStore  ←→  PersistenceManager   │
└──────────────────────────────────────────────────────────┘
```

---

## UI Design Philosophy

The UI must be **clean, minimal, and not cluttered**. Every section has breathing room. The design uses a premium dark theme with glassmorphism cards and subtle micro-animations.

### Landing Page Layout (Single Page, 4 Sections)

```
┌─────────────────────────────────────────────────────────┐
│  🔴  JEDIS DASHBOARD            [Connected ●]           │
├─────────────────────────────────────────────────────────┤
│                                                         │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌────────┐ │
│  │ Total    │  │ Memory   │  │ Clients  │  │ Uptime │ │
│  │ Keys: 42 │  │ 2.4 MB   │  │ 3        │  │ 1h 23m │ │
│  └──────────┘  └──────────┘  └──────────┘  └────────┘ │
│                                                         │
├─────────────────────────────────────────────────────────┤
│  KEY EXPLORER                            [+ Add Key]    │
│  ┌───────┬──────────┬─────────┬──────────┬──────────┐  │
│  │ Key   │  Type    │  Value  │  TTL     │  Actions │  │
│  ├───────┼──────────┼─────────┼──────────┼──────────┤  │
│  │ user  │ STRING   │ "john"  │  -1      │  🗑️  👁️  │  │
│  │ tasks │ LIST     │ [3 el.] │  120s    │  🗑️  👁️  │  │
│  └───────┴──────────┴─────────┴──────────┴──────────┘  │
│                                                         │
├─────────────────────────────────────────────────────────┤
│  WEB TERMINAL                                           │
│  ┌─────────────────────────────────────────────────┐   │
│  │ jedis> SET greeting "hello world"                │   │
│  │ OK                                               │   │
│  │ jedis> GET greeting                              │   │
│  │ "hello world"                                    │   │
│  │ jedis> _                                         │   │
│  └─────────────────────────────────────────────────┘   │
│                                                         │
├─────────────────────────────────────────────────────────┤
│  SERVER INFO                                            │
│  Port: 6399  │  AOF: Enabled  │  RDB: Enabled          │
│  Eviction: noeviction  │  Max Memory: unlimited         │
└─────────────────────────────────────────────────────────┘
```

### Design Tokens
- **Background**: `#0f0f23` (deep navy-black)
- **Card Background**: `rgba(255, 255, 255, 0.05)` with `backdrop-filter: blur(10px)`
- **Primary Accent**: `#e74c3c` (Jedis red)
- **Secondary Accent**: `#3498db` (info blue)
- **Success**: `#2ecc71` (green)
- **Text Primary**: `#f0f0f0`
- **Text Secondary**: `#8892b0`
- **Font**: `'Inter', sans-serif` (from Google Fonts)
- **Border Radius**: `12px` for cards, `8px` for inputs
- **Transitions**: `all 0.3s cubic-bezier(0.4, 0, 0.2, 1)`

---

## REST API Specification

### `GET /` — Dashboard HTML
Returns the full single-page HTML/CSS/JS application.

### `GET /api/info` — Server Metrics
Returns live server statistics as JSON.
```json
{
  "keys": 42,
  "memoryUsedBytes": 2457600,
  "memoryMaxBytes": 0,
  "connectedClients": 3,
  "uptimeSeconds": 4980,
  "evictionPolicy": "noeviction",
  "aofEnabled": true,
  "rdbEnabled": true,
  "port": 6399,
  "clusterEnabled": false,
  "replicaOf": null
}
```

### `GET /api/keys` — Key Explorer Data
Returns all keys with their type, value preview, and TTL.
```json
{
  "keys": [
    { "key": "user", "type": "STRING", "value": "john", "ttl": -1 },
    { "key": "tasks", "type": "LIST", "value": "[3 elements]", "ttl": 120 },
    { "key": "scores", "type": "SET", "value": "{5 members}", "ttl": -1 }
  ]
}
```

### `POST /api/command` — Execute Raw Command
Executes any Redis command and returns the raw response.
```json
// Request:
{ "command": "SET greeting hello" }

// Response:
{ "response": "OK" }
```

### `POST /api/key` — Create/Update a Key
Creates or updates a key-value pair from the UI form.
```json
// Request:
{ "key": "mykey", "value": "myvalue", "ttl": 60 }

// Response:
{ "status": "OK" }
```

### `DELETE /api/key?key=mykey` — Delete a Key
Deletes a specific key.
```json
// Response:
{ "status": "OK", "deleted": 1 }
```

---

## Proposed Changes

### New Package: `com.miniredis.dashboard`

#### [NEW] `DashboardServer.java`
**Location**: `src/main/java/com/miniredis/dashboard/DashboardServer.java`

Responsibilities:
- Creates a `com.sun.net.httpserver.HttpServer` on port `8080` (configurable via `--dashboard-port`).
- Registers API route handlers from `ApiHandler`.
- Starts/stops with the main application lifecycle.
- Adds CORS headers for local development.

```java
public class DashboardServer {
    private HttpServer server;

    public DashboardServer(int port, DataStore dataStore,
                           CommandRouter commandRouter,
                           ServerConfig config,
                           ConnectionManager connectionManager) { ... }

    public void start() throws IOException { ... }
    public void stop() { ... }
}
```

---

#### [NEW] `ApiHandler.java`
**Location**: `src/main/java/com/miniredis/dashboard/ApiHandler.java`

Responsibilities:
- Implements `HttpHandler` for each REST endpoint.
- Reads request body JSON, delegates to `CommandRouter` / `DataStore`.
- Builds JSON responses manually (no external JSON library — uses simple string concatenation for our small payloads).
- Handles errors gracefully and returns proper HTTP status codes.

Key methods:
```java
void handleInfo(HttpExchange exchange)     // GET /api/info
void handleKeys(HttpExchange exchange)     // GET /api/keys
void handleCommand(HttpExchange exchange)  // POST /api/command
void handleKeyWrite(HttpExchange exchange) // POST /api/key
void handleKeyDelete(HttpExchange exchange)// DELETE /api/key
```

---

#### [NEW] `DashboardHtml.java`
**Location**: `src/main/java/com/miniredis/dashboard/DashboardHtml.java`

Responsibilities:
- Contains a single static method `getHtml()` that returns the entire dashboard HTML/CSS/JS as a Java `String`.
- This approach avoids file-path issues across different OSes and keeps deployment as a single JAR.
- The HTML contains:
  - **Header bar** with Jedis branding and connection status indicator.
  - **Metrics section** — 4 glassmorphism cards with live stats (auto-refresh every 3 seconds).
  - **Key Explorer** — searchable, sortable table with add/delete/view actions.
  - **Web Terminal** — command-line interface with history (up/down arrows), auto-scroll, and syntax-colored output.
  - **Server Info footer** — displays persistence and config details.

---

### Modified Files

#### [MODIFY] `ServerConfig.java`
**Location**: `src/main/java/com/miniredis/config/ServerConfig.java`

Add:
```java
private int dashboardPort = 8080;
private boolean dashboardEnabled = true;

public int getDashboardPort() { return dashboardPort; }
public void setDashboardPort(int port) { this.dashboardPort = port; }
public boolean isDashboardEnabled() { return dashboardEnabled; }
public void setDashboardEnabled(boolean enabled) { this.dashboardEnabled = enabled; }
```

---

#### [MODIFY] `MiniRedisApplication.java`
**Location**: `src/main/java/com/miniredis/MiniRedisApplication.java`

Changes:
1. Import and instantiate `DashboardServer` after all components are initialized.
2. Start the dashboard server before the TCP server starts.
3. Print dashboard URL in the startup banner: `🌐 Dashboard: http://localhost:8080`.
4. Stop the dashboard in the shutdown hook.
5. Add `--dashboard-port` and `--no-dashboard` argument parsing.

---

#### [MODIFY] `RedisServer.java`
**Location**: `src/main/java/com/miniredis/server/RedisServer.java`

Changes:
1. Expose `getConnectionManager()` getter so the dashboard can query active client count.

---

## Project Directory Structure (After Changes)

```
mini-redis/
├── src/main/java/com/miniredis/
│   ├── MiniRedisApplication.java        ← MODIFIED (start dashboard)
│   ├── dashboard/                       ← NEW PACKAGE
│   │   ├── DashboardServer.java         ← NEW (HTTP server)
│   │   ├── ApiHandler.java              ← NEW (REST routes)
│   │   └── DashboardHtml.java           ← NEW (embedded HTML/CSS/JS)
│   ├── config/
│   │   └── ServerConfig.java            ← MODIFIED (dashboard port)
│   ├── server/
│   │   ├── RedisServer.java             ← MODIFIED (expose getter)
│   │   ├── ClientHandler.java
│   │   ├── ConnectionManager.java
│   │   └── NioEventLoop.java
│   ├── command/
│   ├── store/
│   ├── persistence/
│   ├── protocol/
│   ├── memory/
│   ├── security/
│   ├── pubsub/
│   ├── replication/
│   └── cluster/
├── docs/
│   ├── FEATURES.md
│   ├── NEW_FEATURES.md
│   ├── WALKTHROUGH.md
│   └── webImplementation.md             ← THIS FILE
└── pom.xml
```

---

## How Users Access Jedis

1. **Start Jedis** (one single command):
   ```bash
   mvn exec:java "-Dexec.mainClass=com.miniredis.MiniRedisApplication" "-Dexec.args=--port 6399"
   ```

2. **Open browser** → `http://localhost:8080`
   - The dashboard loads immediately.
   - Users can see all keys, run commands, and monitor the server — all from the browser.

3. **Programmatic access** (for developers):
   - Connect via `redis-cli -p 6399` for command-line usage.
   - Connect via any Redis client library (Jedis Java client, redis-py, node-redis, etc.) since we speak the RESP2 protocol.

---

## Verification Plan

### Automated Tests
```bash
mvn test
```
Ensure all existing 47 tests still pass after the changes.

### Manual Verification
1. Start Jedis with the dashboard:
   ```bash
   mvn exec:java "-Dexec.mainClass=com.miniredis.MiniRedisApplication" "-Dexec.args=--port 6399"
   ```
2. Open `http://localhost:8080` in a browser.
3. Verify:
   - **Metrics cards** display correct key count, memory, clients, and uptime.
   - **Key Explorer** lists all keys; clicking "Add Key" creates a new key; clicking delete removes it.
   - **Web Terminal** accepts commands like `SET foo bar`, `GET foo`, `KEYS *` and shows correct responses.
   - **Server Info** shows correct port, persistence, and eviction settings.
4. Disable dashboard with `--no-dashboard` flag and verify it doesn't start.
