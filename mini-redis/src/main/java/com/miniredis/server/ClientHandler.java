package com.miniredis.server;

import com.miniredis.command.Command;
import com.miniredis.command.CommandRouter;
import com.miniredis.persistence.PersistenceManager;
import com.miniredis.protocol.RespEncoder;
import com.miniredis.protocol.RespParser;
import com.miniredis.pubsub.PubSubManager;

import java.io.*;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.Selector;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.*;
import com.miniredis.protocol.NioRespParser;
import com.miniredis.security.AclUser;
import com.miniredis.security.TlsConnection;
import com.miniredis.replication.ReplicationManager;

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
    private final ReplicationManager replicationManager;

    private SocketChannel socketChannel;
    private Selector nioSelector;
    private final Queue<ByteBuffer> writeQueue = new java.util.concurrent.ConcurrentLinkedQueue<>();
    private final NioRespParser nioParser = new NioRespParser();

    private OutputStream outputStream;
    private boolean connected;
    private volatile long lastActivityTime;
    private AclUser authenticatedUser;
    private TlsConnection tlsConnection;
    private volatile boolean isReplica = false;

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

    public static boolean isWriteCommand(String name) {
        return WRITE_COMMANDS.contains(name.toUpperCase());
    }

    /** Commands allowed in subscriber mode */
    private static final Set<String> PUBSUB_ALLOWED_COMMANDS = Set.of(
            "SUBSCRIBE", "UNSUBSCRIBE", "PING"
    );

    public ClientHandler(Socket socket, CommandRouter commandRouter,
                          PubSubManager pubSubManager, PersistenceManager persistenceManager, ReplicationManager replicationManager) {
        this.socket = socket;
        this.commandRouter = commandRouter;
        this.pubSubManager = pubSubManager;
        this.persistenceManager = persistenceManager;
        this.replicationManager = replicationManager;
        this.connected = true;
        this.connectedAt = System.currentTimeMillis();
        this.lastActivityTime = System.currentTimeMillis();
        this.authenticatedUser = commandRouter != null ? commandRouter.getAclManager().getUser("default") : null;
        String address = "unknown";
        if (socket != null) {
            address = socket.getRemoteSocketAddress().toString();
            try {
                this.outputStream = socket.getOutputStream();
            } catch (IOException e) {
                // Ignore
            }
        }
        this.clientId = address;
    }

    public ClientHandler(SocketChannel socketChannel, Selector selector, CommandRouter commandRouter,
                         PubSubManager pubSubManager, PersistenceManager persistenceManager, ReplicationManager replicationManager) {
        this.socket = null;
        this.socketChannel = socketChannel;
        this.nioSelector = selector;
        this.commandRouter = commandRouter;
        this.pubSubManager = pubSubManager;
        this.persistenceManager = persistenceManager;
        this.replicationManager = replicationManager;
        this.connected = true;
        this.connectedAt = System.currentTimeMillis();
        this.lastActivityTime = System.currentTimeMillis();
        this.authenticatedUser = commandRouter != null ? commandRouter.getAclManager().getUser("default") : null;
        String address = "unknown";
        if (socketChannel != null) {
            try {
                address = socketChannel.getRemoteAddress().toString();
            } catch (IOException e) {
                // Ignore
            }
        }
        this.clientId = address;
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
    public String processCommand(Command command) {
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

        // Log write commands to AOF and propagate to replicas
        if (WRITE_COMMANDS.contains(command.name())) {
            if (persistenceManager != null && !isReplica) {
                persistenceManager.logCommand(command);
            }
            if (replicationManager != null && !isReplica) {
                replicationManager.propagateWrite(command);
            }
        }

        return response;
    }

    // ── Response Writing ──

    /**
     * Send a raw RESP2-encoded response to the client.
     * Thread-safe — used by pub/sub message delivery.
     */
    public synchronized void sendRawResponse(String response) throws IOException {
        if (!connected) return;
        if (socketChannel != null) {
            ByteBuffer data = ByteBuffer.wrap(response.getBytes("UTF-8"));
            if (tlsConnection != null) {
                data = tlsConnection.encrypt(data);
            }
            writeQueue.add(data);
            if (nioSelector != null) {
                SelectionKey key = socketChannel.keyFor(nioSelector);
                if (key != null && key.isValid()) {
                    key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);
                    nioSelector.wakeup();
                }
            }
        } else if (outputStream != null) {
            outputStream.write(response.getBytes("UTF-8"));
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

    public Queue<ByteBuffer> getWriteQueue() {
        return writeQueue;
    }

    public NioRespParser getNioParser() {
        return nioParser;
    }

    public void touchActivity() {
        this.lastActivityTime = System.currentTimeMillis();
    }

    public long getLastActivityTime() {
        return lastActivityTime;
    }

    public AclUser getAuthenticatedUser() {
        return authenticatedUser;
    }

    public void setAuthenticatedUser(AclUser authenticatedUser) {
        this.authenticatedUser = authenticatedUser;
    }

    public TlsConnection getTlsConnection() {
        return tlsConnection;
    }

    public void setTlsConnection(TlsConnection tlsConnection) {
        this.tlsConnection = tlsConnection;
    }

    public boolean isReplica() {
        return isReplica;
    }

    public void setReplica(boolean isReplica) {
        this.isReplica = isReplica;
    }

    // ── Cleanup ──

    public void cleanup() {
        connected = false;

        // Unsubscribe from all channels
        if (!subscriptions.isEmpty()) {
            pubSubManager.unsubscribeAll(this);
        }

        // Close socket or socket channel
        try {
            if (socket != null) {
                socket.close();
            }
            if (socketChannel != null) {
                socketChannel.close();
            }
        } catch (IOException e) {
            // Ignore close errors
        }

        System.out.println("[Client] Disconnected: " + clientId);
    }
}
