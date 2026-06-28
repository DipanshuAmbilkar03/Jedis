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

public class AclRedisIntegrationTest {

    private static final int TEST_PORT = 6393;
    private RedisServer server;
    private ExecutorService serverExecutor;
    private ServerConfig config;

    @BeforeEach
    public void setUp() throws Exception {
        Path tempDir = Path.of("target/temp-test-data-acl");
        Files.createDirectories(tempDir);

        config = new ServerConfig();
        config.setPort(TEST_PORT);
        config.setDataDir(tempDir.toString());
        config.setAofEnabled(false);
        config.setRdbEnabled(false);

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
    public void testAclAuthorizationCommands() throws Exception {
        try (Socket socket = new Socket("localhost", TEST_PORT)) {
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();

            // Set up a user: ACL SETUSER alice on >pwd123 +GET +ACL
            out.write("*7\r\n$3\r\nACL\r\n$7\r\nSETUSER\r\n$5\r\nalice\r\n$2\r\non\r\n$7\r\n>pwd123\r\n$4\r\n+GET\r\n$4\r\n+ACL\r\n".getBytes());
            out.flush();

            byte[] buffer = new byte[1024];
            int read = in.read(buffer);
            assertTrue(read > 0);
            String response = new String(buffer, 0, read);
            assertEquals("+OK\r\n", response);

            // AUTH alice pwd123
            out.write("*3\r\n$4\r\nAUTH\r\n$5\r\nalice\r\n$6\r\npwd123\r\n".getBytes());
            out.flush();

            read = in.read(buffer);
            assertTrue(read > 0);
            response = new String(buffer, 0, read);
            assertEquals("+OK\r\n", response);

            // WHOAMI check
            out.write("*2\r\n$3\r\nACL\r\n$6\r\nWHOAMI\r\n".getBytes());
            out.flush();

            read = in.read(buffer);
            assertTrue(read > 0);
            response = new String(buffer, 0, read);
            assertEquals("+alice\r\n", response);

            // Try GET (which is allowed)
            out.write("*2\r\n$3\r\nGET\r\n$3\r\nkey\r\n".getBytes());
            out.flush();

            read = in.read(buffer);
            assertTrue(read > 0);
            response = new String(buffer, 0, read);
            assertEquals("$-1\r\n", response); // Null bulk string response

            // Try SET (which is denied/not allowed)
            out.write("*3\r\n$3\r\nSET\r\n$3\r\nkey\r\n$5\r\nvalue\r\n".getBytes());
            out.flush();

            read = in.read(buffer);
            assertTrue(read > 0);
            response = new String(buffer, 0, read);
            assertTrue(response.startsWith("-NOPERM"));
        }
    }
}
