package com.miniredis.cluster;

import com.miniredis.config.ServerConfig;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ClusterManager — manages cluster topology, slot owners, and Gossip joins.
 */
public class ClusterManager {

    private final ServerConfig config;
    private final String selfNodeId;
    private final Map<String, ClusterNode> nodes = new ConcurrentHashMap<>();
    private final ClusterNode[] slotOwners = new ClusterNode[16384];

    public ClusterManager(ServerConfig config) {
        this.config = config;
        this.selfNodeId = UUID.randomUUID().toString().replace("-", "");
        
        // Register self node
        ClusterNode selfNode = new ClusterNode(selfNodeId, "127.0.0.1", config.getPort());
        nodes.put(selfNodeId, selfNode);
    }

    public boolean isClusterEnabled() {
        return config.isClusterEnabled();
    }

    public String getSelfNodeId() {
        return selfNodeId;
    }

    public ClusterNode getSelfNode() {
        return nodes.get(selfNodeId);
    }

    public List<ClusterNode> getNodes() {
        return new ArrayList<>(nodes.values());
    }

    public ClusterNode getNodeForSlot(int slot) {
        return slotOwners[slot];
    }

    /**
     * Checks if the slot is owned by this local node.
     * Unassigned slots default to local node ownership for easy single-node testing.
     */
    public boolean isLocalSlot(int slot) {
        ClusterNode owner = slotOwners[slot];
        return owner == null || owner.getNodeId().equals(selfNodeId);
    }

    /**
     * Add a node to cluster list.
     */
    public synchronized void meet(String host, int port) {
        // Prevent duplicate nodes
        for (ClusterNode node : nodes.values()) {
            if (node.getHost().equals(host) && node.getPort() == port) {
                return;
            }
        }

        String nodeId = UUID.randomUUID().toString().replace("-", "");
        ClusterNode newNode = new ClusterNode(nodeId, host, port);
        nodes.put(nodeId, newNode);
        System.out.println("[Cluster] Node met and added: " + nodeId + " (" + host + ":" + port + ")");
    }

    /**
     * Assign slot ranges to the local node.
     */
    public synchronized void addSlots(int... slots) {
        ClusterNode self = getSelfNode();
        for (int slot : slots) {
            if (slot >= 0 && slot < 16384) {
                self.addSlot(slot);
                slotOwners[slot] = self;
            }
        }
    }

    public synchronized void assignSlotToNode(int slot, String nodeId) {
        ClusterNode node = nodes.get(nodeId);
        if (node != null && slot >= 0 && slot < 16384) {
            ClusterNode oldOwner = slotOwners[slot];
            if (oldOwner != null) {
                oldOwner.removeSlot(slot);
            }
            node.addSlot(slot);
            slotOwners[slot] = node;
        }
    }
}
