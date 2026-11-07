package com.miniredis.dashboard;

import com.sun.net.httpserver.HttpServer;
import com.miniredis.store.DataStore;
import com.miniredis.command.CommandRouter;
import com.miniredis.config.ServerConfig;
import com.miniredis.server.ConnectionManager;
import com.miniredis.pubsub.PubSubManager;
import com.miniredis.persistence.PersistenceManager;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.Executors;

public class DashboardServer {

    private final HttpServer server;
    private final String token;
    private final String bindAddress;

    public DashboardServer(int port, DataStore dataStore, CommandRouter commandRouter,
                           ServerConfig config, ConnectionManager connectionManager,
                           PubSubManager pubSubManager, PersistenceManager persistenceManager) throws IOException {
        this(port, dataStore, commandRouter, config, connectionManager, pubSubManager, persistenceManager,
                resolveToken(), resolveBindAddress(), resolveAllowedOrigin());
    }

    public DashboardServer(int port, DataStore dataStore, CommandRouter commandRouter,
                           ServerConfig config, ConnectionManager connectionManager,
                           PubSubManager pubSubManager, PersistenceManager persistenceManager,
                           String token, String bindAddress) throws IOException {
        this(port, dataStore, commandRouter, config, connectionManager, pubSubManager, persistenceManager,
                token, bindAddress, resolveAllowedOrigin());
    }

    public DashboardServer(int port, DataStore dataStore, CommandRouter commandRouter,
                           ServerConfig config, ConnectionManager connectionManager,
                           PubSubManager pubSubManager, PersistenceManager persistenceManager,
                           String token, String bindAddress, String allowedOrigin) throws IOException {
        this.token = token;
        this.bindAddress = bindAddress;
        this.server = HttpServer.create(new InetSocketAddress(InetAddress.getByName(bindAddress), port), 0);

        ApiHandler apiHandler = new ApiHandler(
            dataStore, commandRouter, config, connectionManager, pubSubManager, persistenceManager,
            token, allowedOrigin
        );

        this.server.createContext("/", apiHandler);

        this.server.setExecutor(Executors.newFixedThreadPool(4, r -> {
            Thread t = new Thread(r, "mini-redis-dashboard");
            t.setDaemon(true);
            return t;
        }));
    }

    public int getPort() {
        return server.getAddress().getPort();
    }

    public String getToken() {
        return token;
    }

    public void start() {
        server.start();
        System.out.println("[dashboard] Listening on http://" + bindAddress + ":" + getPort());
        System.out.println("[dashboard] Access token: " + token);
        if (!isLoopback(bindAddress)) {
            System.out.println("[dashboard] WARNING: dashboard is bound to a non-loopback address");
        }
    }

    public void stop() {
        if (server != null) {
            server.stop(1);
            System.out.println("[Dashboard] Stopped dashboard server.");
        }
    }

    private static String resolveToken() {
        String token = System.getenv("JEDIS_DASHBOARD_TOKEN");
        if (token != null && !token.isBlank()) {
            return token;
        }
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String resolveBindAddress() {
        String bind = System.getenv("JEDIS_DASHBOARD_BIND");
        return bind == null || bind.isBlank() ? "127.0.0.1" : bind;
    }

    private static String resolveAllowedOrigin() {
        String origin = System.getenv("JEDIS_DASHBOARD_ORIGIN");
        return origin == null || origin.isBlank() ? null : origin;
    }

    private static boolean isLoopback(String address) {
        try {
            return InetAddress.getByName(address).isLoopbackAddress();
        } catch (Exception e) {
            return false;
        }
    }
}
