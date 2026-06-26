package com.miniredis.command.hash;

import com.miniredis.command.Command;
import com.miniredis.command.CommandHandler;
import com.miniredis.protocol.RespEncoder;
import com.miniredis.server.ClientHandler;
import com.miniredis.store.DataStore;
import com.miniredis.store.DataType;
import com.miniredis.store.RedisObject;

import java.util.LinkedHashMap;

/**
 * HGET key field
 * Returns the value associated with the given field in the hash stored at key.
 * Returns null if the key or field does not exist.
 */
public class HGetCommand implements CommandHandler {

    private final DataStore dataStore;

    public HGetCommand(DataStore dataStore) {
        this.dataStore = dataStore;
    }

    @Override
    public String handle(Command command, ClientHandler client) {
        if (command.argCount() != 3) {
            return RespEncoder.wrongArgCount("hget");
        }

        String key = command.getArg(1);
        String field = command.getArg(2);

        try {
            RedisObject obj = dataStore.getIfType(key, DataType.HASH);
            if (obj == null) {
                return RespEncoder.encodeNull();
            }

            LinkedHashMap<String, String> hash = obj.getHashValue();
            String value = hash.get(field);

            return RespEncoder.encodeBulkString(value);
        } catch (DataStore.WrongTypeException e) {
            return RespEncoder.wrongType();
        }
    }
}
