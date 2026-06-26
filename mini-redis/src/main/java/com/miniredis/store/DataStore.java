package com.miniredis.store;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central thread-safe key-value data store for Mini Redis.
 * 
 * Uses ConcurrentHashMap for lock-free reads and thread-safe writes.
 * Integrates with ExpiryManager for lazy key expiration on every access.
 */
public class DataStore {

    private final ConcurrentHashMap<String, RedisObject> store;
    private final ExpiryManager expiryManager;

    public DataStore() {
        this.store = new ConcurrentHashMap<>();
        this.expiryManager = new ExpiryManager(this);
    }

    // ── Core Operations ──

    /**
     * Get a value by key. Returns null if key doesn't exist or is expired.
     * Performs lazy expiry check.
     */
    public RedisObject get(String key) {
        if (expiryManager.isExpired(key)) {
            delete(key);
            return null;
        }
        return store.get(key);
    }

    /**
     * Set a key-value pair, overwriting any existing value.
     */
    public void set(String key, RedisObject value) {
        store.put(key, value);
    }

    /**
     * Delete one or more keys. Returns the number of keys deleted.
     */
    public int delete(String... keys) {
        int count = 0;
        for (String key : keys) {
            if (store.remove(key) != null) {
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
        return store.containsKey(key);
    }

    /**
     * Get the type of a key. Returns null if key doesn't exist.
     */
    public DataType type(String key) {
        RedisObject obj = get(key);
        return obj != null ? obj.getType() : null;
    }

    /**
     * Get all keys matching a glob pattern.
     * Supports: * (any), ? (single char)
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
     * Get the raw store for iteration (used by ExpiryManager and persistence).
     */
    public ConcurrentHashMap<String, RedisObject> getRawStore() {
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
        store.clear();
        expiryManager.clearAll();
    }

    /**
     * Get the ExpiryManager for setting TTLs.
     */
    public ExpiryManager getExpiryManager() {
        return expiryManager;
    }

    // ── Type-Safe Accessors ──

    /**
     * Get a key's value only if it matches the expected type.
     * Returns null if key doesn't exist.
     * Throws an exception marker if the type doesn't match.
     */
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

    /**
     * Get or create a key with the expected type.
     * If the key exists with a different type, throws WrongTypeException.
     */
    public RedisObject getOrCreate(String key, DataType type) {
        RedisObject obj = get(key);
        if (obj == null) {
            obj = switch (type) {
                case STRING -> RedisObject.string("");
                case LIST -> RedisObject.list();
                case SET -> RedisObject.set();
                case HASH -> RedisObject.hash();
            };
            store.put(key, obj);
            return obj;
        }
        if (obj.getType() != type) {
            throw new WrongTypeException();
        }
        return obj;
    }

    // ── Helper ──

    /**
     * Convert a Redis glob pattern to a Java regex.
     * * → .* (match anything)
     * ? → .  (match single char)
     * Everything else is escaped.
     */
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

    /**
     * Exception thrown when a command is used against a key of the wrong type.
     */
    public static class WrongTypeException extends RuntimeException {
        public WrongTypeException() {
            super("WRONGTYPE Operation against a key holding the wrong kind of value");
        }
    }
}
