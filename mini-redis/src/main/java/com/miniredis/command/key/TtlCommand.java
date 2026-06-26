package com.miniredis.command.key;

import com.miniredis.command.Command;
import com.miniredis.command.CommandHandler;
import com.miniredis.protocol.RespEncoder;
import com.miniredis.server.ClientHandler;
import com.miniredis.store.DataStore;

/**
 * Handles the TTL command.
 * 
 * Usage: TTL key
 * Returns the remaining time to live of a key that has a timeout.
 * Returns -2 if the key does not exist, -1 if the key exists but has no
 * associated expire.
 */
public class TtlCommand implements CommandHandler {

    private final DataStore dataStore;

    public TtlCommand(DataStore dataStore) {
        this.dataStore = dataStore;
    }

    @Override
    public String handle(Command command, ClientHandler client) {
        if (command.argCount() != 2) {
            return RespEncoder.wrongArgCount("ttl");
        }

        String key = command.getArg(1);
        long ttl = dataStore.getExpiryManager().ttlSeconds(key);

        return RespEncoder.encodeInteger(ttl);
    }
}
