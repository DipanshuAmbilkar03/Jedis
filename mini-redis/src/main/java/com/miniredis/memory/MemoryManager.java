package com.miniredis.memory;

import com.miniredis.store.DataStore;
import com.miniredis.store.RedisObject;

import java.util.*;

/**
 * MemoryManager — monitors memory usage and evicts keys when the memory limit is exceeded.
 * 
 * Supports NOEVICTION, LRU, LFU, and RANDOM eviction policies.
 * Employs a sampled approximation algorithm similar to official Redis.
 */
public class MemoryManager {

    private long maxMemoryBytes = 0; // 0 means no limit
    private EvictionPolicy evictionPolicy = EvictionPolicy.NOEVICTION;
    private int sampleSize = 5;

    public MemoryManager() {
    }

    public long getMaxMemoryBytes() {
        return maxMemoryBytes;
    }

    public void setMaxMemoryBytes(long maxMemoryBytes) {
        this.maxMemoryBytes = maxMemoryBytes;
    }

    public EvictionPolicy getEvictionPolicy() {
        return evictionPolicy;
    }

    public void setEvictionPolicy(EvictionPolicy evictionPolicy) {
        this.evictionPolicy = evictionPolicy;
    }

    public int getSampleSize() {
        return sampleSize;
    }

    public void setSampleSize(int sampleSize) {
        this.sampleSize = sampleSize;
    }

    /**
     * Get the currently used JVM memory in bytes.
     */
    public long getUsedMemory() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    /**
     * Check if used memory exceeds max memory before executing a write command.
     * Triggers the eviction algorithm if memory limit is reached.
     * Throws OomException if memory is still exceeded.
     */
    public void checkMemoryBeforeWrite(DataStore dataStore) {
        if (maxMemoryBytes <= 0) {
            return;
        }

        long used = getUsedMemory();
        if (used <= maxMemoryBytes) {
            return;
        }

        // Try to evict keys to free up space
        evictKeys(dataStore);

        // If we are still out of memory, block further writes
        if (getUsedMemory() > maxMemoryBytes && dataStore.size() > 0) {
            throw new OomException();
        }
    }

    private void evictKeys(DataStore dataStore) {
        if (evictionPolicy == EvictionPolicy.NOEVICTION) {
            return;
        }

        var store = dataStore.getRawStore();
        var expiryManager = dataStore.getExpiryManager();
        var expiryMap = expiryManager.getRawExpiryMap();

        int loopCount = 0;
        // Evict until we are under the limit or out of candidates (max 100 evictions per write command to avoid lockup)
        while (getUsedMemory() > maxMemoryBytes && loopCount < 100) {
            loopCount++;

            // 1. Gather candidate pool based on policy
            List<String> pool = new ArrayList<>();
            if (evictionPolicy == EvictionPolicy.VOLATILE_LRU || 
                evictionPolicy == EvictionPolicy.VOLATILE_LFU || 
                evictionPolicy == EvictionPolicy.VOLATILE_RANDOM) {
                pool.addAll(expiryMap.keySet());
            } else {
                pool.addAll(store.keySet());
            }

            if (pool.isEmpty()) {
                break; // No keys available for eviction policy
            }

            // 2. Randomly sample 'sampleSize' keys
            List<String> sample = new ArrayList<>();
            int actualSampleSize = Math.min(sampleSize, pool.size());
            Collections.shuffle(pool);
            for (int i = 0; i < actualSampleSize; i++) {
                sample.add(pool.get(i));
            }

            // 3. Find the best candidate according to policy
            String candidate = null;
            if (evictionPolicy == EvictionPolicy.ALLKEYS_RANDOM || evictionPolicy == EvictionPolicy.VOLATILE_RANDOM) {
                candidate = sample.get(0);
            } else if (evictionPolicy == EvictionPolicy.ALLKEYS_LRU || evictionPolicy == EvictionPolicy.VOLATILE_LRU) {
                long maxIdle = -1;
                for (String key : sample) {
                    OffHeapPointer ptr = (OffHeapPointer) store.get(key);
                    if (ptr != null) {
                        long idle = ptr.getIdleTime();
                        if (idle > maxIdle) {
                            maxIdle = idle;
                            candidate = key;
                        }
                    }
                }
            } else if (evictionPolicy == EvictionPolicy.ALLKEYS_LFU || evictionPolicy == EvictionPolicy.VOLATILE_LFU) {
                int minFreq = Integer.MAX_VALUE;
                for (String key : sample) {
                    OffHeapPointer ptr = (OffHeapPointer) store.get(key);
                    if (ptr != null) {
                        // Apply time decay: reduce frequency counter based on idle time
                        int freq = ptr.getAccessFrequency();
                        int minutesIdle = (int) (ptr.getIdleTime() / 60000);
                        int decayedFreq = Math.max(5, freq - minutesIdle); // min counter is 5
                        
                        if (decayedFreq < minFreq) {
                            minFreq = decayedFreq;
                            candidate = key;
                        }
                    }
                }
            }

            // 4. Evict the selected candidate
            if (candidate != null) {
                System.out.println("[MemoryManager] Evicted key '" + candidate + "' via policy: " + evictionPolicy);
                dataStore.delete(candidate);
            } else {
                break;
            }
        }
    }

    public static class OomException extends RuntimeException {
        public OomException() {
            super("OOM command not allowed when used memory > 'maxmemory'");
        }
    }
}
