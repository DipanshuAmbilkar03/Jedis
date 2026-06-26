package com.miniredis.protocol;

/**
 * RESP2 data types used in the Redis Serialization Protocol.
 * Each type has a unique prefix byte that identifies it on the wire.
 */
public enum RespType {

    /**
     * Simple Strings: +OK\r\n
     * Used for non-binary-safe, short responses like "OK" or "PONG".
     */
    SIMPLE_STRING('+'),

    /**
     * Errors: -ERR message\r\n
     * Used for error responses.
     */
    ERROR('-'),

    /**
     * Integers: :1000\r\n
     * Used for numeric responses (e.g., INCR, DEL count).
     */
    INTEGER(':'),

    /**
     * Bulk Strings: $6\r\nfoobar\r\n
     * Used for binary-safe string data. Null represented as $-1\r\n.
     */
    BULK_STRING('$'),

    /**
     * Arrays: *2\r\n$3\r\nfoo\r\n$3\r\nbar\r\n
     * Used for lists of mixed types. Null array: *-1\r\n.
     */
    ARRAY('*');

    private final char prefix;

    RespType(char prefix) {
        this.prefix = prefix;
    }

    public char getPrefix() {
        return prefix;
    }

    /**
     * Look up a RespType by its wire prefix character.
     */
    public static RespType fromPrefix(char prefix) {
        for (RespType type : values()) {
            if (type.prefix == prefix) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown RESP type prefix: " + prefix);
    }
}
