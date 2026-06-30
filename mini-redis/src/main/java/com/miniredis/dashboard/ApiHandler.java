package com.miniredis.dashboard;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.miniredis.store.DataStore;
import com.miniredis.command.CommandRouter;
import com.miniredis.config.ServerConfig;
import com.miniredis.server.ConnectionManager;
import com.miniredis.pubsub.PubSubManager;
import com.miniredis.persistence.PersistenceManager;

import java.io.*;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ApiHandler implements HttpHandler {

    private final DataStore dataStore;
    private final CommandRouter commandRouter;
    private final ServerConfig config;
    private final ConnectionManager connectionManager;
    private final PubSubManager pubSubManager;
    private final PersistenceManager persistenceManager;

    public ApiHandler(DataStore dataStore, CommandRouter commandRouter, ServerConfig config,
                      ConnectionManager connectionManager, PubSubManager pubSubManager,
                      PersistenceManager persistenceManager) {
        this.dataStore = dataStore;
        this.commandRouter = commandRouter;
        this.config = config;
        this.connectionManager = connectionManager;
        this.pubSubManager = pubSubManager;
        this.persistenceManager = persistenceManager;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();

        // Handle CORS preflight options
        if ("OPTIONS".equalsIgnoreCase(method)) {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, DELETE, OPTIONS");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
            exchange.sendResponseHeaders(204, -1);
            return;
        }

        try {
            if ("/".equals(path) && "GET".equalsIgnoreCase(method)) {
                handleHtml(exchange);
            } else if ("/api/info".equals(path) && "GET".equalsIgnoreCase(method)) {
                handleInfo(exchange);
            } else if ("/api/keys".equals(path) && "GET".equalsIgnoreCase(method)) {
                handleKeys(exchange);
            } else if ("/api/command".equals(path) && "POST".equalsIgnoreCase(method)) {
                handleCommand(exchange);
            } else if ("/api/key".equals(path) && "POST".equalsIgnoreCase(method)) {
                handleKeyWrite(exchange);
            } else if ("/api/key".equals(path) && "DELETE".equalsIgnoreCase(method)) {
                handleKeyDelete(exchange);
            } else {
                sendJsonResponse(exchange, 404, "{\"error\": \"Not Found\"}");
            }
        } catch (Exception e) {
            String errorJson = "{\"error\": \"" + escapeJson(e.getMessage()) + "\"}";
            sendJsonResponse(exchange, 500, errorJson);
        }
    }

    private void handleHtml(HttpExchange exchange) throws IOException {
        String html = DashboardHtml.getHtml();
        byte[] bytes = html.getBytes("UTF-8");
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private void handleInfo(HttpExchange exchange) throws IOException {
        int keys = dataStore.size();
        long memoryUsed = dataStore.getMemoryManager().getUsedMemory();
        long memoryMax = dataStore.getMemoryManager().getMaxMemoryBytes();
        int clients = connectionManager.getActiveConnectionsCount();
        long uptime = (System.currentTimeMillis() - com.miniredis.MiniRedisApplication.getStartTime()) / 1000L;
        String evictionPolicy = config.getEvictionPolicy();
        boolean aofEnabled = config.isAofEnabled();
        boolean rdbEnabled = config.isRdbEnabled();
        int port = config.getPort();
        boolean clusterEnabled = config.isClusterEnabled();
        String replicaOf = config.getReplicaOfHost() != null ? (config.getReplicaOfHost() + ":" + config.getReplicaOfPort()) : null;

        String json = String.format(
            "{\n" +
            "  \"keys\": %d,\n" +
            "  \"memoryUsedBytes\": %d,\n" +
            "  \"memoryMaxBytes\": %d,\n" +
            "  \"connectedClients\": %d,\n" +
            "  \"uptimeSeconds\": %d,\n" +
            "  \"evictionPolicy\": \"%s\",\n" +
            "  \"aofEnabled\": %b,\n" +
            "  \"rdbEnabled\": %b,\n" +
            "  \"port\": %d,\n" +
            "  \"clusterEnabled\": %b,\n" +
            "  \"replicaOf\": %s\n" +
            "}",
            keys, memoryUsed, memoryMax, clients, uptime, evictionPolicy,
            aofEnabled, rdbEnabled, port, clusterEnabled,
            replicaOf == null ? "null" : "\"" + replicaOf + "\""
        );
        sendJsonResponse(exchange, 200, json);
    }

    private void handleKeys(HttpExchange exchange) throws IOException {
        Set<String> allKeys = dataStore.getRawStore().keySet();
        List<String> keyJsonList = new ArrayList<>();
        for (String k : allKeys) {
            if (dataStore.getExpiryManager().isExpired(k)) {
                dataStore.delete(k); // lazy expiry cleanup
                continue;
            }
            com.miniredis.store.DataType type = dataStore.type(k);
            String typeStr = type != null ? type.name() : "STRING";
            long ttl = dataStore.getExpiryManager().ttlSeconds(k);
            
            String valPreview = "";
            try {
                com.miniredis.store.RedisObject obj = dataStore.get(k);
                if (obj != null) {
                    switch (obj.getType()) {
                        case STRING -> valPreview = obj.getStringValue();
                        case LIST -> valPreview = "[" + obj.getListValue().size() + " elements]";
                        case SET -> valPreview = "{" + obj.getSetValue().size() + " members}";
                        case HASH -> valPreview = "{" + obj.getHashValue().size() + " fields}";
                    }
                }
            } catch (Exception e) {
                valPreview = "[Error reading value]";
            }
            
            String escapedKey = escapeJson(k);
            String escapedVal = escapeJson(valPreview);
            
            keyJsonList.add(String.format(
                "{\"key\":\"%s\",\"type\":\"%s\",\"value\":\"%s\",\"ttl\":%d}",
                escapedKey, typeStr, escapedVal, ttl
            ));
        }
        String json = "{\"keys\":[" + String.join(",", keyJsonList) + "]}";
        sendJsonResponse(exchange, 200, json);
    }

    private void handleCommand(HttpExchange exchange) throws IOException {
        String body = readBody(exchange);
        String commandText = getJsonKeyValue(body, "command");
        if (commandText == null || commandText.trim().isEmpty()) {
            sendJsonResponse(exchange, 400, "{\"error\": \"Missing command parameter\"}");
            return;
        }

        List<String> rawArgs = parseCommandText(commandText);
        if (rawArgs.isEmpty()) {
            sendJsonResponse(exchange, 400, "{\"error\": \"Empty command\"}");
            return;
        }

        com.miniredis.command.Command command = com.miniredis.command.Command.from(rawArgs);
        
        // Execute through a mock client handler
        com.miniredis.server.ClientHandler mockClient = new com.miniredis.server.ClientHandler(
            (java.net.Socket) null, commandRouter, pubSubManager, persistenceManager, commandRouter.getReplicationManager()
        );

        try {
            String resp = mockClient.processCommand(command);
            String humanResp = decodeRespToHuman(resp);
            String escapedResp = escapeJson(humanResp);
            sendJsonResponse(exchange, 200, "{\"response\": \"" + escapedResp + "\"}");
        } catch (Exception e) {
            String escapedErr = escapeJson(e.getMessage());
            sendJsonResponse(exchange, 200, "{\"error\": \"(error) " + escapedErr + "\"}");
        }
    }

    private void handleKeyWrite(HttpExchange exchange) throws IOException {
        String body = readBody(exchange);
        String key = getJsonKeyValue(body, "key");
        String value = getJsonKeyValue(body, "value");
        String ttlStr = getJsonKeyValue(body, "ttl");
        
        if (key == null || value == null) {
            sendJsonResponse(exchange, 400, "{\"error\": \"Missing key or value parameter\"}");
            return;
        }

        List<String> rawArgs = new ArrayList<>();
        rawArgs.add("SET");
        rawArgs.add(key);
        rawArgs.add(value);
        
        int ttl = -1;
        if (ttlStr != null) {
            try {
                ttl = Integer.parseInt(ttlStr);
            } catch (NumberFormatException e) {
                // Ignore
            }
        }
        
        if (ttl > 0) {
            rawArgs.add("EX");
            rawArgs.add(String.valueOf(ttl));
        }

        com.miniredis.command.Command command = com.miniredis.command.Command.from(rawArgs);
        com.miniredis.server.ClientHandler mockClient = new com.miniredis.server.ClientHandler(
            (java.net.Socket) null, commandRouter, pubSubManager, persistenceManager, commandRouter.getReplicationManager()
        );

        try {
            String resp = mockClient.processCommand(command);
            if (resp.startsWith("-")) { // Error response from SET
                String errorMsg = decodeRespToHuman(resp);
                sendJsonResponse(exchange, 400, "{\"error\": \"" + escapeJson(errorMsg) + "\"}");
            } else {
                sendJsonResponse(exchange, 200, "{\"status\": \"OK\"}");
            }
        } catch (Exception e) {
            sendJsonResponse(exchange, 500, "{\"error\": \"" + escapeJson(e.getMessage()) + "\"}");
        }
    }

    private void handleKeyDelete(HttpExchange exchange) throws IOException {
        String query = exchange.getRequestURI().getQuery();
        String key = null;
        if (query != null) {
            String[] pairs = query.split("&");
            for (String pair : pairs) {
                int idx = pair.indexOf("=");
                if (idx > 0 && URLDecoder.decode(pair.substring(0, idx), "UTF-8").equals("key")) {
                    key = URLDecoder.decode(pair.substring(idx + 1), "UTF-8");
                    break;
                }
            }
        }

        if (key == null) {
            sendJsonResponse(exchange, 400, "{\"error\": \"Missing key query parameter\"}");
            return;
        }

        List<String> rawArgs = new ArrayList<>();
        rawArgs.add("DEL");
        rawArgs.add(key);

        com.miniredis.command.Command command = com.miniredis.command.Command.from(rawArgs);
        com.miniredis.server.ClientHandler mockClient = new com.miniredis.server.ClientHandler(
            (java.net.Socket) null, commandRouter, pubSubManager, persistenceManager, commandRouter.getReplicationManager()
        );

        try {
            String resp = mockClient.processCommand(command);
            int deleted = 0;
            if (resp.startsWith(":")) { // e.g. :1\r\n
                try {
                    deleted = Integer.parseInt(resp.substring(1, resp.length() - 2));
                } catch (Exception e) {
                    // Ignore
                }
            }
            sendJsonResponse(exchange, 200, "{\"status\": \"OK\", \"deleted\": " + deleted + "}");
        } catch (Exception e) {
            sendJsonResponse(exchange, 500, "{\"error\": \"" + escapeJson(e.getMessage()) + "\"}");
        }
    }

    // ── Helper Methods ──

    private void sendJsonResponse(HttpExchange exchange, int status, String json) throws IOException {
        byte[] bytes = json.getBytes("UTF-8");
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, DELETE, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private String readBody(HttpExchange exchange) throws IOException {
        try (InputStream is = exchange.getRequestBody();
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[1024];
            int len;
            while ((len = is.read(buffer)) != -1) {
                bos.write(buffer, 0, len);
            }
            return bos.toString("UTF-8");
        }
    }

    private static String getJsonKeyValue(String json, String key) {
        String pattern = "\"" + key + "\"[\\s]*:[\\s]*\"([^\"]*)\"";
        Pattern r = Pattern.compile(pattern);
        Matcher m = r.matcher(json);
        if (m.find()) {
            return m.group(1);
        }
        String numberPattern = "\"" + key + "\"[\\s]*:[\\s]*([0-9-]+)";
        r = Pattern.compile(numberPattern);
        m = r.matcher(json);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            switch (ch) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (ch < ' ') {
                        String ss = "00" + Integer.toHexString(ch);
                        sb.append("\\u").append(ss.substring(ss.length() - 4));
                    } else {
                        sb.append(ch);
                    }
                }
            }
        }
        return sb.toString();
    }

    public static List<String> parseCommandText(String commandText) {
        List<String> list = new ArrayList<>();
        if (commandText == null || commandText.trim().isEmpty()) {
            return list;
        }
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < commandText.length(); i++) {
            char c = commandText.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ' ' && !inQuotes) {
                if (current.length() > 0) {
                    list.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0) {
            list.add(current.toString());
        }
        return list;
    }

    public static String decodeRespToHuman(String resp) {
        if (resp == null || resp.isEmpty()) {
            return "";
        }
        return decodeRespHelper(new StringReader(resp), 0);
    }

    private static String decodeRespHelper(StringReader reader, int indent) {
        try {
            int firstChar = reader.read();
            if (firstChar == -1) return "";
            char type = (char) firstChar;
            
            switch (type) {
                case '+' -> { // Simple string
                    return readLine(reader);
                }
                case '-' -> { // Error
                    return "(error) " + readLine(reader);
                }
                case ':' -> { // Integer
                    return "(integer) " + readLine(reader);
                }
                case '$' -> { // Bulk string
                    String lenStr = readLine(reader);
                    int len = Integer.parseInt(lenStr);
                    if (len == -1) {
                        return "(nil)";
                    }
                    char[] buf = new char[len];
                    int read = 0;
                    while (read < len) {
                        int r = reader.read(buf, read, len - read);
                        if (r == -1) break;
                        read += r;
                    }
                    reader.read(); // \r
                    reader.read(); // \n
                    return "\"" + new String(buf) + "\"";
                }
                case '*' -> { // Array
                    String countStr = readLine(reader);
                    int count = Integer.parseInt(countStr);
                    if (count == -1) {
                        return "(nil)";
                    }
                    if (count == 0) {
                        return "(empty array)";
                    }
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < count; i++) {
                        if (i > 0) {
                            sb.append("\n");
                        }
                        String item = decodeRespHelper(reader, indent + 2);
                        sb.append("  ".repeat(indent)).append((i + 1)).append(") ").append(item);
                    }
                    return sb.toString();
                }
                default -> {
                    return type + readLine(reader);
                }
            }
        } catch (IOException e) {
            return "Error parsing response: " + e.getMessage();
        }
    }

    private static String readLine(StringReader reader) throws IOException {
        StringBuilder sb = new StringBuilder();
        int c;
        while ((c = reader.read()) != -1) {
            if (c == '\r') {
                reader.read(); // Skip \n
                break;
            }
            sb.append((char) c);
        }
        return sb.toString();
    }
}
