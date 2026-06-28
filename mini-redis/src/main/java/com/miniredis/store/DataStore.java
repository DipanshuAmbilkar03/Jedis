package com.miniredis.store;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import com.miniredis.memory.MemoryManager;
import com.miniredis.memory.OffHeapAllocator;
import com.miniredis.memory.OffHeapPointer;

/**
 * Central thread-safe key-value data store for Mini Redis.
 * 
 * Stores data off-heap using OffHeapAllocator to eliminate GC pauses.
 * Employs a transient on-heap cache for the duration of a single command.
 */
public class DataStore {

    private final ConcurrentHashMap<String, OffHeapPointer> store;
    private final ConcurrentHashMap<String, RedisObject> onHeapCache;
    private final OffHeapAllocator offHeapAllocator;
    private final ExpiryManager expiryManager;
    private final MemoryManager memoryManager;
    private volatile boolean readOnly = false;

    public DataStore() {
        this.store = new ConcurrentHashMap<>();
        this.onHeapCache = new ConcurrentHashMap<>();
        this.offHeapAllocator = new OffHeapAllocator(16 * 1024 * 1024); // 16MB direct memory
        this.expiryManager = new ExpiryManager(this);
        this.memoryManager = new MemoryManager();
    }

    // ── Core Operations ──

    /**
     * Get a value by key. Returns null if key doesn't exist or is expired.
     */
    public RedisObject get(String key) {
        if (expiryManager.isExpired(key)) {
            delete(key);
            return null;
        }

        RedisObject cached = onHeapCache.get(key);
        if (cached != null) {
            cached.touch();
            return cached;
        }

        OffHeapPointer ptr = store.get(key);
        if (ptr == null) {
            return null;
        }

        // Update LRU/LFU metadata on-heap in the pointer wrapper
        long now = System.currentTimeMillis();
        int freq = ptr.getAccessFrequency();
        double r = Math.random();
        double p = 1.0 / ((freq - 5) * 10.0 + 1.0);
        int newFreq = freq;
        if (r < p) {
            newFreq = Math.min(255, freq + 1);
        }
        OffHeapPointer updatedPtr = new OffHeapPointer(ptr.getOffset(), ptr.getLength(), ptr.getType(), now, newFreq);
        store.put(key, updatedPtr);

        // Read and deserialize from off-heap direct memory
        RedisObject obj = deserializeFromOffHeap(ptr);
        if (obj != null) {
            obj.touch();
            onHeapCache.put(key, obj);
        }
        return obj;
    }

    /**
     * Set a key-value pair, overwriting any existing value.
     */
    public void set(String key, RedisObject value) {
        onHeapCache.put(key, value);
        writeToOffHeap(key, value);
    }

    /**
     * Delete one or more keys. Returns the number of keys deleted.
     */
    public int delete(String... keys) {
        int count = 0;
        for (String key : keys) {
            onHeapCache.remove(key);
            OffHeapPointer ptr = store.remove(key);
            if (ptr != null) {
                offHeapAllocator.free(ptr.getOffset());
                expiryManager.removeExpiry(key);
                count++;
            }
        }
        return count;
    }

    /**
     * Check if a key exists (and is not expired).
     */
    public boolean exists(String key) {
        if (expiryManager.isExpired(key)) {
            delete(key);
            return false;
        }
        return store.containsKey(key) || onHeapCache.containsKey(key);
    }

    /**
     * Get the type of a key. Returns null if key doesn't exist.
     */
    public DataType type(String key) {
        OffHeapPointer ptr = store.get(key);
        if (ptr != null) {
            return ptr.getType();
        }
        RedisObject cached = onHeapCache.get(key);
        return cached != null ? cached.getType() : null;
    }

    /**
     * Get all keys matching a glob pattern.
     */
    public Set<String> keys(String pattern) {
        Set<String> result = new LinkedHashSet<>();
        String regex = globToRegex(pattern);

        for (String key : store.keySet()) {
            if (!expiryManager.isExpired(key) && key.matches(regex)) {
                result.add(key);
            }
        }
        return result;
    }

