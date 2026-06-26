package com.miniredis.protocol;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses the RESP2 (Redis Serialization Protocol v2) wire format.
 * 
 * All client commands arrive as Arrays of Bulk Strings:
 *   *3\r\n$3\r\nSET\r\n$5\r\nmykey\r\n$7\r\nmyvalue\r\n
 * 
 * This parser reads from a raw InputStream and produces structured data.
 */
public class RespParser {

    private final BufferedReader reader;

    public RespParser(InputStream inputStream) {
        this.reader = new BufferedReader(new InputStreamReader(inputStream));
    }

    /**
     * Parse the next RESP value from the stream.
     * Returns null if the connection is closed.
     * 
     * @return parsed object: String, Long, List, or null
     * @throws IOException if reading fails
     */
    public Object parse() throws IOException {
        String line = reader.readLine();
        if (line == null) {
            return null; // Connection closed
        }

        if (line.isEmpty()) {
            return parse(); // Skip empty lines
        }

        char prefix = line.charAt(0);
        String data = line.substring(1);

        return switch (prefix) {
            case '+' -> parseSimpleString(data);
            case '-' -> parseError(data);
            case ':' -> parseInteger(data);
            case '$' -> parseBulkString(data);
            case '*' -> parseArray(data);
            default -> line; // Treat as inline command
        };
    }

    /**
     * Convenience method: parse the next command as a list of strings.
     * Client commands are always arrays of bulk strings.
     * 
     * @return list of command arguments, or null if connection closed
     */
    @SuppressWarnings("unchecked")
    public List<String> parseCommand() throws IOException {
        Object result = parse();
        if (result == null) {
            return null;
        }

        if (result instanceof List<?> list) {
            List<String> command = new ArrayList<>();
            for (Object item : list) {
                command.add(item != null ? item.toString() : "");
            }
            return command;
        }

        // Handle inline commands (plain text without RESP framing)
        // e.g., "PING\r\n" sent directly
        if (result instanceof String str) {
            List<String> command = new ArrayList<>();
            for (String part : str.split("\\s+")) {
                if (!part.isEmpty()) {
                    command.add(part);
                }
            }
            return command;
        }

        throw new IOException("Expected array or string command, got: " + result.getClass());
    }

    // ── Private Parsers ──

    private String parseSimpleString(String data) {
        return data;
    }

    private String parseError(String data) {
        return "ERR:" + data;
    }

    private Long parseInteger(String data) {
        return Long.parseLong(data.trim());
    }

    private String parseBulkString(String data) throws IOException {
        int length = Integer.parseInt(data.trim());

        // Null bulk string: $-1\r\n
        if (length == -1) {
            return null;
        }

        // Read exactly 'length' characters
        char[] buffer = new char[length];
        int totalRead = 0;
        while (totalRead < length) {
            int read = reader.read(buffer, totalRead, length - totalRead);
            if (read == -1) {
                throw new IOException("Unexpected end of stream while reading bulk string");
            }
            totalRead += read;
        }

        // Consume trailing \r\n
        reader.readLine();

        return new String(buffer);
    }

    private List<Object> parseArray(String data) throws IOException {
        int count = Integer.parseInt(data.trim());

        // Null array: *-1\r\n
        if (count == -1) {
            return null;
        }

        List<Object> array = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            array.add(parse());
        }
        return array;
    }
}
