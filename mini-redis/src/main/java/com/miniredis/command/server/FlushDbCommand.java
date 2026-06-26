package com.miniredis.command.server;

import com.miniredis.command.Command;
import com.miniredis.command.CommandHandler;
import com.miniredis.protocol.RespEncoder;
import com.miniredis.server.ClientHandler;
import com.miniredis.store.DataStore;

/**
 * FLUSHDB
 * Deletes all keys from the current database.
 * Returns OK.
 */
public class FlushDbCommand implements CommandHandler {

    private final DataStore dataStore;

    public FlushDbCommand(DataStore dataStore) {
        this.dataStore = dataStore;
    }

    @Override
    public String handle(Command command, ClientHandler client) {
        dataStore.flushAll();
        return RespEncoder.OK;
    }
}
