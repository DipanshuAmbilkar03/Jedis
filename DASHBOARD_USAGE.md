# Jedis Web Console & Dashboard Usage Guide

Jedis features a built-in, production-ready React Web Console and Landing Page that runs directly inside the JDK HTTP Server. This document provides step-by-step instructions on running the database server, opening the dashboard, and utilizing its features.

---

## 🚀 How to Run the Server

To start Jedis with the Web Console enabled, build and execute the Java JAR.

### 1. Build the Project
Open a terminal in the `mini-redis` root folder and build using Maven:
```bash
mvn clean package
```

### 2. Start the Database and Web Console
Run the generated jar. The Web Console is started by default:
```bash
java -jar target/mini-redis-1.0.jar --port 6380
```
* **Default Database TCP Port**: `6380` (replaces Redis standard `6379`).
* **Default Console Web Port**: `8080` (accessible at `http://localhost:8080`).

### 3. CLI Command Options
You can customize the console parameters on launch:
- `--dashboard-port <port>`: Change the web console port (e.g. `--dashboard-port 9090`).
- `--no-dashboard`: Fully disable the built-in HTTP server if running in a headless backend-only environment.

---

## 🖥️ Using the Web Interface

Once started, open your web browser and navigate to **`http://localhost:8080`**.

### 1. The Landing Page (Interactive Sandbox)
- **Visuals**: Features a premium, pitch-black high-tech theme with smooth page animations and scroll reveal effects.
- **Stats Counter**: Real-time display showing total keys, active command counts, memory usage, and connected clients.
- **Interactive Sandbox Console**: Allows clicking pre-written commands (like `PING`, `SET`, `INCR`, `KEYS *`) to auto-execute them in the interactive CLI window. 
- **Offline Mode**: If the database server is stopped, the landing page terminal operates in a sandbox demo mode, letting you play with Redis-like operations locally in the browser.

### 2. The Admin Panel Dashboard
Click **"Open Dashboard"** or **"Admin Console"** on the navigation bar to access the live administrative console:
- **Connection Badge**: A glowing indicator in the top right shows if the UI is actively connected (`CONNECTED` green) or offline (`SANDBOX MODE` red).
- **Live Metrics**: Grid displaying real-time database parameters:
  - **Total Keys**: Active keys in the database.
  - **Memory Usage**: Bytes allocated by direct buffers (off-heap memory).
  - **Connected Clients**: Count of live TCP client sockets.
  - **Server Uptime**: Server run duration (HH:MM:SS format).
- **Key Explorer**:
  - **CRUD Operations**: Use the form to set new keys, values, and expiration times (TTL in seconds).
  - **Live Filter**: Search box to instantly filter database keys by name.
  - **Dynamic Preview**: Shows data types (`STRING`, `LIST`, `SET`, `HASH`) along with key value previews.
  - **Delete Actions**: Direct trashcan buttons to remove keys with confirmation.
- **Live Web CLI**:
  - A real-time command terminal connected directly to the database engine.
  - Type commands (e.g. `GET key`, `LPUSH mylist item`, `EXPIRE key 60`) and hit **Enter** to execute.
  - Supports command history: Press the **Up Arrow** and **Down Arrow** keys to scrub through previously run commands.
- **Footer Diagnostics**: Real-time status display of the persistence engine (AOF/RDB state), replication mode (Master or Replica), cluster sharding, and memory eviction policy.
