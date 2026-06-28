package com.miniredis.persistence;

import com.miniredis.command.Command;
import com.miniredis.store.DataStore;
import com.miniredis.store.DataType;
import com.miniredis.store.RedisObject;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Append-Only File (AOF) persistence engine.
 * 
 * Every write command is appended to the AOF file as a simple text format.
 * On startup, the file is replayed to rebuild the in-memory state.
 * 
 * Format (one command per line):
 *   SET key value
 *   DEL key1 key2
 *   LPUSH key val1 val2
 */
public class AofEngine {

    private final String filePath;
    private BufferedWriter writer;

    public AofEngine(String filePath) {
        this.filePath = filePath;
        openWriter();
    }

    /**
     * Append a write command to the AOF file.
     */
    public synchronized void appendCommand(Command command) {
        try {
            if (writer == null) {
                openWriter();
            }

            // Write as tab-separated values (handles spaces in values)
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < command.args().size(); i++) {
                if (i > 0) sb.append('\t');
                sb.append(escapeValue(command.args().get(i)));
            }
            writer.write(sb.toString());
            writer.newLine();
            writer.flush();
        } catch (IOException e) {
            System.err.println("[AOF] Failed to append command: " + e.getMessage());
        }
    }

    /**
     * Replay the AOF file to rebuild the data store.
     */
    public void replay(DataStore dataStore) {
        Path path = Path.of(filePath);
        if (!Files.exists(path)) {
            return;
        }

        int commands = 0;
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                try {
                    List<String> args = parseLine(line);
                    if (!args.isEmpty()) {
                        replayCommand(dataStore, args);
                        commands++;
                    }
                } catch (Exception e) {
                    System.err.println("[AOF] Skipping malformed command: " + line);
                }
            }
        } catch (IOException e) {
            System.err.println("[AOF] Error replaying: " + e.getMessage());
        }

        if (commands > 0) {
            System.out.println("[AOF] Replayed " + commands + " commands from " + filePath);
        }
    }

    /**
     * Replay a single command during AOF recovery.
     */
    private void replayCommand(DataStore dataStore, List<String> args) {
        String cmd = args.getFirst().toUpperCase();

        switch (cmd) {
            case "SET" -> {
                if (args.size() >= 3) {
                    dataStore.set(args.get(1), RedisObject.string(args.get(2)));
                    // Handle EX option
                    if (args.size() >= 5 && "EX".equalsIgnoreCase(args.get(3))) {
                        long seconds = Long.parseLong(args.get(4));
                        dataStore.getExpiryManager().setExpirySeconds(args.get(1), seconds);
                    }
                }
            }
            case "MSET" -> {
                for (int i = 1; i + 1 < args.size(); i += 2) {
                    dataStore.set(args.get(i), RedisObject.string(args.get(i + 1)));
                }
            }
            case "DEL" -> {
                for (int i = 1; i < args.size(); i++) {
                    dataStore.delete(args.get(i));
                }
            }
            case "INCR" -> {
                if (args.size() >= 2) {
                    String key = args.get(1);
                    RedisObject obj = dataStore.get(key);
                    long val = 0;
                    if (obj != null && obj.getType() == DataType.STRING) {
                        val = Long.parseLong(obj.getStringValue());
                    }
                    dataStore.set(key, RedisObject.string(String.valueOf(val + 1)));
                }
            }
            case "DECR" -> {
                if (args.size() >= 2) {
                    String key = args.get(1);
                    RedisObject obj = dataStore.get(key);
                    long val = 0;
                    if (obj != null && obj.getType() == DataType.STRING) {
                        val = Long.parseLong(obj.getStringValue());
                    }
                    dataStore.set(key, RedisObject.string(String.valueOf(val - 1)));
                }
            }
            case "EXPIRE" -> {
                if (args.size() >= 3) {
                    long seconds = Long.parseLong(args.get(2));
                    dataStore.getExpiryManager().setExpirySeconds(args.get(1), seconds);
                }
            }
            case "LPUSH" -> {
                if (args.size() >= 3) {
                    String key = args.get(1);
                    RedisObject obj = dataStore.get(key);
                    if (obj == null) {
                        obj = RedisObject.list();
                        dataStore.set(key, obj);
                    }
                    for (int i = 2; i < args.size(); i++) {
                        obj.getListValue().addFirst(args.get(i));
                    }
                }
            }
            case "RPUSH" -> {
                if (args.size() >= 3) {
                    String key = args.get(1);
                    RedisObject obj = dataStore.get(key);
                    if (obj == null) {
                        obj = RedisObject.list();
                        dataStore.set(key, obj);
                    }
                    for (int i = 2; i < args.size(); i++) {
                        obj.getListValue().addLast(args.get(i));
                    }
                }
            }
            case "LPOP" -> {
                if (args.size() >= 2) {
                    RedisObject obj = dataStore.get(args.get(1));
                    if (obj != null && obj.getType() == DataType.LIST && !obj.getListValue().isEmpty()) {
                        obj.getListValue().removeFirst();
                    }
                }
            }
            case "RPOP" -> {
                if (args.size() >= 2) {
                    RedisObject obj = dataStore.get(args.get(1));
                    if (obj != null && obj.getType() == DataType.LIST && !obj.getListValue().isEmpty()) {
                        obj.getListValue().removeLast();
                    }
                }
            }
            case "SADD" -> {
                if (args.size() >= 3) {
                    String key = args.get(1);
                    RedisObject obj = dataStore.get(key);
                    if (obj == null) {
                        obj = RedisObject.set();
                        dataStore.set(key, obj);
                    }
                    for (int i = 2; i < args.size(); i++) {
                        obj.getSetValue().add(args.get(i));
                    }
                }
            }
            case "SREM" -> {
                if (args.size() >= 3) {
                    RedisObject obj = dataStore.get(args.get(1));
                    if (obj != null && obj.getType() == DataType.SET) {
                        for (int i = 2; i < args.size(); i++) {
                            obj.getSetValue().remove(args.get(i));
                        }
                    }
                }
            }
            case "HSET" -> {
                if (args.size() >= 4) {
                    String key = args.get(1);
                    RedisObject obj = dataStore.get(key);
                    if (obj == null) {
                        obj = RedisObject.hash();
                        dataStore.set(key, obj);
                    }
                    for (int i = 2; i + 1 < args.size(); i += 2) {
                        obj.getHashValue().put(args.get(i), args.get(i + 1));
                    }
                }
            }
            case "FLUSHDB" -> dataStore.flushAll();
        }
    }

    /**
     * Get the size of the AOF file in bytes.
     */
    public long getFileSize() {
        try {
            Path path = Path.of(filePath);
            if (Files.exists(path)) {
                return Files.size(path);
            }
        } catch (IOException e) {
            // Ignore
        }
        return 0;
    }

    /**
     * Rewrite (compact) the AOF file.
     */
    public synchronized void rewrite(DataStore dataStore) {
        System.out.println("[AOF] Starting AOF rewrite compaction...");
        long start = System.currentTimeMillis();
        close();

        String tempPath = filePath + ".rewrite";
        try {
            try (BufferedWriter tempWriter = new BufferedWriter(new FileWriter(tempPath))) {
                var store = dataStore.getRawStore();
                var expiryMap = dataStore.getExpiryManager().getRawExpiryMap();
                long now = System.currentTimeMillis();

                for (String key : store.keySet()) {
                    RedisObject obj = dataStore.get(key);
                    if (obj == null) {
                        continue;
                    }

                    // Skip expired keys
                    Long expiry = expiryMap.get(key);
                    if (expiry != null && expiry <= now) {
                        continue;
                    }

                    List<String> args = new ArrayList<>();
                    switch (obj.getType()) {
                        case STRING -> {
                            args.add("SET");
                            args.add(key);
                            args.add(obj.getStringValue());
                            writeCommandToWriter(tempWriter, args);
                        }
                        case LIST -> {
                            args.add("RPUSH");
                            args.add(key);
                            args.addAll(obj.getListValue());
                            writeCommandToWriter(tempWriter, args);
                        }
                        case SET -> {
                            args.add("SADD");
                            args.add(key);
                            args.addAll(obj.getSetValue());
                            writeCommandToWriter(tempWriter, args);
                        }
                        case HASH -> {
                            args.add("HSET");
                            args.add(key);
                            for (Map.Entry<String, String> fieldEntry : obj.getHashValue().entrySet()) {
                                args.add(fieldEntry.getKey());
                                args.add(fieldEntry.getValue());
                            }
                            writeCommandToWriter(tempWriter, args);
                        }
                    }

                    // If key has TTL, write EXPIRE command
                    if (expiry != null) {
                        long remainingSeconds = (expiry - now) / 1000;
                        if (remainingSeconds > 0) {
                            List<String> expireArgs = List.of("EXPIRE", key, String.valueOf(remainingSeconds));
                            writeCommandToWriter(tempWriter, expireArgs);
                        }
                    }
                }
            }

            // Atomically rename temp rewrite file to main file
            Files.move(Path.of(tempPath), Path.of(filePath), StandardCopyOption.REPLACE_EXISTING);
            System.out.println("[AOF] AOF rewrite completed in " + (System.currentTimeMillis() - start) + " ms");
        } catch (IOException e) {
            System.err.println("[AOF] Rewrite failed: " + e.getMessage());
            // Try to cleanup temp file if still exists
            try {
                Files.deleteIfExists(Path.of(tempPath));
            } catch (IOException ioEx) {
                // Ignore
            }
        } finally {
            openWriter();
        }
    }

    private void writeCommandToWriter(BufferedWriter w, List<String> args) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < args.size(); i++) {
            if (i > 0) sb.append('\t');
            sb.append(escapeValue(args.get(i)));
        }
        w.write(sb.toString());
        w.newLine();
    }

    /**
     * Close the AOF writer.
     */
    public void close() {
        try {
            if (writer != null) {
                writer.flush();
                writer.close();
            }
        } catch (IOException e) {
            System.err.println("[AOF] Error closing: " + e.getMessage());
        }
    }

    // ── Helpers ──

    private void openWriter() {
        try {
            writer = new BufferedWriter(new FileWriter(filePath, true)); // append mode
        } catch (IOException e) {
            System.err.println("[AOF] Failed to open file: " + e.getMessage());
        }
    }

    private String escapeValue(String value) {
        return value.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n");
    }

    private String unescapeValue(String value) {
        return value.replace("\\n", "\n").replace("\\t", "\t").replace("\\\\", "\\");
    }

    private List<String> parseLine(String line) {
        String[] parts = line.split("\t");
        List<String> args = new ArrayList<>();
        for (String part : parts) {
            args.add(unescapeValue(part));
        }
        return args;
    }
}
