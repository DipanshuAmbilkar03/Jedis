package com.miniredis.command.server;

import com.miniredis.command.Command;
import com.miniredis.command.CommandHandler;
import com.miniredis.command.CommandRouter;
import com.miniredis.persistence.PersistenceManager;
import com.miniredis.protocol.RespEncoder;
import com.miniredis.server.ClientHandler;

/**
 * PSYNC command — initiates replication handshake between a master and a replica.
 * Syntax: PSYNC <replid> <offset>
 */
public class PsyncCommand implements CommandHandler {

    private final CommandRouter router;
    private final PersistenceManager persistenceManager;

    public PsyncCommand(CommandRouter router, PersistenceManager persistenceManager) {
        this.router = router;
        this.persistenceManager = persistenceManager;
    }

    @Override
    public String handle(Command command, ClientHandler client) {
        if (command.argCount() != 3) {
            return RespEncoder.encodeError("ERR wrong number of arguments for 'psync' command");
        }

        if (client == null) {
            return RespEncoder.encodeError("ERR no client context available");
        }

        String requestedReplId = command.getArg(1);
        String offsetStr = command.getArg(2);

        var replicationManager = router.getReplicationManager();
        if (replicationManager == null) {
            return RespEncoder.encodeError("ERR replication manager is not initialized");
        }

        try {
            long requestedOffset = Long.parseLong(offsetStr);
            replicationManager.handlePsync(client, requestedReplId, requestedOffset, persistenceManager);
            return null; // Output is streamed asynchronously by handlePsync
        } catch (NumberFormatException e) {
            return RespEncoder.encodeError("ERR value is not an integer or out of range");
        }
    }
}
