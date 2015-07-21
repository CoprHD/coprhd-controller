/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.server.upgrade.impl.callback;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.Volume.ReplicationState;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.db.client.upgrade.callbacks.BlockSnapshotReplicationGroupInstanceMigration;
import com.emc.storageos.db.server.DbsvcTestBase;
import com.emc.storageos.db.server.upgrade.DbSimpleMigrationTestBase;

public class BlockSnapshotReplicationGroupInstanceMigrationTest extends DbSimpleMigrationTestBase {
    private static final Logger log = LoggerFactory.getLogger(BlockSnapshotReplicationGroupInstanceMigrationTest.class);
    

    private URI snapURI = null;
    
    private String groupInstance = null;

    @BeforeClass
    public static void setup() throws IOException {
        customMigrationCallbacks.put("2.2", new ArrayList<BaseCustomMigrationCallback>() {
            private static final long serialVersionUID = 2L;
            {
                add(new BlockSnapshotReplicationGroupInstanceMigration());
            }
        });

        DbsvcTestBase.setup();
    }

    @Override
    protected String getSourceVersion() {
        return "2.2";
    }

    @Override
    protected String getTargetVersion() {
        return "2.5";
    }

    @Override
    protected void prepareData() throws Exception { 
        prepareSnapData();
    }

    @Override
    protected void verifyResults() throws Exception {
        verifySnapResults();
    }
    
    /**
     * Prepares the data for RP volume tests.
     * 
     * @throws Exception When an error occurs preparing the RP volume data.
     */
    private void prepareSnapData() throws Exception {
        log.info("Preparing block snapshots for BlockSnapshotReplicationGroupInstanceMigration");
        BlockSnapshot snapshot = new BlockSnapshot();
        snapURI = URIUtil.createId(BlockSnapshot.class);
        groupInstance = UUID.randomUUID().toString();
        snapshot.setId(snapURI);
        snapshot.setSnapshotGroupInstance(groupInstance);
        _dbClient.createObject(snapshot);
            
    }

    /**
     * Verifies the results for migrating volumes
     * 
     * @throws Exception When an error occurs verifying the Volume
     *         migration results.
     */
    private void verifySnapResults() throws Exception {
        log.info("Verifying updated snapshot sresults for BlockSnapshotReplicationGroupInstanceMigration.");

        BlockSnapshot snap = _dbClient.queryObject(BlockSnapshot.class, snapURI);
            
        Assert.assertNotNull("replicationGroupInstance shouldn't be null", snap.getReplicationGroupInstance());
        Assert.assertEquals("replicationGroupInstance should be set from the snapGroupInstance", groupInstance,
                                    snap.getReplicationGroupInstance());
        
    }


}
