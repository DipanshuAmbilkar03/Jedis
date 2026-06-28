package com.miniredis.server;

import com.miniredis.command.Command;
import com.miniredis.command.CommandRouter;
import com.miniredis.config.ServerConfig;
import com.miniredis.persistence.PersistenceManager;
import com.miniredis.pubsub.PubSubManager;
import com.miniredis.security.TlsConfig;
import com.miniredis.security.TlsConnection;
import com.miniredis.replication.ReplicationManager;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;
import java.util.Set;

/**
 * NioEventLoop — Java NIO selector event loop for handling thousands of connections.
 * Runs on a single thread and manages non-blocking socket I/O.
 */
public class NioEventLoop {

    private final ServerConfig config;
    private final CommandRouter commandRouter;
    private final PubSubManager pubSubManager;
    private final PersistenceManager persistenceManager;
    private final ConnectionManager connectionManager;
    private final ReplicationManager replicationManager;

    private Selector selector;
    private ServerSocketChannel serverChannel;
    private volatile boolean running;
    private SSLContext sslContext;

    public NioEventLoop(ServerConfig config, CommandRouter commandRouter, PubSubManager pubSubManager, PersistenceManager persistenceManager, ConnectionManager connectionManager, ReplicationManager replicationManager) {
        this.config = config;
        this.commandRouter = commandRouter;
        this.pubSubManager = pubSubManager;
        this.persistenceManager = persistenceManager;
        this.connectionManager = connectionManager;
        this.replicationManager = replicationManager;
    }

    /**
     * Start the NIO selector loop on the main thread (blocking).
     */
    public void start() throws IOException {
        if (config.isTlsEnabled()) {
            try {
                this.sslContext = TlsConfig.createSSLContext(config);
            } catch (Exception e) {
                throw new IOException("Failed to initialize SSLContext: " + e.getMessage(), e);
            }
        }

        selector = Selector.open();
        serverChannel = ServerSocketChannel.open();
        serverChannel.bind(new InetSocketAddress(config.getPort()));
        serverChannel.configureBlocking(false);
        serverChannel.register(selector, SelectionKey.OP_ACCEPT);

        running = true;
        printBanner();

        ByteBuffer readBuffer = ByteBuffer.allocateDirect(4096);

        while (running) {
            try {
                int select = selector.select(1000); // 1s timeout
                if (select == 0) {
                    continue;
                }

                Set<SelectionKey> selectedKeys = selector.selectedKeys();
                Iterator<SelectionKey> iter = selectedKeys.iterator();

                while (iter.hasNext()) {
                    SelectionKey key = iter.next();
                    iter.remove();

                    if (!key.isValid()) {
                        continue;
                    }

                    if (key.isAcceptable()) {
                        handleAccept();
                    } else if (key.isReadable()) {
                        handleRead(key, readBuffer);
                    } else if (key.isWritable()) {
                        handleWrite(key);
                    }
                }
            } catch (IOException e) {
                if (running) {
                    System.err.println("[NIO] Error in event loop: " + e.getMessage());
                }
            }
        }
    }

    /**
     * Stop the event loop and close channels.
     */
    public void stop() {
        running = false;
        System.out.println("[NIO] Shutting down event loop...");
        if (selector != null) {
            try {
                for (SelectionKey key : selector.keys()) {
                    try {
                        key.channel().close();
                    } catch (IOException e) {
                        // Ignore
                    }
                }
                selector.close();
            } catch (IOException e) {
                System.err.println("[NIO] Error closing selector: " + e.getMessage());
            }
        }
        System.out.println("[NIO] Event loop shutdown complete.");
    }

