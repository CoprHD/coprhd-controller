/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.server.upgrade.impl.callback;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.UUID;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.db.client.upgrade.callbacks.XtremioBlockSnapshotReplicationGroupInstanceMigration;
import com.emc.storageos.db.server.DbsvcTestBase;
import com.emc.storageos.db.server.upgrade.DbSimpleMigrationTestBase;

public class XtremioBlockSnapshotReplicationGroupInstanceMigrationTest extends DbSimpleMigrationTestBase {

    private static final Logger log = LoggerFactory.getLogger(XtremioBlockSnapshotReplicationGroupInstanceMigrationTest.class);

    private URI snapURI = null;

    private String groupInstance = null;

    @BeforeClass
    public static void setup() throws IOException {
        customMigrationCallbacks.put("2.2", new ArrayList<BaseCustomMigrationCallback>() {
            private static final long serialVersionUID = 2L;
            {
                add(new XtremioBlockSnapshotReplicationGroupInstanceMigration());
            }
        });

        DbsvcTestBase.setup();
    }

    @Override
    protected String getSourceVersion() {
        return "2.4.1";
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

        StorageSystem storageSystem = new StorageSystem();
        URI systemURI = URIUtil.createId(StorageSystem.class);
        storageSystem.setId(systemURI);
        storageSystem.setSystemType(DiscoveredDataObject.Type.xtremio.name());
        _dbClient.createObject(storageSystem);

        BlockSnapshot snapshot = new BlockSnapshot();
        snapURI = URIUtil.createId(BlockSnapshot.class);
        groupInstance = UUID.randomUUID().toString();

        snapshot.setId(snapURI);
        snapshot.setStorageController(systemURI);
        snapshot.setSnapsetLabel(groupInstance);
        _dbClient.createObject(snapshot);

    }

    /**
     * Verifies the results for migrating volumes
     * 
     * @throws Exception When an error occurs verifying the Volume
     *             migration results.
     */
    private void verifySnapResults() throws Exception {
        log.info("Verifying updated snapshot sresults for XtremioBlockSnapshotReplicationGroupInstanceMigration.");

        BlockSnapshot snap = _dbClient.queryObject(BlockSnapshot.class, snapURI);

        Assert.assertNotNull("replicationGroupInstance shouldn't be null", snap.getReplicationGroupInstance());
        Assert.assertEquals("replicationGroupInstance should be set from the snapsetLabel", groupInstance,
                snap.getReplicationGroupInstance());

    }

}
