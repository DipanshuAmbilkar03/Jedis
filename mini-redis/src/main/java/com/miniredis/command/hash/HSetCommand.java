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
 * HSET key field value [field value ...]
 * Sets the specified field-value pairs in the hash stored at key.
 * Creates a new hash if the key does not exist.
 * Returns the number of NEW fields that were added (not counting updated fields).
 */
public class HSetCommand implements CommandHandler {

    private final DataStore dataStore;

    public HSetCommand(DataStore dataStore) {
        this.dataStore = dataStore;
    }

    @Override
    public String handle(Command command, ClientHandler client) {
        // HSET key field value [field value ...] → need at least 4 args, and pairs must be even
        if (command.argCount() < 4 || (command.argCount() - 2) % 2 != 0) {
            return RespEncoder.wrongArgCount("hset");
        }

        String key = command.getArg(1);

        try {
            RedisObject obj = dataStore.getOrCreate(key, DataType.HASH);
            LinkedHashMap<String, String> hash = obj.getHashValue();

            int added = 0;
            for (int i = 2; i < command.argCount(); i += 2) {
                String field = command.getArg(i);
                String value = command.getArg(i + 1);

                if (!hash.containsKey(field)) {
                    added++;
                }
                hash.put(field, value);
            }

            return RespEncoder.encodeInteger(added);
        } catch (DataStore.WrongTypeException e) {
            return RespEncoder.wrongType();
        }
    }
}
