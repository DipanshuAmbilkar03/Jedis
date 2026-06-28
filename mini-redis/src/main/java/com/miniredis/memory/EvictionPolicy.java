package com.miniredis.memory;

/**
 * Supported memory eviction policies in Mini Redis.
 */
public enum EvictionPolicy {
    NOEVICTION,
    ALLKEYS_LRU,
    ALLKEYS_LFU,
    VOLATILE_LRU,
    VOLATILE_LFU,
    ALLKEYS_RANDOM,
    VOLATILE_RANDOM;

    public static EvictionPolicy fromString(String policy) {
        try {
            return EvictionPolicy.valueOf(policy.toUpperCase().replace("-", "_"));
        } catch (IllegalArgumentException e) {
            return NOEVICTION;
        }
    }
}
