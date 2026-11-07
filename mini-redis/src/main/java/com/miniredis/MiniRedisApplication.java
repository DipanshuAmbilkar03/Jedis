package com.miniredis;

import com.miniredis.command.CommandRouter;
import com.miniredis.config.ServerConfig;
import com.miniredis.persistence.PersistenceManager;
import com.miniredis.pubsub.PubSubManager;
import com.miniredis.server.RedisServer;
import com.miniredis.store.DataStore;

import java.security.SecureRandom;
import java.util.Base64;

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
        System.out.println("🔴 Starting Jedis In-Memory Database...");
        System.out.println("""
                
                ==================================================================================================
                Jedis is a high-performance, concurrent, in-memory key-value database engine built from scratch
                in pure Java (zero external dependencies), featuring a modern React-based web admin dashboard.
                ==================================================================================================
                
                      ___  ________  ________  ___  ________      ________  ________     
                     |\\  \\|\\   ____\\|\\   __  \\|\\  \\|\\   ____\\    |\\   ___ \\|\\   __  \\    
                     \\ \\  \\ \\  \\___|\\ \\  \\|\\  \\ \\  \\ \\  \\___|    \\ \\  \\_|\\ \\ \\  \\|\\  \\   
                   __ \\ \\  \\ \\  \\    \\ \\  \\\\\\  \\ \\  \\ \\_____  \\    \\ \\  \\ \\\\ \\ \\  __  \\  
                  |\\  \\\\_\\  \\ \\  \\____\\ \\  \\\\\\  \\ \\  \\ \\|____|\\  \\   \\ \\  \\_/ \\ \\  \\|\\  \\ 
                  \\ \\________\\ \\_______\\ \\_______\\ \\__\\____\\_\\  \\   \\ \\_______\\ \\_______\\
                   \\|________|\\|_______|\\|_______|\\|__|\\_________\\   \\|_______|\\|_______|
                                                      \\|_________|                       
                                                 in-memory database engine
                """);

        // ── Configuration ──
        ServerConfig config = new ServerConfig();
        parseArgs(args, config);

        String defaultPassword = config.getRequirepass();
        if (defaultPassword == null || defaultPassword.isEmpty()) {
            defaultPassword = System.getenv("JEDIS_DEFAULT_PASSWORD");
        }
        if (defaultPassword == null || defaultPassword.isEmpty()) {
            if (!config.isAllowPasswordless()) {
                defaultPassword = generatePassword();
                System.out.println("[security] Generated default user password: " + defaultPassword);
                System.out.println("[security] Authenticate with: AUTH " + defaultPassword);
            } else {
                System.out.println("[security] WARNING: authentication is disabled by --insecure-no-auth");
            }
        } else if (config.getRequirepass() != null && !config.getRequirepass().isEmpty()) {
            System.out.println("[security] Using password from --requirepass");
        } else {
            System.out.println("[security] Using password from JEDIS_DEFAULT_PASSWORD");
        }
        config.setRequirepass(defaultPassword);

        // ── Initialize Components ──
        DataStore dataStore = new DataStore();
        dataStore.getMemoryManager().setMaxMemoryBytes(config.getMaxMemoryBytes());
        dataStore.getMemoryManager().setEvictionPolicy(com.miniredis.memory.EvictionPolicy.fromString(config.getEvictionPolicy()));
        dataStore.getMemoryManager().setSampleSize(config.getMaxMemorySamples());
        PubSubManager pubSubManager = new PubSubManager();
        PersistenceManager persistenceManager = new PersistenceManager(config, dataStore);
        CommandRouter commandRouter = new CommandRouter(dataStore, pubSubManager, persistenceManager,
                config.getRequirepass(), config.isAllowPasswordless());
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
        persistenceManager.startAofAutoRewrite();

        // ── Start Dashboard Server ──
        com.miniredis.dashboard.DashboardServer dashboardServer = null;
        if (config.isDashboardEnabled()) {
            try {
                dashboardServer = new com.miniredis.dashboard.DashboardServer(
                    config.getDashboardPort(), dataStore, commandRouter, config,
                    server.getConnectionManager(), pubSubManager, persistenceManager
                );
                dashboardServer.start();
            } catch (Exception e) {
                System.err.println("⚠️ Failed to start Dashboard: " + e.getMessage());
            }
        }

        final com.miniredis.dashboard.DashboardServer finalDashboard = dashboardServer;

        // ── Shutdown Hook ──
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n🔴 Received shutdown signal...");
            if (finalDashboard != null) {
                finalDashboard.stop();
            }
            server.stop();
            dataStore.getExpiryManager().stop();
            persistenceManager.shutdown();
            System.out.println("🔴 Jedis stopped. Goodbye!");
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
                case "--dashboard-port" -> {
                    if (i + 1 < args.length) {
                        config.setDashboardPort(Integer.parseInt(args[++i]));
                    }
                }
                case "--no-dashboard" -> config.setDashboardEnabled(false);
                case "--requirepass" -> {
                    if (i + 1 < args.length) {
                        config.setRequirepass(args[++i]);
                    }
                }
                case "--insecure-no-auth" -> config.setAllowPasswordless(true);
                case "--dir" -> {
                    if (i + 1 < args.length) {
                        config.setDataDir(args[++i]);
                    }
                }
                case "--no-aof" -> config.setAofEnabled(false);
                case "--no-rdb" -> config.setRdbEnabled(false);
                case "--nio" -> config.setNioEnabled(true);
                case "--tls-enabled" -> config.setTlsEnabled(true);
                case "--tls-cert" -> {
                    if (i + 1 < args.length) {
                        config.setTlsCertPath(args[++i]);
                    }
                }
                case "--tls-key" -> {
                    if (i + 1 < args.length) {
                        config.setTlsKeyPath(args[++i]);
                    }
                }
                case "--tls-port" -> {
                    if (i + 1 < args.length) {
                        config.setTlsPort(Integer.parseInt(args[++i]));
                    }
                }
                case "--replicaof" -> {
                    if (i + 2 < args.length) {
                        config.setReplicaOfHost(args[++i]);
                        config.setReplicaOfPort(Integer.parseInt(args[++i]));
                    }
                }
                case "--repl-backlog-size" -> {
                    if (i + 1 < args.length) {
                        config.setReplBacklogSize(Long.parseLong(args[++i]));
                    }
                }
                case "--cluster-enabled" -> {
                    if (i + 1 < args.length) {
                        config.setClusterEnabled("yes".equalsIgnoreCase(args[++i]));
                    }
                }
                case "--maxmemory" -> {
                    if (i + 1 < args.length) {
                        config.setMaxMemoryBytes(Long.parseLong(args[++i]));
                    }
                }
                case "--eviction-policy" -> {
                    if (i + 1 < args.length) {
                        config.setEvictionPolicy(args[++i]);
                    }
                }
                case "--help", "-h" -> {
                    printHelp();
                    System.exit(0);
                }
            }
        }
    }

    private static String generatePassword() {
        byte[] bytes = new byte[24];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static void printHelp() {
        System.out.println("""
                Usage: java -jar mini-redis-1.0.jar [options]
                
                Options:
                  --port, -p <port>          Set server port (default: 6380)
                  --dashboard-port <port>    Set dashboard port (default: 8080)
                  --no-dashboard             Disable dashboard server
                  --requirepass <password>   Set the default user password
                  --insecure-no-auth         Disable authentication (not recommended)
                  --dir <path>               Set data directory (default: data)
                  --no-aof                   Disable AOF persistence
                  --no-rdb                   Disable RDB snapshots
                  --help, -h                 Show this help message
                
                Environment:
                  JEDIS_DEFAULT_PASSWORD     Default user password (used if --requirepass is absent)
                  JEDIS_DASHBOARD_TOKEN      Fixed dashboard access token
                  JEDIS_DASHBOARD_BIND       Dashboard bind address (default: 127.0.0.1)
                  JEDIS_DASHBOARD_ORIGIN     Allowed CORS origin for a separate dev frontend
                
                Examples:
                  java -jar mini-redis-1.0.jar
                  java -jar mini-redis-1.0.jar --port 6381
                  java -jar mini-redis-1.0.jar --requirepass change-me
                  java -jar mini-redis-1.0.jar --no-aof --no-rdb
                """);
    }
}
