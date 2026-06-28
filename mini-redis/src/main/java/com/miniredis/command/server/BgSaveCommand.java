package com.miniredis.command.server;

import com.miniredis.command.Command;
import com.miniredis.command.CommandHandler;
import com.miniredis.persistence.PersistenceManager;
import com.miniredis.protocol.RespEncoder;
import com.miniredis.server.ClientHandler;

/**
 * BGSAVE
 * Performs a background save of the dataset, producing an RDB snapshot.
 * Returns "+Background saving started\r\n".
 */
public class BgSaveCommand implements CommandHandler {

    private final PersistenceManager persistenceManager;

    public BgSaveCommand(PersistenceManager persistenceManager) {
        this.persistenceManager = persistenceManager;
    }

    @Override
    public String handle(Command command, ClientHandler client) {
        persistenceManager.bgSaveSnapshot();
        return RespEncoder.encodeSimpleString("Background saving started");
    }
}
