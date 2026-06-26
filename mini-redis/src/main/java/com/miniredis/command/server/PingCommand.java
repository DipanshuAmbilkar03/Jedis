package com.miniredis.command.server;

import com.miniredis.command.Command;
import com.miniredis.command.CommandHandler;
import com.miniredis.protocol.RespEncoder;
import com.miniredis.server.ClientHandler;

/**
 * PING [message]
 * Returns PONG if no argument is provided.
 * Returns the argument as a bulk string if one is given.
 */
public class PingCommand implements CommandHandler {

    @Override
    public String handle(Command command, ClientHandler client) {
        if (command.argCount() == 1) {
            return RespEncoder.PONG;
        }

        // PING with a message — echo it back as a bulk string
        return RespEncoder.encodeBulkString(command.getArg(1));
    }
}
