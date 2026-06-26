package com.miniredis.command.list;

import com.miniredis.command.Command;
import com.miniredis.command.CommandHandler;
import com.miniredis.protocol.RespEncoder;
import com.miniredis.server.ClientHandler;
import com.miniredis.store.DataStore;
import com.miniredis.store.DataType;
import com.miniredis.store.RedisObject;

import java.util.LinkedList;

/**
 * RPOP key
 * Removes and returns the last element of the list stored at key.
 * Returns null if the key does not exist or the list is empty.
 */
public class RPopCommand implements CommandHandler {

    private final DataStore dataStore;

    public RPopCommand(DataStore dataStore) {
        this.dataStore = dataStore;
    }

    @Override
    public String handle(Command command, ClientHandler client) {
        if (command.argCount() != 2) {
            return RespEncoder.wrongArgCount("rpop");
        }

        String key = command.getArg(1);

        try {
            RedisObject obj = dataStore.getIfType(key, DataType.LIST);
            if (obj == null) {
                return RespEncoder.encodeNull();
            }

            LinkedList<String> list = obj.getListValue();
            if (list.isEmpty()) {
                return RespEncoder.encodeNull();
            }

            String value = list.removeLast();

            // Clean up empty lists
            if (list.isEmpty()) {
                dataStore.delete(key);
            }

            return RespEncoder.encodeBulkString(value);
        } catch (DataStore.WrongTypeException e) {
            return RespEncoder.wrongType();
        }
    }
}
