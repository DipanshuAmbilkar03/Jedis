package com.miniredis.command.transaction;

import com.miniredis.command.Command;
import com.miniredis.command.CommandHandler;
import com.miniredis.protocol.RespEncoder;
import com.miniredis.server.ClientHandler;

/**
 * MULTI
 * Marks the start of a transaction block.
 * Subsequent commands will be queued for atomic execution via EXEC.
 * Returns an error if the client is already in a transaction.
 */
public class MultiCommand implements CommandHandler {

    @Override
    public String handle(Command command, ClientHandler client) {
        if (client.isInTransaction()) {
            return RespEncoder.encodeError("ERR MULTI calls can not be nested");
        }

        client.startTransaction();
        return RespEncoder.OK;
    }
}
