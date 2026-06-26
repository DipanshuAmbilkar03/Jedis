package com.miniredis.command.string;

import com.miniredis.command.Command;
import com.miniredis.command.CommandHandler;
import com.miniredis.protocol.RespEncoder;
import com.miniredis.server.ClientHandler;
import com.miniredis.store.DataStore;
import com.miniredis.store.RedisObject;

/**
 * Handles the SET command.
 * 
 * Usage: SET key value [EX seconds] [PX milliseconds] [NX|XX]
 * 
 * Options:
 *   EX seconds  — Set the specified expire time, in seconds.
 *   PX ms       — Set the specified expire time, in milliseconds.
 *   NX          — Only set the key if it does not already exist.
 *   XX          — Only set the key if it already exists.
 * 
 * Returns OK on success, or null bulk string if NX/XX condition not met.
 */
public class SetCommand implements CommandHandler {

    private final DataStore dataStore;

    public SetCommand(DataStore dataStore) {
        this.dataStore = dataStore;
    }

    @Override
    public String handle(Command command, ClientHandler client) {
        if (command.argCount() < 3) {
            return RespEncoder.wrongArgCount("set");
        }

        String key = command.getArg(1);
        String value = command.getArg(2);

        boolean nx = false;
        boolean xx = false;
        Long exSeconds = null;
        Long pxMillis = null;

        // Parse optional flags starting at index 3
        int i = 3;
        while (i < command.argCount()) {
            String option = command.getArg(i).toUpperCase();
            switch (option) {
                case "NX" -> {
                    nx = true;
                    i++;
                }
                case "XX" -> {
                    xx = true;
                    i++;
                }
                case "EX" -> {
                    if (i + 1 >= command.argCount()) {
                        return RespEncoder.encodeError("ERR syntax error");
                    }
                    try {
                        exSeconds = Long.parseLong(command.getArg(i + 1));
                    } catch (NumberFormatException e) {
                        return RespEncoder.notAnInteger();
                    }
                    if (exSeconds <= 0) {
                        return RespEncoder.encodeError("ERR invalid expire time in 'set' command");
                    }
                    i += 2;
                }
                case "PX" -> {
                    if (i + 1 >= command.argCount()) {
                        return RespEncoder.encodeError("ERR syntax error");
                    }
                    try {
                        pxMillis = Long.parseLong(command.getArg(i + 1));
                    } catch (NumberFormatException e) {
                        return RespEncoder.notAnInteger();
                    }
                    if (pxMillis <= 0) {
                        return RespEncoder.encodeError("ERR invalid expire time in 'set' command");
                    }
                    i += 2;
                }
                default -> {
                    return RespEncoder.encodeError("ERR syntax error");
                }
            }
        }

        // NX: only set if key does NOT exist
        if (nx && dataStore.exists(key)) {
            return RespEncoder.encodeNull();
        }

        // XX: only set if key DOES exist
        if (xx && !dataStore.exists(key)) {
            return RespEncoder.encodeNull();
        }

        dataStore.set(key, RedisObject.string(value));

        // Set expiry if specified (PX takes precedence if both given)
        if (pxMillis != null) {
            dataStore.getExpiryManager().setExpiryMillis(key, pxMillis);
        } else if (exSeconds != null) {
            dataStore.getExpiryManager().setExpirySeconds(key, exSeconds);
        } else {
            // Setting a key without expiry clears any previous expiry
            dataStore.getExpiryManager().removeExpiry(key);
        }

        return RespEncoder.OK;
    }
}
