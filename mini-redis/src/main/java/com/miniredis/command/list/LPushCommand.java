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
 * LPUSH key value [value ...]
 * Pushes each value to the front (head) of the list stored at key.
 * Creates a new list if the key does not exist.
 * Returns the length of the list after the push operations.
 */
public class LPushCommand implements CommandHandler {

    private final DataStore dataStore;

    public LPushCommand(DataStore dataStore) {
        this.dataStore = dataStore;
    }

    @Override
    public String handle(Command command, ClientHandler client) {
        // LPUSH key val [val ...] → argCount >= 3 (cmd + key + at least one value)
        if (command.argCount() < 3) {
            return RespEncoder.wrongArgCount("lpush");
        }

        String key = command.getArg(1);

        try {
            RedisObject obj = dataStore.getOrCreate(key, DataType.LIST);
            LinkedList<String> list = obj.getListValue();

            for (int i = 2; i < command.argCount(); i++) {
                list.addFirst(command.getArg(i));
            }

            return RespEncoder.encodeInteger(list.size());
        } catch (DataStore.WrongTypeException e) {
            return RespEncoder.wrongType();
        }
    }
}
