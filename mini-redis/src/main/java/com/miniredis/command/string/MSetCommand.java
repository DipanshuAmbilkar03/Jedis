package com.miniredis.command.string;

import com.miniredis.command.Command;
import com.miniredis.command.CommandHandler;
import com.miniredis.protocol.RespEncoder;
import com.miniredis.server.ClientHandler;
import com.miniredis.store.DataStore;
import com.miniredis.store.RedisObject;

/**
 * Handles the MSET command.
 * 
 * Usage: MSET key1 value1 [key2 value2 ...]
 * Sets the given keys to their respective values.
 * Always returns OK. Requires an even number of arguments (key-value pairs).
 */
public class MSetCommand implements CommandHandler {

    private final DataStore dataStore;

    public MSetCommand(DataStore dataStore) {
        this.dataStore = dataStore;
    }

    @Override
    public String handle(Command command, ClientHandler client) {
        // Must have at least one key-value pair, and pairs must be even
        // argCount includes command name, so (argCount - 1) must be even and >= 2
        int pairArgs = command.argCount() - 1;
        if (pairArgs < 2 || pairArgs % 2 != 0) {
            return RespEncoder.wrongArgCount("mset");
        }

        for (int i = 1; i < command.argCount(); i += 2) {
            String key = command.getArg(i);
            String value = command.getArg(i + 1);
            dataStore.set(key, RedisObject.string(value));
            // MSET clears any existing expiry on overwritten keys
            dataStore.getExpiryManager().removeExpiry(key);
        }

        return RespEncoder.OK;
    }
}