    private void handleAccept() {
        try {
            SocketChannel clientChannel = serverChannel.accept();
            if (clientChannel != null) {
                clientChannel.configureBlocking(false);
                ClientHandler client = new ClientHandler(
                        clientChannel, selector, commandRouter, pubSubManager, persistenceManager, replicationManager
                );
                if (connectionManager.acceptConnection(client)) {
                    if (config.isTlsEnabled() && sslContext != null) {
                        try {
                            TlsConnection tls = new TlsConnection(sslContext);
                            tls.handshake(clientChannel);
                            client.setTlsConnection(tls);
                        } catch (IOException e) {
                            System.err.println("[NIO] TLS Handshake failed: " + e.getMessage());
                            client.cleanup();
                            return;
                        }
                    }
                    clientChannel.register(selector, SelectionKey.OP_READ, client);
                    System.out.println("[NIO Client] Connected: " + client.getClientId());
                } else {
                    ByteBuffer rejectResponse = ByteBuffer.wrap("-ERR max number of clients reached\r\n".getBytes("UTF-8"));
                    if (config.isTlsEnabled() && sslContext != null) {
                        try {
                            TlsConnection tls = new TlsConnection(sslContext);
                            tls.handshake(clientChannel);
                            ByteBuffer encryptedReject = tls.encrypt(rejectResponse);
                            clientChannel.write(encryptedReject);
                        } catch (Exception e) {
                            // Ignore
                        }
                    } else {
                        clientChannel.write(rejectResponse);
                    }
                    client.cleanup();
                }
            }
        } catch (IOException e) {
            System.err.println("[NIO] Failed to accept connection: " + e.getMessage());
        }
    }

    private void handleRead(SelectionKey key, ByteBuffer readBuffer) {
        SocketChannel channel = (SocketChannel) key.channel();
        ClientHandler client = (ClientHandler) key.attachment();

        readBuffer.clear();
        int bytesRead;
        try {
            bytesRead = channel.read(readBuffer);
        } catch (IOException e) {
            System.err.println("[NIO Client] Read error: " + e.getMessage());
            client.cleanup();
            key.cancel();
            return;
        }

        client.touchActivity();

        if (bytesRead == -1) {
            client.cleanup();
            key.cancel();
            return;
        }

        readBuffer.flip();

        ByteBuffer dataToParse = readBuffer;
        if (client.getTlsConnection() != null) {
            try {
                dataToParse = client.getTlsConnection().decrypt(readBuffer);
                if (dataToParse == null) {
                    return; // Incomplete encrypted packet
                }
            } catch (IOException e) {
                System.err.println("[NIO Client] TLS Decryption error: " + e.getMessage());
                client.cleanup();
                key.cancel();
                return;
            }
        }

        // Feed bytes to the non-blocking incremental RESP parser
        var parser = client.getNioParser();
        while (dataToParse.hasRemaining()) {
            List<String> rawCmd = parser.parseNextCommand(dataToParse);
            if (rawCmd == null) {
                break; // Command is incomplete, wait for more data
            }
            if (rawCmd.isEmpty()) {
                continue;
            }

            try {
                Command command = Command.from(rawCmd);
                String response = client.processCommand(command);
                if (response != null) {
                    client.sendRawResponse(response);
                }
            } catch (Exception e) {
                System.err.println("[NIO Client] Error processing command: " + e.getMessage());
                try {
                    client.sendRawResponse("-ERR internal server error\r\n");
                } catch (IOException ioEx) {
                    // Ignore
                }
            }
        }
    }

    private void handleWrite(SelectionKey key) {
        SocketChannel channel = (SocketChannel) key.channel();
        ClientHandler client = (ClientHandler) key.attachment();
        Queue<ByteBuffer> queue = client.getWriteQueue();

        client.touchActivity();
        try {
            while (true) {
                ByteBuffer buf = queue.peek();
                if (buf == null) {
                    // Drained write queue, remove write interest
                    key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);
                    break;
                }

                channel.write(buf);
                if (buf.hasRemaining()) {
                    // OS buffer full, wait for next OP_WRITE trigger
                    break;
                }
                queue.poll(); // Finished writing this buffer
            }
        } catch (IOException e) {
            System.err.println("[NIO Client] Write error: " + e.getMessage());
            client.cleanup();
            key.cancel();
        }
    }

    private void printBanner() {
        System.out.println("""
                
                ╔══════════════════════════════════════════╗
                ║       ⚡ MINI REDIS NIO EVENT LOOP       ║
                ║    High-Speed Non-Blocking I/O           ║
                ║                                          ║
                ║    Port: %d                              ║
                ║    PID:  %d                         ║
                ║    Ready to scale to 100k+ clients       ║
                ╚══════════════════════════════════════════╝
                """.formatted(config.getPort(), ProcessHandle.current().pid()));
    }
}
