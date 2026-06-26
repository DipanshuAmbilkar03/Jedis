package com.miniredis.command;

import com.miniredis.protocol.RespEncoder;
import com.miniredis.store.DataStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class HashCommandsTest {

    private DataStore store;
    private CommandRouter router;

    @BeforeEach
    public void setUp() {
        store = new DataStore();
        router = new CommandRouter(store, null, null);
    }

    @Test
    public void testHashOperations() {
        // HSET key f1 v1 f2 v2 -> returns 2
        String hsetResp = router.execute(Command.from(List.of("HSET", "myhash", "f1", "v1", "f2", "v2")), null);
        assertEquals(RespEncoder.encodeInteger(2), hsetResp);

        // HGET key f1 -> returns v1
        String hgetResp = router.execute(Command.from(List.of("HGET", "myhash", "f1")), null);
        assertEquals(RespEncoder.encodeBulkString("v1"), hgetResp);

        // HGET key nonexistent -> returns null
        hgetResp = router.execute(Command.from(List.of("HGET", "myhash", "f3")), null);
        assertEquals(RespEncoder.NULL, hgetResp);

        // HGETALL -> returns array of all fields and values
        String hgetallResp = router.execute(Command.from(List.of("HGETALL", "myhash")), null);
        assertTrue(hgetallResp.contains("f1"));
        assertTrue(hgetallResp.contains("v1"));
        assertTrue(hgetallResp.contains("f2"));
        assertTrue(hgetallResp.contains("v2"));
    }
}
