package com.miniredis.command.hash;

import com.miniredis.command.Command;
import com.miniredis.command.CommandHandler;
import com.miniredis.protocol.RespEncoder;
import com.miniredis.server.ClientHandler;
import com.miniredis.store.DataStore;
import com.miniredis.store.DataType;
import com.miniredis.store.RedisObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * HGETALL key
 * Returns all fields and values of the hash stored at key as a flat array:
 * [field1, value1, field2, value2, ...]
 * Returns an empty array if the key does not exist.
 */
public class HGetAllCommand implements CommandHandler {

    private final DataStore dataStore;

    public HGetAllCommand(DataStore dataStore) {
        this.dataStore = dataStore;
    }

    @Override
    public String handle(Command command, ClientHandler client) {
        if (command.argCount() != 2) {
            return RespEncoder.wrongArgCount("hgetall");
        }

        String key = command.getArg(1);

        try {
            RedisObject obj = dataStore.getIfType(key, DataType.HASH);
            if (obj == null) {
                return RespEncoder.encodeEmptyArray();
            }

            LinkedHashMap<String, String> hash = obj.getHashValue();

            if (hash.isEmpty()) {
                return RespEncoder.encodeEmptyArray();
            }

            List<String> result = new ArrayList<>(hash.size() * 2);
            for (Map.Entry<String, String> entry : hash.entrySet()) {
                result.add(entry.getKey());
                result.add(entry.getValue());
            }

            return RespEncoder.encodeStringArray(result);
        } catch (DataStore.WrongTypeException e) {
            return RespEncoder.wrongType();
        }
    }
}
