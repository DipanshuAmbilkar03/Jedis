package com.miniredis.server;

import com.miniredis.command.CommandRouter;
import com.miniredis.config.ServerConfig;
import com.miniredis.persistence.PersistenceManager;
import com.miniredis.pubsub.PubSubManager;
import com.miniredis.replication.ReplicationManager;
import com.miniredis.cluster.ClusterManager;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * TCP server that listens for client connections on the configured port.
 * Each client gets its own thread from a fixed thread pool.
 */
public class RedisServer {

    private final ServerConfig config;
    private final CommandRouter commandRouter;
    private final PubSubManager pubSubManager;
    private final PersistenceManager persistenceManager;
    private final Set<ClientHandler> activeClients;
    private final ReplicationManager replicationManager;
    private final ClusterManager clusterManager;

    private ServerSocket serverSocket;
    private ExecutorService threadPool;
    private volatile boolean running;
    private NioEventLoop nioEventLoop;
    private final ConnectionManager connectionManager;

    public RedisServer(ServerConfig config, CommandRouter commandRouter,
                       PubSubManager pubSubManager, PersistenceManager persistenceManager) {
        this.config = config;
        this.commandRouter = commandRouter;
        this.pubSubManager = pubSubManager;
        this.persistenceManager = persistenceManager;
        this.activeClients = Collections.synchronizedSet(new LinkedHashSet<>());
        this.connectionManager = new ConnectionManager(config);
        this.replicationManager = new ReplicationManager(config);
        this.commandRouter.setReplicationManager(this.replicationManager);
        this.clusterManager = new ClusterManager(config);
        this.commandRouter.setClusterManager(this.clusterManager);
    }

    /**
     * Start the TCP server and begin accepting connections.
     */
    public void start() throws IOException {
        connectionManager.startIdleMonitor();
        replicationManager.start(commandRouter.getDataStore(), commandRouter, persistenceManager);

        if (config.isNioEnabled()) {
            nioEventLoop = new NioEventLoop(config, commandRouter, pubSubManager, persistenceManager, connectionManager, replicationManager);
            running = true;
            nioEventLoop.start();
            return;
        }

        if (config.isTlsEnabled() && !config.isNioEnabled()) {
            try {
                var sslContext = com.miniredis.security.TlsConfig.createSSLContext(config);
                var ssf = sslContext.getServerSocketFactory();
                serverSocket = ssf.createServerSocket(config.getPort());
            } catch (Exception e) {
                throw new IOException("Failed to initialize SSLServerSocket: " + e.getMessage(), e);
            }
        } else {
            serverSocket = new ServerSocket(config.getPort());
        }
        threadPool = Executors.newFixedThreadPool(config.getMaxClients());
        running = true;

        printBanner();

        while (running) {
            try {
                Socket clientSocket = serverSocket.accept();
                ClientHandler handler = new ClientHandler(
                        clientSocket, commandRouter, pubSubManager, persistenceManager, replicationManager
                );
                if (connectionManager.acceptConnection(handler)) {
                    activeClients.add(handler);
                    threadPool.submit(handler);
                } else {
                    try {
                        handler.sendRawResponse("-ERR max number of clients reached\r\n");
                    } catch (IOException ioEx) {
                        // Ignore
                    }
                    handler.cleanup();
                }
            } catch (IOException e) {
                if (running) {
                    System.err.println("[Server] Error accepting connection: " + e.getMessage());
                }
            }
        }
    }

    /**
     * Gracefully stop the server.
     */
    public void stop() {
        running = false;

        System.out.println("[Server] Shutting down...");

        connectionManager.stop();
        replicationManager.stop();

        if (nioEventLoop != null) {
            nioEventLoop.stop();
        }

        // Close server socket
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            System.err.println("[Server] Error closing server socket: " + e.getMessage());
        }

        // Shutdown thread pool
        if (threadPool != null) {
            threadPool.shutdown();
        }

        System.out.println("[Server] Shutdown complete.");
    }

    /**
     * Get the number of currently connected clients.
     */
    public int getActiveClientCount() {
        return connectionManager.getActiveConnectionsCount();
    }

    /**
     * Get the connection manager.
     */
    public ConnectionManager getConnectionManager() {
        return connectionManager;
    }

    private void printBanner() {
        System.out.println("""
                
                ╔══════════════════════════════════════════╗
                ║         MINI REDIS v1.0.0                ║
                ║  In-Memory Key-Value Data Store          ║
                ║                                          ║
                ║    Port: %d                              ║
                ║    PID:  %d                              ║
                ║    Ready to accept connections           ║
                ╚══════════════════════════════════════════╝
                """.formatted(config.getPort(), ProcessHandle.current().pid()));
    }
}
