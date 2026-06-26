package com.miniredis.protocol;

import java.util.List;

/**
 * Encodes Java objects into RESP2 wire format for sending responses to clients.
 * 
 * All methods return a String in RESP2 format terminated with \r\n.
 */
public class RespEncoder {

    private static final String CRLF = "\r\n";

    /**
     * Encode a Simple String response.
     * Example: +OK\r\n
     */
    public static String encodeSimpleString(String value) {
        return "+" + value + CRLF;
    }

    /**
     * Encode an Error response.
     * Example: -ERR unknown command 'foobar'\r\n
     */
    public static String encodeError(String message) {
        return "-" + message + CRLF;
    }

    /**
     * Encode an Integer response.
     * Example: :1000\r\n
     */
    public static String encodeInteger(long value) {
        return ":" + value + CRLF;
    }

    /**
     * Encode a Bulk String response.
     * Example: $6\r\nfoobar\r\n
     * Null: $-1\r\n
     */
    public static String encodeBulkString(String value) {
        if (value == null) {
            return "$-1" + CRLF;
        }
        return "$" + value.length() + CRLF + value + CRLF;
    }

    /**
     * Encode a Null Bulk String.
     * Used when a key doesn't exist.
     */
    public static String encodeNull() {
        return "$-1" + CRLF;
    }

    /**
     * Encode a Null Array.
     * Used for aborted transactions.
     */
    public static String encodeNullArray() {
        return "*-1" + CRLF;
    }

    /**
     * Encode an Array of Bulk Strings.
     * Example: *2\r\n$3\r\nfoo\r\n$3\r\nbar\r\n
     */
    public static String encodeStringArray(List<String> values) {
        if (values == null) {
            return encodeNullArray();
        }

        StringBuilder sb = new StringBuilder();
        sb.append("*").append(values.size()).append(CRLF);
        for (String value : values) {
            sb.append(encodeBulkString(value));
        }
        return sb.toString();
    }

    /**
     * Encode an Array of mixed RESP-encoded elements.
     * Each element should already be RESP-encoded.
     */
    public static String encodeArray(List<String> encodedElements) {
        if (encodedElements == null) {
            return encodeNullArray();
        }

        StringBuilder sb = new StringBuilder();
        sb.append("*").append(encodedElements.size()).append(CRLF);
        for (String element : encodedElements) {
            sb.append(element);
        }
        return sb.toString();
    }

    /**
     * Encode an empty Array.
     */
    public static String encodeEmptyArray() {
        return "*0" + CRLF;
    }

    // ── Convenience constants ──

    public static final String OK = encodeSimpleString("OK");
    public static final String PONG = encodeSimpleString("PONG");
    public static final String QUEUED = encodeSimpleString("QUEUED");
    public static final String NULL = encodeNull();
    public static final String ZERO = encodeInteger(0);
    public static final String ONE = encodeInteger(1);

    /**
     * Standard error for wrong number of arguments.
     */
    public static String wrongArgCount(String command) {
        return encodeError("ERR wrong number of arguments for '" + command + "' command");
    }

    /**
     * Standard error for wrong type operation.
     */
    public static String wrongType() {
        return encodeError("WRONGTYPE Operation against a key holding the wrong kind of value");
    }

    /**
     * Standard error for value not being an integer.
     */
    public static String notAnInteger() {
        return encodeError("ERR value is not an integer or out of range");
    }
}
