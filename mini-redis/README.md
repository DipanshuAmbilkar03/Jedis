# 🔴 Mini Redis Engine

A fully functional, in-memory, thread-safe key-value data store built completely from scratch in **pure Java** (zero third-party dependencies, except JUnit for tests). 

It implements the real Redis **RESP2 wire protocol**, making it compatible out-of-the-box with the official `redis-cli`, Jedis, Lettuce, or any standard Redis client library.

---

## Architecture Overview

The database engine is built around a concurrent storage engine with modular subsystems for connection handling, command routing, wire serialization, expiration policies, and active disk persistence.

```
┌────────────────────────────────────────────────────────────────────────┐
│                          Mini Redis Server                             │
│                                                                        │
│  ┌──────────────┐     ┌─────────────────┐      ┌────────────────────┐  │
│  │ TCP Listener │────▶│  RESP Protocol  │─────▶│   Command Router   │  │
│  │ (ServerSocket)│    │     Parser      │      │ (Command Dispatch) │  │
│  └──────────────┘     └─────────────────┘      └──────────┬─────────┘  │
│                                                           │            │
│          ┌────────────────────────────────────────────────┘            │
│          ▼                                                             │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │                        Command Handlers                          │  │
│  │   String   │   List   │   Set   │   Hash   │   Key   │   PubSub    │  │
│  └────────────────────────────────┬─────────────────────────────────┘  │
│                                   ▼                                    │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │                       Data Store Layer                           │  │
│  │  ┌─────────────────┐ ┌─────────────────┐ ┌────────────────────┐  │  │
│  │  │  KeyValueStore  │ │  ExpiryManager  │ │   PubSubManager    │  │  │
│  │  │ (ConcurrentMap) │ │ (Lazy + Active) │ │ (Channels → Subs)  │  │  │
│  │  └─────────────────┘ └─────────────────┘ └────────────────────┘  │  │
│  └────────────────────────────────┬─────────────────────────────────┘  │
│                                   ▼                                    │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │                       Persistence Layer                          │  │
│  │           AOF Engine           │          RDB Snapshotter        │  │
│  │     (Write-Ahead Logging)      │     (Periodic State Dumps)      │  │
│  └──────────────────────────────────────────────────────────────────┘  │
└────────────────────────────────────────────────────────────────────────┘
```

### Request Lifecycle & Data Flow:
1. **TCP Connection**: Clients establish TCP connections on port `6380`. Each client handler runs on a thread pool managed by an `ExecutorService`.
2. **RESP2 Protocol Parser**: The handler parses incoming bytes according to the RESP2 format (e.g. bulk strings, arrays) into a structured `Command`.
3. **Routing**: The `CommandRouter` identifies the command name (e.g., `SET`, `HGET`) and dispatches it to the corresponding command execution subclass.
4. **Data Store Operations**: The command handler performs atomic or read-write locked operations against the `DataStore` (which leverages thread-safe data structures like `ConcurrentHashMap` wrapped with helper objects).
5. **AOF Logging**: If the command modifies the data store, it is written to the Append-Only File (AOF) on disk.
6. **Response Encoding**: The command output is encoded back into a RESP2 byte payload and flushed over the client socket.

---

## Key Features

* **Zero External Dependencies** — Written using standard JDK classes from first principles (sockets, threads, locking, concurrency).
* **RESP2 Compatibility** — Full compliance with Redis RESP2. Test it by pointing your browser, `redis-cli`, or external Java libraries directly to port `6380`.
* **25+ Supported Commands** — Complete implementation of Strings, Lists, Sets, Hashes, Key Expiry, Transactions, and Pub/Sub routing.
* **Dual Expiry Strategy** — Combines passive (lazy) eviction upon key access with a background active task that periodically samples and evicts expired keys.
* **Dual Persistence Engines**:
  - **AOF (Append Only File)**: Logs every write command sequentially for durability.
  - **RDB (Redis Database Snapshot)**: Performs periodic, non-blocking state dumps serialized as JSON.
- **Embedded Web UI Server**: Starts a lightweight HTTP server on port `8080` (or custom port) to expose the Admin Console.

---

##  Technical Stack

