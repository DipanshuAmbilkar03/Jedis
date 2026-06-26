package com.miniredis.command.server;

import com.miniredis.command.Command;
import com.miniredis.command.CommandHandler;
import com.miniredis.persistence.PersistenceManager;
import com.miniredis.protocol.RespEncoder;
import com.miniredis.server.ClientHandler;

/**
 * SAVE
 * Performs a synchronous save of the dataset, producing an RDB snapshot.
 * Returns OK on success.
 */
public class SaveCommand implements CommandHandler {

    private final PersistenceManager persistenceManager;

    public SaveCommand(PersistenceManager persistenceManager) {
        this.persistenceManager = persistenceManager;
    }

    @Override
    public String handle(Command command, ClientHandler client) {
        persistenceManager.saveSnapshot();
        return RespEncoder.OK;
    }
}
