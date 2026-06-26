package com.miniredis.protocol;

import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class RespEncoderTest {

    @Test
    public void testEncodeSimpleString() {
        assertEquals("+OK\r\n", RespEncoder.encodeSimpleString("OK"));
    }

    @Test
    public void testEncodeError() {
        assertEquals("-ERR error message\r\n", RespEncoder.encodeError("ERR error message"));
    }

    @Test
    public void testEncodeInteger() {
        assertEquals(":1000\r\n", RespEncoder.encodeInteger(1000));
        assertEquals(":-5\r\n", RespEncoder.encodeInteger(-5));
    }

    @Test
    public void testEncodeBulkString() {
        assertEquals("$6\r\nfoobar\r\n", RespEncoder.encodeBulkString("foobar"));
        assertEquals("$-1\r\n", RespEncoder.encodeBulkString(null));
    }

    @Test
    public void testEncodeStringArray() {
        assertEquals("*2\r\n$3\r\nfoo\r\n$3\r\nbar\r\n", RespEncoder.encodeStringArray(List.of("foo", "bar")));
        assertEquals("*-1\r\n", RespEncoder.encodeStringArray(null));
    }

    @Test
    public void testEncodeArray() {
        String simple = RespEncoder.encodeSimpleString("OK");
        String integer = RespEncoder.encodeInteger(42);
        assertEquals("*2\r\n+OK\r\n:42\r\n", RespEncoder.encodeArray(List.of(simple, integer)));
    }
}
