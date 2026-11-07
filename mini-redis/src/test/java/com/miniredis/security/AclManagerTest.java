package com.miniredis.security;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

public class AclManagerTest {

    @Test
    public void testDefaultUserIsDisabledByDefault() {
        AclManager manager = new AclManager();
        AclUser defaultUser = manager.getUser("default");
        assertNotNull(defaultUser);
        assertFalse(defaultUser.isEnabled());
        assertTrue(manager.isAuthRequired());
        assertFalse(manager.isAllowed(null, "PING"));
        assertFalse(manager.isAllowed(defaultUser, "SET"));
    }

    @Test
    public void testDefaultUserWithPassword() {
        AclManager manager = new AclManager("s3cret");
        AclUser authenticated = manager.authenticate("default", "s3cret");
        assertNotNull(authenticated);
        assertTrue(authenticated.isEnabled());
        assertNull(manager.authenticate("default", "wrong"));
        assertNull(manager.authenticate("default", ""));
        assertTrue(manager.isAuthRequired());
        assertFalse(manager.isAllowed(null, "PING"));
        assertTrue(manager.isAllowed(authenticated, "SET"));
    }

    @Test
    public void testPasswordlessDefaultRequiresExplicitOptIn() {
        AclManager manager = new AclManager(null, true);
        AclUser defaultUser = manager.getUser("default");
        assertTrue(defaultUser.isEnabled());
        assertFalse(manager.isAuthRequired());
        assertTrue(manager.isAllowed(null, "PING"));
    }

    @Test
    public void testSetUserAndAclRules() {
        AclManager manager = new AclManager();

        manager.setUserRules("alice", List.of("on", ">secret123", "+PING", "+GET", "-SET"));

        AclUser alice = manager.getUser("alice");
        assertNotNull(alice);
        assertTrue(alice.isEnabled());

        assertNotNull(manager.authenticate("alice", "secret123"));
        assertNull(manager.authenticate("alice", "wrong"));

        assertTrue(manager.isAllowed(alice, "PING"));
        assertTrue(manager.isAllowed(alice, "GET"));
        assertFalse(manager.isAllowed(alice, "SET"));
        assertFalse(manager.isAllowed(alice, "DEL"));
    }

    @Test
    public void testAclAllAllowedAndDenied() {
        AclManager manager = new AclManager();
        manager.setUserRules("bob", List.of("+@all", "-DEL"));

        AclUser bob = manager.getUser("bob");
        assertTrue(manager.isAllowed(bob, "SET"));
        assertTrue(manager.isAllowed(bob, "GET"));
        assertFalse(manager.isAllowed(bob, "DEL"));
    }

    @Test
    public void testPasswordHashIsSalted() {
        String first = AclManager.hashPassword("same-password");
        String second = AclManager.hashPassword("same-password");
        assertNotEquals(first, second);
        assertTrue(AclManager.verifyPassword("same-password", first));
        assertTrue(AclManager.verifyPassword("same-password", second));
        assertFalse(AclManager.verifyPassword("wrong", first));
    }
}
