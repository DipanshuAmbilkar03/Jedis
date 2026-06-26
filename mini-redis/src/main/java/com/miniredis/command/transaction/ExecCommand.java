package com.miniredis.command.transaction;

import com.miniredis.command.Command;
import com.miniredis.command.CommandHandler;
import com.miniredis.command.CommandRouter;
import com.miniredis.protocol.RespEncoder;
import com.miniredis.server.ClientHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * EXEC
 * Executes all commands queued after MULTI.
 * Returns an array of results, one per queued command.
 * Returns an error if the client is not in a transaction.
 * Returns EXECABORT if the transaction was aborted due to errors during queueing.
 */
public class ExecCommand implements CommandHandler {

    private final CommandRouter commandRouter;

    public ExecCommand(CommandRouter commandRouter) {
        this.commandRouter = commandRouter;
    }

    @Override
    public String handle(Command command, ClientHandler client) {
        if (!client.isInTransaction()) {
            return RespEncoder.encodeError("ERR EXEC without MULTI");
        }

        // Check if the transaction was aborted (e.g., due to unknown commands while queueing)
        if (client.isTransactionAborted()) {
            client.endTransaction();
            return RespEncoder.encodeError("EXECABORT Transaction discarded because of previous errors");
        }

        // Execute all queued commands and collect results
        List<Command> queue = client.getTransactionQueue();
        List<String> results = new ArrayList<>();

        // End transaction before executing so commands don't get re-queued
        client.endTransaction();

        for (Command queuedCmd : queue) {
            String result = commandRouter.execute(queuedCmd, client);
            results.add(result);
        }

        return RespEncoder.encodeArray(results);
    }
}
