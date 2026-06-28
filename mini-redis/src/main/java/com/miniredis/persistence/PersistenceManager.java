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
    private ScheduledExecutorService aofScheduler;
    private long lastRewriteSize = 0;

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
                rdbSnapshotter.backgroundSave();
            } catch (Exception e) {
                System.err.println("[RDB] Snapshot failed: " + e.getMessage());
            }
        }, interval, interval, TimeUnit.SECONDS);

        System.out.println("[Persistence] RDB snapshots every " + interval + " seconds");
    }

    /**
     * Start the background thread that checks AOF size and triggers auto-rewrites.
     */
    public void startAofAutoRewrite() {
        if (aofEngine == null) {
            return;
        }

        aofScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "mini-redis-aof-rewrite");
            t.setDaemon(true);
            return t;
        });

        // Initialize lastRewriteSize on startup
        lastRewriteSize = aofEngine.getFileSize();

        // Check AOF size every 30 seconds
        aofScheduler.scheduleAtFixedRate(() -> {
            try {
                checkAndTriggerAofRewrite();
            } catch (Exception e) {
                System.err.println("[AOF] Auto-rewrite check failed: " + e.getMessage());
            }
        }, 30, 30, TimeUnit.SECONDS);

        System.out.println("[Persistence] AOF auto-rewrite enabled (Min size: " + 
                           config.getAofRewriteMinSize() + " bytes, Growth: " + 
                           config.getAofRewriteGrowthPercent() + "%)");
    }

    private synchronized void checkAndTriggerAofRewrite() {
        long currentSize = aofEngine.getFileSize();
        long minSize = config.getAofRewriteMinSize();

        if (currentSize < minSize) {
            return; // AOF size is below threshold
        }

        long growth = currentSize - lastRewriteSize;
        double growthPercent = lastRewriteSize == 0 ? 100.0 : ((double) growth / lastRewriteSize) * 100.0;

        if (growthPercent >= config.getAofRewriteGrowthPercent()) {
            System.out.println("[AOF] Auto-rewrite triggered. Current size: " + currentSize + 
                               " bytes, Last rewrite size: " + lastRewriteSize + 
                               " bytes (" + String.format("%.1f", growthPercent) + "% growth)");
            aofEngine.rewrite(dataStore);
            lastRewriteSize = aofEngine.getFileSize();
        }
    }

    /**
     * Trigger a background manual AOF rewrite (BGREWRITEAOF command).
     */
    public void bgRewriteAof() {
        if (aofEngine == null) {
            return;
        }
        new Thread(() -> {
            aofEngine.rewrite(dataStore);
            synchronized (this) {
                lastRewriteSize = aofEngine.getFileSize();
            }
        }, "mini-redis-bgrewriteaof").start();
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
     * Trigger a background manual RDB snapshot (BGSAVE command).
     */
    public void bgSaveSnapshot() {
        if (rdbSnapshotter != null) {
            rdbSnapshotter.backgroundSave();
        }
    }

    /**
     * Check if a background save is currently in progress.
     */
    public boolean isBgSaveInProgress() {
        return rdbSnapshotter != null && rdbSnapshotter.isSaving();
    }

    public RdbSnapshotter getSnapshotter() {
        return rdbSnapshotter;
    }

    public String getSnapshotFile() {
        return rdbSnapshotter != null ? rdbSnapshotter.getFilePath() : null;
    }

    /**
     * Graceful shutdown: flush AOF, take final snapshot.
     */
    public void shutdown() {
        System.out.println("[Persistence] Shutting down...");

        if (rdbScheduler != null) {
            rdbScheduler.shutdown();
        }

        if (aofScheduler != null) {
            aofScheduler.shutdown();
        }

        // Final snapshot
        if (rdbSnapshotter != null) {
            rdbSnapshotter.save();
            rdbSnapshotter.shutdown(); // Stop background executor
            System.out.println("[Persistence] Final RDB snapshot saved.");
        }

        // Close AOF
        if (aofEngine != null) {
            aofEngine.close();
            System.out.println("[Persistence] AOF file closed.");
        }
    }
}
