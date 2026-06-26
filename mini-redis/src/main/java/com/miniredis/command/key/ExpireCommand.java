package com.miniredis.command.key;

import com.miniredis.command.Command;
import com.miniredis.command.CommandHandler;
import com.miniredis.protocol.RespEncoder;
import com.miniredis.server.ClientHandler;
import com.miniredis.store.DataStore;

/**
 * Handles the EXPIRE command.
 * 
 * Usage: EXPIRE key seconds
 * Set a timeout on key. After the timeout has expired, the key will
 * automatically be deleted. Returns 1 if the timeout was set, or 0
 * if the key does not exist.
 */
public class ExpireCommand implements CommandHandler {

    private final DataStore dataStore;

    public ExpireCommand(DataStore dataStore) {
        this.dataStore = dataStore;
    }

    @Override
    public String handle(Command command, ClientHandler client) {
        if (command.argCount() != 3) {
            return RespEncoder.wrongArgCount("expire");
        }

        String key = command.getArg(1);
        long seconds;

        try {
            seconds = Long.parseLong(command.getArg(2));
        } catch (NumberFormatException e) {
            return RespEncoder.notAnInteger();
        }

        if (!dataStore.exists(key)) {
            return RespEncoder.ZERO;
        }

        dataStore.getExpiryManager().setExpirySeconds(key, seconds);
        return RespEncoder.ONE;
    }
}
