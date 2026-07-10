# Jedis In-Memory Database & Web Console

```
        ██╗███████╗██████╗ ██╗███████╗    ██████╗ ██████╗ 
        ██║██╔════╝██╔══██╗██║██╔════╝    ██╔══██╗██╔══██╗
        ██║█████╗  ██║  ██║██║███████╗    ██║  ██║██████╔╝
   ██   ██║██╔══╝  ██║  ██║██║╚════██║    ██║  ██║██╔══██╗
   ╚█████╔╝███████╗██████╔╝██║███████║    ██████╔╝██████╔╝
    ╚════╝ ╚══════╝╚═════╝ ╚═╝╚══════╝    ╚═════╝ ╚═════╝ 
                   in-memory database engine
```

> A high-performance, concurrent, in-memory key-value database engine built from the ground up in **pure Java ☕ (zero external dependencies)**, featuring a modern React-based **built-in web admin dashboard console**.

This repository is split into two primary components: the backend Java database engine (`mini-redis`) and the React-based developer administration console (`frontend`).

---

## System Architecture Overview

The system is designed to run as a single process with zero configuration. The React frontend is compiled into a single self-contained HTML page and embedded directly into the Java executable JAR so that the database and its control console are delivered as a single package.

```
                  ┌────────────────────────────────────────────────────────┐
                  │                    User's Browser                      │
                  │   http://localhost:8080                                 │
                  │   (Live Metrics, Key Explorer, Web CLI Terminal)       │
                  └──────────────────────────┬─────────────────────────────┘
                                             │ REST API (JSON)
                                             ▼
┌──────────────────────────────────────────────────────────────────────────┐
│                            Jedis Database Engine                         │
│                                                                          │
│  ┌───────────────────────┐   ┌───────────────────┐   ┌────────────────┐  │
│  │   DashboardServer     │──▶│   CommandRouter   │◀──│   TCP Server   │  │
│  │     (Port 8080)       │   │    (RESP2 Parser)  │   │  (Port 6380)   │  │
│  └───────────────────────┘   └─────────┬─────────┘   └────────┬───────┘  │
│                                        │                      │          │
│                                        ▼                      ▼          │
│                              ┌───────────────────┐   ┌────────────────┐  │
│                              │    Data Store     │◀──│   redis-cli    │  │
│                              │ (ConcurrentHashMap│   │ (External App) │  │
│                              │   & ExpiryMgr)    │   └────────────────┘  │
│                              └─────────┬─────────┘                       │
│                                        │                                 │
│                                        ▼                                 │
│                              ┌───────────────────┐                       │
│                              │ Persistence Layer │                       │
│                              │  (AOF + RDB JSON) │                       │
│                              └───────────────────┘                       │
└──────────────────────────────────────────────────────────────────────────┘
```

---

## Repository Structure

* **[`/mini-redis`](file:///d:/project/java%20project/mini-redis)**: The backend Java source code. Built from first principles, utilizing non-blocking sockets, a thread-per-client model, and custom expiration/persistence tasks.
* **[`/frontend`](file:///d:/project/java%20project/frontend)**: A React + Vite dashboard styled with premium glassmorphism. It includes real-time telemetry gauges, a searchable key explorer, a visual add-key editor, and an interactive web terminal shell.

---

##  Quick Start (Build & Run)

To compile and start both the backend server and the embedded web console, follow these simple steps:

### Prerequisites
- **Java JDK 25** or higher installed.
- **Node.js** (for building the frontend assets).
- **Maven** (for compiling the Java database engine).

### Build steps:

1. **Build the Frontend Assets**:
   ```bash
   cd frontend
   npm install
   npm run build
   node update_dashboard_html.js
   ```

2. **Package and Run the Database**:
   ```bash
   cd ../mini-redis
   mvn clean package
   java -jar target/mini-redis-1.0.0.jar
   ```

3. **Access the Console**:
   - Open your browser to [http://localhost:8080](http://localhost:8080) for the public landing page and interactive database playground.
   - Go to [http://localhost:8080/admin](http://localhost:8080/admin) to log in as admin (Credentials: `admin` / `admin`) and manage the live database.

4. **Connect with `redis-cli`**:
   ```bash
   redis-cli -p 6380
   ```

For detailed specifications of the backend database or frontend dashboard, view the READMEs in their respective directories:
- [Backend (mini-redis) Specification](file:///d:/project/java%20project/mini-redis/README.md)
- [Frontend (React + Vite Console) Guide](file:///d:/project/java%20project/frontend/README.md)


---

## Marketing Landing Page (Separate Module)

The product marketing page lives at `UI/landingPage` and is **not** part of the embedded dashboard build.

```bash
cd UI/landingPage
npm install
npm run dev
```

See `UI/landingPage/README.md` for details. It does not share code or build steps with `/frontend` or `/mini-redis`.
