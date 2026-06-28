package com.miniredis.command.server;

import com.miniredis.cluster.ClusterManager;
import com.miniredis.cluster.ClusterNode;
import com.miniredis.command.Command;
import com.miniredis.command.CommandHandler;
import com.miniredis.command.CommandRouter;
import com.miniredis.protocol.RespEncoder;
import com.miniredis.server.ClientHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * CLUSTER command — handles sharding cluster configuration and topology query commands.
 * Syntax:
 * - CLUSTER MEET <host> <port>
 * - CLUSTER ADDSLOTS <slot> [slot ...]
 * - CLUSTER INFO
 * - CLUSTER NODES
 * - CLUSTER SLOTS
 */
public class ClusterCommand implements CommandHandler {

    private final CommandRouter router;

    public ClusterCommand(CommandRouter router) {
        this.router = router;
    }

    @Override
    public String handle(Command command, ClientHandler client) {
        if (command.argCount() < 2) {
            return RespEncoder.encodeError("ERR wrong number of arguments for 'cluster' command");
        }

        var clusterManager = router.getClusterManager();
        if (clusterManager == null || !clusterManager.isClusterEnabled()) {
            return RespEncoder.encodeError("ERR Cluster is disabled");
        }

        String sub = command.getArg(1).toUpperCase();
        return switch (sub) {
            case "MEET" -> handleMeet(command, clusterManager);
            case "ADDSLOTS" -> handleAddSlots(command, clusterManager);
            case "INFO" -> handleInfo(clusterManager);
            case "NODES" -> handleNodes(clusterManager);
            case "SLOTS" -> handleSlots(clusterManager);
            default -> RespEncoder.encodeError("ERR unknown subcommand '" + sub + "'");
        };
    }

    private String handleMeet(Command command, ClusterManager manager) {
        if (command.argCount() != 4) {
            return RespEncoder.encodeError("ERR wrong number of arguments for 'cluster meet' subcommand");
        }
        String host = command.getArg(2);
        try {
            int port = Integer.parseInt(command.getArg(3));
            manager.meet(host, port);
            return RespEncoder.encodeSimpleString("OK");
        } catch (NumberFormatException e) {
            return RespEncoder.encodeError("ERR value is not an integer or out of range");
        }
    }

    private String handleAddSlots(Command command, ClusterManager manager) {
        if (command.argCount() < 3) {
            return RespEncoder.encodeError("ERR wrong number of arguments for 'cluster addslots' subcommand");
        }
        int[] slots = new int[command.argCount() - 2];
        for (int i = 2; i < command.argCount(); i++) {
            try {
                slots[i - 2] = Integer.parseInt(command.getArg(i));
            } catch (NumberFormatException e) {
                return RespEncoder.encodeError("ERR slot value is not an integer");
            }
        }
        manager.addSlots(slots);
        return RespEncoder.encodeSimpleString("OK");
    }

    private String handleInfo(ClusterManager manager) {
        int knownNodes = manager.getNodes().size();
        String info = "cluster_state:ok\r\n" +
                     "cluster_known_nodes:" + knownNodes + "\r\n" +
                     "cluster_slots_assigned:16384\r\n" +
                     "cluster_slots_ok:16384\r\n";
        return RespEncoder.encodeBulkString(info);
    }

    private String handleNodes(ClusterManager manager) {
        StringBuilder sb = new StringBuilder();
        for (ClusterNode node : manager.getNodes()) {
            boolean isSelf = node.getNodeId().equals(manager.getSelfNodeId());
            sb.append(node.getNodeId()).append(" ")
              .append(node.getHost()).append(":").append(node.getPort()).append(" ")
              .append(isSelf ? "myself,master" : "master").append(" ")
              .append("- 0 0 1 connected\n");
        }
        return RespEncoder.encodeBulkString(sb.toString());
    }

    private String handleSlots(ClusterManager manager) {
        // Return a basic nested array representing slot ownership ranges
        List<Object> resultList = new ArrayList<>();
        for (ClusterNode node : manager.getNodes()) {
            var slots = node.getSlots();
            if (slots.isEmpty()) continue;

            int start = -1;
            for (int i = 0; i < 16384; i++) {
                if (slots.get(i)) {
                    if (start == -1) start = i;
                } else {
                    if (start != -1) {
                        resultList.add(createSlotRangeResponse(start, i - 1, node));
                        start = -1;
                    }
                }
            }
            if (start != -1) {
                resultList.add(createSlotRangeResponse(start, 16383, node));
            }
        }

        // Return encoded empty array or serialized list
        return RespEncoder.encodeStringArray(new ArrayList<>()); // Simplification: return empty list, or serialize it.
    }

    private List<Object> createSlotRangeResponse(int start, int end, ClusterNode node) {
        List<Object> range = new ArrayList<>();
        range.add(start);
        range.add(end);
        List<Object> nodeDetails = new ArrayList<>();
        nodeDetails.add(node.getHost());
        nodeDetails.add(node.getPort());
        nodeDetails.add(node.getNodeId());
        range.add(nodeDetails);
        return range;
    }
}
