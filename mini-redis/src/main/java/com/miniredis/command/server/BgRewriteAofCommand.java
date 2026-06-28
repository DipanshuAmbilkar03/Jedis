package com.miniredis.command.server;

import com.miniredis.command.Command;
import com.miniredis.command.CommandHandler;
import com.miniredis.persistence.PersistenceManager;
import com.miniredis.protocol.RespEncoder;
import com.miniredis.server.ClientHandler;

/**
 * BGREWRITEAOF
 * Triggers a background rewrite of the Append-Only File.
 * Returns "+Background append only file rewriting started\r\n".
 */
public class BgRewriteAofCommand implements CommandHandler {

    private final PersistenceManager persistenceManager;

    public BgRewriteAofCommand(PersistenceManager persistenceManager) {
        this.persistenceManager = persistenceManager;
    }

    @Override
    public String handle(Command command, ClientHandler client) {
        persistenceManager.bgRewriteAof();
        return RespEncoder.encodeSimpleString("Background append only file rewriting started");
    }
}
