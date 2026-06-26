package com.miniredis.command;

import com.miniredis.command.hash.*;
import com.miniredis.command.key.*;
import com.miniredis.command.list.*;
import com.miniredis.command.pubsub.*;
import com.miniredis.command.server.*;
import com.miniredis.command.set.*;
import com.miniredis.command.string.*;
import com.miniredis.command.transaction.*;
import com.miniredis.persistence.PersistenceManager;
import com.miniredis.protocol.RespEncoder;
import com.miniredis.pubsub.PubSubManager;
import com.miniredis.store.DataStore;

import java.util.HashMap;
import java.util.Map;

/**
 * Routes incoming commands to their appropriate handlers.
 * Registers all supported commands on initialization.
 */
public class CommandRouter {

    private final Map<String, CommandHandler> handlers;
    private final DataStore dataStore;

    public CommandRouter(DataStore dataStore, PubSubManager pubSubManager, PersistenceManager persistenceManager) {
        this.handlers = new HashMap<>();
        this.dataStore = dataStore;
        registerAllCommands(dataStore, pubSubManager, persistenceManager);
    }

    /**
     * Route a command to its handler and execute it.
     * Returns a RESP2-encoded error if the command is unknown.
     */
    public String execute(Command command, com.miniredis.server.ClientHandler client) {
        CommandHandler handler = handlers.get(command.name());
        if (handler == null) {
            return RespEncoder.encodeError("ERR unknown command '" + command.name().toLowerCase() + "'");
        }
        return handler.handle(command, client);
    }

    /**
     * Check if a command name is registered.
     */
    public boolean isKnownCommand(String name) {
        return handlers.containsKey(name.toUpperCase());
    }

    /**
     * Get the data store (for persistence/recovery).
     */
    public DataStore getDataStore() {
        return dataStore;
    }

    // ── Command Registration ──

    private void registerAllCommands(DataStore store, PubSubManager pubSub, PersistenceManager persistence) {
        // Server commands
        handlers.put("PING", new PingCommand());
        handlers.put("ECHO", new EchoCommand());
        handlers.put("INFO", new InfoCommand(store));
        handlers.put("SAVE", new SaveCommand(persistence));
        handlers.put("FLUSHDB", new FlushDbCommand(store));

        // String commands
        handlers.put("SET", new SetCommand(store));
        handlers.put("GET", new GetCommand(store));
        handlers.put("MSET", new MSetCommand(store));
        handlers.put("MGET", new MGetCommand(store));
        handlers.put("INCR", new IncrCommand(store));
        handlers.put("DECR", new DecrCommand(store));

        // Key commands
        handlers.put("DEL", new DelCommand(store));
        handlers.put("EXPIRE", new ExpireCommand(store));
        handlers.put("TTL", new TtlCommand(store));
        handlers.put("KEYS", new KeysCommand(store));
        handlers.put("EXISTS", new ExistsCommand(store));
        handlers.put("TYPE", new TypeCommand(store));

        // List commands
        handlers.put("LPUSH", new LPushCommand(store));
        handlers.put("RPUSH", new RPushCommand(store));
        handlers.put("LPOP", new LPopCommand(store));
        handlers.put("RPOP", new RPopCommand(store));
        handlers.put("LRANGE", new LRangeCommand(store));

        // Set commands
        handlers.put("SADD", new SAddCommand(store));
        handlers.put("SMEMBERS", new SMembersCommand(store));
        handlers.put("SREM", new SRemCommand(store));

        // Hash commands
        handlers.put("HSET", new HSetCommand(store));
        handlers.put("HGET", new HGetCommand(store));
        handlers.put("HGETALL", new HGetAllCommand(store));

        // Transaction commands
        handlers.put("MULTI", new MultiCommand());
        handlers.put("EXEC", new ExecCommand(this));
        handlers.put("DISCARD", new DiscardCommand());

        // Pub/Sub commands
        handlers.put("SUBSCRIBE", new SubscribeCommand(pubSub));
        handlers.put("PUBLISH", new PublishCommand(pubSub));
        handlers.put("UNSUBSCRIBE", new UnsubscribeCommand(pubSub));
    }
}
