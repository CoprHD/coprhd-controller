/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.server.upgrade.impl.callback;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.db.client.upgrade.callbacks.XtremioBlockSnapshotDeviceLabelMigration;
import com.emc.storageos.db.server.upgrade.DbSimpleMigrationTestBase;

public class XtremioBlockSnapshotDeviceLabelMigrationTest extends DbSimpleMigrationTestBase {

    private static final Logger log = LoggerFactory.getLogger(XtremioBlockSnapshotDeviceLabelMigrationTest.class);
    private static final String SNAPSHOT_LABEL = "snapshotLabel";
    private static final String SNAPSHOT_DEVICE_LABEL = "snapshot-device-label";
    private static final String SNAPSHOT_WITH_DEVICE_LABEL = "snap-with-device-label";
    private static final String NON_XIO_SNAPSHOT_DEVICE_LABEL = "vnx-snapshot-device-label";

    @BeforeClass
    public static void setup() throws IOException {
        customMigrationCallbacks.put("2.3", new ArrayList<BaseCustomMigrationCallback>() {
            {
                add(new XtremioBlockSnapshotDeviceLabelMigration());
            }
        });

        // Adding this, which is typically executed in the base class
        // call, as it is needed to clear the DB file between runs.
        _dataDir = new File(dataDir);
        if (_dataDir.exists() && _dataDir.isDirectory()) {
            cleanDirectory(_dataDir);
        }
        _dataDir.mkdir();

        // Commenting this out as it prevents the migration callback
        // from being executed when the test is executed.
        // DbsvcTestBase.setup();
        log.info("completed setup");
    }

    @Override
    protected String getSourceVersion() {
        return "2.3";
    }

    @Override
    protected String getTargetVersion() {
        return "2.4";
    }

    @Override
    protected void prepareData() throws Exception {
        createXIOSnapshots();
        createNonXIOSnapshots();
    }

    @Override
    protected void verifyResults() throws Exception {
        List<URI> storageSystemURIList = _dbClient.queryByType(StorageSystem.class, true);
        Iterator<StorageSystem> storageSystems = _dbClient.queryIterativeObjects(StorageSystem.class, storageSystemURIList);
        while (storageSystems.hasNext()) {
            StorageSystem storageSystem = storageSystems.next();
            if (DiscoveredDataObject.Type.xtremio.name().equalsIgnoreCase(storageSystem.getSystemType())) {
                URIQueryResultList snapshotURIs = new URIQueryResultList();
                _dbClient.queryByConstraint(ContainmentConstraint.Factory.getStorageDeviceSnapshotConstraint(storageSystem.getId()),
                        snapshotURIs);
                Iterator<BlockSnapshot> xioSnapshots = _dbClient.queryIterativeObjects(BlockSnapshot.class, snapshotURIs);
                while (xioSnapshots.hasNext()) {
                    BlockSnapshot xioSnapshot = xioSnapshots.next();
                    if (!xioSnapshot.getInactive()) {
                        Assert.assertNotNull("XtremIO snapshot should have deviceLabel set", xioSnapshot.getDeviceLabel());
                        if (SNAPSHOT_LABEL.equals(xioSnapshot.getLabel())) {
                            // Verify that in case of snapshot without deviceLabel, we set it to the label
                            Assert.assertTrue("deviceLabel should be set to " + SNAPSHOT_LABEL,
                                    xioSnapshot.getDeviceLabel().equals(SNAPSHOT_LABEL));
                        } else if (SNAPSHOT_WITH_DEVICE_LABEL.equals(xioSnapshot.getLabel())) {
                            // Verify that in case of snapshot with deviceLabel, we do not change it
                            Assert.assertTrue("deviceLabel should be set to " + SNAPSHOT_DEVICE_LABEL,
                                    xioSnapshot.getDeviceLabel().equals(SNAPSHOT_DEVICE_LABEL));
                        }
                    }
                }
            }

            if (DiscoveredDataObject.Type.vnxblock.name().equalsIgnoreCase(storageSystem.getSystemType())) {
                URIQueryResultList snapshotURIs = new URIQueryResultList();
                _dbClient.queryByConstraint(ContainmentConstraint.Factory.getStorageDeviceSnapshotConstraint(storageSystem.getId()),
                        snapshotURIs);
                Iterator<BlockSnapshot> vnxSnapshots = _dbClient.queryIterativeObjects(BlockSnapshot.class, snapshotURIs);
                while (vnxSnapshots.hasNext()) {
                    BlockSnapshot vnxSnapshot = vnxSnapshots.next();
                    if (!vnxSnapshot.getInactive()) {
                        Assert.assertNotNull("snapshot should have deviceLabel set", vnxSnapshot.getDeviceLabel());
                        // Verify that in case of snapshot with deviceLabel, we do not change it
                        Assert.assertTrue("deviceLabel should be set to " + NON_XIO_SNAPSHOT_DEVICE_LABEL,
                                vnxSnapshot.getDeviceLabel().equals(NON_XIO_SNAPSHOT_DEVICE_LABEL));
                    }
                }
            }
        }
    }

