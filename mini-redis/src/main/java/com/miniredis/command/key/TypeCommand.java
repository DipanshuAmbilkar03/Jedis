package com.miniredis.command.key;

import com.miniredis.command.Command;
import com.miniredis.command.CommandHandler;
import com.miniredis.protocol.RespEncoder;
import com.miniredis.server.ClientHandler;
import com.miniredis.store.DataStore;
import com.miniredis.store.DataType;

/**
 * Handles the TYPE command.
 * 
 * Usage: TYPE key
 * Returns the string representation of the type of the value stored at key.
 * Possible return values: string, list, set, hash, none.
 */
public class TypeCommand implements CommandHandler {

    private final DataStore dataStore;

    public TypeCommand(DataStore dataStore) {
        this.dataStore = dataStore;
    }

    @Override
    public String handle(Command command, ClientHandler client) {
        if (command.argCount() != 2) {
            return RespEncoder.wrongArgCount("type");
        }

        String key = command.getArg(1);
        DataType type = dataStore.type(key);

        if (type == null) {
            return RespEncoder.encodeSimpleString("none");
        }

        return RespEncoder.encodeSimpleString(type.getTypeName());
    }
}
