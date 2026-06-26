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
 * SREM key member [member ...]
 * Removes the specified members from the set stored at key.
 * Returns the number of members that were actually removed.
 * Non-existing members are ignored. If the key does not exist, returns 0.
 */
public class SRemCommand implements CommandHandler {

    private final DataStore dataStore;

    public SRemCommand(DataStore dataStore) {
        this.dataStore = dataStore;
    }

    @Override
    public String handle(Command command, ClientHandler client) {
        if (command.argCount() < 3) {
            return RespEncoder.wrongArgCount("srem");
        }

        String key = command.getArg(1);

        try {
            RedisObject obj = dataStore.getIfType(key, DataType.SET);
            if (obj == null) {
                return RespEncoder.ZERO;
            }

            LinkedHashSet<String> set = obj.getSetValue();

            int removed = 0;
            for (int i = 2; i < command.argCount(); i++) {
                if (set.remove(command.getArg(i))) {
                    removed++;
                }
            }

            // Clean up empty sets
            if (set.isEmpty()) {
                dataStore.delete(key);
            }

            return RespEncoder.encodeInteger(removed);
        } catch (DataStore.WrongTypeException e) {
            return RespEncoder.wrongType();
        }
    }
}
