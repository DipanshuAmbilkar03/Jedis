package com.miniredis.memory;

import com.miniredis.store.DataStore;
import com.miniredis.store.RedisObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class MemoryManagerTest {

    private DataStore store;
    private MemoryManager mm;

    @BeforeEach
    public void setUp() {
        store = new DataStore();
        mm = store.getMemoryManager();
    }

    @Test
    public void testEvictionDisabledByDefault() {
        assertEquals(0, mm.getMaxMemoryBytes());
        assertEquals(EvictionPolicy.NOEVICTION, mm.getEvictionPolicy());

        // Should not throw any OOM with default config
        for (int i = 0; i < 100; i++) {
            store.set("key" + i, RedisObject.string("value" + i));
        }
        assertEquals(100, store.size());
    }

    @Test
    public void testNoEvictionThrowsOom() {
        store.set("key", RedisObject.string("value"));
        mm.setMaxMemoryBytes(1); // Set to 1 byte to guarantee OOM
        mm.setEvictionPolicy(EvictionPolicy.NOEVICTION);

        assertThrows(MemoryManager.OomException.class, () -> {
            mm.checkMemoryBeforeWrite(store);
        });
    }

    @Test
    public void testLruEviction() throws InterruptedException {
        mm.setSampleSize(3);
        mm.setEvictionPolicy(EvictionPolicy.ALLKEYS_LRU);
        mm.setMaxMemoryBytes(1); // Force eviction

        // Store keys
        store.set("key1", RedisObject.string("val1"));
        Thread.sleep(10);
        store.set("key2", RedisObject.string("val2"));
        Thread.sleep(10);
        store.set("key3", RedisObject.string("val3"));

        // Touch key1 to update its access time
        store.get("key1");

        // key2 is now the oldest (LRU) candidate among key2 and key3
        // Trigger eviction
        mm.checkMemoryBeforeWrite(store);

        // Verify key2 was evicted
        assertNull(store.get("key2"));
    }
}
