package com.miniredis.security;

import javax.net.ssl.*;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

/**
 * TlsConnection — manages SSLEngine handshake and encrypt/decrypt states.
 * Temporarily uses blocking mode during handshake to ensure simplicity and correctness.
 */
public class TlsConnection {

    private final SSLEngine engine;
    private final ByteBuffer peerNetData;
    private final ByteBuffer peerAppData;
    private final ByteBuffer myNetData;

    public TlsConnection(SSLContext sslContext) {
        this.engine = sslContext.createSSLEngine();
        this.engine.setUseClientMode(false);
        this.engine.setNeedClientAuth(false);

        SSLSession session = engine.getSession();
        this.peerNetData = ByteBuffer.allocate(session.getPacketBufferSize());
        this.peerAppData = ByteBuffer.allocate(session.getApplicationBufferSize());
        this.myNetData = ByteBuffer.allocate(session.getPacketBufferSize());
    }

    /**
     * Perform SSL/TLS handshake synchronously on connection accept.
     */
    public synchronized void handshake(SocketChannel channel) throws IOException {
        boolean oldBlocking = channel.isBlocking();
        channel.configureBlocking(true);
        try {
            engine.beginHandshake();
            SSLEngineResult.HandshakeStatus status = engine.getHandshakeStatus();
            ByteBuffer empty = ByteBuffer.allocate(0);

            while (status != SSLEngineResult.HandshakeStatus.FINISHED && status != SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING) {
                switch (status) {
                    case NEED_WRAP -> {
                        myNetData.clear();
                        SSLEngineResult res = engine.wrap(empty, myNetData);
                        status = res.getHandshakeStatus();
                        myNetData.flip();
                        while (myNetData.hasRemaining()) {
                            channel.write(myNetData);
                        }
                    }
                    case NEED_UNWRAP -> {
                        if (peerNetData.position() == 0 || status == SSLEngineResult.HandshakeStatus.NEED_UNWRAP) {
                            int read = channel.read(peerNetData);
                            if (read < 0) {
                                throw new IOException("TLS Handshake EOF reached prematurely.");
                            }
                        }
                        peerNetData.flip();
                        peerAppData.clear();
                        SSLEngineResult res = engine.unwrap(peerNetData, peerAppData);
                        status = res.getHandshakeStatus();
                        peerNetData.compact();
                    }
                    case NEED_TASK -> {
                        Runnable task;
                        while ((task = engine.getDelegatedTask()) != null) {
                            task.run();
                        }
                        status = engine.getHandshakeStatus();
                    }
                    default -> throw new IllegalStateException("Unexpected TLS Handshake status: " + status);
                }
            }
        } finally {
            channel.configureBlocking(oldBlocking);
        }
    }

    /**
     * Decrypt incoming network bytes.
     */
    public synchronized ByteBuffer decrypt(ByteBuffer readInput) throws IOException {
        peerAppData.clear();
        SSLEngineResult res = engine.unwrap(readInput, peerAppData);
        if (res.getStatus() == SSLEngineResult.Status.BUFFER_UNDERFLOW) {
            return null; // Needs more bytes to decrypt
        }
        peerAppData.flip();
        // Return a copy since peerAppData will be reused
        byte[] copy = new byte[peerAppData.remaining()];
        peerAppData.get(copy);
        return ByteBuffer.wrap(copy);
    }

    /**
     * Encrypt outgoing application bytes.
     */
    public synchronized ByteBuffer encrypt(ByteBuffer writeInput) throws IOException {
        myNetData.clear();
        engine.wrap(writeInput, myNetData);
        myNetData.flip();
        byte[] copy = new byte[myNetData.remaining()];
        myNetData.get(copy);
        return ByteBuffer.wrap(copy);
    }
}
