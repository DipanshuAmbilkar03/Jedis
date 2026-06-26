# 🔴 Mini Redis

A fully functional in-memory key-value data store built from scratch in pure Java (zero external dependencies, except JUnit for tests). 

It implements the real Redis **RESP2 wire protocol**, making it compatible with the official `redis-cli` and any standard Redis client libraries.

---

## 🚀 Key Features

* **Zero External Dependencies** — Built using Java standard library features.
* **TCP Multi-Client Support** — Handles multiple concurrent client connections via a thread pool.
* **Redis RESP2 Protocol Compliant** — Works out-of-the-box with `redis-cli`.
* **25+ Redis Commands Supported** — Strings, Lists, Sets, Hashes, Key Management, Transactions (`MULTI`/`EXEC`), and Pub/Sub.
* **Dual Expiry Strategy** — Lazy eviction (on access) and Active eviction (background randomized sampling).
* **Two Persistence Engines** — Append-Only File (AOF) for write journaling, and RDB JSON snapshots for fast recovery.

---

## 🛠️ Tech Stack & Architecture

* **Language**: Java 25 (JDK 25)
* **Networking**: TCP `ServerSocket` + Thread-per-client pool
* **Data Storage**: Thread-safe `ConcurrentHashMap` with type-tagged wrappers
* **Build System**: Maven 3.9+
* **Testing**: JUnit 5

---

## 📁 Directory Structure

```
mini-redis/
├── pom.xml                                    # Maven Build Configuration
├── README.md                                  # Project Documentation
├── IMPLEMENTATION_PLAN.md                     # Design & Architecture Spec
├── PROJECT_DESCRIPTION.md                     # Project Scope Overview
├── data/                                      # Runtime Persistence Directory (gitignored)
│   ├── appendonly.aof                         # Write-Ahead Log (AOF)
│   └── dump.json                              # JSON State Snapshots (RDB)
│
└── src/
    ├── main/java/com/miniredis/
    │   ├── MiniRedisApplication.java          # Server main entrypoint
    │   ├── config/                            # Server settings
    │   ├── server/                            # TCP handler and client connection loops
    │   ├── protocol/                          # RESP2 parser and encoder
    │   ├── command/                           # 25+ command handlers (sub-packages for string, list, set, etc.)
    │   ├── store/                             # DataStore and ExpiryManager
    │   ├── pubsub/                            # Channels subscription manager
    │   └── persistence/                       # AOF and RDB snapshot engines
    │
    └── test/java/com/miniredis/               # JUnit 5 Unit & Integration Tests
```

---

## ⚙️ Supported Commands

### 📝 Strings
* `SET key value [EX seconds] [PX ms] [NX|XX]`
* `GET key`
* `MSET key value [key value ...]`
* `MGET key [key ...]`
* `INCR key`
* `DECR key`

### 📋 Lists
* `LPUSH key value [value ...]`
* `RPUSH key value [value ...]`
* `LPOP key`
* `RPOP key`
* `LRANGE key start stop`

### 👥 Sets
* `SADD key member [member ...]`
* `SMEMBERS key`
* `SREM key member [member ...]`

### 🔍 Hashes
* `HSET key field value [field value ...]`
* `HGET key field`
* `HGETALL key`

### 🔑 Key Management
* `DEL key [key ...]`
* `EXPIRE key seconds`
* `TTL key`
* `EXISTS key [key ...]`
* `TYPE key`
* `KEYS pattern` (Supports glob: `*`, `?`)

### 🤝 Transactions
* `MULTI` (Enter transaction mode / queue commands)
* `EXEC` (Execute queued commands atomically)
* `DISCARD` (Discard queued commands)

### 📣 Pub/Sub
* `SUBSCRIBE channel [channel ...]`
* `PUBLISH channel message`
* `UNSUBSCRIBE [channel ...]`

### ⚙️ Server Management
* `PING [message]`
* `ECHO message`
* `INFO`
* `FLUSHDB`
* `SAVE`

---

## ⚡ How to Build and Run

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
* `--port, -p <port>`: Run on a custom port
* `--dir <path>`: Specify data storage directory
* `--no-aof`: Disable AOF persistence
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

### Example usage:
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
