/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.server.upgrade.impl.callback;

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
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.db.client.upgrade.callbacks.VplexVolumeBackingReplicationGroupInstanceMigration;
import com.emc.storageos.db.server.DbsvcTestBase;
import com.emc.storageos.db.server.upgrade.DbSimpleMigrationTestBase;

/**
 * Test Class for VplexVolumeThinlyProvisionedMigration migration callback.
 */
public class VplexVolumeBackingReplicationGroupInstanceMigrationTest extends DbSimpleMigrationTestBase {

    private static final Logger log = LoggerFactory.getLogger(VplexVolumeBackingReplicationGroupInstanceMigrationTest.class);

    private static final String DIST_REPL_GRP_SRC = "DistributedReplicationGroupInstance123Source";
    private static final String VPLEX_DIST_VOL_LABEL = "VplexMigrationTester_Distributed";
    private static final String VPLEX_LOCAL_VOL_LABEL = "VplexMigrationTester_Local";
    private static final String VPLEX_LOCAL_ALREADY_SET_LABEL = "VplexMigrationTester_Local_alreadySet";
    private static final String VPLEX_LOCAL_ALREADY_SET_LABEL_VAL = "ALREADY_SET_SO_SKIP";
    private static final String VPLEX_LOCAL_NO_BACKEND = "VplexMigrationTester_Local_noBackendVols";
    private static final String NON_VPLEX_VOL = "VnxMigrationTest_NotAVplexVolume";
    private static final String BVOL = "Bvol";

    @BeforeClass
    public static void setup() throws IOException {
        customMigrationCallbacks.put("3.5", new ArrayList<BaseCustomMigrationCallback>() {
            private static final long serialVersionUID = 2L;
            {
                add(new VplexVolumeBackingReplicationGroupInstanceMigration());
            }
        });

        DbsvcTestBase.setup();
    }

    @Override
    protected void prepareData() throws Exception {
        log.info("preparing data for VPLEX volumes for BackingReplicationGroupInstance migration test.");

        // set up a vplex system
        StorageSystem vplex = new StorageSystem();
        vplex.setId(URIUtil.createId(StorageSystem.class));
        vplex.setLabel("TEST_VPLEX");
        vplex.setSystemType(DiscoveredDataObject.Type.vplex.name());
        _dbClient.createObject(vplex);

        // create a vnx system
        StorageSystem vnx = new StorageSystem();
        vnx.setId(URIUtil.createId(StorageSystem.class));
        vnx.setLabel("TEST_VNX");
        vnx.setSystemType(DiscoveredDataObject.Type.vnxblock.name());
        _dbClient.createObject(vnx);

        URI varray1Uri = URIUtil.createId(VirtualArray.class);
        URI varray2Uri = URIUtil.createId(VirtualArray.class);

        // create a vplex distributed volume
        Volume volume1 = new Volume();
        volume1.setId(URIUtil.createId(Volume.class));
        volume1.setLabel(VPLEX_DIST_VOL_LABEL);
        volume1.setStorageController(vplex.getId());
        volume1.setVirtualArray(varray1Uri);

        // create backend volumes for distributed
        Volume bvol1 = new Volume();
        bvol1.setId(URIUtil.createId(Volume.class));
        bvol1.setLabel(BVOL + "1");
        bvol1.setStorageController(vnx.getId());
        bvol1.setReplicationGroupInstance(DIST_REPL_GRP_SRC);
        bvol1.setVirtualArray(varray1Uri);
        _dbClient.createObject(bvol1);

        Volume bvol2 = new Volume();
        bvol2.setId(URIUtil.createId(Volume.class));
        bvol2.setLabel(BVOL + "2");
        bvol2.setStorageController(vnx.getId());
        bvol2.setReplicationGroupInstance("DistributedReplicationGroupInstance123HA");
        bvol2.setVirtualArray(varray2Uri);
        _dbClient.createObject(bvol2);

        StringSet associatedVolumes = new StringSet();
        associatedVolumes.add(bvol1.getId().toString());
        associatedVolumes.add(bvol2.getId().toString());
        volume1.setAssociatedVolumes(associatedVolumes);
        _dbClient.createObject(volume1);

        // create a vplex local volume
        Volume volume2 = new Volume();
        volume2.setId(URIUtil.createId(Volume.class));
        volume2.setLabel(VPLEX_LOCAL_VOL_LABEL);
        volume2.setStorageController(vplex.getId());
        volume2.setVirtualArray(varray1Uri);

        Volume bvol3 = new Volume();
        bvol3.setId(URIUtil.createId(Volume.class));
        bvol3.setLabel(BVOL + "3");
        bvol3.setStorageController(vnx.getId());
        bvol3.setReplicationGroupInstance(DIST_REPL_GRP_SRC);
        bvol3.setVirtualArray(varray1Uri);
        _dbClient.createObject(bvol3);
        StringSet associatedVolumes2 = new StringSet();
        associatedVolumes2.add(bvol3.getId().toString());
        volume2.setAssociatedVolumes(associatedVolumes2);
        _dbClient.createObject(volume2);

        // create a vnx volume with thin set to true;
        // this is to test that we didn't regress anything
        Volume volume3 = new Volume();
        volume3.setId(URIUtil.createId(Volume.class));
        volume3.setLabel(NON_VPLEX_VOL);
        volume3.setStorageController(vnx.getId());
        _dbClient.createObject(volume3);

        // create a vplex local volume that already has the property set
        Volume volume4 = new Volume();
        volume4.setId(URIUtil.createId(Volume.class));
        volume4.setLabel(VPLEX_LOCAL_ALREADY_SET_LABEL);
        volume4.setStorageController(vplex.getId());
        volume4.setVirtualArray(varray1Uri);
        volume4.setBackingReplicationGroupInstance(VPLEX_LOCAL_ALREADY_SET_LABEL_VAL);

        Volume bvol4 = new Volume();
        bvol4.setId(URIUtil.createId(Volume.class));
        bvol4.setLabel(BVOL + "4");
        bvol4.setStorageController(vnx.getId());
        bvol4.setReplicationGroupInstance(DIST_REPL_GRP_SRC);
        bvol4.setVirtualArray(varray1Uri);
        _dbClient.createObject(bvol4);
        StringSet associatedVolumes3 = new StringSet();
        associatedVolumes3.add(bvol4.getId().toString());
        volume4.setAssociatedVolumes(associatedVolumes3);
        _dbClient.createObject(volume4);

        // create a vplex local volume with no backend volumes, which should be skipped
        Volume volume5 = new Volume();
        volume5.setId(URIUtil.createId(Volume.class));
        volume5.setLabel(VPLEX_LOCAL_NO_BACKEND);
        volume5.setStorageController(vplex.getId());
        volume5.setVirtualArray(varray1Uri);
        _dbClient.createObject(volume5);

    }

