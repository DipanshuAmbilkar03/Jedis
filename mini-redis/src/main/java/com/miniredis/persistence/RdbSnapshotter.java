package com.miniredis.persistence;

import com.miniredis.store.DataStore;
import com.miniredis.store.DataType;
import com.miniredis.store.RedisObject;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * RDB Snapshotter — periodically dumps the entire in-memory store to a JSON file.
 * 
 * Provides fast recovery on startup (load the full snapshot at once).
 * Simpler than real Redis binary RDB format — uses human-readable JSON.
 */
public class RdbSnapshotter {

    private final String filePath;
    private final DataStore dataStore;

    public RdbSnapshotter(String filePath, DataStore dataStore) {
        this.filePath = filePath;
        this.dataStore = dataStore;
    }

    /**
     * Save the entire store to a JSON snapshot file.
     */
    public synchronized void save() {
        try {
            // Write to a temp file first, then rename (atomic)
            String tempPath = filePath + ".tmp";

            try (BufferedWriter writer = new BufferedWriter(new FileWriter(tempPath))) {
                writer.write("{\n");

                var store = dataStore.getRawStore();
                var expiryMap = dataStore.getExpiryManager().getRawExpiryMap();
                Iterator<Map.Entry<String, RedisObject>> it = store.entrySet().iterator();

                boolean first = true;
                while (it.hasNext()) {
                    Map.Entry<String, RedisObject> entry = it.next();
                    String key = entry.getKey();
                    RedisObject obj = entry.getValue();

                    // Skip expired keys
                    if (dataStore.getExpiryManager().isExpired(key)) {
                        continue;
                    }

                    if (!first) writer.write(",\n");
                    first = false;

                    writer.write("  " + jsonString(key) + ": {\n");
                    writer.write("    \"type\": " + jsonString(obj.getType().name()) + ",\n");
                    writer.write("    \"value\": " + serializeValue(obj) + "\n");

                    // Write expiry if present
                    Long expiry = expiryMap.get(key);
                    if (expiry != null) {
                        writer.write("    ,\"expiry\": " + expiry + "\n");
                    }

                    writer.write("  }");
                }

                writer.write("\n}\n");
            }

            // Atomic rename
            Files.move(Path.of(tempPath), Path.of(filePath), StandardCopyOption.REPLACE_EXISTING);

            System.out.println("[RDB] Snapshot saved: " + dataStore.getRawStore().size() + " keys → " + filePath);
        } catch (IOException e) {
            System.err.println("[RDB] Snapshot failed: " + e.getMessage());
        }
    }

    /**
     * Load a snapshot from file into the data store.
     */
    public void load() {
        Path path = Path.of(filePath);
        if (!Files.exists(path)) {
            return;
        }

        try {
            String content = Files.readString(path).trim();
            if (content.isEmpty() || content.equals("{}")) {
                return;
            }

            // Simple JSON parser for our known format
            parseSnapshot(content);

        } catch (IOException e) {
            System.err.println("[RDB] Failed to load snapshot: " + e.getMessage());
        }
    }

    // ── Serialization ──

    private String serializeValue(RedisObject obj) {
        return switch (obj.getType()) {
            case STRING -> jsonString(obj.getStringValue());
            case LIST -> jsonStringArray(obj.getListValue());
            case SET -> jsonStringArray(new ArrayList<>(obj.getSetValue()));
            case HASH -> jsonStringMap(obj.getHashValue());
        };
    }

    // ── Simple JSON helpers (no dependency needed) ──

    private String jsonString(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t") + "\"";
    }

