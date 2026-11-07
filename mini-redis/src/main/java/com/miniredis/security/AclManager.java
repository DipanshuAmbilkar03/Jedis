package com.miniredis.security;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * AclManager — manages users, authentication, and command level permissions.
 * Implements Redis-like ACL rules (+command, -command, on/off, >pass).
 */
public class AclManager {

    private static final String HASH_PREFIX = "pbkdf2";
    private static final int ITERATIONS = 210_000;
    private static final int SALT_BYTES = 16;
    private static final int KEY_BITS = 256;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ConcurrentHashMap<String, AclUser> users = new ConcurrentHashMap<>();
    private final boolean allowPasswordlessDefault;

    public AclManager() {
        this(null, false);
    }

    public AclManager(String defaultPassword) {
        this(defaultPassword, false);
    }

    public AclManager(String defaultPassword, boolean allowPasswordlessDefault) {
        this.allowPasswordlessDefault = allowPasswordlessDefault;
        Set<String> defaultAllowed = new HashSet<>();
        defaultAllowed.add("*");
        if (defaultPassword != null && !defaultPassword.isEmpty()) {
            users.put("default", new AclUser("default", hashPassword(defaultPassword), true, defaultAllowed, new HashSet<>()));
        } else if (allowPasswordlessDefault) {
            users.put("default", new AclUser("default", "", true, defaultAllowed, new HashSet<>()));
        } else {
            users.put("default", new AclUser("default", "", false, defaultAllowed, new HashSet<>()));
        }
    }

    public boolean isAuthRequired() {
        AclUser user = users.get("default");
        return user == null || !user.isEnabled() || !user.getPasswordHash().isEmpty() || !allowPasswordlessDefault;
    }

    public AclUser authenticate(String username, String password) {
        AclUser user = users.get(username);
        if (user == null || !user.isEnabled() || user.getPasswordHash().isEmpty()) {
            return null;
        }
        if (password == null || !verifyPassword(password, user.getPasswordHash())) {
            return null;
        }
        return user;
    }

    public boolean isAllowed(AclUser user, String commandName) {
        if (user == null) {
            AclUser defaultUser = users.get("default");
            if (defaultUser == null || !defaultUser.isEnabled()) {
                return false;
            }
            if (!allowPasswordlessDefault || !defaultUser.getPasswordHash().isEmpty()) {
                return false;
            }
            user = defaultUser;
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
            } else if ("resetpass".equalsIgnoreCase(rule)) {
                passHash = "";
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
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        byte[] hash = pbkdf2(password.toCharArray(), salt, ITERATIONS, KEY_BITS);
        return HASH_PREFIX + "$" + ITERATIONS + "$"
                + Base64.getEncoder().encodeToString(salt) + "$"
                + Base64.getEncoder().encodeToString(hash);
    }

    public static boolean verifyPassword(String password, String stored) {
        if (password == null || stored == null || stored.isEmpty()) {
            return false;
        }
        String[] parts = stored.split("\\$");
        if (parts.length != 4 || !HASH_PREFIX.equals(parts[0])) {
            return false;
        }
        try {
            int iterations = Integer.parseInt(parts[1]);
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] expected = Base64.getDecoder().decode(parts[3]);
            byte[] actual = pbkdf2(password.toCharArray(), salt, iterations, expected.length * 8);
            return MessageDigest.isEqual(expected, actual);
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static byte[] pbkdf2(char[] password, byte[] salt, int iterations, int keyBits) {
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, keyBits);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec)
                    .getEncoded();
        } catch (Exception e) {
            throw new IllegalStateException("Unable to hash password", e);
        } finally {
            spec.clearPassword();
        }
    }
}
