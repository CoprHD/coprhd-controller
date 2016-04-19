/*
 * Copyright (c) 2013-2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.server.upgrade.impl.callback;

import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.db.client.upgrade.callbacks.VplexVolumeAllocatedCapacityMigration;
import com.emc.storageos.db.server.DbsvcTestBase;
import com.emc.storageos.db.server.upgrade.DbSimpleMigrationTestBase;

import org.junit.BeforeClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.junit.Assert;

/**
 * Tester for the migration of a VPLEX volumes' allocatedCapacity
 * value to be equal to 0
 * 
 * @since 3.0
 * @author nbeach
 */
public class VplexVolumeAllocatedCapacityMigrationTest extends DbSimpleMigrationTestBase {

    private static final Logger log = LoggerFactory.getLogger(VplexVolumeAllocatedCapacityMigrationTest.class);

    @BeforeClass
    public static void setup() throws IOException {

        customMigrationCallbacks.put("1.1", new ArrayList<BaseCustomMigrationCallback>() {
            private static final long serialVersionUID = 1L;
            {
                // Add your implementation of migration callback below.
                add(new VplexVolumeAllocatedCapacityMigration());
            }
        });

        DbsvcTestBase.setup();
    }

    @Override
    protected String getSourceVersion() {
        return "1.1";
    }

    @Override
    protected String getTargetVersion() {
        return "2.0";
    }

    @Override
    protected void prepareData() throws Exception {
        log.info("preparing data for VPLEX vol migration test.");
        Volume volume = new Volume();
        volume.setId(URIUtil.createId(Volume.class));
        volume.setLabel("VplexMigrationTester");

        // set capacities
        volume.setAllocatedCapacity(3227516928L);
        volume.setProvisionedCapacity(8589934592L);
        volume.setCapacity(8589934592L);

        // associated vols required so that it looks like a vplex vol
        StringSet set = new StringSet();
        set.add("urn:storageos:Volume:0d3ade72-06f2-4a20-b68e-433507275da1:vdc1");
        volume.setAssociatedVolumes(set);
        _dbClient.createObject(volume);
        log.info("create VPLEX-like volume with URI: " + volume.getId());
    }

    @Override
    protected void verifyResults() throws Exception {
        log.info("Verifying results of VPLEX volume migration test.");
        List<URI> volumeUris = _dbClient.queryByType(Volume.class, true);
        Iterator<Volume> volumes = _dbClient.queryIterativeObjects(Volume.class, volumeUris, true);

        int count = 0;
        while (volumes.hasNext()) {
            Volume volume = volumes.next();

            StringSet associatedVolumes = volume.getAssociatedVolumes();
            if ((associatedVolumes != null) && (!associatedVolumes.isEmpty())) {

                log.info("looking a VPLEX volume with id: " + volume.getId());
                count = 1;

                // associated volumes indicate that this is a vplex volume
                Long allocatedCapacity = volume.getAllocatedCapacity();
                Long provisionedCapacity = volume.getProvisionedCapacity();

                Assert.assertEquals("Allocated capacity "
                        + "should be equal to 0 for VPLEX virtual volumes.",
                        allocatedCapacity, new Long(0L));
                log.info("okay, everything looks good: allocatedCapacity is {} and provisionedCapacity is {}",
                        allocatedCapacity, provisionedCapacity);
            }
        }

        Assert.assertEquals("We should have found one test VPLEX volume.", 1, count);
    }
}
