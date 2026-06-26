package com.miniredis.server;

import com.miniredis.command.Command;
import com.miniredis.command.CommandRouter;
import com.miniredis.persistence.PersistenceManager;
import com.miniredis.protocol.RespEncoder;
import com.miniredis.protocol.RespParser;
import com.miniredis.pubsub.PubSubManager;

import java.io.*;
import java.net.Socket;
import java.util.*;

/**
 * Handles a single client connection in its own thread.
 * 
 * Manages the read/write loop, transaction state, and pub/sub subscriptions.
 */
public class ClientHandler implements Runnable {

    private final Socket socket;
    private final CommandRouter commandRouter;
    private final PubSubManager pubSubManager;
    private final PersistenceManager persistenceManager;

    private OutputStream outputStream;
    private boolean connected;

    // ── Transaction State ──
    private boolean inTransaction = false;
    private List<Command> transactionQueue = new ArrayList<>();
    private boolean transactionAborted = false;

    // ── Pub/Sub State ──
    private final Set<String> subscriptions = Collections.synchronizedSet(new LinkedHashSet<>());

    // ── Connection Tracking ──
    private final long connectedAt;
    private final String clientId;

    /** Set of commands that are write operations (for AOF logging) */
    private static final Set<String> WRITE_COMMANDS = Set.of(
            "SET", "MSET", "INCR", "DECR", "DEL", "EXPIRE",
            "LPUSH", "RPUSH", "LPOP", "RPOP",
            "SADD", "SREM",
            "HSET",
            "FLUSHDB"
    );

    /** Commands allowed in subscriber mode */
    private static final Set<String> PUBSUB_ALLOWED_COMMANDS = Set.of(
            "SUBSCRIBE", "UNSUBSCRIBE", "PING"
    );

    public ClientHandler(Socket socket, CommandRouter commandRouter,
                          PubSubManager pubSubManager, PersistenceManager persistenceManager) {
        this.socket = socket;
        this.commandRouter = commandRouter;
        this.pubSubManager = pubSubManager;
        this.persistenceManager = persistenceManager;
        this.connected = true;
        this.connectedAt = System.currentTimeMillis();
        this.clientId = socket.getRemoteSocketAddress().toString();
    }

    @Override
    public void run() {
        try {
            outputStream = socket.getOutputStream();
            RespParser parser = new RespParser(socket.getInputStream());

            System.out.println("[Client] Connected: " + clientId);

            while (connected) {
                List<String> rawCommand = parser.parseCommand();
                if (rawCommand == null) {
                    break; // Client disconnected
                }

                if (rawCommand.isEmpty()) {
                    continue;
                }

                Command command = Command.from(rawCommand);
                String response = processCommand(command);

                if (response != null) {
                    sendRawResponse(response);
                }
            }
        } catch (IOException e) {
            // Client disconnected unexpectedly
            if (connected) {
                System.err.println("[Client] Disconnected unexpectedly: " + clientId + " — " + e.getMessage());
            }
        } finally {
            cleanup();
        }
    }

    /**
     * Process a command considering transaction and pub/sub state.
     */
    private String processCommand(Command command) {
        // ── Pub/Sub Mode Check ──
        if (isInSubscriberMode() && !PUBSUB_ALLOWED_COMMANDS.contains(command.name())) {
            return RespEncoder.encodeError(
                    "ERR Can't execute '" + command.name().toLowerCase() +
                    "': only SUBSCRIBE, UNSUBSCRIBE, and PING are allowed in this context"
            );
        }

        // ── Transaction Mode ──
        if (inTransaction) {
            // EXEC, DISCARD, and MULTI are handled directly
            if ("EXEC".equals(command.name()) || "DISCARD".equals(command.name()) || "MULTI".equals(command.name())) {
                return commandRouter.execute(command, this);
            }

            // Check if command is valid
            if (!commandRouter.isKnownCommand(command.name())) {
                transactionAborted = true;
                return RespEncoder.encodeError("ERR unknown command '" + command.name().toLowerCase() + "'");
            }

            // Queue the command
            transactionQueue.add(command);
            return RespEncoder.QUEUED;
        }

        // ── Normal Execution ──
        String response = commandRouter.execute(command, this);

        // Log write commands to AOF
        if (WRITE_COMMANDS.contains(command.name()) && persistenceManager != null) {
            persistenceManager.logCommand(command);
        }

        return response;
    }

    // ── Response Writing ──

    /**
     * Send a raw RESP2-encoded response to the client.
     * Thread-safe — used by pub/sub message delivery.
     */
    public synchronized void sendRawResponse(String response) throws IOException {
        if (outputStream != null && connected) {
            outputStream.write(response.getBytes());
            outputStream.flush();
        }
    }

    // ── Transaction Methods ──

    public boolean isInTransaction() {
        return inTransaction;
    }

    public void startTransaction() {
        inTransaction = true;
        transactionQueue = new ArrayList<>();
        transactionAborted = false;
    }

    public List<Command> getTransactionQueue() {
        return transactionQueue;
    }

    public boolean isTransactionAborted() {
        return transactionAborted;
    }

    public void endTransaction() {
        inTransaction = false;
        transactionQueue = new ArrayList<>();
        transactionAborted = false;
    }

    // ── Pub/Sub Methods ──

    public boolean isInSubscriberMode() {
        return !subscriptions.isEmpty();
    }

    public void addSubscription(String channel) {
        subscriptions.add(channel);
    }

    public void removeSubscription(String channel) {
        subscriptions.remove(channel);
    }

    public Set<String> getSubscriptions() {
        return subscriptions;
    }

    public int getSubscriptionCount() {
        return subscriptions.size();
    }

    // ── Connection Info ──

    public String getClientId() {
        return clientId;
    }

    public long getConnectedAt() {
        return connectedAt;
    }

    public boolean isConnected() {
        return connected;
    }

    // ── Cleanup ──

    private void cleanup() {
        connected = false;

        // Unsubscribe from all channels
        if (!subscriptions.isEmpty()) {
            pubSubManager.unsubscribeAll(this);
        }

        // Close the socket
        try {
            socket.close();
        } catch (IOException e) {
            // Ignore close errors
        }

        System.out.println("[Client] Disconnected: " + clientId);
    }
}
