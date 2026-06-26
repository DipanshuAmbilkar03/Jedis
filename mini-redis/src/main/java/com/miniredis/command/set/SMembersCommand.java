package com.miniredis.command.set;

import com.miniredis.command.Command;
import com.miniredis.command.CommandHandler;
import com.miniredis.protocol.RespEncoder;
import com.miniredis.server.ClientHandler;
import com.miniredis.store.DataStore;
import com.miniredis.store.DataType;
import com.miniredis.store.RedisObject;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * SMEMBERS key
 * Returns all the members of the set stored at key.
 * Returns an empty array if the key does not exist.
 */
public class SMembersCommand implements CommandHandler {

    private final DataStore dataStore;

    public SMembersCommand(DataStore dataStore) {
        this.dataStore = dataStore;
    }

    @Override
    public String handle(Command command, ClientHandler client) {
        if (command.argCount() != 2) {
            return RespEncoder.wrongArgCount("smembers");
        }

        String key = command.getArg(1);

        try {
            RedisObject obj = dataStore.getIfType(key, DataType.SET);
            if (obj == null) {
                return RespEncoder.encodeEmptyArray();
            }

            LinkedHashSet<String> set = obj.getSetValue();
            List<String> members = new ArrayList<>(set);

            return RespEncoder.encodeStringArray(members);
        } catch (DataStore.WrongTypeException e) {
            return RespEncoder.wrongType();
        }
    }
}
