package com.miniredis.store;

/**
 * Enum representing the data types supported by Mini Redis.
 * Each key in the store is tagged with one of these types.
 */
public enum DataType {

    STRING("string"),
    LIST("list"),
    SET("set"),
    HASH("hash");

    private final String name;

    DataType(String name) {
        this.name = name;
    }

    /**
     * Returns the Redis-compatible type name (lowercase).
     * Used by the TYPE command.
     */
    public String getTypeName() {
        return name;
    }
}
