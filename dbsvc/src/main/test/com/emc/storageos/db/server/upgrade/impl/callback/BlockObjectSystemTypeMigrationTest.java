/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.server.upgrade.impl.callback;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.BlockMirror;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.db.client.upgrade.callbacks.BlockObjectSystemTypeMigration;
import com.emc.storageos.db.server.DbsvcTestBase;
import com.emc.storageos.db.server.upgrade.DbSimpleMigrationTestBase;

public class BlockObjectSystemTypeMigrationTest extends DbSimpleMigrationTestBase {

    private static final Logger log = LoggerFactory.getLogger(BlockObjectSystemTypeMigrationTest.class);

    private static final int VPLEX_VOLUME_COUNT = 125;
    private static final int VNX_VOLUME_COUNT = 125;
    private static final int VMAX_VOLUME_COUNT = 50;
    private static final int VMAX3_VOLUME_COUNT = 50;
    private static final int SNAPSHOT_COUNT = 33;
    private static final int MIRROR_COUNT = 33;

    @BeforeClass
    public static void setup() throws IOException {
        customMigrationCallbacks.put("3.5", new ArrayList<BaseCustomMigrationCallback>() {
            private static final long serialVersionUID = 2L;
            {
                add(new BlockObjectSystemTypeMigration());
            }
        });

        DbsvcTestBase.setup();
    }

    @Override
    protected void prepareData() throws Exception {

        List<Volume> volumesToCreate = new ArrayList<Volume>();

        // set up a vplex system
        StorageSystem vplex = new StorageSystem();
        vplex.setId(URIUtil.createId(StorageSystem.class));
        vplex.setLabel("TEST_VPLEX");
        vplex.setSystemType(DiscoveredDataObject.Type.vplex.name());
        _dbClient.createObject(vplex);

        // create vplex test volumes
        for (int i = 1; i <= VPLEX_VOLUME_COUNT; i++) {
            Volume volume = new Volume();
            volume.setId(URIUtil.createId(Volume.class));
            volume.setLabel("VplexSystemTypeMigrationTester" + i);
            volume.setStorageController(vplex.getId());
            volumesToCreate.add(volume);
        }

        // set up a vnx system
        StorageSystem vnx = new StorageSystem();
        vnx.setId(URIUtil.createId(StorageSystem.class));
        vnx.setLabel("TEST_VNX");
        vnx.setSystemType(DiscoveredDataObject.Type.vnxblock.name());
        _dbClient.createObject(vnx);

        // create vnx test volumes
        for (int i = 1; i <= VNX_VOLUME_COUNT; i++) {
            Volume volume = new Volume();
            volume.setId(URIUtil.createId(Volume.class));
            volume.setLabel("VnxSystemTypeMigrationTester" + i);
            volume.setStorageController(vnx.getId());
            volumesToCreate.add(volume);
        }

        // set up a vmax system
        StorageSystem vmax = new StorageSystem();
        vmax.setId(URIUtil.createId(StorageSystem.class));
        vmax.setLabel("TEST_VMAX");
        vmax.setSystemType(DiscoveredDataObject.Type.vmax.name());
        _dbClient.createObject(vmax);

        // create vmax test volumes
        for (int i = 1; i <= VMAX_VOLUME_COUNT; i++) {
            Volume volume = new Volume();
            volume.setId(URIUtil.createId(Volume.class));
            volume.setLabel("VmaxSystemTypeMigrationTester" + i);
            volume.setStorageController(vmax.getId());
            volumesToCreate.add(volume);
        }

        // set up a vmax3 system
        StorageSystem vmax3 = new StorageSystem();
        vmax3.setId(URIUtil.createId(StorageSystem.class));
        vmax3.setLabel("TEST_VMAX3");
        // vmax3 systems are created with a "vmax" type, just to make things difficult;
        // actual type is determine by the firmware version number
        vmax3.setSystemType(DiscoveredDataObject.Type.vmax.name());
        vmax3.setFirmwareVersion("5977.931.886");
        _dbClient.createObject(vmax3);

        // create vmax3 test volumes
        for (int i = 1; i <= VMAX3_VOLUME_COUNT; i++) {
            Volume volume = new Volume();
            volume.setId(URIUtil.createId(Volume.class));
            volume.setLabel("Vmax3SystemTypeMigrationTester" + i);
            volume.setStorageController(vmax3.getId());
            volumesToCreate.add(volume);
        }

        _dbClient.createObject(volumesToCreate);

        List<BlockSnapshot> snapsToCreate = new ArrayList<BlockSnapshot>();
        // create vmax3 test snapshots
        for (int i = 1; i <= SNAPSHOT_COUNT; i++) {
            BlockSnapshot snap = new BlockSnapshot();
            snap.setId(URIUtil.createId(BlockSnapshot.class));
            snap.setLabel("SnapshotSystemTypeMigrationTester" + i);
            snap.setStorageController(vmax3.getId());
            snapsToCreate.add(snap);
        }
        _dbClient.createObject(snapsToCreate);

        List<BlockMirror> mirrorsToCreate = new ArrayList<BlockMirror>();
        // create vmax3 test mirrors
        for (int i = 1; i <= MIRROR_COUNT; i++) {
            BlockMirror mirror = new BlockMirror();
            mirror.setId(URIUtil.createId(BlockMirror.class));
            mirror.setLabel("MirrorSystemTypeMigrationTester" + i);
            mirror.setStorageController(vmax3.getId());
            mirrorsToCreate.add(mirror);
        }
        _dbClient.createObject(mirrorsToCreate);
    }

