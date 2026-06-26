package com.miniredis.command.set;

import com.miniredis.command.Command;
import com.miniredis.command.CommandHandler;
import com.miniredis.protocol.RespEncoder;
import com.miniredis.server.ClientHandler;
import com.miniredis.store.DataStore;
import com.miniredis.store.DataType;
import com.miniredis.store.RedisObject;

import java.util.LinkedHashSet;

/**
 * SADD key member [member ...]
 * Adds the specified members to the set stored at key.
 * Creates a new set if the key does not exist.
 * Returns the number of members that were actually added (ignores duplicates).
 */
public class SAddCommand implements CommandHandler {

    private final DataStore dataStore;

    public SAddCommand(DataStore dataStore) {
        this.dataStore = dataStore;
    }

    @Override
    public String handle(Command command, ClientHandler client) {
        if (command.argCount() < 3) {
            return RespEncoder.wrongArgCount("sadd");
        }

        String key = command.getArg(1);

        try {
            RedisObject obj = dataStore.getOrCreate(key, DataType.SET);
            LinkedHashSet<String> set = obj.getSetValue();

            int added = 0;
            for (int i = 2; i < command.argCount(); i++) {
                if (set.add(command.getArg(i))) {
                    added++;
                }
            }

            return RespEncoder.encodeInteger(added);
        } catch (DataStore.WrongTypeException e) {
            return RespEncoder.wrongType();
        }
    }
}
