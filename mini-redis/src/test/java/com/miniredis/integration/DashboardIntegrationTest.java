package com.miniredis.integration;

import com.miniredis.command.CommandRouter;
import com.miniredis.config.ServerConfig;
import com.miniredis.dashboard.DashboardServer;
import com.miniredis.persistence.PersistenceManager;
import com.miniredis.pubsub.PubSubManager;
import com.miniredis.server.RedisServer;
import com.miniredis.store.DataStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;

public class DashboardIntegrationTest {

    private static final int TEST_PORT = 18380;
    private DashboardServer dashboardServer;
    private DataStore dataStore;
    private ServerConfig config;
    private HttpClient client;

    @BeforeEach
    public void setUp() throws Exception {
        config = new ServerConfig();
        config.setPort(6380);
        config.setDataDir("target/temp-dashboard-data");
        config.setAofEnabled(false);
        config.setRdbEnabled(false);

        dataStore = new DataStore();
        PubSubManager pubSubManager = new PubSubManager();
        PersistenceManager persistenceManager = new PersistenceManager(config, dataStore);
        CommandRouter commandRouter = new CommandRouter(dataStore, pubSubManager, persistenceManager);
        RedisServer redisServer = new RedisServer(config, commandRouter, pubSubManager, persistenceManager);

        dashboardServer = new DashboardServer(
                TEST_PORT, dataStore, commandRouter, config,
                redisServer.getConnectionManager(), pubSubManager, persistenceManager
        );
        dashboardServer.start();

        client = HttpClient.newHttpClient();
        Thread.sleep(200);
    }

    @AfterEach
    public void tearDown() throws Exception {
        if (dashboardServer != null) {
            dashboardServer.stop();
        }
        Thread.sleep(100);
    }

    @Test
    public void testGetHtml() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + TEST_PORT + "/"))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("<!doctype html>") || response.body().contains("<!DOCTYPE html>"));
        assertTrue(response.body().contains("Jedis") || response.body().contains("JEDIS"));
    }

    @Test
    public void testGetInfo() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + TEST_PORT + "/api/info"))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"keys\""));
        assertTrue(response.body().contains("\"memoryUsedBytes\""));
    }

    @Test
    public void testAddAndGetAndDeleteKey() throws Exception {
        // Set key using POST /api/key
        String jsonPayload = "{\"key\":\"testkey\",\"value\":\"testvalue\",\"ttl\":100}";
        HttpRequest postRequest = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + TEST_PORT + "/api/key"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

        HttpResponse<String> postResponse = client.send(postRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, postResponse.statusCode());
        assertTrue(postResponse.body().contains("\"status\""));
        assertTrue(postResponse.body().contains("\"OK\""));

        // Verify key exists in DataStore
        assertTrue(dataStore.exists("testkey"));
        assertEquals("testvalue", dataStore.get("testkey").getStringValue());

        // Get keys via /api/keys
        HttpRequest getKeysRequest = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + TEST_PORT + "/api/keys"))
                .GET()
                .build();

        HttpResponse<String> getKeysResponse = client.send(getKeysRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, getKeysResponse.statusCode());
        assertTrue(getKeysResponse.body().contains("\"testkey\""));

        // Delete key using DELETE /api/key?key=testkey
        HttpRequest deleteRequest = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + TEST_PORT + "/api/key?key=testkey"))
                .DELETE()
                .build();

        HttpResponse<String> deleteResponse = client.send(deleteRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, deleteResponse.statusCode());
        assertTrue(deleteResponse.body().contains("\"status\""));
        assertTrue(deleteResponse.body().contains("\"deleted\""));

        // Verify key is deleted
        assertFalse(dataStore.exists("testkey"));
    }

    @Test
    public void testExecuteCommand() throws Exception {
        String jsonPayload = "{\"command\":\"PING\"}";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + TEST_PORT + "/api/command"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"response\""));
        assertTrue(response.body().contains("PONG"));
    }
}
