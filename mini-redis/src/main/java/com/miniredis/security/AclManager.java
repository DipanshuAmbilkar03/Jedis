package com.miniredis.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AclManager — manages users, authentication, and command level permissions.
 * Implements Redis-like ACL rules (+command, -command, on/off, >pass).
 */
public class AclManager {

    private final ConcurrentHashMap<String, AclUser> users = new ConcurrentHashMap<>();

    public AclManager() {
        // Initialize default user with full access and no password
        Set<String> defaultAllowed = new HashSet<>();
        defaultAllowed.add("*");
        users.put("default", new AclUser("default", "", true, defaultAllowed, new HashSet<>()));
    }

    public AclUser authenticate(String username, String password) {
        AclUser user = users.get(username);
        if (user == null || !user.isEnabled()) {
            return null;
        }

        // If user has no password configured, anyone can authenticate as them
        if (user.getPasswordHash().isEmpty() && (password == null || password.isEmpty())) {
            return user;
        }

        String checkHash = hashPassword(password);
        if (checkHash.equals(user.getPasswordHash())) {
            return user;
        }
        return null;
    }

    public boolean isAllowed(AclUser user, String commandName) {
        if (user == null) {
            user = users.get("default");
        }
        if (user == null) {
            return true;
        }
        if (!user.isEnabled()) {
            return false;
        }

        String cmd = commandName.toUpperCase();
        if (user.getDeniedCommands().contains("*") || user.getDeniedCommands().contains(cmd)) {
            return false;
        }
        if (user.getAllowedCommands().contains("*") || user.getAllowedCommands().contains(cmd)) {
            return true;
        }
        return false;
    }

    public synchronized void setUserRules(String username, List<String> rules) {
        AclUser existing = users.get(username);
        boolean enabled = existing != null ? existing.isEnabled() : true;
        String passHash = existing != null ? existing.getPasswordHash() : "";
        Set<String> allowed = existing != null ? new HashSet<>(existing.getAllowedCommands()) : new HashSet<>();
        Set<String> denied = existing != null ? new HashSet<>(existing.getDeniedCommands()) : new HashSet<>();

        for (String rule : rules) {
            if ("on".equalsIgnoreCase(rule)) {
                enabled = true;
            } else if ("off".equalsIgnoreCase(rule)) {
                enabled = false;
            } else if (rule.startsWith(">")) {
                passHash = hashPassword(rule.substring(1));
            } else if (rule.startsWith("+")) {
                String cmd = rule.substring(1).toUpperCase();
                if ("@ALL".equals(cmd)) {
                    allowed.add("*");
                    denied.clear();
                } else {
                    allowed.add(cmd);
                    denied.remove(cmd);
                }
            } else if (rule.startsWith("-")) {
                String cmd = rule.substring(1).toUpperCase();
                if ("@ALL".equals(cmd)) {
                    allowed.clear();
                    denied.add("*");
                } else {
                    denied.add(cmd);
                    allowed.remove(cmd);
                }
            }
        }

        AclUser newUser = new AclUser(username, passHash, enabled, allowed, denied);
        users.put(username, newUser);
    }

    public AclUser getUser(String username) {
        return users.get(username);
    }

    public List<String> getUserNames() {
        return new ArrayList<>(users.keySet());
    }

    public void removeUser(String username) {
        if (!"default".equals(username)) {
            users.remove(username);
        }
    }

    public static String hashPassword(String password) {
        if (password == null || password.isEmpty()) {
            return "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(password.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            return password;
        }
    }
}
