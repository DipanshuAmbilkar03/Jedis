package com.miniredis.cluster;

import com.miniredis.command.Command;
import com.miniredis.command.CommandRouter;
import com.miniredis.config.ServerConfig;
import com.miniredis.persistence.PersistenceManager;
import com.miniredis.pubsub.PubSubManager;
import com.miniredis.store.DataStore;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ClusterTest {

    @Test
    public void testCrc16HashedSlots() {
        int slot1 = CRC16.getSlot("user123");
        int slot2 = CRC16.getSlot("user{123}");
        int slot3 = CRC16.getSlot("user{123}profile");
        int slot4 = CRC16.getSlot("{123}");

        assertTrue(slot1 >= 0 && slot1 < 16384);
        assertTrue(slot2 >= 0 && slot2 < 16384);
        assertEquals(slot2, slot3);
        assertEquals(slot3, slot4);
    }

    @Test
    public void testClusterManagerSlotOwnership() {
        ServerConfig config = new ServerConfig();
        config.setClusterEnabled(true);

        ClusterManager manager = new ClusterManager(config);
        assertTrue(manager.isClusterEnabled());

        assertTrue(manager.isLocalSlot(100));

        manager.meet("127.0.0.1", 6381);
        List<ClusterNode> nodes = manager.getNodes();
        assertEquals(2, nodes.size());

        manager.addSlots(10, 20, 30);
        assertTrue(manager.isLocalSlot(10));
        assertTrue(manager.isLocalSlot(20));
        assertTrue(manager.isLocalSlot(30));
        assertEquals(manager.getSelfNodeId(), manager.getNodeForSlot(10).getNodeId());
    }

    @Test
    public void testRouterMovedRedirections() {
        ServerConfig config = new ServerConfig();
        config.setClusterEnabled(true);

        DataStore dataStore = new DataStore();
        PubSubManager pubSub = new PubSubManager();
        PersistenceManager pm = new PersistenceManager(config, dataStore);
        CommandRouter router = new CommandRouter(dataStore, pubSub, pm);
        ClusterManager manager = new ClusterManager(config);
        router.setClusterManager(manager);

        manager.meet("127.0.0.1", 6381);
        ClusterNode nodeB = null;
        for (ClusterNode n : manager.getNodes()) {
            if (!n.getNodeId().equals(manager.getSelfNodeId())) {
                nodeB = n;
                break;
            }
        }
        assertNotNull(nodeB);

        int slot = CRC16.getSlot("myKey");
        manager.assignSlotToNode(slot, nodeB.getNodeId());

        assertFalse(manager.isLocalSlot(slot));

        Command cmd = Command.from(List.of("SET", "myKey", "myVal"));
        String resp = router.execute(cmd, null);
        assertTrue(resp.startsWith("-MOVED"));
        assertTrue(resp.contains(String.valueOf(slot)));
        assertTrue(resp.contains("127.0.0.1:6381"));
    }
}
