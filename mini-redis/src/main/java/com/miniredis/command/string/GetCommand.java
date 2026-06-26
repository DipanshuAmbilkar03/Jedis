package com.miniredis.command.string;

import com.miniredis.command.Command;
import com.miniredis.command.CommandHandler;
import com.miniredis.protocol.RespEncoder;
import com.miniredis.server.ClientHandler;
import com.miniredis.store.DataStore;
import com.miniredis.store.DataType;
import com.miniredis.store.RedisObject;

/**
 * Handles the GET command.
 * 
 * Usage: GET key
 * Returns the string value of key, or null if the key does not exist.
 * An error is returned if the value stored at key is not a string.
 */
public class GetCommand implements CommandHandler {

    private final DataStore dataStore;

    public GetCommand(DataStore dataStore) {
        this.dataStore = dataStore;
    }

    @Override
    public String handle(Command command, ClientHandler client) {
        if (command.argCount() != 2) {
            return RespEncoder.wrongArgCount("get");
        }

        String key = command.getArg(1);

        try {
            RedisObject obj = dataStore.getIfType(key, DataType.STRING);
            if (obj == null) {
                return RespEncoder.encodeNull();
            }
            return RespEncoder.encodeBulkString(obj.getStringValue());
        } catch (DataStore.WrongTypeException e) {
            return RespEncoder.wrongType();
        }
    }
}
