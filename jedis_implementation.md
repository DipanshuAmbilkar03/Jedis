# Jedis Database Engine - Implementation Architecture

This document provides a detailed explanation of the project directory structure, code packages, and the individual source files that make up the **Jedis** in-memory key-value database engine (`mini-redis` project).

---

## 📂 Project Directory Structure

```text
java project/
│
├── mini-redis/                  # Main Java Backend Project (Maven)
│   ├── src/
│   │   ├── main/java/com/miniredis/
│   │   │   ├── MiniRedisApplication.java   # App Entry Point & CLI flags
│   │   │   │
│   │   │   ├── cluster/         # CRC16, Node topologies, Slot mapping
│   │   │   ├── command/         # Routing and specific Command Handlers
│   │   │   ├── config/          # Server configuration properties
│   │   │   ├── dashboard/       # Embedded HTTP server & web console endpoints
│   │   │   ├── memory/          # Off-heap allocators & LRU/LFU evictions
│   │   │   ├── persistence/     # Write-Ahead AOF logs & RDB Snapshots
│   │   │   ├── protocol/        # RESP2 wire format parser & encoder
│   │   │   ├── pubsub/          # Broadcast channel brokers
│   │   │   ├── replication/     # Master-Replica sync buffers & replication loops
│   │   │   ├── security/        # ACL users & TLS socket connection wrappers
│   │   │   ├── server/          # Client socket handlers & NIO Selectors
│   │   │   └── store/           # Core in-memory maps & key expiry engines
│   │   │
│   │   └── test/java/com/miniredis/        # Unit & Integration Test Suites
│   │
│   └── pom.xml                  # Maven Project Object Model (Dependencies)
│
├── frontend/                    # Vite React Frontend Project (Admin UI Console)
│   ├── src/                     # React Components, Global styles, App views
│   ├── package.json             # NPM package scripts & Vite configurations
│   └── vite.config.js           # Bundle settings (configured for single-file inline compilation)
│
└── DASHBOARD_USAGE.md           # Guide on how to build, start, and run the server
```

---

## 📦 Package & File Breakdown (Backend Engine)

### 1. App Entry Point (`com.miniredis`)
* **`MiniRedisApplication.java`**: Integrates the database engine lifecycle. Parses command-line arguments (e.g. `--port`, `--no-aof`, `--dashboard-port`), starts the thread-safe eviction engines, spins up the web dashboard, and registers JVM shutdown hooks to gracefully flush snapshots to disk.

### 2. `cluster` (Clustering & Sharding)
* **`CRC16.java`**: Implements the CRC16 checksum calculation used to map keys to one of the 16,384 database cluster slots.
* **`ClusterNode.java`**: Models a cluster node representation (ID, Host, Port, Role, and managed slots).
* **`ClusterManager.java`**: Manages cluster topologies, cluster join operations, redirection logic (e.g. `-ASK` or `-MOVED`), and slot mapping.

### 3. `command` (Command Dispatching & Logic)
* **`Command.java`**: Data class representing a parsed client command with its name and argument array.
* **`CommandHandler.java`**: Interface specifying the contract (`execute()`) for all executable command implementations.
* **`CommandRouter.java`**: Maps command names to their handlers. Directs execution to datatype-specific packages:
  * **`string/`**: Key-value operations (GET, SET, MSET, MGET, INCR, DECR).
  * **`list/`**: Queue structures (LPUSH, RPUSH, LPOP, RPOP, LRANGE).
  * **`set/`**: Unordered unique groups (SADD, SREM, SMEMBERS).
  * **`hash/`**: Structured objects (HSET, HGET, HGETALL).
  * **`key/`**: Metadata controls (DEL, EXPIRE, TTL, KEYS, EXISTS, TYPE).
  * **`pubsub/`**: Broadcasting (SUBSCRIBE, PUBLISH, UNSUBSCRIBE).
  * **`transaction/`**: Atomic queues (MULTI, EXEC, DISCARD).
  * **`server/`**: Maintenance tasks (PING, ECHO, INFO, SAVE, FLUSHDB).

### 4. `config` (Server Options)
* **`ServerConfig.java`**: Data holder for runtime settings. Includes defaults and fields for ports, data directories, persistence engines, off-heap allocations, replication masters, memory limits, and the built-in dashboard properties.

