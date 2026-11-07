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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;

public class DashboardIntegrationTest {

    private static final String TOKEN = "test-dashboard-token";
    private DashboardServer dashboardServer;
    private DataStore dataStore;
    private HttpClient client;
    private String baseUrl;

    @BeforeEach
    public void setUp() throws Exception {
        ServerConfig config = new ServerConfig();
        config.setPort(6380);
        config.setDataDir("target/temp-dashboard-data");
        config.setAofEnabled(false);
        config.setRdbEnabled(false);

        dataStore = new DataStore();
        PubSubManager pubSubManager = new PubSubManager();
        PersistenceManager persistenceManager = new PersistenceManager(config, dataStore);
        CommandRouter commandRouter = new CommandRouter(dataStore, pubSubManager, persistenceManager, null, true);
        RedisServer redisServer = new RedisServer(config, commandRouter, pubSubManager, persistenceManager);

        dashboardServer = new DashboardServer(
                0, dataStore, commandRouter, config,
                redisServer.getConnectionManager(), pubSubManager, persistenceManager,
                TOKEN, "127.0.0.1"
        );
        dashboardServer.start();
        baseUrl = "http://127.0.0.1:" + dashboardServer.getPort();

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

    private HttpRequest.Builder api(String path) {
        return HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .header("Authorization", "Bearer " + TOKEN);
    }

    @Test
    public void testGetHtml() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/"))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("<!doctype html>") || response.body().contains("<!DOCTYPE html>"));
        assertTrue(response.body().contains("Jedis") || response.body().contains("JEDIS"));
    }

    @Test
    public void testApiRequiresToken() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/info"))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(401, response.statusCode());
        assertTrue(response.body().contains("Unauthorized"));
    }

    @Test
    public void testApiRejectsWrongToken() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/info"))
                .header("Authorization", "Bearer wrong-token")
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(401, response.statusCode());
    }

    @Test
    public void testGetInfo() throws Exception {
        HttpRequest request = api("/api/info").GET().build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"keys\""));
        assertTrue(response.body().contains("\"memoryUsedBytes\""));
    }

    @Test
    public void testAddAndGetAndDeleteKey() throws Exception {
        String jsonPayload = "{\"key\":\"testkey\",\"value\":\"testvalue\",\"ttl\":100}";
        HttpRequest postRequest = api("/api/key")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

        HttpResponse<String> postResponse = client.send(postRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, postResponse.statusCode());
        assertTrue(postResponse.body().contains("\"status\""));
        assertTrue(postResponse.body().contains("\"OK\""));

        assertTrue(dataStore.exists("testkey"));
        assertEquals("testvalue", dataStore.get("testkey").getStringValue());

        HttpRequest getKeysRequest = api("/api/keys").GET().build();

        HttpResponse<String> getKeysResponse = client.send(getKeysRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, getKeysResponse.statusCode());
        assertTrue(getKeysResponse.body().contains("\"testkey\""));

        HttpRequest deleteRequest = api("/api/key?key=testkey").DELETE().build();

        HttpResponse<String> deleteResponse = client.send(deleteRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, deleteResponse.statusCode());
        assertTrue(deleteResponse.body().contains("\"status\""));
        assertTrue(deleteResponse.body().contains("\"deleted\""));

        assertFalse(dataStore.exists("testkey"));
    }

    @Test
    public void testExecuteCommand() throws Exception {
        String jsonPayload = "{\"command\":\"PING\"}";
        HttpRequest request = api("/api/command")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"response\""));
        assertTrue(response.body().contains("PONG"));
    }

    @Test
    public void testOversizedBodyIsRejected() throws Exception {
        String jsonPayload = "{\"command\":\"" + "A".repeat(1024 * 1024 + 1) + "\"}";
        HttpRequest request = api("/api/command")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(413, response.statusCode());
    }
}
