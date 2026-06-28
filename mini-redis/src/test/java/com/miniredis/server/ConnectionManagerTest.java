package com.miniredis.server;

import com.miniredis.config.ServerConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ConnectionManagerTest {

    static class TestClientHandler extends ClientHandler {
        private boolean connected = true;
        private final String clientId;
        private long lastActivityTime;
        private boolean cleanedUp = false;

        public TestClientHandler(String clientId) {
            super((java.net.Socket) null, null, null, null, null);
            this.clientId = clientId;
            this.lastActivityTime = System.currentTimeMillis();
        }

        @Override
        public boolean isConnected() {
            return connected;
        }

        public void setConnected(boolean connected) {
            this.connected = connected;
        }

        @Override
        public String getClientId() {
            return clientId;
        }

        @Override
        public long getLastActivityTime() {
            return lastActivityTime;
        }

        public void setLastActivityTime(long lastActivityTime) {
            this.lastActivityTime = lastActivityTime;
        }

        @Override
        public void cleanup() {
            this.connected = false;
            this.cleanedUp = true;
        }

        public boolean isCleanedUp() {
            return cleanedUp;
        }
    }

    @Test
    public void testMaxConnectionsLimit() {
        ServerConfig config = new ServerConfig();
        config.setMaxClients(2);

        ConnectionManager manager = new ConnectionManager(config);

        TestClientHandler client1 = new TestClientHandler("client1");
        TestClientHandler client2 = new TestClientHandler("client2");
        TestClientHandler client3 = new TestClientHandler("client3");

        assertTrue(manager.acceptConnection(client1));
        assertTrue(manager.acceptConnection(client2));
        assertFalse(manager.acceptConnection(client3));
    }

    @Test
    public void testIdleTimeoutCleanup() throws Exception {
        ServerConfig config = new ServerConfig();
        config.setMaxClients(5);
        config.setIdleTimeoutSeconds(1); // 1 second idle limit

        ConnectionManager manager = new ConnectionManager(config);

        TestClientHandler client = new TestClientHandler("client-idle");
        // Simulate idle client (2 seconds ago)
        client.setLastActivityTime(System.currentTimeMillis() - 2000L);

        manager.acceptConnection(client);
        manager.startIdleMonitor();

        // Wait for connection manager to check (every 5 seconds)
        Thread.sleep(6000);

        assertTrue(client.isCleanedUp());
        assertFalse(client.isConnected());

        manager.stop();
    }
}
