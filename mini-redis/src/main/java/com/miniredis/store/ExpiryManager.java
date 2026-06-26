package com.miniredis.store;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Manages key expiration using a dual strategy (like real Redis):
 * 
 * 1. LAZY EXPIRY: Every key access checks isExpired() first.
 *    If expired → the caller deletes it and returns null.
 * 
 * 2. ACTIVE EXPIRY: Background thread runs every 100ms,
 *    samples 20 random keys from the expiry map, deletes expired ones.
 *    If >25% were expired, repeat immediately.
 */
public class ExpiryManager {

    /** Maps key → absolute expiry timestamp in milliseconds */
    private final ConcurrentHashMap<String, Long> expiryMap;

    /** Reference back to the DataStore for active deletion */
    private final DataStore dataStore;

    /** Background scheduler for active expiry */
    private ScheduledExecutorService scheduler;

    // ── Configuration ──
    private int sampleSize = 20;
    private double threshold = 0.25;

    public ExpiryManager(DataStore dataStore) {
        this.dataStore = dataStore;
        this.expiryMap = new ConcurrentHashMap<>();
    }

    /**
     * Start the active expiry background thread.
     * 
     * @param intervalMs how often to run (default: 100ms)
     * @param sampleSize how many keys to sample per cycle (default: 20)
     * @param threshold if more than this fraction expired, repeat (default: 0.25)
     */
    public void startActiveExpiry(int intervalMs, int sampleSize, double threshold) {
        this.sampleSize = sampleSize;
        this.threshold = threshold;

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "mini-redis-expiry");
            t.setDaemon(true);
            return t;
        });

        scheduler.scheduleAtFixedRate(this::activeExpiryCycle, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
    }

    /**
     * Stop the active expiry background thread.
     */
    public void stop() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
        }
    }

    // ── Set / Remove Expiry ──

    /**
     * Set expiry on a key (absolute time in ms since epoch).
     */
    public void setExpiry(String key, long expiryTimeMs) {
        expiryMap.put(key, expiryTimeMs);
    }

    /**
     * Set expiry in seconds from now.
     */
    public void setExpirySeconds(String key, long seconds) {
        setExpiry(key, System.currentTimeMillis() + seconds * 1000);
    }

    /**
     * Set expiry in milliseconds from now.
     */
    public void setExpiryMillis(String key, long millis) {
        setExpiry(key, System.currentTimeMillis() + millis);
    }

    /**
     * Remove expiry from a key (make it persistent).
     */
    public void removeExpiry(String key) {
        expiryMap.remove(key);
    }

    /**
     * Clear all expiry entries.
     */
    public void clearAll() {
        expiryMap.clear();
    }

    // ── Query Expiry ──

    /**
     * Check if a key is expired.
     * Returns false if the key has no expiry set.
     */
    public boolean isExpired(String key) {
        Long expiry = expiryMap.get(key);
        if (expiry == null) {
            return false;
        }
        return System.currentTimeMillis() > expiry;
    }

    /**
     * Check if a key has an expiry set.
     */
    public boolean hasExpiry(String key) {
        return expiryMap.containsKey(key);
    }

    /**
     * Get the remaining TTL in seconds for a key.
     * Returns -1 if no expiry, -2 if key doesn't exist.
     */
    public long ttlSeconds(String key) {
        if (!dataStore.getRawStore().containsKey(key)) {
            return -2;
        }
        Long expiry = expiryMap.get(key);
        if (expiry == null) {
            return -1;
        }
        long remaining = (expiry - System.currentTimeMillis()) / 1000;
        if (remaining <= 0) {
            return -2; // Already expired
        }
        return remaining;
    }

    /**
     * Get the raw expiry map (for persistence).
     */
    public ConcurrentHashMap<String, Long> getRawExpiryMap() {
        return expiryMap;
    }

    // ── Active Expiry Engine ──

    /**
     * One cycle of active expiry:
     * 1. Sample 'sampleSize' random keys from the expiry map
     * 2. Delete any that are expired
     * 3. If >threshold fraction were expired, repeat immediately
     */
    private void activeExpiryCycle() {
        try {
            boolean repeat;
            do {
                repeat = sampleAndExpire();
            } while (repeat);
        } catch (Exception e) {
            // Don't let exceptions kill the scheduled task
            System.err.println("[ExpiryManager] Error in active expiry: " + e.getMessage());
        }
    }

    /**
     * Sample keys and expire them. Returns true if we should repeat.
     */
    private boolean sampleAndExpire() {
        if (expiryMap.isEmpty()) {
            return false;
        }

        // Get a sample of keys
        List<String> keys = new ArrayList<>(expiryMap.keySet());
        Collections.shuffle(keys);

        int sampled = Math.min(sampleSize, keys.size());
        int expired = 0;

        for (int i = 0; i < sampled; i++) {
            String key = keys.get(i);
            if (isExpired(key)) {
                dataStore.delete(key);
                expired++;
            }
        }

        // Repeat if more than 25% were expired
        return sampled > 0 && ((double) expired / sampled) > threshold;
    }
}