    private void createXIOSnapshots() throws Exception {
        log.info("Preparing BlockSnapshot for XtremioBlockSnapshotDeviceLabelMigrationTest");
        StorageSystem xioStorageSystem = new StorageSystem();
        xioStorageSystem.setId(URIUtil.createId(StorageSystem.class));
        xioStorageSystem.setSystemType(DiscoveredDataObject.Type.xtremio.name());
        _dbClient.createObject(xioStorageSystem);

        Volume parentVolume = new Volume();
        URI volumeUri = URIUtil.createId(Volume.class);
        parentVolume.setId(volumeUri);
        parentVolume.setLabel("parentVolume");
        _dbClient.createObject(parentVolume);

        NamedURI parentVolNamedUri = new NamedURI(parentVolume.getId(), parentVolume.getLabel());

        BlockSnapshot snapshot = createSnapshot(SNAPSHOT_LABEL, xioStorageSystem.getId(), parentVolNamedUri);
        _dbClient.createObject(snapshot);

        BlockSnapshot snapshotWithDeviceLabel = createSnapshot("snap-with-device-label", xioStorageSystem.getId(), parentVolNamedUri);
        snapshotWithDeviceLabel.setDeviceLabel("snapshot-device-label");
        _dbClient.createObject(snapshotWithDeviceLabel);

        BlockSnapshot inactiveSnapshot = createSnapshot("inactive-snapshot", xioStorageSystem.getId(), parentVolNamedUri);
        _dbClient.createObject(inactiveSnapshot);
        inactiveSnapshot.setInactive(true);
        _dbClient.updateObject(inactiveSnapshot);
    }

    private BlockSnapshot createSnapshot(String label, URI storageController, NamedURI parentURI) {
        BlockSnapshot snapshot = new BlockSnapshot();
        snapshot.setId(URIUtil.createId(BlockSnapshot.class));
        snapshot.setStorageController(storageController);
        snapshot.setLabel(label);
        snapshot.setParent(parentURI);

        return snapshot;
    }

    private void createNonXIOSnapshots() throws Exception {
        log.info("Preparing BlockSnapshot for XtremioBlockSnapshotDeviceLabelMigrationTest");
        StorageSystem storageSystem = new StorageSystem();
        storageSystem.setId(URIUtil.createId(StorageSystem.class));
        storageSystem.setSystemType(DiscoveredDataObject.Type.vnxblock.name());
        _dbClient.createObject(storageSystem);

        Volume parentVolume = new Volume();
        URI volumeUri = URIUtil.createId(Volume.class);
        parentVolume.setId(volumeUri);
        parentVolume.setLabel("vnx-parentVolume");
        _dbClient.createObject(parentVolume);

        NamedURI parentVolNamedUri = new NamedURI(parentVolume.getId(), parentVolume.getLabel());

        BlockSnapshot snapshot = createSnapshot("vnx-snapshot", storageSystem.getId(), parentVolNamedUri);
        snapshot.setDeviceLabel(NON_XIO_SNAPSHOT_DEVICE_LABEL);
        _dbClient.createObject(snapshot);
    }

}
