package com.miniredis.store;

/**
 * Wraps a value stored in Mini Redis with its type tag.
 * 
 * The value field holds the actual data:
 *   - STRING → String
 *   - LIST   → java.util.LinkedList<String>
 *   - SET    → java.util.LinkedHashSet<String>
 *   - HASH   → java.util.LinkedHashMap<String, String>
 */
public class RedisObject {

    private final DataType type;
    private final Object value;

    public RedisObject(DataType type, Object value) {
        this.type = type;
        this.value = value;
    }

    public DataType getType() {
        return type;
    }

    public Object getValue() {
        return value;
    }

    /**
     * Get the value cast to String (for STRING type).
     */
    @SuppressWarnings("unchecked")
    public String getStringValue() {
        return (String) value;
    }

    /**
     * Get the value cast to LinkedList (for LIST type).
     */
    @SuppressWarnings("unchecked")
    public java.util.LinkedList<String> getListValue() {
        return (java.util.LinkedList<String>) value;
    }

    /**
     * Get the value cast to LinkedHashSet (for SET type).
     */
    @SuppressWarnings("unchecked")
    public java.util.LinkedHashSet<String> getSetValue() {
        return (java.util.LinkedHashSet<String>) value;
    }

    /**
     * Get the value cast to LinkedHashMap (for HASH type).
     */
    @SuppressWarnings("unchecked")
    public java.util.LinkedHashMap<String, String> getHashValue() {
        return (java.util.LinkedHashMap<String, String>) value;
    }

    /**
     * Create a STRING RedisObject.
     */
    public static RedisObject string(String value) {
        return new RedisObject(DataType.STRING, value);
    }

    /**
     * Create a LIST RedisObject with an empty LinkedList.
     */
    public static RedisObject list() {
        return new RedisObject(DataType.LIST, new java.util.LinkedList<String>());
    }

    /**
     * Create a SET RedisObject with an empty LinkedHashSet.
     */
    public static RedisObject set() {
        return new RedisObject(DataType.SET, new java.util.LinkedHashSet<String>());
    }

    /**
     * Create a HASH RedisObject with an empty LinkedHashMap.
     */
    public static RedisObject hash() {
        return new RedisObject(DataType.HASH, new java.util.LinkedHashMap<String, String>());
    }

    @Override
    public String toString() {
        return "RedisObject{type=" + type + ", value=" + value + "}";
    }
}
