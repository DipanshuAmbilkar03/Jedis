package com.miniredis.store;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ExpiryManagerTest {

    private DataStore store;
    private ExpiryManager expiryManager;

    @BeforeEach
    public void setUp() {
        store = new DataStore();
        expiryManager = store.getExpiryManager();
    }

    @Test
    public void testLazyExpiration() throws InterruptedException {
        store.set("temp", RedisObject.string("val"));
        expiryManager.setExpiryMillis("temp", 50); // Expire in 50ms

        assertTrue(store.exists("temp"));
        assertNotNull(store.get("temp"));

        Thread.sleep(100);

        // Lazy expiry should trigger
        assertFalse(store.exists("temp"));
        assertNull(store.get("temp"));
    }

    @Test
    public void testTtl() {
        store.set("temp", RedisObject.string("val"));
        assertEquals(-1, expiryManager.ttlSeconds("temp"));

        expiryManager.setExpirySeconds("temp", 10);
        long ttl = expiryManager.ttlSeconds("temp");
        assertTrue(ttl > 0 && ttl <= 10);

        assertEquals(-2, expiryManager.ttlSeconds("nonexistent"));
    }

    @Test
    public void testRemoveExpiry() {
        store.set("temp", RedisObject.string("val"));
        expiryManager.setExpirySeconds("temp", 10);
        assertTrue(expiryManager.hasExpiry("temp"));

        expiryManager.removeExpiry("temp");
        assertFalse(expiryManager.hasExpiry("temp"));
        assertEquals(-1, expiryManager.ttlSeconds("temp"));
    }
}