* **Language**: Java 25 (using modern features like record types, pattern matching, text blocks)
* **Concurrency**: `ConcurrentHashMap`, `ReentrantReadWriteLock`, `ScheduledExecutorService`
* **Networking**: Multiplexed `ServerSocket` connection handler
* **Testing**: JUnit 5 + Integration Roundtrip TCP Tests

---

## Directory Structure

```
mini-redis/
├── pom.xml                                    # Maven Build Configuration
├── README.md                                  # Project Documentation
├── data/                                      # Runtime Persistence Directory (gitignored)
│   ├── appendonly.aof                         # Write-Ahead Log (AOF)
│   └── dump.json                              # JSON State Snapshots (RDB)
│
└── src/
    ├── main/java/com/miniredis/
    │   ├── MiniRedisApplication.java          # Server main entrypoint
    │   ├── config/                            # Server configurations
    │   ├── server/                            # TCP handler & client connection loops
    │   ├── protocol/                          # RESP2 parser & encoder
    │   ├── command/                           # 25+ command handlers (sub-packages for string, list, set, etc.)
    │   ├── store/                             # DataStore & ExpiryManager
    │   ├── pubsub/                            # Pub/Sub channels subscription manager
    │   └── persistence/                       # AOF & RDB snapshot engines
    │
    └── test/java/com/miniredis/               # JUnit 5 Unit & Integration Tests
```

---

##  Supported Commands

###  Strings
* `SET key value [EX seconds] [PX ms] [NX|XX]`
* `GET key`
* `MSET key value [key value ...]`
* `MGET key [key ...]`
* `INCR key`
* `DECR key`

### Lists
* `LPUSH key value [value ...]`
* `RPUSH key value [value ...]`
* `LPOP key`
* `RPOP key`
* `LRANGE key start stop`

### Sets
* `SADD key member [member ...]`
* `SMEMBERS key`
* `SREM key member [member ...]`

### Hashes
* `HSET key field value [field value ...]`
* `HGET key field`
* `HGETALL key`

### Key Management
* `DEL key [key ...]`
* `EXPIRE key seconds`
* `TTL key`
* `EXISTS key [key ...]`
* `TYPE key`
* `KEYS pattern` (Supports glob matching: `*`, `?`)

### Transactions
* `MULTI` (Enter transaction mode / queue commands)
* `EXEC` (Execute queued commands atomically)
* `DISCARD` (Discard queued commands)

### Pub/Sub
* `SUBSCRIBE channel [channel ...]`
* `PUBLISH channel message`
* `UNSUBSCRIBE [channel ...]`

### Server Management
* `PING [message]`
* `ECHO message`
* `INFO`
* `FLUSHDB`
* `SAVE`

---

### How to Build and Run

### 1. Compile and Run Tests
Ensure you have JDK 25 and Maven installed:
```bash
mvn clean test
```

### 2. Build the Executable JAR
```bash
mvn package
```

### 3. Run the Server
The executable JAR is generated in the `target/` directory:
```bash
java -jar target/mini-redis-1.0.0.jar
```
By default, the server starts on port **`6380`** (to avoid conflicts with standard Redis running on `6379`).

#### Options:
* `--port, -p <port>`: Run database server on a custom TCP port (default: `6380`)
* `--dashboard-port <port>`: Run web dashboard on custom HTTP port (default: `8080`)
* `--no-dashboard`: Disable the dashboard web server
* `--dir <path>`: Specify data storage directory
* `--no-aof`: Disable AOF persistence logging
* `--no-rdb`: Disable periodic RDB snapshot dumps

Example:
```bash
java -jar target/mini-redis-1.0.0.jar -p 6381 --no-aof
```

---

## 🔌 Connecting with `redis-cli`

Once the server is running, you can connect using the official `redis-cli` client:
```bash
redis-cli -p 6380
```

### Example Usage:
```
127.0.0.1:6380> PING
PONG
127.0.0.1:6380> SET hello "world"
OK
127.0.0.1:6380> GET hello
"world"
127.0.0.1:6380> EXPIRE hello 10
(integer) 1
127.0.0.1:6380> TTL hello
(integer) 9
127.0.0.1:6380> MULTI
OK
127.0.0.1:6380(TX)> SET x 100
QUEUED
127.0.0.1:6380(TX)> INCR x
QUEUED
127.0.0.1:6380(TX)> EXEC
1) OK
2) (integer) 101
```
