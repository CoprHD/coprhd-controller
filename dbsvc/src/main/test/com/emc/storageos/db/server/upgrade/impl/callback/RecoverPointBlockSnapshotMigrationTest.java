/**
 *  Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.db.server.upgrade.impl.callback;

import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.ProtectionSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.db.client.upgrade.callbacks.RecoverPointBlockSnapshotMigration;
import com.emc.storageos.db.server.DbsvcTestBase;
import com.emc.storageos.db.server.upgrade.DbSimpleMigrationTestBase;

import org.junit.BeforeClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;

import org.junit.Assert;

public class RecoverPointBlockSnapshotMigrationTest extends DbSimpleMigrationTestBase {
    private static final Logger log = LoggerFactory.getLogger(RecoverPointBlockSnapshotMigrationTest.class);
    private static final String DEVICE_LABEL = "targetVolumeDeviceLabel";

    private URI snapURI = null;

    @BeforeClass
    public static void setup() throws IOException {
        customMigrationCallbacks.put("2.2", new ArrayList<BaseCustomMigrationCallback>() {
            private static final long serialVersionUID = 2L;
            {
                add(new RecoverPointBlockSnapshotMigration());
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
        return "2.3";
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
     * Prepares the data for RP BlockSnapshot migration tests.
     * 
     * @throws Exception When an error occurs preparing the BlockSnapshot migration data.
     */
    private void prepareSnapData() throws Exception {
        log.info("Preparing BlockSnapshots for RecoverPointBlockSnapshotMigration");
        ProtectionSystem ps = new ProtectionSystem();
        URI protectionSystemUri = URIUtil.createId(ProtectionSystem.class);
        ps.setId(protectionSystemUri);
        _dbClient.createObject(ps);

        VirtualArray targetVarray = new VirtualArray();
        URI targetVarrayUri = URIUtil.createId(VirtualArray.class);
        targetVarray.setId(targetVarrayUri);
        _dbClient.createObject(targetVarray);

        Volume targetVolume = new Volume();
        URI targetVolumeUri = URIUtil.createId(Volume.class);
        targetVolume.setId(targetVolumeUri);
        targetVolume.setLabel("targetVolume");
        targetVolume.setDeviceLabel(DEVICE_LABEL);
        targetVolume.setVirtualArray(targetVarrayUri);
        _dbClient.createObject(targetVolume);

        Volume parentVolume = new Volume();
        URI volumeUri = URIUtil.createId(Volume.class);
        parentVolume.setId(volumeUri);
        parentVolume.setLabel("parentVolume");
        StringSet rpTargets = new StringSet();
        rpTargets.add(targetVolume.getId().toString());
        parentVolume.setRpTargets(rpTargets);
        _dbClient.createObject(parentVolume);

        BlockSnapshot snapshot = new BlockSnapshot();
        snapURI = URIUtil.createId(BlockSnapshot.class);
        snapshot.setId(snapURI);
        snapshot.setProtectionController(protectionSystemUri);
        NamedURI parentVolNamedUri = new NamedURI(parentVolume.getId(), parentVolume.getLabel());
        snapshot.setParent(parentVolNamedUri);
        snapshot.setVirtualArray(targetVarrayUri);
        _dbClient.createObject(snapshot);
    }

    /**
     * Verifies the results for migrating RP BlockSnapshots.
     * 
     * @throws Exception When an error occurs verifying the BlockSnapshot
     *             migration results.
     */
    private void verifySnapResults() throws Exception {
        log.info("Verifying updated snapshot sresults for BlockSnapshotReplicationGroupInstanceMigration.");

        BlockSnapshot snap = _dbClient.queryObject(BlockSnapshot.class, snapURI);

        Assert.assertNotNull("deviceLabel should be set.", snap.getDeviceLabel());
        Assert.assertTrue("deviceLabel should be set to " + DEVICE_LABEL, snap.getDeviceLabel().equals(DEVICE_LABEL));
    }
}
