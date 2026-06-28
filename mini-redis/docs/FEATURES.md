# Jedis Feature List

This document lists all the features, commands, and architectural mechanisms available in **Jedis**.

---

## 1. Core Key-Value Operations
* **String Commands**: `SET`, `GET`, `DEL`, `EXISTS`, `INCR`, `DECR`.
* **List Commands**: `LPUSH`, `RPUSH`, `LPOP`, `RPOP`, `LRANGE`.
* **Set Commands**: `SADD`, `SREM`, `SMEMBERS`, `SISMEMBER`.
* **Hash Commands**: `HSET`, `HGET`, `HDEL`, `HGETALL`.
* **Keys Querying**: Supports pattern matching using glob patterns (e.g., `KEYS user:*`).
* **Transactions**: Group multiple commands together using `MULTI`, `EXEC`, and `DISCARD` blocks.
* **Pub/Sub messaging**: Real-time pub/sub channels using `SUBSCRIBE`, `UNSUBSCRIBE`, and `PUBLISH`.

---

## 2. Lifetimes & Expiry (TTL)
* **TTL setting**: Configure key lifetime using `EXPIRE` and query remaining lifetime with `TTL`.
* **Lazy Expiration**: Automatically checks and deletes a key when a client attempts to read it.
* **Active Expiration**: Run periodic background cleanups to clean expired keys so they don't consume memory.

---

## 3. GC-Free Off-Heap Memory Storage
* **Off-Heap Slab Allocator**: Data is stored outside the JVM heap in direct native buffers to completely bypass Garbage Collection pauses.
* **Memory Limits & Approximated Eviction**: Limit database memory usage via `CONFIG SET maxmemory`. Employs 7 sampled LRU/LFU cache policies (e.g., `ALLKEYS_LRU`, `VOLATILE_LFU`) with frequency decay.

---

## 4. Non-Blocking I/O Networking & Concurrency
* **NIO Selector Event Loop**: Runs on a single-thread loop using Java NIO channels and Selectors to scale to 100k+ concurrent connections without crashing.
* **Non-Blocking RESP Parser**: Stateless, incremental RESP bytes parser.
* **Connection Manager**: Limits max client capacity and drops inactive connections automatically after an idle timeout.

---

## 5. Multi-Phase Data Durability
* **Non-Blocking Snapshots (`BGSAVE`)**: Serializes memory state to a custom RDB format (`dump.rdb`) containing CRC32 verification codes, running on a background worker thread.
* **Automatic AOF Compaction (`BGREWRITEAOF`)**: Periodically rewrites the command append log (`appendonly.aof`) to reduce file size.

---

## 6. TLS Security & Access Control Lists (ACL)
* **SSL/TLS Encryption**: Protects data transmission by encrypting socket traffic using Java's `SSLEngine`.
* **ACL User Profiles**: Manage credentials and permissions using `AUTH` and `ACL SETUSER`. Define command exclusions/inclusions on a per-user level.

---

## 7. Scale-Out & Clustering
* **Leader-Follower Replication**: Replicates data from master to multiple replica servers. Streams write commands continuously and uses a circular backlog for partial resyncs (`PSYNC`).
* **Cluster Sharding**: Hashes keys into 16,384 slots using CRC16 with bracket tags support. Automatically redirects clients with `-MOVED <slot> <ip>:<port>`.
