package com.miniredis.replication;

import com.miniredis.command.Command;
import com.miniredis.command.CommandRouter;
import com.miniredis.config.ServerConfig;
import com.miniredis.persistence.PersistenceManager;
import com.miniredis.protocol.RespEncoder;
import com.miniredis.protocol.RespParser;
import com.miniredis.server.ClientHandler;
import com.miniredis.store.DataStore;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * ReplicationManager — orchestrates master-replica synchronization and replication stream propagation.
 */
public class ReplicationManager {

    private final ServerConfig config;
    private final String replId;
    private final ReplicationBacklog backlog;
    private final Set<ClientHandler> replicas = ConcurrentHashMap.newKeySet();
    private final ExecutorService replicaExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "mini-redis-replication-client");
        t.setDaemon(true);
        return t;
    });

    private volatile String masterHost;
    private volatile int masterPort = -1;
    private volatile boolean isReplica = false;
    private volatile long replicaOffset = 0;
    private Socket masterSocket;

    public ReplicationManager(ServerConfig config) {
        this.config = config;
        this.replId = UUID.randomUUID().toString().replace("-", "");
        this.backlog = new ReplicationBacklog((int) config.getReplBacklogSize());

        if (config.getReplicaOfHost() != null && config.getReplicaOfPort() != -1) {
            this.isReplica = true;
            this.masterHost = config.getReplicaOfHost();
            this.masterPort = config.getReplicaOfPort();
        }
    }

    public String getReplId() {
        return replId;
    }

    public long getMasterOffset() {
        return backlog.getMasterOffset();
    }

    public boolean isReplica() {
        return isReplica;
    }

    public String getMasterHost() {
        return masterHost;
    }

    public int getMasterPort() {
        return masterPort;
    }

    /**
     * Start the replica connection thread if running in replica mode.
     */
    public void start(DataStore dataStore, CommandRouter router, PersistenceManager pm) {
        if (isReplica) {
            dataStore.setReadOnly(true);
            replicaExecutor.submit(() -> runReplicaLoop(dataStore, router, pm));
        }
    }

    public void stop() {
        replicaExecutor.shutdownNow();
        try {
            if (masterSocket != null) {
                masterSocket.close();
            }
        } catch (IOException e) {
            // Ignore
        }
        for (ClientHandler replica : replicas) {
            replica.cleanup();
        }
        replicas.clear();
    }

    /**
     * Propagate a write command to all registered replicas and add to backlog.
     */
    public void propagateWrite(Command command) {
        String resp = serializeCommand(command);
        backlog.addCommand(resp);

        Iterator<ClientHandler> iter = replicas.iterator();
        while (iter.hasNext()) {
            ClientHandler replica = iter.next();
            if (!replica.isConnected()) {
                iter.remove();
                continue;
            }
            try {
                replica.sendRawResponse(resp);
            } catch (IOException e) {
                System.err.println("[Replication] Propagate failed for " + replica.getClientId() + ", disconnecting.");
                replica.cleanup();
                iter.remove();
            }
        }
    }

    /**
     * Process a PSYNC handshake request from a replica connection.
     */
    public synchronized void handlePsync(ClientHandler replica, String requestedReplId, long requestedOffset, PersistenceManager pm) {
        try {
            List<String> partialCommands = null;
            if (replId.equals(requestedReplId) && requestedOffset >= 0) {
                partialCommands = backlog.getCommandsFromOffset(requestedOffset);
            }

            if (partialCommands != null) {
                System.out.println("[Replication] Partial resync approved for replica: " + replica.getClientId() + " (Offset: " + requestedOffset + ")");
                replica.sendRawResponse("+CONTINUE\r\n");
                for (String cmd : partialCommands) {
                    replica.sendRawResponse(cmd);
                }
                replica.setReplica(true);
                replicas.add(replica);
            } else {
                System.out.println("[Replication] Full resync requested for replica: " + replica.getClientId());
                replica.sendRawResponse("+FULLRESYNC " + replId + " " + backlog.getMasterOffset() + "\r\n");

                // Trigger synchronous RDB save to generate current snapshot
                pm.getSnapshotter().save();
                File rdbFile = new File(pm.getSnapshotFile());
                if (!rdbFile.exists()) {
                    replica.sendRawResponse("-ERR RDB file not found\r\n");
                    replica.cleanup();
                    return;
                }

                // Send RDB contents as a bulk string payload
                long length = rdbFile.length();
                replica.sendRawResponse("$" + length + "\r\n");

                byte[] buffer = new byte[4096];
                try (FileInputStream fis = new FileInputStream(rdbFile);
                     OutputStream os = replica.getWriteQueue() != null ? new BufferedOutputStream(new OutputStream() {
                         @Override
                         public void write(int b) throws IOException {
                             // Not used
                         }
                         @Override
                         public void write(byte[] b, int off, int len) throws IOException {
                             replica.sendRawResponse(new String(b, off, len, StandardCharsets.ISO_8859_1));
                         }
                     }) : new BufferedOutputStream(replica.getWriteQueue() == null ? null : null)) {
                    
                    // Actually let's just write to the replica stream directly or push bytes to queue
                    int read;
                    if (replica.getWriteQueue() != null) {
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        while ((read = fis.read(buffer)) != -1) {
                            baos.write(buffer, 0, read);
                        }
                        replica.sendRawResponse(baos.toString(StandardCharsets.ISO_8859_1));
                    } else {
                        // Blocking socket mode - write directly to client socket
                        while ((read = fis.read(buffer)) != -1) {
                            replica.sendRawResponse(new String(buffer, 0, read, StandardCharsets.ISO_8859_1));
                        }
                    }
                }
                System.out.println("[Replication] Full RDB snapshot transfer complete.");
                replica.setReplica(true);
                replicas.add(replica);
            }
        } catch (IOException e) {
            System.err.println("[Replication] PSYNC synchronization failed: " + e.getMessage());
            replica.cleanup();
        }
    }

    /**
     * Change replication state to replica or master.
     */
    public synchronized void setReplicaOf(String host, int port, DataStore dataStore, CommandRouter router, PersistenceManager pm) {
        if (host == null || port == -1) {
            // REPLICAOF NO ONE
            isReplica = false;
            masterHost = null;
            masterPort = -1;
            dataStore.setReadOnly(false);
            try {
                if (masterSocket != null) {
                    masterSocket.close();
                }
            } catch (IOException e) {
                // Ignore
            }
            System.out.println("[Replication] promoted to MASTER.");
        } else {
            isReplica = true;
            masterHost = host;
            masterPort = port;
            dataStore.setReadOnly(true);
            replicaExecutor.submit(() -> runReplicaLoop(dataStore, router, pm));
            System.out.println("[Replication] configured as REPLICA of " + host + ":" + port);
        }
    }

    private void runReplicaLoop(DataStore dataStore, CommandRouter router, PersistenceManager pm) {
        while (isReplica) {
            try {
                System.out.println("[Replication] Connecting to master at " + masterHost + ":" + masterPort + "...");
                masterSocket = new Socket(masterHost, masterPort);
                InputStream is = masterSocket.getInputStream();
                OutputStream os = masterSocket.getOutputStream();

                // 1. PING handshake
                os.write("*1\r\n$4\r\nPING\r\n".getBytes(StandardCharsets.UTF_8));
                os.flush();

                BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
                String pingResponse = reader.readLine();
                if (pingResponse == null || !pingResponse.contains("PONG")) {
                    throw new IOException("Failed PING handshake response: " + pingResponse);
                }

                // 2. PSYNC command
                String psyncCmd = "*3\r\n$5\r\nPSYNC\r\n$1\r\n?\r\n$2\r\n-1\r\n"; // Full sync initially
                os.write(psyncCmd.getBytes(StandardCharsets.UTF_8));
                os.flush();

                String psyncResponse = reader.readLine();
                if (psyncResponse == null) {
                    throw new IOException("Empty PSYNC response.");
                }

                if (psyncResponse.startsWith("+FULLRESYNC")) {
                    // Read RDB snapshot size
                    String sizeLine = reader.readLine();
                    if (sizeLine == null || !sizeLine.startsWith("$")) {
                        throw new IOException("Invalid full resync size line: " + sizeLine);
                    }
                    long size = Long.parseLong(sizeLine.substring(1));
                    System.out.println("[Replication] Receiving full sync RDB of size: " + size + " bytes");

                    // Read RDB payload from stream
                    byte[] rdbData = new byte[(int) size];
                    int totalRead = 0;
                    while (totalRead < size) {
                        int read = is.read(rdbData, totalRead, (int) (size - totalRead));
                        if (read == -1) {
                            throw new IOException("EOF during RDB sync transfer.");
                        }
                        totalRead += read;
                    }

                    // Save snapshot and load it
                    File rdbFile = new File(pm.getSnapshotFile());
                    try (FileOutputStream fos = new FileOutputStream(rdbFile)) {
                        fos.write(rdbData);
                    }
                    dataStore.flushAll();
                    pm.getSnapshotter().load();
                    System.out.println("[Replication] Loaded RDB snapshot successfully.");
                }

                // 3. Command Streaming loop
                // We create a dummy ClientHandler with isReplica = true to bypass readOnly checks
                ClientHandler replicaClient = new ClientHandler(masterSocket, router, null, null, this);
                replicaClient.setReplica(true);

                RespParser streamParser = new RespParser(is);
                while (isReplica) {
                    List<String> rawCmd = streamParser.parseCommand();
                    if (rawCmd == null) {
                        break; // EOF
                    }
                    if (rawCmd.isEmpty()) {
                        continue;
                    }

                    Command cmd = Command.from(rawCmd);
                    // Force routing execution on replica dataStore bypassing read-only limits
                    router.execute(cmd, replicaClient);
                }

            } catch (Exception e) {
                System.err.println("[Replication] Error in replication connection: " + e.getMessage());
                try {
                    Thread.sleep(5000); // Wait 5s before reconnecting
                } catch (InterruptedException ex) {
                    break;
                }
            }
        }
    }

    private static String serializeCommand(Command cmd) {
        StringBuilder sb = new StringBuilder();
        sb.append("*").append(cmd.argCount()).append("\r\n");
        for (int i = 0; i < cmd.argCount(); i++) {
            String arg = cmd.getArg(i);
            sb.append("$").append(arg.getBytes(StandardCharsets.UTF_8).length).append("\r\n");
            sb.append(arg).append("\r\n");
        }
        return sb.toString();
    }
}
