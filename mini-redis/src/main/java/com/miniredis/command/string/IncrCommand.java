package com.miniredis.command.string;

import com.miniredis.command.Command;
import com.miniredis.command.CommandHandler;
import com.miniredis.protocol.RespEncoder;
import com.miniredis.server.ClientHandler;
import com.miniredis.store.DataStore;
import com.miniredis.store.DataType;
import com.miniredis.store.RedisObject;

/**
 * Handles the INCR command.
 * 
 * Usage: INCR key
 * Increments the number stored at key by one. If the key does not exist,
 * it is set to 0 before performing the operation. An error is returned if
 * the key contains a value of the wrong type or the string cannot be
 * represented as an integer.
 */
public class IncrCommand implements CommandHandler {

    private final DataStore dataStore;

    public IncrCommand(DataStore dataStore) {
        this.dataStore = dataStore;
    }

    @Override
    public String handle(Command command, ClientHandler client) {
        if (command.argCount() != 2) {
            return RespEncoder.wrongArgCount("incr");
        }

        String key = command.getArg(1);

        try {
            RedisObject obj = dataStore.getIfType(key, DataType.STRING);

            long current;
            if (obj == null) {
                current = 0;
            } else {
                try {
                    current = Long.parseLong(obj.getStringValue());
                } catch (NumberFormatException e) {
                    return RespEncoder.notAnInteger();
                }
            }

            long newValue = current + 1;
            dataStore.set(key, RedisObject.string(String.valueOf(newValue)));

            return RespEncoder.encodeInteger(newValue);
        } catch (DataStore.WrongTypeException e) {
            return RespEncoder.wrongType();
        }
    }
}
