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

public class FullCopyVolumeReplicaStateMigrationTest extends DbSimpleMigrationTestBase {
    private static final Logger log = LoggerFactory.getLogger(FullCopyVolumeReplicaStateMigrationTest.class);

    private static Map<URI, URI> _sourceCloneMap = new HashMap<URI, URI>();

    private final int INSTANCES_TO_CREATE = 3;

    @BeforeClass
    public static void setup() throws IOException {
        customMigrationCallbacks.put("2.2", new ArrayList<BaseCustomMigrationCallback>() {
            private static final long serialVersionUID = 2L;
            {
                add(new FullCopyVolumeReplicaStateMigration());
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
        prepareVolumeData();
    }

    @Override
    protected void verifyResults() throws Exception {
        verifyVolumeResults();
    }

    /**
     * Prepares the data for RP volume tests.
     * 
     * @throws Exception When an error occurs preparing the RP volume data.
     */
    private void prepareVolumeData() throws Exception {
        log.info("Preparing Volumes for FullCopyVolumeReplicaStateMigration");
        for (int i = 0; i < INSTANCES_TO_CREATE; i++) {
            Volume cloneVol = new Volume();
            Volume sourceVol = new Volume();
            URI cloneURI = URIUtil.createId(Volume.class);
            URI sourceURI = URIUtil.createId(Volume.class);
            cloneVol.setId(cloneURI);
            cloneVol.setAssociatedSourceVolume(sourceURI);
            sourceVol.setId(sourceURI);
            StringSet clones = new StringSet();
            clones.add(cloneURI.toString());
            sourceVol.setFullCopies(clones);
            _dbClient.createObject(cloneVol);
            _dbClient.createObject(sourceVol);
            _sourceCloneMap.put(sourceURI, cloneURI);
        }
    }

    /**
     * Verifies the results for migrating volumes
     * 
     * @throws Exception When an error occurs verifying the Volume
     *             migration results.
     */
    private void verifyVolumeResults() throws Exception {
        log.info("Verifying updated full copy Volume sresults for FullCopyVolumeReplicaStateMigration.");
        for (URI sourceURI : _sourceCloneMap.keySet()) {
            Volume source = _dbClient.queryObject(Volume.class, sourceURI);
            URI cloneURI = _sourceCloneMap.get(sourceURI);
            Volume clone = _dbClient.queryObject(Volume.class, cloneURI);

            Assert.assertNotNull("replicaState shouldn't be null", clone.getReplicaState());
            Assert.assertEquals("replica state should be DETACHED", ReplicationState.DETACHED.name(),
                    clone.getReplicaState());
            Assert.assertEquals("associated source should be null", clone.getAssociatedSourceVolume(), NullColumnValueGetter.getNullURI());
            
            StringSet clones = source.getFullCopies();
            if (clones != null) {
                Assert.assertFalse("full copies should not contain the clone", clones.contains(cloneURI.toString()));
            }
        }
    }
}
