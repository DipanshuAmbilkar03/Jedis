package com.miniredis.command;

import com.miniredis.server.ClientHandler;

/**
 * Interface for all Redis command handlers.
 * Each command (SET, GET, DEL, etc.) implements this interface.
 */
@FunctionalInterface
public interface CommandHandler {

    /**
     * Execute the command and return a RESP2-encoded response string.
     * 
     * @param command the parsed command with arguments
     * @param client  the client handler (for pub/sub and transaction state)
     * @return RESP2-encoded response string
     */
    String handle(Command command, ClientHandler client);
}
