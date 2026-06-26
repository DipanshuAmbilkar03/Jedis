package com.miniredis.command.key;

import com.miniredis.command.Command;
import com.miniredis.command.CommandHandler;
import com.miniredis.protocol.RespEncoder;
import com.miniredis.server.ClientHandler;
import com.miniredis.store.DataStore;

/**
 * Handles the EXISTS command.
 * 
 * Usage: EXISTS key [key ...]
 * Returns the number of keys that exist among the specified arguments.
 * If the same existing key is mentioned multiple times, it will be
 * counted multiple times (matching Redis behavior).
 */
public class ExistsCommand implements CommandHandler {

    private final DataStore dataStore;

    public ExistsCommand(DataStore dataStore) {
        this.dataStore = dataStore;
    }

    @Override
    public String handle(Command command, ClientHandler client) {
        if (command.argCount() < 2) {
            return RespEncoder.wrongArgCount("exists");
        }

        int count = 0;
        for (int i = 1; i < command.argCount(); i++) {
            if (dataStore.exists(command.getArg(i))) {
                count++;
            }
        }

        return RespEncoder.encodeInteger(count);
    }
}
