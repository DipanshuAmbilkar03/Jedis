package com.miniredis.protocol;

import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class RespParserTest {

    private RespParser createParser(String input) {
        return new RespParser(new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    public void testParseSimpleString() throws IOException {
        RespParser parser = createParser("+OK\r\n");
        assertEquals("OK", parser.parse());
    }

    @Test
    public void testParseError() throws IOException {
        RespParser parser = createParser("-ERR error message\r\n");
        assertEquals("ERR:ERR error message", parser.parse());
    }

    @Test
    public void testParseInteger() throws IOException {
        RespParser parser = createParser(":1000\r\n");
        assertEquals(1000L, parser.parse());

        parser = createParser(":-5\r\n");
        assertEquals(-5L, parser.parse());
    }

    @Test
    public void testParseBulkString() throws IOException {
        RespParser parser = createParser("$6\r\nfoobar\r\n");
        assertEquals("foobar", parser.parse());

        parser = createParser("$-1\r\n");
        assertNull(parser.parse());
    }

    @Test
    public void testParseArray() throws IOException {
        RespParser parser = createParser("*2\r\n$3\r\nfoo\r\n$3\r\nbar\r\n");
        Object result = parser.parse();
        assertTrue(result instanceof List<?>);
        List<?> list = (List<?>) result;
        assertEquals(2, list.size());
        assertEquals("foo", list.get(0));
        assertEquals("bar", list.get(1));
    }

    @Test
    public void testParseCommand() throws IOException {
        RespParser parser = createParser("*3\r\n$3\r\nSET\r\n$3\r\nkey\r\n$5\r\nvalue\r\n");
        List<String> command = parser.parseCommand();
        assertNotNull(command);
        assertEquals(3, command.size());
        assertEquals("SET", command.get(0));
        assertEquals("key", command.get(1));
        assertEquals("value", command.get(2));
    }

    @Test
    public void testParseInlineCommand() throws IOException {
        RespParser parser = createParser("PING hello\r\n");
        List<String> command = parser.parseCommand();
        assertNotNull(command);
        assertEquals(2, command.size());
        assertEquals("PING", command.get(0));
        assertEquals("hello", command.get(1));
    }
}
