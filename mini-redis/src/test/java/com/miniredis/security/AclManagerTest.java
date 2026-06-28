package com.miniredis.security;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

public class AclManagerTest {

    @Test
    public void testDefaultUserPermissions() {
        AclManager manager = new AclManager();
        AclUser defaultUser = manager.getUser("default");
        assertNotNull(defaultUser);
        assertTrue(defaultUser.isEnabled());
        assertTrue(manager.isAllowed(defaultUser, "PING"));
        assertTrue(manager.isAllowed(defaultUser, "SET"));
        assertTrue(manager.isAllowed(defaultUser, "GET"));
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
}
