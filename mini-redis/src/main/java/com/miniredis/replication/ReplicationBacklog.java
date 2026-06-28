package com.miniredis.replication;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * ReplicationBacklog — circular-like buffer storing recent write commands.
 * Used for partial resynchronization (PSYNC) when replicas temporarily disconnect.
 */
public class ReplicationBacklog {

    private final int maxSize;
    private final LinkedList<BacklogEntry> buffer = new LinkedList<>();
    private long masterOffset = 0;

    public ReplicationBacklog(int maxSize) {
        this.maxSize = maxSize;
    }

    public static class BacklogEntry {
        public final long offset;
        public final String respCommand;

        public BacklogEntry(long offset, String respCommand) {
            this.offset = offset;
            this.respCommand = respCommand;
        }
    }

    /**
     * Add a raw RESP command to the backlog.
     */
    public synchronized void addCommand(String respCommand) {
        byte[] bytes = respCommand.getBytes(StandardCharsets.UTF_8);
        masterOffset += bytes.length;

        buffer.addLast(new BacklogEntry(masterOffset, respCommand));

        // Bounded size pruning
        long totalSize = getBufferSize();
        while (totalSize > maxSize && !buffer.isEmpty()) {
            BacklogEntry removed = buffer.removeFirst();
            totalSize -= removed.respCommand.getBytes(StandardCharsets.UTF_8).length;
        }
    }

    public synchronized long getMasterOffset() {
        return masterOffset;
    }

    /**
     * Retrieve commands since a specific replica offset.
     * Returns null if offset is too old (not in backlog), triggering full resync.
     */
    public synchronized List<String> getCommandsFromOffset(long replicaOffset) {
        if (buffer.isEmpty()) {
            if (replicaOffset == masterOffset) {
                return new ArrayList<>();
            }
            return null;
        }

        long oldestOffset = buffer.getFirst().offset - buffer.getFirst().respCommand.getBytes(StandardCharsets.UTF_8).length;
        if (replicaOffset < oldestOffset || replicaOffset > masterOffset) {
            return null; // Trigger full sync
        }

        List<String> commands = new ArrayList<>();
        for (BacklogEntry entry : buffer) {
            long entryStartOffset = entry.offset - entry.respCommand.getBytes(StandardCharsets.UTF_8).length;
            if (entryStartOffset >= replicaOffset) {
                commands.add(entry.respCommand);
            }
        }
        return commands;
    }

    private long getBufferSize() {
        long size = 0;
        for (BacklogEntry entry : buffer) {
            size += entry.respCommand.getBytes(StandardCharsets.UTF_8).length;
        }
        return size;
    }
}
