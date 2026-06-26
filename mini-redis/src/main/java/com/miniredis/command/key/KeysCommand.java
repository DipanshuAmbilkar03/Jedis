package com.miniredis.command.key;

import com.miniredis.command.Command;
import com.miniredis.command.CommandHandler;
import com.miniredis.protocol.RespEncoder;
import com.miniredis.server.ClientHandler;
import com.miniredis.store.DataStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Handles the KEYS command.
 * 
 * Usage: KEYS pattern
 * Returns all keys matching the glob-style pattern.
 * Supported patterns: * (match any), ? (match single char).
 */
public class KeysCommand implements CommandHandler {

    private final DataStore dataStore;

    public KeysCommand(DataStore dataStore) {
        this.dataStore = dataStore;
    }

    @Override
    public String handle(Command command, ClientHandler client) {
        if (command.argCount() != 2) {
            return RespEncoder.wrongArgCount("keys");
        }

        String pattern = command.getArg(1);
        Set<String> matchingKeys = dataStore.keys(pattern);

        if (matchingKeys.isEmpty()) {
            return RespEncoder.encodeEmptyArray();
        }

        List<String> keyList = new ArrayList<>(matchingKeys);
        return RespEncoder.encodeStringArray(keyList);
    }
}
