package com.miniredis.command.server;

import com.miniredis.command.Command;
import com.miniredis.command.CommandHandler;
import com.miniredis.protocol.RespEncoder;
import com.miniredis.server.ClientHandler;

/**
 * ECHO message
 * Returns the given message as a bulk string.
 */
public class EchoCommand implements CommandHandler {

    @Override
    public String handle(Command command, ClientHandler client) {
        if (command.argCount() != 2) {
            return RespEncoder.wrongArgCount("echo");
        }

        return RespEncoder.encodeBulkString(command.getArg(1));
    }
}
