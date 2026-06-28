package com.miniredis.replication;

import com.miniredis.command.CommandRouter;
import com.miniredis.config.ServerConfig;
import com.miniredis.persistence.PersistenceManager;
import com.miniredis.pubsub.PubSubManager;
import com.miniredis.store.DataStore;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ReplicationTest {

    @Test
    public void testReplicationBacklogRange() {
        ReplicationBacklog backlog = new ReplicationBacklog(100);
        
        backlog.addCommand("SET k1 v1\r\n");
        long offset1 = backlog.getMasterOffset();
        assertTrue(offset1 > 0);

        backlog.addCommand("SET k2 v2\r\n");
        long offset2 = backlog.getMasterOffset();
        assertTrue(offset2 > offset1);

        List<String> cmds = backlog.getCommandsFromOffset(offset1);
        assertNotNull(cmds);
        assertEquals(1, cmds.size());
        assertEquals("SET k2 v2\r\n", cmds.get(0));

        List<String> allCmds = backlog.getCommandsFromOffset(0);
        assertNotNull(allCmds);
        assertEquals(2, allCmds.size());

        List<String> invalidCmds = backlog.getCommandsFromOffset(9999);
        assertNull(invalidCmds);
    }

    @Test
    public void testReplicaOfPromotion() {
        ServerConfig config = new ServerConfig();
        DataStore dataStore = new DataStore();
        PubSubManager pubSub = new PubSubManager();
        PersistenceManager pm = new PersistenceManager(config, dataStore);
        CommandRouter router = new CommandRouter(dataStore, pubSub, pm);
        ReplicationManager repManager = new ReplicationManager(config);
        router.setReplicationManager(repManager);

        assertFalse(dataStore.isReadOnly());
        assertFalse(repManager.isReplica());

        repManager.setReplicaOf("localhost", 6380, dataStore, router, pm);
        assertTrue(repManager.isReplica());
        assertTrue(dataStore.isReadOnly());
        assertEquals("localhost", repManager.getMasterHost());
        assertEquals(6380, repManager.getMasterPort());

        repManager.setReplicaOf(null, -1, dataStore, router, pm);
        assertFalse(repManager.isReplica());
        assertFalse(dataStore.isReadOnly());
    }
}
