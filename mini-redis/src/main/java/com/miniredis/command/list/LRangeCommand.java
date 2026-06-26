package com.miniredis.command.list;

import com.miniredis.command.Command;
import com.miniredis.command.CommandHandler;
import com.miniredis.protocol.RespEncoder;
import com.miniredis.server.ClientHandler;
import com.miniredis.store.DataStore;
import com.miniredis.store.DataType;
import com.miniredis.store.RedisObject;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * LRANGE key start stop
 * Returns the elements in the list from index start to stop (both inclusive).
 * Indices are 0-based. Negative indices count from the end (-1 = last element).
 * Out-of-range indices are clamped. Returns an empty array if the key is missing.
 */
public class LRangeCommand implements CommandHandler {

    private final DataStore dataStore;

    public LRangeCommand(DataStore dataStore) {
        this.dataStore = dataStore;
    }

    @Override
    public String handle(Command command, ClientHandler client) {
        if (command.argCount() != 4) {
            return RespEncoder.wrongArgCount("lrange");
        }

        String key = command.getArg(1);
        int start;
        int stop;

        try {
            start = Integer.parseInt(command.getArg(2));
            stop = Integer.parseInt(command.getArg(3));
        } catch (NumberFormatException e) {
            return RespEncoder.notAnInteger();
        }

        try {
            RedisObject obj = dataStore.getIfType(key, DataType.LIST);
            if (obj == null) {
                return RespEncoder.encodeEmptyArray();
            }

            LinkedList<String> list = obj.getListValue();
            int size = list.size();

            // Convert negative indices
            if (start < 0) start = size + start;
            if (stop < 0) stop = size + stop;

            // Clamp to valid range
            if (start < 0) start = 0;
            if (stop >= size) stop = size - 1;

            // Empty range check
            if (start > stop || start >= size) {
                return RespEncoder.encodeEmptyArray();
            }

            List<String> result = new ArrayList<>(stop - start + 1);
            for (int i = start; i <= stop; i++) {
                result.add(list.get(i));
            }

            return RespEncoder.encodeStringArray(result);
        } catch (DataStore.WrongTypeException e) {
            return RespEncoder.wrongType();
        }
    }
}
