package com.miniredis.dashboard;

import com.sun.net.httpserver.HttpServer;
import com.miniredis.store.DataStore;
import com.miniredis.command.CommandRouter;
import com.miniredis.config.ServerConfig;
import com.miniredis.server.ConnectionManager;
import com.miniredis.pubsub.PubSubManager;
import com.miniredis.persistence.PersistenceManager;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

public class DashboardServer {

    private final HttpServer server;
    private final int port;

    public DashboardServer(int port, DataStore dataStore, CommandRouter commandRouter,
                           ServerConfig config, ConnectionManager connectionManager,
                           PubSubManager pubSubManager, PersistenceManager persistenceManager) throws IOException {
        this.port = port;
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        
        ApiHandler apiHandler = new ApiHandler(
            dataStore, commandRouter, config, connectionManager, pubSubManager, persistenceManager
        );
        
        this.server.createContext("/", apiHandler);
        
        this.server.setExecutor(Executors.newFixedThreadPool(4, r -> {
            Thread t = new Thread(r, "mini-redis-dashboard");
            t.setDaemon(true);
            return t;
        }));
    }

    public void start() {
        server.start();
        System.out.println("🌐 Dashboard: http://localhost:" + port);
    }

    public void stop() {
        if (server != null) {
            server.stop(1);
            System.out.println("[Dashboard] Stopped dashboard server.");
        }
    }
}
