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

public class RedisProtocolIntegrationTest {

    private static final int TEST_PORT = 6389;
    private RedisServer server;
    private ExecutorService serverExecutor;
    private ServerConfig config;

    @BeforeEach
    public void setUp() throws Exception {
        // Setup temp data directory
        Path tempDir = Path.of("target/temp-test-data");
        Files.createDirectories(tempDir);

        config = new ServerConfig();
        config.setPort(TEST_PORT);
        config.setDataDir(tempDir.toString());
        config.setAofEnabled(false); // disable AOF during integration tests to prevent file locking issues
        config.setRdbEnabled(false); // disable RDB too

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

            // Send: *1\r\n$4\r\nPING\r\n
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

    @Test
    public void testTransactions() throws Exception {
        try (Socket socket = new Socket("localhost", TEST_PORT)) {
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();
            byte[] buffer = new byte[1024];

            // 1. Send MULTI
            out.write("*1\r\n$5\r\nMULTI\r\n".getBytes());
            out.flush();
            int read = in.read(buffer);
            assertEquals("+OK\r\n", new String(buffer, 0, read));

            // 2. Queue command SET txkey txval
            out.write("*3\r\n$3\r\nSET\r\n$5\r\ntxkey\r\n$5\r\ntxval\r\n".getBytes());
            out.flush();
            read = in.read(buffer);
            assertEquals("+QUEUED\r\n", new String(buffer, 0, read));

            // 3. Queue command GET txkey
            out.write("*2\r\n$3\r\nGET\r\n$5\r\ntxkey\r\n".getBytes());
            out.flush();
            read = in.read(buffer);
            assertEquals("+QUEUED\r\n", new String(buffer, 0, read));

            // 4. Send EXEC
            out.write("*1\r\n$4\r\nEXEC\r\n".getBytes());
            out.flush();
            read = in.read(buffer);
            String response = new String(buffer, 0, read);
            assertTrue(response.startsWith("*2\r\n"));
            assertTrue(response.contains("+OK\r\n"));
            assertTrue(response.contains("$5\r\ntxval\r\n"));
        }
    }
}
