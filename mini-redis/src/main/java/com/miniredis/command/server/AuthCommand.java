package com.miniredis.command.server;

import com.miniredis.command.Command;
import com.miniredis.command.CommandHandler;
import com.miniredis.protocol.RespEncoder;
import com.miniredis.server.ClientHandler;
import com.miniredis.security.AclManager;
import com.miniredis.security.AclUser;

/**
 * AUTH command — authenticates a client against a password (and optionally username).
 * Syntax: AUTH [username] password
 */
public class AuthCommand implements CommandHandler {

    private final AclManager aclManager;

    public AuthCommand(AclManager aclManager) {
        this.aclManager = aclManager;
    }

    @Override
    public String handle(Command command, ClientHandler client) {
        if (command.argCount() < 2 || command.argCount() > 3) {
            return RespEncoder.encodeError("ERR wrong number of arguments for 'auth' command");
        }

        String username = "default";
        String password;

        if (command.argCount() == 2) {
            // AUTH password
            password = command.getArg(1);
        } else {
            // AUTH username password
            username = command.getArg(1);
            password = command.getArg(2);
        }

        AclUser authenticated = aclManager.authenticate(username, password);
        if (authenticated == null) {
            return RespEncoder.encodeError("WRONGPASS invalid username-password pair or user is disabled");
        }

        client.setAuthenticatedUser(authenticated);
        return RespEncoder.encodeSimpleString("OK");
    }
}
