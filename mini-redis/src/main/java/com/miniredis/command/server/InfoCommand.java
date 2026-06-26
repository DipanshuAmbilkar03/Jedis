package com.miniredis.command.server;

import com.miniredis.command.Command;
import com.miniredis.command.CommandHandler;
import com.miniredis.protocol.RespEncoder;
import com.miniredis.server.ClientHandler;
import com.miniredis.store.DataStore;

/**
 * INFO [section]
 * Returns server information and statistics as a bulk string.
 * Includes version, uptime, and key count.
 */
public class InfoCommand implements CommandHandler {

    private static final String VERSION = "mini-redis 1.0.0";
    private static final long START_TIME = System.currentTimeMillis();

    private final DataStore dataStore;

    public InfoCommand(DataStore dataStore) {
        this.dataStore = dataStore;
    }

    @Override
    public String handle(Command command, ClientHandler client) {
        long uptimeSeconds = (System.currentTimeMillis() - START_TIME) / 1000;

        StringBuilder info = new StringBuilder();
        info.append("# Server\r\n");
        info.append("redis_version:").append(VERSION).append("\r\n");
        info.append("uptime_in_seconds:").append(uptimeSeconds).append("\r\n");
        info.append("uptime_in_days:").append(uptimeSeconds / 86400).append("\r\n");
        info.append("\r\n");
        info.append("# Keyspace\r\n");
        info.append("db0:keys=").append(dataStore.size()).append("\r\n");

        return RespEncoder.encodeBulkString(info.toString());
    }
}
