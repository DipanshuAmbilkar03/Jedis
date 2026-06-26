package com.miniredis.command.string;

import com.miniredis.command.Command;
import com.miniredis.command.CommandHandler;
import com.miniredis.protocol.RespEncoder;
import com.miniredis.server.ClientHandler;
import com.miniredis.store.DataStore;
import com.miniredis.store.DataType;
import com.miniredis.store.RedisObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles the MGET command.
 * 
 * Usage: MGET key1 [key2 ...]
 * Returns the values of all specified keys. For every key that does not exist
 * or does not hold a string value, null is returned.
 */
public class MGetCommand implements CommandHandler {

    private final DataStore dataStore;

    public MGetCommand(DataStore dataStore) {
        this.dataStore = dataStore;
    }

    @Override
    public String handle(Command command, ClientHandler client) {
        if (command.argCount() < 2) {
            return RespEncoder.wrongArgCount("mget");
        }

        List<String> results = new ArrayList<>();

        for (int i = 1; i < command.argCount(); i++) {
            String key = command.getArg(i);
            RedisObject obj = dataStore.get(key);

            if (obj == null || obj.getType() != DataType.STRING) {
                results.add(null);
            } else {
                results.add(obj.getStringValue());
            }
        }

        return RespEncoder.encodeStringArray(results);
    }
}
