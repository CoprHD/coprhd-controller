/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.server.upgrade.impl.callback;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.Volume.ReplicationState;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.db.client.upgrade.callbacks.FullCopyVolumeReplicaStateMigration;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.db.server.DbsvcTestBase;
import com.emc.storageos.db.server.upgrade.DbSimpleMigrationTestBase;

/**
 * Database migration test class for upgrade callback FullCopyVolumeDetachedStateMigration
 */
public class FullCopyVolumeDetachedStateMigrationTest extends DbSimpleMigrationTestBase {

    // Number of source and full copy test volumes created.
    private final int INSTANCES_TO_CREATE = 3;

    // Maps the URIs of the test data source volumes to the URIs of their respective full copies.
    private static Map<URI, URI> _sourceFullCopyMap = new HashMap<URI, URI>();

    // Reference to a logger.
    private static final Logger s_logger = LoggerFactory.getLogger(FullCopyVolumeDetachedStateMigrationTest.class);

    /**
     * {@inheritDoc}
     */
    @BeforeClass
    public static void setup() throws IOException {
        customMigrationCallbacks.put("2.3", new ArrayList<BaseCustomMigrationCallback>() {
            private static final long serialVersionUID = 2L;
            {
                add(new FullCopyVolumeReplicaStateMigration());
            }
        });

        DbsvcTestBase.setup();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected String getSourceVersion() {
        return "2.3";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected String getTargetVersion() {
        return "2.4";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void prepareData() throws Exception {
        prepareVolumeData();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void verifyResults() throws Exception {
        verifyVolumeResults();
    }

    /**
     * Prepares the data for tests.
     * 
     * @throws Exception When an error occurs preparing the test data.
     */
    private void prepareVolumeData() throws Exception {
        s_logger.info("Preparing Volumes for FullCopyVolumeDetachedStateMigrationTest");
        for (int i = 0; i < INSTANCES_TO_CREATE; i++) {
            Volume fullCopyVol = new Volume();
            Volume sourceVol = new Volume();
            URI fullCopyURI = URIUtil.createId(Volume.class);
            URI sourceURI = URIUtil.createId(Volume.class);
            fullCopyVol.setId(fullCopyURI);
            fullCopyVol.setAssociatedSourceVolume(sourceURI);
            fullCopyVol.setReplicaState(ReplicationState.DETACHED.name());
            sourceVol.setId(sourceURI);
            StringSet fullCopies = new StringSet();
            fullCopies.add(fullCopyURI.toString());
            sourceVol.setFullCopies(fullCopies);
            _dbClient.createObject(fullCopyVol);
            _dbClient.createObject(sourceVol);
            _sourceFullCopyMap.put(sourceURI, fullCopyURI);
        }
    }

    /**
     * Verifies the results for migrating volumes
     * 
     * @throws Exception When an error occurs verifying the Volume
     *             migration results.
     */
    private void verifyVolumeResults() throws Exception {
        s_logger.info("Verifying updated full copy results for FullCopyVolumeDetachedStateMigrationTest.");
        for (URI sourceURI : _sourceFullCopyMap.keySet()) {
            Volume source = _dbClient.queryObject(Volume.class, sourceURI);
            URI fullCopyURI = _sourceFullCopyMap.get(sourceURI);
            Volume fullCopy = _dbClient.queryObject(Volume.class, fullCopyURI);

            Assert.assertNotNull("replicaState shouldn't be null", fullCopy.getReplicaState());
            Assert.assertEquals("replica state should be DETACHED", ReplicationState.DETACHED.name(),
                    fullCopy.getReplicaState());
            Assert.assertEquals("associated source should be null", fullCopy.getAssociatedSourceVolume(),
                    NullColumnValueGetter.getNullURI());

            StringSet fullCopies = source.getFullCopies();
            if (fullCopies != null) {
                Assert.assertFalse("full copies should not contain the clone", fullCopies.contains(fullCopyURI.toString()));
            }
        }
    }
}