### 5. `dashboard` (Built-in Console Web Panel)
* **`DashboardServer.java`**: Sets up the JDK's built-in `com.sun.net.httpserver.HttpServer` with a thread pool to handle concurrent dashboard clients.
* **`ApiHandler.java`**: Implements HTTP endpoints to serve the UI bundle (`GET /`), database statistics (`GET /api/info`), key previews (`GET /api/keys`), CRUD write actions (`POST /api/key`), delete requests (`DELETE /api/key`), and CLI terminal commands (`POST /api/command`).
* **`DashboardHtml.java`**: Contains the compiled React frontend code represented as inlined, chunked Java string variables.

### 6. `memory` (Direct Memory Allocation & Evictions)
* **`EvictionPolicy.java`**: Enum representing active eviction strategies (`NOEVICTION`, `ALLKEYS_LRU`, `ALLKEYS_LFU`, `VOLATILE_LRU`, etc.).
* **`MemoryManager.java`**: Monitors heap and off-heap memory thresholds. Triggers eviction algorithms when memory bounds are reached to purge expired or least-used keys.
* **`OffHeapPointer.java`**: Structure that tracks the offset and capacity address of data stored directly inside off-heap buffers.
* **`OffHeapAllocator.java`**: Manages native Direct ByteBuffers to allocate and free memory outside JVM Garbage Collection cycles.

### 7. `persistence` (Disk Durability)
* **`AofEngine.java`**: Handles the Append-Only File transaction logging. Automatically triggers AOF rewrite tasks when the log file grows too large to optimize disk space.
* **`RdbSnapshotter.java`**: Periodically dumps the database structure to a binary JSON representation (`dump.rdb`) in the background.
* **`PersistenceManager.java`**: Orchestrates state recovery on boot (loads RDB then replays AOF) and coordinates background snapshot threads.

### 8. `protocol` (RESP2 Protocol Parser)
* **`RespType.java`**: Enum defining RESP2 data type prefixes (Simple String `+`, Error `-`, Integer `:`, Bulk String `$`, Array `*`).
* **`RespEncoder.java`**: Converts Java types back into the RESP2 wire format bytes.
* **`RespParser.java`**: A stateful stream parser that processes incoming byte streams from standard blocking client sockets.
* **`NioRespParser.java`**: A non-blocking, buffer-based parser that handles asynchronous socket reads inside the NIO Selector event loop.

### 9. `pubsub` (Publish/Subscribe Channel broker)
* **`PubSubManager.java`**: Implements thread-safe pub/sub messaging channels, maintaining lists of active subscribers to broadcast messages across client TCP sockets.

### 10. `replication` (HA Master-Replica Sync)
* **`ReplicationBacklog.java`**: Circular buffer that caches recent write commands to allow disconnected replica nodes to perform partial resynchronization (PSYNC) on reconnection.
* **`ReplicationManager.java`**: Coordinates socket links between Master and Replica nodes, replicating transaction logs in real time.

### 11. `security` (Access Control Lists & TLS)
* **`AclUser.java`**: Models a user account with passwords, enabled flags, and authorized command rules.
* **`AclManager.java`**: Enforces system access permissions based on credentials.
* **`TlsConfig.java`**: Loads keystores and certificate files to initialize `SSLContext`.
* **`TlsConnection.java`**: Wraps standard raw sockets with TLS/SSL encryption.

### 12. `server` (Networking Layer)
* **`ConnectionManager.java`**: Monitors client connections, enforces maximum client counts, and closes connections that exceed the configured idle timeout.
* **`ClientHandler.java`**: A thread-bound task that reads commands from a client socket, parses them, executes them via the Command Router, and writes the RESP replies back to the client.
* **`NioEventLoop.java`**: Uses Java NIO Selector multiplexing to handle thousands of concurrent client socket events on a single loop thread.
* **`RedisServer.java`**: Boots the network listener, configures thread pools, manages connection timeouts, and coordinates network shutdown.

### 13. `store` (Data Store Layer)
* **`DataType.java`**: Enum for the database types (STRING, LIST, SET, HASH).
* **`RedisObject.java`**: Wrapper class for values in the database, containing type info, raw value object references, and direct pointers if the object resides off-heap.
* **`ExpiryManager.java`**: Tracks key TTL timestamps and periodically cleans up expired keys in the background.
* **`DataStore.java`**: The core in-memory engine storing the key-value dictionary and coordinating read, write, update, and expiration checks.
