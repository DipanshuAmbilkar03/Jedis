package com.miniredis.command.key;

import com.miniredis.command.Command;
import com.miniredis.command.CommandHandler;
import com.miniredis.protocol.RespEncoder;
import com.miniredis.server.ClientHandler;
import com.miniredis.store.DataStore;

/**
 * Handles the DEL command.
 * 
 * Usage: DEL key [key ...]
 * Removes the specified keys. A key is ignored if it does not exist.
 * Returns the number of keys that were removed.
 */
public class DelCommand implements CommandHandler {

    private final DataStore dataStore;

    public DelCommand(DataStore dataStore) {
        this.dataStore = dataStore;
    }

    @Override
    public String handle(Command command, ClientHandler client) {
        if (command.argCount() < 2) {
            return RespEncoder.wrongArgCount("del");
        }

        String[] keys = new String[command.argCount() - 1];
        for (int i = 1; i < command.argCount(); i++) {
            keys[i - 1] = command.getArg(i);
        }

        int deleted = dataStore.delete(keys);
        return RespEncoder.encodeInteger(deleted);
    }
}
