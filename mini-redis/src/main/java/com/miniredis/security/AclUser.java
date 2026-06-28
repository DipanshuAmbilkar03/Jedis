package com.miniredis.security;

import java.util.Set;

/**
 * AclUser — represents a user and their command authorization permissions.
 */
public class AclUser {
    private final String username;
    private final String passwordHash;
    private final boolean enabled;
    private final Set<String> allowedCommands;
    private final Set<String> deniedCommands;

    public AclUser(String username, String passwordHash, boolean enabled, Set<String> allowedCommands, Set<String> deniedCommands) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.enabled = enabled;
        this.allowedCommands = allowedCommands;
        this.deniedCommands = deniedCommands;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Set<String> getAllowedCommands() {
        return allowedCommands;
    }

    public Set<String> getDeniedCommands() {
        return deniedCommands;
    }
}
