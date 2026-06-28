package com.miniredis.command.server;

import com.miniredis.command.Command;
import com.miniredis.command.CommandHandler;
import com.miniredis.memory.EvictionPolicy;
import com.miniredis.protocol.RespEncoder;
import com.miniredis.server.ClientHandler;
import com.miniredis.store.DataStore;

import java.util.List;

/**
 * CONFIG GET parameter | CONFIG SET parameter value
 */
public class ConfigCommand implements CommandHandler {

    private final DataStore dataStore;

    public ConfigCommand(DataStore dataStore) {
        this.dataStore = dataStore;
    }

    @Override
    public String handle(Command command, ClientHandler client) {
        if (command.argCount() < 3) {
            return RespEncoder.encodeError("ERR wrong number of arguments for 'config' command");
        }

        String subCommand = command.getArg(1).toUpperCase();
        String parameter = command.getArg(2).toLowerCase();

        var mm = dataStore.getMemoryManager();

        if ("GET".equals(subCommand)) {
            if ("maxmemory".equals(parameter)) {
                return RespEncoder.encodeStringArray(List.of("maxmemory", String.valueOf(mm.getMaxMemoryBytes())));
            } else if ("maxmemory-policy".equals(parameter)) {
                return RespEncoder.encodeStringArray(List.of("maxmemory-policy", mm.getEvictionPolicy().name().toLowerCase().replace("_", "-")));
            } else {
                return RespEncoder.encodeError("ERR Unknown option or security restriction at CONFIG GET");
            }
        } else if ("SET".equals(subCommand)) {
            if (command.argCount() < 4) {
                return RespEncoder.encodeError("ERR wrong number of arguments for 'config set' command");
            }
            String value = command.getArg(3);

            if ("maxmemory".equals(parameter)) {
                try {
                    long bytes = Long.parseLong(value);
                    mm.setMaxMemoryBytes(bytes);
                    return RespEncoder.OK;
                } catch (NumberFormatException e) {
                    return RespEncoder.encodeError("ERR value is not an integer or out of range");
                }
            } else if ("maxmemory-policy".equals(parameter)) {
                EvictionPolicy policy = EvictionPolicy.fromString(value);
                mm.setEvictionPolicy(policy);
                return RespEncoder.OK;
            } else {
                return RespEncoder.encodeError("ERR Unknown option or security restriction at CONFIG SET");
            }
        }

        return RespEncoder.encodeError("ERR Unknown CONFIG subcommand: " + subCommand);
    }
}
