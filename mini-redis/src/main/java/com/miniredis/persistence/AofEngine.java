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
