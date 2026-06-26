package com.miniredis;

import com.miniredis.command.CommandRouter;
import com.miniredis.config.ServerConfig;
import com.miniredis.persistence.PersistenceManager;
import com.miniredis.pubsub.PubSubManager;
import com.miniredis.server.RedisServer;
import com.miniredis.store.DataStore;

/**
 * 🔴 Mini Redis — Entry Point
 * 
 * A fully functional in-memory key-value data store built from scratch in pure Java.
 * Implements the Redis RESP2 protocol — compatible with redis-cli.
 * 
 * Usage:
 *   java -jar mini-redis-1.0.jar
 *   java -jar mini-redis-1.0.jar --port 6380
 */
public class MiniRedisApplication {

    private static final long START_TIME = System.currentTimeMillis();

    public static long getStartTime() {
        return START_TIME;
    }

    public static void main(String[] args) {
        System.out.println("🔴 Starting Mini Redis...");

        // ── Configuration ──
        ServerConfig config = new ServerConfig();
        parseArgs(args, config);

        // ── Initialize Components ──
        DataStore dataStore = new DataStore();
        PubSubManager pubSubManager = new PubSubManager();
        PersistenceManager persistenceManager = new PersistenceManager(config, dataStore);
        CommandRouter commandRouter = new CommandRouter(dataStore, pubSubManager, persistenceManager);
        RedisServer server = new RedisServer(config, commandRouter, pubSubManager, persistenceManager);

        // ── Initialize Persistence ──
        persistenceManager.initialize();
        persistenceManager.loadData();

        // ── Start Active Expiry ──
        dataStore.getExpiryManager().startActiveExpiry(
                config.getActiveExpiryIntervalMs(),
                config.getActiveExpirySampleSize(),
                config.getActiveExpiryThreshold()
        );

        // ── Start Periodic RDB Snapshots ──
        persistenceManager.startPeriodicSnapshots();

        // ── Shutdown Hook ──
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n🔴 Received shutdown signal...");
            server.stop();
            dataStore.getExpiryManager().stop();
            persistenceManager.shutdown();
            System.out.println("🔴 Mini Redis stopped. Goodbye!");
        }));

        // ── Start TCP Server (blocking) ──
        try {
            server.start();
        } catch (Exception e) {
            System.err.println("❌ Failed to start server: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * Parse command-line arguments.
     * Supports: --port <number>
     */
    private static void parseArgs(String[] args, ServerConfig config) {
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--port", "-p" -> {
                    if (i + 1 < args.length) {
                        config.setPort(Integer.parseInt(args[++i]));
                    }
                }
                case "--dir" -> {
                    if (i + 1 < args.length) {
                        config.setDataDir(args[++i]);
                    }
                }
                case "--no-aof" -> config.setAofEnabled(false);
                case "--no-rdb" -> config.setRdbEnabled(false);
                case "--help", "-h" -> {
                    printHelp();
                    System.exit(0);
                }
            }
        }
    }

    private static void printHelp() {
        System.out.println("""
                Usage: java -jar mini-redis-1.0.jar [options]
                
                Options:
                  --port, -p <port>    Set server port (default: 6380)
                  --dir <path>         Set data directory (default: data)
                  --no-aof             Disable AOF persistence
                  --no-rdb             Disable RDB snapshots
                  --help, -h           Show this help message
                
                Examples:
                  java -jar mini-redis-1.0.jar
                  java -jar mini-redis-1.0.jar --port 6381
                  java -jar mini-redis-1.0.jar --no-aof --no-rdb
                """);
    }
}
