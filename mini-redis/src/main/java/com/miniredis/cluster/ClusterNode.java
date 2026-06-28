package com.miniredis.cluster;

import java.util.BitSet;

/**
 * ClusterNode — represents a single database node inside a cluster configuration.
 */
public class ClusterNode {

    private final String nodeId;
    private final String host;
    private final int port;
    private final BitSet slots = new BitSet(16384);
    private volatile boolean alive = true;

    public ClusterNode(String nodeId, String host, int port) {
        this.nodeId = nodeId;
        this.host = host;
        this.port = port;
    }

    public String getNodeId() {
        return nodeId;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public BitSet getSlots() {
        return slots;
    }

    public boolean isAlive() {
        return alive;
    }

    public void setAlive(boolean alive) {
        this.alive = alive;
    }

    public void addSlot(int slot) {
        slots.set(slot);
    }

    public void removeSlot(int slot) {
        slots.clear(slot);
    }
}
