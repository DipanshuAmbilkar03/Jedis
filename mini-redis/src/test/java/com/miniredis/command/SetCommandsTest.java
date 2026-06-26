package com.miniredis.command;

import com.miniredis.protocol.RespEncoder;
import com.miniredis.store.DataStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class SetCommandsTest {

    private DataStore store;
    private CommandRouter router;

    @BeforeEach
    public void setUp() {
        store = new DataStore();
        router = new CommandRouter(store, null, null);
    }

    @Test
    public void testSetOperations() {
        // SADD key m1 m2 -> returns 2
        String saddResp = router.execute(Command.from(List.of("SADD", "myset", "m1", "m2")), null);
        assertEquals(RespEncoder.encodeInteger(2), saddResp);

        // SADD key m1 m3 -> returns 1 (m1 is duplicate)
        saddResp = router.execute(Command.from(List.of("SADD", "myset", "m1", "m3")), null);
        assertEquals(RespEncoder.encodeInteger(1), saddResp);

        // SMEMBERS key -> returns array containing m1, m2, m3
        String smembersResp = router.execute(Command.from(List.of("SMEMBERS", "myset")), null);
        assertTrue(smembersResp.contains("m1"));
        assertTrue(smembersResp.contains("m2"));
        assertTrue(smembersResp.contains("m3"));

        // SREM key m2 m4 -> returns 1 (m4 not in set)
        String sremResp = router.execute(Command.from(List.of("SREM", "myset", "m2", "m4")), null);
        assertEquals(RespEncoder.encodeInteger(1), sremResp);

        // SMEMBERS -> only m1, m3 left
        smembersResp = router.execute(Command.from(List.of("SMEMBERS", "myset")), null);
        assertTrue(smembersResp.contains("m1"));
        assertFalse(smembersResp.contains("m2"));
        assertTrue(smembersResp.contains("m3"));
    }
}
