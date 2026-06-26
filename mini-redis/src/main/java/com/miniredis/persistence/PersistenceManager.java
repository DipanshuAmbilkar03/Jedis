package com.miniredis.persistence;

import com.miniredis.command.Command;
import com.miniredis.config.ServerConfig;
import com.miniredis.store.DataStore;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Coordinates both persistence engines:
 * - AOF (Append-Only File) — logs every write command for replay on restart
 * - RDB (Snapshot) — periodic JSON dump of entire store for fast recovery
 * 
 * On startup: loads RDB snapshot first, then replays AOF for any newer writes.
 */
public class PersistenceManager {

    private final ServerConfig config;
    private final DataStore dataStore;

    private AofEngine aofEngine;
    private RdbSnapshotter rdbSnapshotter;
    private ScheduledExecutorService rdbScheduler;

    public PersistenceManager(ServerConfig config, DataStore dataStore) {
        this.config = config;
        this.dataStore = dataStore;
    }

    /**
     * Initialize persistence: create data directory, load existing data.
     */
    public void initialize() {
        // Create data directory if needed
        try {
            Files.createDirectories(Path.of(config.getDataDir()));
        } catch (IOException e) {
            System.err.println("[Persistence] Failed to create data directory: " + e.getMessage());
        }

        // Initialize engines
        if (config.isAofEnabled()) {
            aofEngine = new AofEngine(config.getAofFilePath());
        }
        if (config.isRdbEnabled()) {
            rdbSnapshotter = new RdbSnapshotter(config.getRdbFilePath(), dataStore);
        }
    }

    /**
     * Load persisted data into the store.
     * Order: RDB first (base state), then AOF (incremental updates).
     */
    public void loadData() {
        // Load RDB snapshot
        if (rdbSnapshotter != null) {
            rdbSnapshotter.load();
        }

        // Replay AOF (this will re-apply any writes since the last RDB snapshot)
        if (aofEngine != null) {
            aofEngine.replay(dataStore);
        }
    }

    /**
     * Start the periodic RDB snapshot scheduler.
     */
    public void startPeriodicSnapshots() {
        if (rdbSnapshotter == null) {
            return;
        }

        rdbScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "mini-redis-rdb");
            t.setDaemon(true);
            return t;
        });

        int interval = config.getRdbSaveIntervalSeconds();
        rdbScheduler.scheduleAtFixedRate(() -> {
            try {
                rdbSnapshotter.save();
            } catch (Exception e) {
                System.err.println("[RDB] Snapshot failed: " + e.getMessage());
            }
        }, interval, interval, TimeUnit.SECONDS);

        System.out.println("[Persistence] RDB snapshots every " + interval + " seconds");
    }

    /**
     * Log a write command to AOF.
     */
    public void logCommand(Command command) {
        if (aofEngine != null) {
            aofEngine.appendCommand(command);
        }
    }

    /**
     * Trigger a manual RDB snapshot (SAVE command).
     */
    public void saveSnapshot() {
        if (rdbSnapshotter != null) {
            rdbSnapshotter.save();
        }
    }

    /**
     * Graceful shutdown: flush AOF, take final snapshot.
     */
    public void shutdown() {
        System.out.println("[Persistence] Shutting down...");

        if (rdbScheduler != null) {
            rdbScheduler.shutdown();
        }

        // Final snapshot
        if (rdbSnapshotter != null) {
            rdbSnapshotter.save();
            System.out.println("[Persistence] Final RDB snapshot saved.");
        }

        // Close AOF
        if (aofEngine != null) {
            aofEngine.close();
            System.out.println("[Persistence] AOF file closed.");
        }
    }
}
