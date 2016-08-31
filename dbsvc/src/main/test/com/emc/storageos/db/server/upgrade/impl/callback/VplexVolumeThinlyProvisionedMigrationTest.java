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
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.db.client.upgrade.callbacks.VplexVolumeThinlyProvisionedMigration;
import com.emc.storageos.db.server.DbsvcTestBase;
import com.emc.storageos.db.server.upgrade.DbSimpleMigrationTestBase;

/**
 * Test Class for VplexVolumeThinlyProvisionedMigration migration callback.
 */
public class VplexVolumeThinlyProvisionedMigrationTest extends DbSimpleMigrationTestBase {

    private static final Logger log = LoggerFactory.getLogger(VplexVolumeThinlyProvisionedMigrationTest.class);

    @BeforeClass
    public static void setup() throws IOException {
        customMigrationCallbacks.put("3.0", new ArrayList<BaseCustomMigrationCallback>() {
            private static final long serialVersionUID = 2L;
            {
                add(new VplexVolumeThinlyProvisionedMigration());
            }
        });

        DbsvcTestBase.setup();
    }

    @Override
    protected String getSourceVersion() {
        return "3.0";
    }

    @Override
    protected String getTargetVersion() {
        return "3.5";
    }

    @Override
    protected void prepareData() throws Exception {
        log.info("preparing data for VPLEX volumes for thinly-provisioned migration test.");

        Volume volume = new Volume();
        volume.setId(URIUtil.createId(Volume.class));
        volume.setLabel("VplexThinMigrationTester_True");
        volume.setThinlyProvisioned(true);
        _dbClient.createObject(volume);

        Volume volume2 = new Volume();
        volume2.setId(URIUtil.createId(Volume.class));
        volume2.setLabel("VplexThinMigrationTester_AlreadyFalse");
        volume2.setThinlyProvisioned(false);
        _dbClient.createObject(volume2);
    }

    @Override
    protected void verifyResults() throws Exception {
        log.info("Verifying results of VPLEX volume thinly provisioned migration test.");
        List<URI> volumeUris = _dbClient.queryByType(Volume.class, true);
        Iterator<Volume> volumes = _dbClient.queryIterativeObjects(Volume.class, volumeUris, true);

        int count = 0;
        while (volumes.hasNext()) {
            Volume volume = volumes.next();

            Assert.assertEquals("Thinly provisioned should be false", false, volume.getThinlyProvisioned());
            log.info("okay, everything looks good: thinlyProvisioned is {} on migrated VPLEX volume {}",
                    volume.getThinlyProvisioned(), volume.forDisplay());
        }

        Assert.assertEquals("We should have found two test VPLEX volumes.", 2, count);
    }

}
