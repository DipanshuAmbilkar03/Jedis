package com.miniredis.command.server;

import com.miniredis.command.Command;
import com.miniredis.command.CommandHandler;
import com.miniredis.command.CommandRouter;
import com.miniredis.persistence.PersistenceManager;
import com.miniredis.protocol.RespEncoder;
import com.miniredis.server.ClientHandler;
import com.miniredis.store.DataStore;

/**
 * REPLICAOF command — makes the server a replica of another master server, or promotes it to master.
 * Syntax:
 * - REPLICAOF <host> <port>
 * - REPLICAOF NO ONE
 */
public class ReplicaOfCommand implements CommandHandler {

    private final CommandRouter router;
    private final DataStore dataStore;
    private final PersistenceManager persistenceManager;

    public ReplicaOfCommand(CommandRouter router, DataStore dataStore, PersistenceManager persistenceManager) {
        this.router = router;
        this.dataStore = dataStore;
        this.persistenceManager = persistenceManager;
    }

    @Override
    public String handle(Command command, ClientHandler client) {
        if (command.argCount() != 3) {
            return RespEncoder.encodeError("ERR wrong number of arguments for 'replicaof' command");
        }

        String host = command.getArg(1);
        String portStr = command.getArg(2);

        var replicationManager = router.getReplicationManager();
        if (replicationManager == null) {
            return RespEncoder.encodeError("ERR replication manager is not initialized");
        }

        if ("NO".equalsIgnoreCase(host) && "ONE".equalsIgnoreCase(portStr)) {
            replicationManager.setReplicaOf(null, -1, dataStore, router, persistenceManager);
            return RespEncoder.encodeSimpleString("OK");
        }

        try {
            int port = Integer.parseInt(portStr);
            replicationManager.setReplicaOf(host, port, dataStore, router, persistenceManager);
            return RespEncoder.encodeSimpleString("OK");
        } catch (NumberFormatException e) {
            return RespEncoder.encodeError("ERR value is not an integer or out of range");
        }
    }
}