    /**
     * Get the raw store for iteration (used by ExpiryManager, MemoryManager and persistence).
     */
    public ConcurrentHashMap<String, OffHeapPointer> getRawStore() {
        return store;
    }

    /**
     * Get the number of keys in the store.
     */
    public int size() {
        return store.size();
    }

    /**
     * Clear all keys from the store.
     */
    public void flushAll() {
        onHeapCache.clear();
        store.clear();
        offHeapAllocator.clear();
        expiryManager.clearAll();
    }

    /**
     * Get the ExpiryManager for setting TTLs.
     */
    public ExpiryManager getExpiryManager() {
        return expiryManager;
    }

    public MemoryManager getMemoryManager() {
        return memoryManager;
    }

    // ── Type-Safe Accessors ──

    public RedisObject getIfType(String key, DataType expectedType) {
        RedisObject obj = get(key);
        if (obj == null) {
            return null;
        }
        if (obj.getType() != expectedType) {
            throw new WrongTypeException();
        }
        return obj;
    }

    public RedisObject getOrCreate(String key, DataType type) {
        RedisObject obj = get(key);
        if (obj == null) {
            obj = switch (type) {
                case STRING -> RedisObject.string("");
                case LIST -> RedisObject.list();
                case SET -> RedisObject.set();
                case HASH -> RedisObject.hash();
            };
            set(key, obj);
            return obj;
        }
        if (obj.getType() != type) {
            throw new WrongTypeException();
        }
        return obj;
    }

    // ── Off-Heap Serialization & Deserialization ──

    private synchronized void writeToOffHeap(String key, RedisObject value) {
        try {
            byte[] data = value.toBytes();

            OffHeapPointer oldPtr = store.get(key);
            if (oldPtr != null) {
                offHeapAllocator.free(oldPtr.getOffset());
            }

            int offset = offHeapAllocator.allocate(data.length);
            offHeapAllocator.write(offset, data);

            long now = System.currentTimeMillis();
            int freq = oldPtr != null ? oldPtr.getAccessFrequency() : 5;
            OffHeapPointer newPtr = new OffHeapPointer(offset, data.length, value.getType(), now, freq);
            store.put(key, newPtr);
        } catch (java.io.IOException e) {
            System.err.println("[DataStore] Off-heap write failed for key " + key + ": " + e.getMessage());
        }
    }

    private synchronized RedisObject deserializeFromOffHeap(OffHeapPointer ptr) {
        try {
            byte[] dest = new byte[ptr.getLength()];
            offHeapAllocator.read(ptr.getOffset(), dest);
            return RedisObject.fromBytes(dest);
        } catch (java.io.IOException e) {
            System.err.println("[DataStore] Off-heap read failed: " + e.getMessage());
            return null;
        }
    }

    public synchronized void syncOffHeap(String key) {
        RedisObject cached = onHeapCache.get(key);
        if (cached != null) {
            writeToOffHeap(key, cached);
        }
    }

    public void clearOnHeapCache() {
        onHeapCache.clear();
    }

    public boolean isReadOnly() {
        return readOnly;
    }

    public void setReadOnly(boolean readOnly) {
        this.readOnly = readOnly;
    }

    // ── Helper ──

    private String globToRegex(String glob) {
        StringBuilder regex = new StringBuilder("^");
        for (char c : glob.toCharArray()) {
            switch (c) {
                case '*' -> regex.append(".*");
                case '?' -> regex.append(".");
                case '.' -> regex.append("\\.");
                case '\\' -> regex.append("\\\\");
                case '[' -> regex.append("[");
                case ']' -> regex.append("]");
                case '{' -> regex.append("\\{");
                case '}' -> regex.append("\\}");
                case '(' -> regex.append("\\(");
                case ')' -> regex.append("\\)");
                case '+' -> regex.append("\\+");
                case '^' -> regex.append("\\^");
                case '$' -> regex.append("\\$");
                case '|' -> regex.append("\\|");
                default -> regex.append(c);
            }
        }
        regex.append("$");
        return regex.toString();
    }

    public static class WrongTypeException extends RuntimeException {
        public WrongTypeException() {
            super("WRONGTYPE Operation against a key holding the wrong kind of value");
        }
    }
}
