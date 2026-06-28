package com.miniredis.integration;

import com.miniredis.command.CommandRouter;
import com.miniredis.config.ServerConfig;
import com.miniredis.persistence.PersistenceManager;
import com.miniredis.pubsub.PubSubManager;
import com.miniredis.server.RedisServer;
import com.miniredis.store.DataStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

public class NioRedisProtocolIntegrationTest {

    private static final int TEST_PORT = 6391;
    private RedisServer server;
    private ExecutorService serverExecutor;
    private ServerConfig config;

    @BeforeEach
    public void setUp() throws Exception {
        Path tempDir = Path.of("target/temp-test-data-nio");
        Files.createDirectories(tempDir);

        config = new ServerConfig();
        config.setPort(TEST_PORT);
        config.setDataDir(tempDir.toString());
        config.setAofEnabled(false);
        config.setRdbEnabled(false);
        config.setNioEnabled(true); // Enable NIO mode!

        DataStore dataStore = new DataStore();
        PubSubManager pubSubManager = new PubSubManager();
        PersistenceManager persistenceManager = new PersistenceManager(config, dataStore);
        CommandRouter commandRouter = new CommandRouter(dataStore, pubSubManager, persistenceManager);
        
        server = new RedisServer(config, commandRouter, pubSubManager, persistenceManager);

        serverExecutor = Executors.newSingleThreadExecutor();
        serverExecutor.submit(() -> {
            try {
                server.start();
            } catch (IOException e) {
                // Server stopped
            }
        });

        // Wait for server to start
        Thread.sleep(500);
    }

    @AfterEach
    public void tearDown() throws Exception {
        if (server != null) {
            server.stop();
        }
        if (serverExecutor != null) {
            serverExecutor.shutdownNow();
        }
        Thread.sleep(100);
    }

    @Test
    public void testTcpPingAndSetGet() throws Exception {
        try (Socket socket = new Socket("localhost", TEST_PORT)) {
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();

            // Send PING
            out.write("*1\r\n$4\r\nPING\r\n".getBytes());
            out.flush();

            byte[] buffer = new byte[1024];
            int read = in.read(buffer);
            assertTrue(read > 0);
            String response = new String(buffer, 0, read);
            assertEquals("+PONG\r\n", response);

            // Send: SET key value
            String setCommand = "*3\r\n$3\r\nSET\r\n$3\r\nkey\r\n$5\r\nvalue\r\n";
            out.write(setCommand.getBytes());
            out.flush();

            read = in.read(buffer);
            assertTrue(read > 0);
            response = new String(buffer, 0, read);
            assertEquals("+OK\r\n", response);

            // Send: GET key
            String getCommand = "*2\r\n$3\r\nGET\r\n$3\r\nkey\r\n";
            out.write(getCommand.getBytes());
            out.flush();

            read = in.read(buffer);
            assertTrue(read > 0);
            response = new String(buffer, 0, read);
            assertEquals("$5\r\nvalue\r\n", response);
        }
    }
}
