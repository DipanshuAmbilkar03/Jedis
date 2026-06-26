package com.miniredis.command.transaction;

import com.miniredis.command.Command;
import com.miniredis.command.CommandHandler;
import com.miniredis.protocol.RespEncoder;
import com.miniredis.server.ClientHandler;

/**
 * DISCARD
 * Flushes all previously queued commands in a transaction and restores
 * the connection state to normal.
 * Returns an error if the client is not in a transaction.
 */
public class DiscardCommand implements CommandHandler {

    @Override
    public String handle(Command command, ClientHandler client) {
        if (!client.isInTransaction()) {
            return RespEncoder.encodeError("ERR DISCARD without MULTI");
        }

        client.endTransaction();
        return RespEncoder.OK;
    }
}
