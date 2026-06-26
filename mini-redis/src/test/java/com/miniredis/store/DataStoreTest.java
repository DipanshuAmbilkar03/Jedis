package com.miniredis.store;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class DataStoreTest {

    private DataStore store;

    @BeforeEach
    public void setUp() {
        store = new DataStore();
    }

    @Test
    public void testBasicSetAndGet() {
        store.set("key1", RedisObject.string("val1"));
        RedisObject obj = store.get("key1");
        assertNotNull(obj);
        assertEquals(DataType.STRING, obj.getType());
        assertEquals("val1", obj.getStringValue());
    }

    @Test
    public void testDelete() {
        store.set("key1", RedisObject.string("val1"));
        store.set("key2", RedisObject.string("val2"));

        int deleted = store.delete("key1", "key3");
        assertEquals(1, deleted);
        assertNull(store.get("key1"));
        assertNotNull(store.get("key2"));
    }

    @Test
    public void testExists() {
        store.set("key1", RedisObject.string("val1"));
        assertTrue(store.exists("key1"));
        assertFalse(store.exists("key2"));
    }

    @Test
    public void testType() {
        store.set("s", RedisObject.string("val"));
        store.set("l", RedisObject.list());
        store.set("set", RedisObject.set());
        store.set("h", RedisObject.hash());

        assertEquals(DataType.STRING, store.type("s"));
        assertEquals(DataType.LIST, store.type("l"));
        assertEquals(DataType.SET, store.type("set"));
        assertEquals(DataType.HASH, store.type("h"));
        assertNull(store.type("nonexistent"));
    }

    @Test
    public void testKeysPatternMatching() {
        store.set("foobar", RedisObject.string("1"));
        store.set("foo", RedisObject.string("2"));
        store.set("bar", RedisObject.string("3"));
        store.set("foobaz", RedisObject.string("4"));

        Set<String> matched = store.keys("foo*");
        assertEquals(3, matched.size());
        assertTrue(matched.contains("foo"));
        assertTrue(matched.contains("foobar"));
        assertTrue(matched.contains("foobaz"));

        matched = store.keys("foo?");
        assertEquals(0, matched.size());

        matched = store.keys("bar");
        assertEquals(1, matched.size());
        assertTrue(matched.contains("bar"));
    }

    @Test
    public void testGetIfTypeAndGetOrCreate() {
        store.set("mykey", RedisObject.string("myval"));

        // getIfType checks
        assertNotNull(store.getIfType("mykey", DataType.STRING));
        assertThrows(DataStore.WrongTypeException.class, () -> store.getIfType("mykey", DataType.LIST));

        // getOrCreate checks
        assertThrows(DataStore.WrongTypeException.class, () -> store.getOrCreate("mykey", DataType.LIST));

        RedisObject newList = store.getOrCreate("mylist", DataType.LIST);
        assertNotNull(newList);
        assertEquals(DataType.LIST, newList.getType());
    }
}
