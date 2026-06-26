package com.miniredis.command;

import com.miniredis.protocol.RespEncoder;
import com.miniredis.store.DataStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class StringCommandsTest {

    private DataStore store;
    private CommandRouter router;

    @BeforeEach
    public void setUp() {
        store = new DataStore();
        router = new CommandRouter(store, null, null);
    }

    @Test
    public void testSetAndGet() {
        String setResp = router.execute(Command.from(List.of("SET", "name", "antigravity")), null);
        assertEquals(RespEncoder.OK, setResp);

        String getResp = router.execute(Command.from(List.of("GET", "name")), null);
        assertEquals(RespEncoder.encodeBulkString("antigravity"), getResp);
    }

    @Test
    public void testGetNonExistent() {
        String getResp = router.execute(Command.from(List.of("GET", "nonexistent")), null);
        assertEquals(RespEncoder.NULL, getResp);
    }

    @Test
    public void testMSetAndMGet() {
        String msetResp = router.execute(Command.from(List.of("MSET", "k1", "v1", "k2", "v2")), null);
        assertEquals(RespEncoder.OK, msetResp);

        String mgetResp = router.execute(Command.from(List.of("MGET", "k1", "k2", "k3")), null);
        String expected = "*3\r\n$2\r\nv1\r\n$2\r\nv2\r\n$-1\r\n";
        assertEquals(expected, mgetResp);
    }

    @Test
    public void testIncrAndDecr() {
        // INCR empty key -> 1
        String incrResp = router.execute(Command.from(List.of("INCR", "counter")), null);
        assertEquals(RespEncoder.encodeInteger(1), incrResp);

        // INCR again -> 2
        incrResp = router.execute(Command.from(List.of("INCR", "counter")), null);
        assertEquals(RespEncoder.encodeInteger(2), incrResp);

        // DECR -> 1
        String decrResp = router.execute(Command.from(List.of("DECR", "counter")), null);
        assertEquals(RespEncoder.encodeInteger(1), decrResp);

        // INCR non-integer value -> error
        router.execute(Command.from(List.of("SET", "nonint", "abc")), null);
        String errResp = router.execute(Command.from(List.of("INCR", "nonint")), null);
        assertTrue(errResp.startsWith("-ERR"));
    }
}
