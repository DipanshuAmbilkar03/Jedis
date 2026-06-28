package com.miniredis.protocol;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * NioRespParser — incremental, non-blocking RESP2 protocol parser.
 * Handles fragmented packets by maintaining state between reads.
 */
public class NioRespParser {

    private enum State {
        READING_COMMAND_START,
        READING_ARRAY_SIZE,
        READING_ARG_START,
        READING_BULK_LENGTH,
        READING_BULK_DATA,
        READING_BULK_LF,
        READING_INLINE
    }

    private State state = State.READING_COMMAND_START;
    private int arrayCount = -1;
    private int currentArgIndex = 0;
    private final List<String> args = new ArrayList<>();

    private int bulkLength = -1;
    private byte[] bulkBuffer;
    private int bulkBufferPos = 0;

    private final StringBuilder lineBuffer = new StringBuilder();

    /**
     * Consume bytes from the buffer and try to parse a complete command.
     * Returns the list of parsed arguments, or null if the command is incomplete.
     */
    public List<String> parseNextCommand(ByteBuffer buffer) {
        while (buffer.hasRemaining()) {
            byte b = buffer.get();
            char c = (char) b;

            switch (state) {
                case READING_COMMAND_START -> {
                    if (c == '*') {
                        state = State.READING_ARRAY_SIZE;
                        lineBuffer.setLength(0);
                        args.clear();
                    } else if (c != '\r' && c != '\n' && c != ' ') {
                        state = State.READING_INLINE;
                        lineBuffer.setLength(0);
                        lineBuffer.append(c);
                    }
                }
                case READING_ARRAY_SIZE -> {
                    if (c == '\n') {
                        try {
                            arrayCount = Integer.parseInt(lineBuffer.toString().trim());
                            if (arrayCount <= 0) {
                                state = State.READING_COMMAND_START;
                            } else {
                                state = State.READING_ARG_START;
                                currentArgIndex = 0;
                            }
                        } catch (NumberFormatException e) {
                            state = State.READING_COMMAND_START;
                        }
                    } else if (c != '\r') {
                        lineBuffer.append(c);
                    }
                }
                case READING_ARG_START -> {
                    if (c == '$') {
                        state = State.READING_BULK_LENGTH;
                        lineBuffer.setLength(0);
                    } else if (c != '\r' && c != '\n') {
                        state = State.READING_COMMAND_START;
                    }
                }
                case READING_BULK_LENGTH -> {
                    if (c == '\n') {
                        try {
                            bulkLength = Integer.parseInt(lineBuffer.toString().trim());
                            if (bulkLength == -1) {
                                args.add(null);
                                checkCommandCompletion();
                            } else {
                                bulkBuffer = new byte[bulkLength];
                                bulkBufferPos = 0;
                                if (bulkLength == 0) {
                                    state = State.READING_BULK_LF;
                                } else {
                                    state = State.READING_BULK_DATA;
                                }
                            }
                        } catch (NumberFormatException e) {
                            state = State.READING_COMMAND_START;
                        }
                    } else if (c != '\r') {
                        lineBuffer.append(c);
                    }
                }
                case READING_BULK_DATA -> {
                    bulkBuffer[bulkBufferPos++] = b;
                    if (bulkBufferPos == bulkLength) {
                        args.add(new String(bulkBuffer, StandardCharsets.UTF_8));
                        state = State.READING_BULK_LF;
                    }
                }
                case READING_BULK_LF -> {
                    if (c == '\n') {
                        checkCommandCompletion();
                    }
                }
                case READING_INLINE -> {
                    if (c == '\n') {
                        String inlineStr = lineBuffer.toString().trim();
                        List<String> commandArgs = new ArrayList<>();
                        for (String part : inlineStr.split("\\s+")) {
                            if (!part.isEmpty()) {
                                commandArgs.add(part);
                            }
                        }
                        state = State.READING_COMMAND_START;
                        if (!commandArgs.isEmpty()) {
                            return commandArgs;
                        }
                    } else if (c != '\r') {
                        lineBuffer.append(c);
                    }
                }
            }

            if (state == State.READING_COMMAND_START && !args.isEmpty() && currentArgIndex == arrayCount) {
                List<String> completedCommand = new ArrayList<>(args);
                args.clear();
                return completedCommand;
            }
        }
        return null;
    }

    private void checkCommandCompletion() {
        currentArgIndex++;
        if (currentArgIndex == arrayCount) {
            state = State.READING_COMMAND_START;
        } else {
            state = State.READING_ARG_START;
        }
    }
}