    @Override
    protected void verifyResults() throws Exception {
        log.info("Verifying results of VPLEX volume BackingReplicationGroupInstance migration test.");
        List<URI> volumeUris = _dbClient.queryByType(Volume.class, true);
        Iterator<Volume> volumes = _dbClient.queryIterativeObjects(Volume.class, volumeUris, true);

        int updatedCount = 0;
        int notUpdatedCount = 0;
        while (volumes.hasNext()) {
            Volume volume = volumes.next();
            switch (volume.getLabel()) {
                case VPLEX_DIST_VOL_LABEL:
                    Assert.assertEquals(DIST_REPL_GRP_SRC, volume.getBackingReplicationGroupInstance());
                    updatedCount++;
                    break;
                case VPLEX_LOCAL_VOL_LABEL:
                    Assert.assertEquals(DIST_REPL_GRP_SRC, volume.getBackingReplicationGroupInstance());
                    updatedCount++;
                    break;
                case VPLEX_LOCAL_ALREADY_SET_LABEL:
                    Assert.assertEquals(VPLEX_LOCAL_ALREADY_SET_LABEL_VAL, volume.getBackingReplicationGroupInstance());
                    notUpdatedCount++;
                    break;
                case VPLEX_LOCAL_NO_BACKEND:
                    Assert.assertNull(volume.getBackingReplicationGroupInstance());
                    notUpdatedCount++;
                    break;
                case NON_VPLEX_VOL:
                    Assert.assertNull(volume.getBackingReplicationGroupInstance());
                    notUpdatedCount++;
                    break;
                default:
                    if (volume.getLabel() != null && !volume.getLabel().startsWith(BVOL)) {
                        throw new AssertionError("Unexpected volume found: " + volume.forDisplay());
                    }
            }
        }

        Assert.assertEquals("We should have found two updated VPLEX volumes.", 2, updatedCount);
        Assert.assertEquals("We should have found three not updated volumes.", 3, notUpdatedCount);
    }

}
