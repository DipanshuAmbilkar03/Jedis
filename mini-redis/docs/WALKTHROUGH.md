# Mini Redis: A Java-Centric Architectural Guide

Welcome! Since you already know Java, this guide is designed to explain **what was built**, **why it matters**, and **how it works under the hood** using Java concepts you are already familiar with.

---

## 1. The Big Picture
Originally, your educational prototype was a simple Java program that listened on a TCP socket, spawned a new `Thread` for every connecting client, and stored keys and values in a standard on-heap `ConcurrentHashMap`. 

While fine for learning, that approach has major production limits:
1. **Thread limits**: Spawning 10,000 threads for 10,000 connections crashes the JVM with `OutOfMemoryError` (due to thread stack allocation limits).
2. **GC Pauses**: Storing millions of keys on the JVM Heap causes Java's Garbage Collector (GC) to trigger "Stop-the-World" pauses, making the database freeze.
3. **Loss of Data**: If the server crashes, all in-memory data is lost.
4. **No Security**: Anyone could connect and read or delete all data.

Here is how we solved these problems in Java.

---

## 2. The 5 Core Upgrades Explained

### 🚀 Upgrade A: High-Performance I/O (NIO Event Loop)
* **What it is**: Instead of spawning a Java `Thread` for every connection, we use a single thread to manage thousands of client connections.
* **How it works in Java**:
  * We use **Java NIO (Non-blocking I/O)**.
  * We set up a `ServerSocketChannel` in non-blocking mode and register it with a `Selector`.
  * The selector acts as an OS-level event notifier. When a client sends bytes, the selector alerts our single event loop thread, which parses and executes the command.
  * **Java class to check**: [NioEventLoop.java](file:///d:/project/java%20project/mini-redis/src/main/java/com/miniredis/server/NioEventLoop.java)

### 🧠 Upgrade B: GC-Free Storage (Off-Heap Allocator)
* **What it is**: Storing data outside the JVM Garbage Collector's control to guarantee zero GC pauses.
* **How it works in Java**:
  * We allocated direct native memory buffers outside the JVM heap using `ByteBuffer.allocateDirect(size)`.
  * We built a custom **Slab Allocator** (`OffHeapAllocator`). It requests chunks of native memory and cuts them into smaller slices for keys/values.
  * Since the data is off-heap, Java's Garbage Collector never scans it. We manage this memory manually using pointers (`OffHeapPointer`).
  * **Java classes to check**:
    * [OffHeapAllocator.java](file:///d:/project/java%20project/mini-redis/src/main/java/com/miniredis/memory/OffHeapAllocator.java)
    * [OffHeapPointer.java](file:///d:/project/java%20project/mini-redis/src/main/java/com/miniredis/memory/OffHeapPointer.java)

### 💾 Upgrade C: Non-Blocking Persistence (RDB & AOF)
* **What it is**: Writing data to disk periodically without blocking client operations.
* **How it works in Java**:
  * **Binary RDB (Snapshots)**: We write the state to a binary file `dump.rdb`. To prevent blocking, we use a background thread pool executor. The main thread makes a fast, thread-safe shallow copy of the key pointers, while the background thread writes them to disk.
  * **AOF Compaction (Rewrite)**: Every write is appended to a log file (`appendonly.aof`). When it gets too large, a background scheduler reads the current off-heap state and writes a brand new, minimal log file, replacing the old one.
  * **Java classes to check**:
    * [RdbSnapshotter.java](file:///d:/project/java%20project/mini-redis/src/main/java/com/miniredis/persistence/RdbSnapshotter.java)
    * [AofEngine.java](file:///d:/project/java%20project/mini-redis/src/main/java/com/miniredis/persistence/AofEngine.java)

### 🔒 Upgrade D: Security (TLS & ACL)
* **What it is**: Encrypting database connections and restricting who can run which commands.
* **How it works in Java**:
  * **TLS Encryption**: We wrap our non-blocking `SocketChannel` streams in Java's `SSLEngine`. It encrypts/decrypts byte packets in-flight.
  * **Access Control Lists (ACL)**: We created `AclUser` and `AclManager`. When a command is routed, the system verifies if the currently authenticated user has the permission rule (e.g., `+GET`, `-SET`).
  * **Java classes to check**:
    * [TlsConnection.java](file:///d:/project/java%20project/mini-redis/src/main/java/com/miniredis/security/TlsConnection.java)
    * [AclManager.java](file:///d:/project/java%20project/mini-redis/src/main/java/com/miniredis/security/AclManager.java)

### 🔄 Upgrade E: Scaling Out (Replication & Sharding)
* **What it is**: Syncing data to replicas and sharding keys across multiple server nodes.
* **How it works in Java**:
  * **Replication**: When a replica connects, the master opens a background thread to send a full RDB snapshot. While it sends, a circular byte-backlog (`ReplicationBacklog`) records new write commands. After the snapshot is sent, the master continues streaming new writes to all replica socket connections.
  * **Sharding**: The cluster space is divided into 16,384 hash slots. We compute `CRC16(key) % 16384` to find which slot the key belongs to. If a query lands on the wrong node, the server throws a `MOVED` error containing the target node's IP and port.
  * **Java classes to check**:
    * [ReplicationManager.java](file:///d:/project/java%20project/mini-redis/src/main/java/com/miniredis/replication/ReplicationManager.java)
    * [ClusterManager.java](file:///d:/project/java%20project/mini-redis/src/main/java/com/miniredis/cluster/ClusterManager.java)

---

## 3. Step-by-Step Hands-On Tutorial

Let's test these upgrades yourself using a terminal.

### Step 1: Compile the Project
Open your terminal in `d:\project\java project\mini-redis` and run:
```bash
mvn clean compile
```

### Step 2: Try ACL Security
1. Start the server on port `6399`:
   ```bash
   mvn exec:java "-Dexec.mainClass=com.miniredis.MiniRedisApplication" "-Dexec.args=--port 6399"
   ```
2. Open another terminal and connect to it using a Redis client:
   ```bash
   redis-cli -p 6399
   ```
3. The default user has full access. Create a restricted user:
   ```
   ACL SETUSER student on >secret123 +GET
   ```
4. Authenticate as the new user:
   ```
   AUTH student secret123
   ```
5. Try executing commands:
   * `GET testkey` -> Allowed! (Returns nil or value)
   * `SET testkey value` -> Fails with `NOPERM` error!

### Step 3: Try Master-Replica Replication
1. Stop any running servers.
2. Start the Master server on port `6399`:
   ```bash
   mvn exec:java "-Dexec.mainClass=com.miniredis.MiniRedisApplication" "-Dexec.args=--port 6399"
   ```
3. Open a new terminal and start the Replica on port `6400` pointing to the Master:
   ```bash
   mvn exec:java "-Dexec.mainClass=com.miniredis.MiniRedisApplication" "-Dexec.args=--port 6400 --replicaof localhost 6399"
   ```
4. Connect to the Master on port `6399` using `redis-cli -p 6399` and write a key:
   ```
   SET message "Hello, replication works!"
   ```
5. Connect to the Replica on port `6400` using `redis-cli -p 6400` and read it:
   ```
   GET message
   ```
   *You will see the message replicated instantly!*

### Step 4: Try Cluster Sharding (Hash Slots)
1. Stop any running servers.
2. Start a cluster-enabled server on port `6399`:
   ```bash
   mvn exec:java "-Dexec.mainClass=com.miniredis.MiniRedisApplication" "-Dexec.args=--port 6399 --cluster-enabled yes"
   ```
3. Open another terminal and connect using `redis-cli`:
   ```bash
   redis-cli -p 6399
   ```
4. Run cluster status commands:
   ```redis
   CLUSTER INFO
   ```
   *Should return `cluster_state:ok`, indicating the cluster is successfully enabled.*
