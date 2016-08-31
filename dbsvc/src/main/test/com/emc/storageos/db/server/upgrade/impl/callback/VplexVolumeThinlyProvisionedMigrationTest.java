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
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.db.client.upgrade.callbacks.VplexVolumeThinlyProvisionedMigration;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.db.server.DbsvcTestBase;
import com.emc.storageos.db.server.upgrade.DbSimpleMigrationTestBase;

/**
 * Test Class for VplexVolumeThinlyProvisionedMigration migration callback.
 */
public class VplexVolumeThinlyProvisionedMigrationTest extends DbSimpleMigrationTestBase {

    private static final Logger log = LoggerFactory.getLogger(VplexVolumeThinlyProvisionedMigrationTest.class);

    @BeforeClass
    public static void setup() throws IOException {
        customMigrationCallbacks.put("3.1", new ArrayList<BaseCustomMigrationCallback>() {
            private static final long serialVersionUID = 2L;
            {
                add(new VplexVolumeThinlyProvisionedMigration());
            }
        });

        DbsvcTestBase.setup();
    }

    @Override
    protected String getSourceVersion() {
        return "3.1";
    }

    @Override
    protected String getTargetVersion() {
        return "3.5";
    }

    @Override
    protected void prepareData() throws Exception {
        log.info("preparing data for VPLEX volumes for thinly-provisioned migration test.");

        // set up a vplex system
        StorageSystem vplex = new StorageSystem();
        vplex.setId(URIUtil.createId(StorageSystem.class));
        vplex.setLabel("TEST_VPLEX");
        vplex.setSystemType(DiscoveredDataObject.Type.vplex.name());
        _dbClient.createObject(vplex);

        // create a vplex volume with thin set to true
        Volume volume = new Volume();
        volume.setId(URIUtil.createId(Volume.class));
        volume.setLabel("VplexThinMigrationTester_True");
        volume.setThinlyProvisioned(true);
        volume.setStorageController(vplex.getId());
        _dbClient.createObject(volume);

        // create a vplex volume with thin set to false
        Volume volume2 = new Volume();
        volume2.setId(URIUtil.createId(Volume.class));
        volume2.setLabel("VplexThinMigrationTester_AlreadyFalse");
        volume2.setThinlyProvisioned(false);
        volume2.setStorageController(vplex.getId());
        _dbClient.createObject(volume2);

        // create a vnx system
        StorageSystem vnx = new StorageSystem();
        vnx.setId(URIUtil.createId(StorageSystem.class));
        vnx.setLabel("TEST_VNX");
        vnx.setSystemType(DiscoveredDataObject.Type.vnxblock.name());
        _dbClient.createObject(vnx);

        // create a vnx volume with thin set to true;
        // this is to test that we didn't regress anything 
        Volume volume3 = new Volume();
        volume3.setId(URIUtil.createId(Volume.class));
        volume3.setLabel("VnxThinMigrationTest_True");
        volume3.setThinlyProvisioned(true);
        volume3.setStorageController(vnx.getId());
        _dbClient.createObject(volume3);

    }

    @Override
    protected void verifyResults() throws Exception {
        log.info("Verifying results of VPLEX volume thinly provisioned migration test.");
        List<URI> volumeUris = _dbClient.queryByType(Volume.class, true);
        Iterator<Volume> volumes = _dbClient.queryIterativeObjects(Volume.class, volumeUris, true);

        int vplexCount = 0;
        int vnxCount = 0;
        while (volumes.hasNext()) {
            Volume volume = volumes.next();
            URI systemURI = volume.getStorageController();
            if (!NullColumnValueGetter.isNullURI(systemURI)) {
                StorageSystem system = _dbClient.queryObject(StorageSystem.class, systemURI);
                if (system != null) {
                    if (DiscoveredDataObject.Type.vplex.name().equals(system.getSystemType())) {
                        Assert.assertEquals("Thinly provisioned should be false", false, volume.getThinlyProvisioned());
                        log.info("okay, everything looks good: thinlyProvisioned is {} on migrated VPLEX volume {}",
                                volume.getThinlyProvisioned(), volume.forDisplay());
                        vplexCount++;
                    }
                    if (DiscoveredDataObject.Type.vnxblock.name().equals(system.getSystemType())) {
                        Assert.assertEquals("Thinly provisioned should be true", true, volume.getThinlyProvisioned());
                        log.info("okay, everything looks good: thinlyProvisioned is still {} on non-migrated vnx volume {}",
                                volume.getThinlyProvisioned(), volume.forDisplay());
                        vnxCount++;
                    }
                }
            }
        }

        Assert.assertEquals("We should have found two test VPLEX volumes.", 2, vplexCount);
        Assert.assertEquals("We should have found one test VNX volume.", 1, vnxCount);
    }

}