    @Override
    protected void verifyResults() throws Exception {
        log.info("Verifying results of volume system type migration test now.");

        Map<URI, String> storageSystemTypeMap = new HashMap<URI, String>();

        List<URI> volumeUris = _dbClient.queryByType(Volume.class, true);
        Iterator<Volume> volumes = _dbClient.queryIterativeObjects(Volume.class, volumeUris, true);

        int vplexMigratedCount = 0;
        int vnxMigratedCount = 0;
        int vmaxMigratedCount = 0;
        int vmax3MigratedCount = 0;
        while (volumes.hasNext()) {
            Volume volume = volumes.next();
            String deviceSystemType = getDeviceSystemType(storageSystemTypeMap, volume);

            if (deviceSystemType != null && deviceSystemType.equalsIgnoreCase(volume.getSystemType())) {
                log.info("found block object system type {}", volume.getSystemType());
                switch (deviceSystemType) {
                    case "vplex":
                        vplexMigratedCount++;
                        break;
                    case "vnxblock":
                        vnxMigratedCount++;
                        break;
                    case "vmax":
                        vmaxMigratedCount++;
                        break;
                    case "vmax3":
                        vmax3MigratedCount++;
                        break;
                    default:
                        log.error("unknown device system type {}", deviceSystemType);
                        break;
                }
            } else {
                log.error("volume {} found not migrated properly with system type {}",
                        volume.forDisplay(), volume.getSystemType());
            }
        }

        int snapshotMigratedCount = 0;
        List<URI> snapshotUris = _dbClient.queryByType(BlockSnapshot.class, true);
        Iterator<BlockSnapshot> snapshots = _dbClient.queryIterativeObjects(BlockSnapshot.class, snapshotUris, true);
        while (snapshots.hasNext()) {
            BlockSnapshot snapshot = snapshots.next();
            String deviceSystemType = getDeviceSystemType(storageSystemTypeMap, snapshot);

            if (deviceSystemType != null && deviceSystemType.equalsIgnoreCase(snapshot.getSystemType())) {
                log.info("found block snapshot system type {}", snapshot.getSystemType());
                snapshotMigratedCount++;
            } else {
                log.error("snapshot {} found not migrated properly with system type {}",
                        snapshot.forDisplay(), snapshot.getSystemType());
            }
        }

        int mirrorMigratedCount = 0;
        List<URI> mirrorUris = _dbClient.queryByType(BlockMirror.class, true);
        Iterator<BlockMirror> mirrors = _dbClient.queryIterativeObjects(BlockMirror.class, mirrorUris, true);
        while (mirrors.hasNext()) {
            BlockMirror mirror = mirrors.next();
            String deviceSystemType = getDeviceSystemType(storageSystemTypeMap, mirror);

            if (deviceSystemType != null && deviceSystemType.equalsIgnoreCase(mirror.getSystemType())) {
                log.info("found block mirror system type {}", mirror.getSystemType());
                mirrorMigratedCount++;
            } else {
                log.error("mirror {} found not migrated properly with system type {}",
                        mirror.forDisplay(), mirror.getSystemType());
            }
        }

        log.info("vplexMigratedCount: " + vplexMigratedCount);
        log.info("vnxMigratedCount: " + vnxMigratedCount);
        log.info("vmaxMigratedCount: " + vmaxMigratedCount);
        log.info("vmax3MigratedCount: " + vmax3MigratedCount);
        log.info("snapshotMigratedCount: " + snapshotMigratedCount);
        log.info("mirrorMigratedCount: " + mirrorMigratedCount);

        Assert.assertEquals(String.format("We should have found %d migrated VPLEX volumes.", VPLEX_VOLUME_COUNT),
                VPLEX_VOLUME_COUNT, vplexMigratedCount);
        Assert.assertEquals(String.format("We should have found %d migrated VNX volumes.", VNX_VOLUME_COUNT),
                VNX_VOLUME_COUNT, vnxMigratedCount);
        Assert.assertEquals(String.format("We should have found %d migrated VMAX volumes.", VMAX_VOLUME_COUNT),
                VMAX_VOLUME_COUNT, vmaxMigratedCount);
        Assert.assertEquals(String.format("We should have found %d migrated VMAX3 volumes.", VMAX3_VOLUME_COUNT),
                VMAX3_VOLUME_COUNT, vmax3MigratedCount);
        Assert.assertEquals(String.format("We should have found %d migrated VMAX3 snapshots.", SNAPSHOT_COUNT),
                SNAPSHOT_COUNT, snapshotMigratedCount);
        Assert.assertEquals(String.format("We should have found %d migrated VMAX3 mirrors.", MIRROR_COUNT),
                MIRROR_COUNT, mirrorMigratedCount);
    }

    private String getDeviceSystemType(Map<URI, String> storageSystemTypeMap, BlockObject blockObject) {
        String deviceSystemType = null;
        URI storageSystemUri = blockObject.getStorageController();
        if (storageSystemTypeMap.containsKey(storageSystemUri)) {
            deviceSystemType = storageSystemTypeMap.get(storageSystemUri);
        } else {
            StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class, storageSystemUri);
            if (storageSystem != null) {
                deviceSystemType = storageSystem.checkIfVmax3() ? DiscoveredDataObject.Type.vmax3.name()
                        : storageSystem.getSystemType();
                storageSystemTypeMap.put(storageSystemUri, deviceSystemType);
                log.info("adding storage system type {} for storage system URI {}",
                        deviceSystemType, storageSystemUri);
            } else {
                log.warn("could not find storage system by URI {} for BlockObject {}",
                        storageSystemUri, blockObject.forDisplay());
            }
        }
        return deviceSystemType;
    }

}
