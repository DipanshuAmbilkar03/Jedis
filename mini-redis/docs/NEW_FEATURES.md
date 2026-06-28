# New Production-Ready Features in Mini Redis

This document provides an overview of all the advanced features added during the production-grade upgrade of Mini Redis, along with instructions on how to test and verify them.

---

## 1. Summary of New Features

### 🔒 TLS Security & Access Control Lists (ACL)
* **TLS Encrypted Traffic**: Protects client communication via SSL/TLS encryption using Java's `SSLEngine`.
* **Access Control Lists**: Fine-grained command-level permissions. You can create custom users with specific command lists.
* **Commands**:
  * `AUTH <username> <password>`: Authenticates a client connection.
  * `ACL SETUSER <username> [on/off] [>password] [+command] [-command]`: Configures user rules.
  * `ACL WHOAMI`: Returns the currently authenticated username.
  * `ACL LIST`: Dumps all configured users and their rules.

### 🔄 Master-Replica Replication
* **Full & Partial Resync (`PSYNC`)**: Replicas connect to the master, perform a handshake, and synchronize. Supports partial resynchronization via a circular backlog buffer if disconnected temporarily.
* **Write Propagation**: All write commands executed on the master are automatically streamed to connected replicas. Replicas run in read-only mode.
* **Commands**:
  * `REPLICAOF <host> <port>`: Configures this node to replicate a master.
  * `REPLICAOF NO ONE`: Promotes the replica back to a writeable master node.

### 🌐 Cluster Sharding (Hash Slots)
* **Hash Slot Key Routing**: Key space is partitioned into 16,384 hash slots using CRC16 key hashing.
* **Redis Hash Tags**: Supports bracket tagging (e.g. `user:{123}:profile`) to force specific keys onto the same slot.
* **MOVED Redirection**: If a command is sent to a node that does not own the key's hash slot, the node returns a `-MOVED <slot> <host>:<port>` redirect.
* **Commands**:
  * `CLUSTER MEET <host> <port>`: Introduces this node to another cluster node.
  * `CLUSTER ADDSLOTS <slot> [slot ...]`: Assigns slot ownership ranges.
  * `CLUSTER INFO` / `CLUSTER NODES`: Queries cluster health and topology.

### ⚡ Selector-Based NIO Event Loop
* **High-Speed Non-Blocking I/O**: Swaps the legacy thread-per-connection model for a selector-driven non-blocking socket loop (`NioEventLoop`) for scaling to thousands of concurrent connections.
* **Non-Blocking RESP Parser**: Fully stateful incremental bytes parser.

### 🧠 Off-Heap Memory Storage & Eviction Policies
* **Off-Heap Allocator**: Bypasses Java garbage collection pauses by serializing data directly into off-heap direct JVM memory buffers.
* **Eviction Policies**: Employs sampled LRU/LFU approximation logic with decay. Supports 7 policies:
  * `NO_EVICTION`, `ALLKEYS_LRU`, `VOLATILE_LRU`, `ALLKEYS_LFU`, `VOLATILE_LFU`, `ALLKEYS_RANDOM`, `VOLATILE_RANDOM`.
* **Commands**:
  * `CONFIG GET maxmemory` / `CONFIG SET maxmemory <bytes>`
  * `CONFIG GET maxmemory-policy` / `CONFIG SET maxmemory-policy <policy>`

### 💾 Non-Blocking Snapshotting & Compaction
* **Non-Blocking Snapshots**: Background serialization of in-memory data to binary RDB files (`BGSAVE`) via a worker thread pool.
* **AOF Compaction**: Background rewrite engine (`BGREWRITEAOF`) to prune historic write logs into minimal recovery states.

---

## 2. Automated Test Execution

You can run the full automated test suite containing **47 tests** covering all phases (replication, cluster routing, off-heap allocations, ACL permissions, event loops, and eviction checks) using Maven:

```bash
mvn clean test
```

---

## 3. Manual Verification Walks

### A. Testing Access Control Lists (ACL)
1. Start the server on port `6399`:
   ```bash
   mvn exec:java "-Dexec.mainClass=com.miniredis.MiniRedisApplication" "-Dexec.args=--port 6399"
   ```
2. Connect a client and create a new user:
   ```bash
   redis-cli -p 6399
   ```
   *Then inside the prompt:*
   ```
   ACL SETUSER alice on >password123 +GET
   ```
3. Authenticate as `alice`:
   ```
   AUTH alice password123
   ```
4. Verify permissions:
   * `GET somekey` -> returns `(nil)` (Allowed)
   * `SET somekey val` -> returns `-NOPERM this user has no permissions to run the 'set' command` (Blocked)

### B. Testing Replication
1. Start a master node on port `6399`:
   ```bash
   mvn exec:java "-Dexec.mainClass=com.miniredis.MiniRedisApplication" "-Dexec.args=--port 6399"
   ```
2. Start a replica node on port `6400` pointing to the master:
   ```bash
   mvn exec:java "-Dexec.mainClass=com.miniredis.MiniRedisApplication" "-Dexec.args=--port 6400 --replicaof localhost 6399"
   ```
3. Connect to the master (`6399`) and write a key:
   ```bash
   redis-cli -p 6399
   ```
   *Then run:*
   ```
   SET hello "world"
   ```
4. Connect to the replica (`6400`) and read the key:
   ```bash
   redis-cli -p 6400
   ```
   *Then run:*
   ```
   GET hello
   ```
   *Should return `"world"`.*
5. Try to write directly to the replica:
   ```
   SET test val
   ```
   *Should return `-READONLY You can't write against a read only replica.`*

### C. Testing Cluster Sharding
1. Start a cluster-enabled node on port `6399`:
   ```bash
   mvn exec:java "-Dexec.mainClass=com.miniredis.MiniRedisApplication" "-Dexec.args=--port 6399 --cluster-enabled yes"
   ```
2. Meet another node:
   ```
   CLUSTER MEET localhost 6400
   ```
3. Assign slot `100` to a remote node:
   *(Send to node B)*
4. Execute key operation mapping to slot 100 on the master:
   *Should return `-MOVED 100 127.0.0.1:6400` redirect.*
