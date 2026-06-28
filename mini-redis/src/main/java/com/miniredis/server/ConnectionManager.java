package com.miniredis.server;

import com.miniredis.config.ServerConfig;

import java.io.IOException;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * ConnectionManager — manages active client connections, enforces limits, and idle timeouts.
 */
public class ConnectionManager {

    private final int maxConnections;
    private final int idleTimeoutSeconds;
    private final Set<ClientHandler> activeConnections;
    private ScheduledExecutorService scheduler;

    public ConnectionManager(ServerConfig config) {
        this.maxConnections = config.getMaxClients();
        this.idleTimeoutSeconds = config.getIdleTimeoutSeconds();
        this.activeConnections = ConcurrentHashMap.newKeySet();
    }

    /**
     * Attempt to register a new connection.
     * Returns true if connection is accepted, false if connection limit exceeded.
     */
    public boolean acceptConnection(ClientHandler client) {
        // Clean up disconnected clients first
        activeConnections.removeIf(c -> !c.isConnected());

        if (activeConnections.size() >= maxConnections) {
            System.err.println("[ConnectionManager] Max connections reached (" + maxConnections + "). Rejecting client: " + client.getClientId());
            return false;
        }

        activeConnections.add(client);
        return true;
    }

    /**
     * Remove a connection from tracking.
     */
    public void removeConnection(ClientHandler client) {
        activeConnections.remove(client);
    }

    /**
     * Start the idle connection timeout monitor thread.
     */
    public void startIdleMonitor() {
        if (idleTimeoutSeconds <= 0) {
            return;
        }

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "mini-redis-connection-timeout");
            t.setDaemon(true);
            return t;
        });

        // Run check every 5 seconds
        scheduler.scheduleAtFixedRate(this::checkIdleConnections, 5, 5, TimeUnit.SECONDS);
        System.out.println("[ConnectionManager] Started idle timeout monitor (Timeout: " + idleTimeoutSeconds + "s)");
    }

    /**
     * Stop the idle timeout monitor scheduler.
     */
    public void stop() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
        }
        for (ClientHandler client : activeConnections) {
            client.cleanup();
        }
        activeConnections.clear();
    }

    public int getActiveConnectionsCount() {
        activeConnections.removeIf(c -> !c.isConnected());
        return activeConnections.size();
    }

    private void checkIdleConnections() {
        long now = System.currentTimeMillis();
        long timeoutMs = idleTimeoutSeconds * 1000L;

        for (ClientHandler client : activeConnections) {
            if (!client.isConnected()) {
                activeConnections.remove(client);
                continue;
            }

            long idleTime = now - client.getLastActivityTime();
            if (idleTime > timeoutMs) {
                System.out.println("[ConnectionManager] Closing idle connection: " + client.getClientId() + " (Idle for " + (idleTime / 1000) + "s)");
                try {
                    client.sendRawResponse("-ERR connection idle timeout reached\r\n");
                } catch (IOException e) {
                    // Ignore
                }
                client.cleanup();
                activeConnections.remove(client);
            }
        }
    }
}
