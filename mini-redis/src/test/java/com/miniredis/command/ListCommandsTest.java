package com.miniredis.command;

import com.miniredis.protocol.RespEncoder;
import com.miniredis.store.DataStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ListCommandsTest {

    private DataStore store;
    private CommandRouter router;

    @BeforeEach
    public void setUp() {
        store = new DataStore();
        router = new CommandRouter(store, null, null);
    }

    @Test
    public void testPushAndPop() {
        // LPUSH key val1 val2 -> size 2 (val2 is at head, val1 is at tail)
        String lpushResp = router.execute(Command.from(List.of("LPUSH", "mylist", "val1", "val2")), null);
        assertEquals(RespEncoder.encodeInteger(2), lpushResp);

        // RPUSH key val3 -> size 3 (val2, val1, val3)
        String rpushResp = router.execute(Command.from(List.of("RPUSH", "mylist", "val3")), null);
        assertEquals(RespEncoder.encodeInteger(3), rpushResp);

        // LPOP -> val2
        String lpopResp = router.execute(Command.from(List.of("LPOP", "mylist")), null);
        assertEquals(RespEncoder.encodeBulkString("val2"), lpopResp);

        // RPOP -> val3
        String rpopResp = router.execute(Command.from(List.of("RPOP", "mylist")), null);
        assertEquals(RespEncoder.encodeBulkString("val3"), rpopResp);

        // LPOP -> val1
        lpopResp = router.execute(Command.from(List.of("LPOP", "mylist")), null);
        assertEquals(RespEncoder.encodeBulkString("val1"), lpopResp);

        // LPOP empty list -> null
        lpopResp = router.execute(Command.from(List.of("LPOP", "mylist")), null);
        assertEquals(RespEncoder.NULL, lpopResp);
    }

    @Test
    public void testLRange() {
        router.execute(Command.from(List.of("RPUSH", "mylist", "a", "b", "c", "d")), null);

        // LRANGE mylist 0 -1 -> [a, b, c, d]
        String rangeResp = router.execute(Command.from(List.of("LRANGE", "mylist", "0", "-1")), null);
        assertEquals(RespEncoder.encodeStringArray(List.of("a", "b", "c", "d")), rangeResp);

        // LRANGE mylist 1 2 -> [b, c]
        rangeResp = router.execute(Command.from(List.of("LRANGE", "mylist", "1", "2")), null);
        assertEquals(RespEncoder.encodeStringArray(List.of("b", "c")), rangeResp);

        // LRANGE mylist -2 -1 -> [c, d]
        rangeResp = router.execute(Command.from(List.of("LRANGE", "mylist", "-2", "-1")), null);
        assertEquals(RespEncoder.encodeStringArray(List.of("c", "d")), rangeResp);

        // LRANGE mylist 2 100 (clamped) -> [c, d]
        rangeResp = router.execute(Command.from(List.of("LRANGE", "mylist", "2", "100")), null);
        assertEquals(RespEncoder.encodeStringArray(List.of("c", "d")), rangeResp);
    }
}