    private String jsonStringArray(List<String> list) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(jsonString(list.get(i)));
        }
        sb.append("]");
        return sb.toString();
    }

    private String jsonStringMap(Map<String, String> map) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> entry : map.entrySet()) {
            if (!first) sb.append(", ");
            first = false;
            sb.append(jsonString(entry.getKey())).append(": ").append(jsonString(entry.getValue()));
        }
        sb.append("}");
        return sb.toString();
    }

    // ── Simple JSON Parser ──

    private void parseSnapshot(String content) {
        // Remove outer braces
        content = content.trim();
        if (content.startsWith("{")) content = content.substring(1);
        if (content.endsWith("}")) content = content.substring(0, content.length() - 1);

        int pos = 0;
        int keysLoaded = 0;

        while (pos < content.length()) {
            // Skip whitespace
            while (pos < content.length() && Character.isWhitespace(content.charAt(pos))) pos++;
            if (pos >= content.length()) break;

            // Skip comma
            if (content.charAt(pos) == ',') {
                pos++;
                continue;
            }

            // Read key
            if (content.charAt(pos) != '"') break;
            int keyEnd = findClosingQuote(content, pos);
            if (keyEnd == -1) break;
            String key = unescapeJsonString(content.substring(pos + 1, keyEnd));
            pos = keyEnd + 1;

            // Skip colon and whitespace
            while (pos < content.length() && (content.charAt(pos) == ':' || Character.isWhitespace(content.charAt(pos)))) pos++;

            // Read the object block
            if (pos >= content.length() || content.charAt(pos) != '{') break;
            int blockEnd = findMatchingBrace(content, pos);
            if (blockEnd == -1) break;

            String block = content.substring(pos + 1, blockEnd);
            pos = blockEnd + 1;

            try {
                parseKeyBlock(key, block);
                keysLoaded++;
            } catch (Exception e) {
                System.err.println("[RDB] Skipping key '" + key + "': " + e.getMessage());
            }
        }

        if (keysLoaded > 0) {
            System.out.println("[RDB] Loaded " + keysLoaded + " keys from " + filePath);
        }
    }

    private void parseKeyBlock(String key, String block) {
        // Extract type
        String type = extractJsonStringValue(block, "type");
        String valueStr = extractRawValue(block, "value");

        if (type == null || valueStr == null) return;

        DataType dataType = DataType.valueOf(type);
        RedisObject obj = switch (dataType) {
            case STRING -> RedisObject.string(parseJsonString(valueStr));
            case LIST -> {
                RedisObject listObj = RedisObject.list();
                List<String> items = parseJsonStringArray(valueStr);
                listObj.getListValue().addAll(items);
                yield listObj;
            }
            case SET -> {
                RedisObject setObj = RedisObject.set();
                List<String> items = parseJsonStringArray(valueStr);
                setObj.getSetValue().addAll(items);
                yield setObj;
            }
            case HASH -> {
                RedisObject hashObj = RedisObject.hash();
                Map<String, String> map = parseJsonStringMap(valueStr);
                hashObj.getHashValue().putAll(map);
                yield hashObj;
            }
        };

        dataStore.set(key, obj);

        // Load expiry
        String expiryStr = extractRawValue(block, "expiry");
        if (expiryStr != null) {
            long expiry = Long.parseLong(expiryStr.trim());
            if (expiry > System.currentTimeMillis()) {
                dataStore.getExpiryManager().setExpiry(key, expiry);
            }
        }
    }

    // ── JSON parsing helpers ──

    private int findClosingQuote(String s, int openPos) {
        for (int i = openPos + 1; i < s.length(); i++) {
            if (s.charAt(i) == '\\') {
                i++; // skip escaped char
            } else if (s.charAt(i) == '"') {
                return i;
            }
        }
        return -1;
    }

    private int findMatchingBrace(String s, int openPos) {
        int depth = 0;
        boolean inString = false;
        for (int i = openPos; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && inString) {
                i++;
                continue;
            }
            if (c == '"') inString = !inString;
            if (!inString) {
                if (c == '{') depth++;
                if (c == '}') {
                    depth--;
                    if (depth == 0) return i;
                }
            }
        }
        return -1;
    }

    private String extractJsonStringValue(String block, String key) {
        String pattern = "\"" + key + "\"";
        int idx = block.indexOf(pattern);
        if (idx == -1) return null;
        idx += pattern.length();
        // Skip : and whitespace
        while (idx < block.length() && (block.charAt(idx) == ':' || block.charAt(idx) == ' ')) idx++;
        if (idx >= block.length() || block.charAt(idx) != '"') return null;
        int end = findClosingQuote(block, idx);
        if (end == -1) return null;
        return unescapeJsonString(block.substring(idx + 1, end));
    }

    private String extractRawValue(String block, String key) {
        String pattern = "\"" + key + "\"";
        int idx = block.indexOf(pattern);
        if (idx == -1) return null;
        idx += pattern.length();
        // Skip : and whitespace
        while (idx < block.length() && (block.charAt(idx) == ':' || block.charAt(idx) == ' ')) idx++;
        if (idx >= block.length()) return null;

        char first = block.charAt(idx);
        if (first == '"') {
            int end = findClosingQuote(block, idx);
            return end != -1 ? block.substring(idx, end + 1) : null;
        } else if (first == '[') {
            int end = findMatchingBracket(block, idx);
            return end != -1 ? block.substring(idx, end + 1) : null;
        } else if (first == '{') {
            int end = findMatchingBrace(block, idx);
            return end != -1 ? block.substring(idx, end + 1) : null;
        } else {
            // Number or other literal
            int end = idx;
            while (end < block.length() && block.charAt(end) != ',' && block.charAt(end) != '\n' && block.charAt(end) != '}') end++;
            return block.substring(idx, end).trim();
        }
    }

    private int findMatchingBracket(String s, int openPos) {
        int depth = 0;
        boolean inString = false;
        for (int i = openPos; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && inString) { i++; continue; }
            if (c == '"') inString = !inString;
            if (!inString) {
                if (c == '[') depth++;
                if (c == ']') { depth--; if (depth == 0) return i; }
            }
        }
        return -1;
    }

    private String parseJsonString(String s) {
        s = s.trim();
        if (s.startsWith("\"") && s.endsWith("\"")) {
            s = s.substring(1, s.length() - 1);
        }
        return unescapeJsonString(s);
    }

    private List<String> parseJsonStringArray(String s) {
        s = s.trim();
        if (s.startsWith("[")) s = s.substring(1);
        if (s.endsWith("]")) s = s.substring(0, s.length() - 1);

        List<String> result = new ArrayList<>();
        int pos = 0;
        while (pos < s.length()) {
            while (pos < s.length() && (s.charAt(pos) == ' ' || s.charAt(pos) == ',')) pos++;
            if (pos >= s.length()) break;
            if (s.charAt(pos) == '"') {
                int end = findClosingQuote(s, pos);
                if (end == -1) break;
                result.add(unescapeJsonString(s.substring(pos + 1, end)));
                pos = end + 1;
            } else {
                pos++;
            }
        }
        return result;
    }

    private Map<String, String> parseJsonStringMap(String s) {
        s = s.trim();
        if (s.startsWith("{")) s = s.substring(1);
        if (s.endsWith("}")) s = s.substring(0, s.length() - 1);

        Map<String, String> result = new LinkedHashMap<>();
        int pos = 0;
        while (pos < s.length()) {
            while (pos < s.length() && (s.charAt(pos) == ' ' || s.charAt(pos) == ',')) pos++;
            if (pos >= s.length() || s.charAt(pos) != '"') break;

            // Key
            int keyEnd = findClosingQuote(s, pos);
            if (keyEnd == -1) break;
            String mapKey = unescapeJsonString(s.substring(pos + 1, keyEnd));
            pos = keyEnd + 1;

            // Skip : and whitespace
            while (pos < s.length() && (s.charAt(pos) == ':' || s.charAt(pos) == ' ')) pos++;

            // Value
            if (pos >= s.length() || s.charAt(pos) != '"') break;
            int valEnd = findClosingQuote(s, pos);
            if (valEnd == -1) break;
            String mapVal = unescapeJsonString(s.substring(pos + 1, valEnd));
            pos = valEnd + 1;

            result.put(mapKey, mapVal);
        }
        return result;
    }

    private String unescapeJsonString(String s) {
        return s.replace("\\\"", "\"").replace("\\\\", "\\")
                .replace("\\n", "\n").replace("\\r", "\r").replace("\\t", "\t");
    }
}
