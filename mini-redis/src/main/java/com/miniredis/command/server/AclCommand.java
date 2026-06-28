package com.miniredis.command.server;

import com.miniredis.command.Command;
import com.miniredis.command.CommandHandler;
import com.miniredis.protocol.RespEncoder;
import com.miniredis.server.ClientHandler;
import com.miniredis.security.AclManager;
import com.miniredis.security.AclUser;

import java.util.ArrayList;
import java.util.List;

/**
 * ACL command — manages access control list users and permissions.
 * Syntax:
 * - ACL WHOAMI
 * - ACL LIST
 * - ACL SETUSER username [rules ...]
 * - ACL DELUSER username
 */
public class AclCommand implements CommandHandler {

    private final AclManager aclManager;

    public AclCommand(AclManager aclManager) {
        this.aclManager = aclManager;
    }

    @Override
    public String handle(Command command, ClientHandler client) {
        if (command.argCount() < 2) {
            return RespEncoder.encodeError("ERR wrong number of arguments for 'acl' command");
        }

        String subCommand = command.getArg(1).toUpperCase();
        return switch (subCommand) {
            case "WHOAMI" -> handleWhoAmI(client);
            case "LIST" -> handleList();
            case "SETUSER" -> handleSetUser(command);
            case "DELUSER" -> handleDelUser(command);
            default -> RespEncoder.encodeError("ERR unknown subcommand '" + subCommand + "'");
        };
    }

    private String handleWhoAmI(ClientHandler client) {
        AclUser user = client.getAuthenticatedUser();
        String name = user != null ? user.getUsername() : "default";
        return RespEncoder.encodeSimpleString(name);
    }

    private String handleList() {
        List<String> userNames = aclManager.getUserNames();
        List<String> list = new ArrayList<>();
        for (String name : userNames) {
            AclUser user = aclManager.getUser(name);
            if (user != null) {
                StringBuilder sb = new StringBuilder("user ");
                sb.append(user.getUsername());
                sb.append(user.isEnabled() ? " on" : " off");
                if (user.getPasswordHash().isEmpty()) {
                    sb.append(" nopass");
                } else {
                    sb.append(" activepass");
                }
                for (String cmd : user.getAllowedCommands()) {
                    sb.append(" +").append(cmd);
                }
                for (String cmd : user.getDeniedCommands()) {
                    sb.append(" -").append(cmd);
                }
                list.add(sb.toString());
            }
        }
        return RespEncoder.encodeStringArray(list);
    }

    private String handleSetUser(Command command) {
        if (command.argCount() < 3) {
            return RespEncoder.encodeError("ERR wrong number of arguments for 'acl setuser' command");
        }
        String username = command.getArg(2);
        List<String> rules = new ArrayList<>();
        for (int i = 3; i < command.argCount(); i++) {
            rules.add(command.getArg(i));
        }

        aclManager.setUserRules(username, rules);
        return RespEncoder.encodeSimpleString("OK");
    }

    private String handleDelUser(Command command) {
        if (command.argCount() < 3) {
            return RespEncoder.encodeError("ERR wrong number of arguments for 'acl deluser' command");
        }
        String username = command.getArg(2);
        if ("default".equals(username)) {
            return RespEncoder.encodeError("ERR cannot delete default user");
        }

        AclUser user = aclManager.getUser(username);
        if (user == null) {
            return RespEncoder.encodeInteger(0);
        }

        aclManager.removeUser(username);
        return RespEncoder.encodeInteger(1);
    }
}
