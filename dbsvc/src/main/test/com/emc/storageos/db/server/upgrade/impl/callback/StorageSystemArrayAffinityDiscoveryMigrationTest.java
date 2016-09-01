/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.server.upgrade.impl.callback;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.db.client.upgrade.callbacks.StorageSystemArrayAffinityDiscoveryMigration;
import com.emc.storageos.db.server.DbsvcTestBase;
import com.emc.storageos.db.server.upgrade.DbSimpleMigrationTestBase;

/**
 * Test Class for StorageSystemArrayAffinityDiscoveryMigration migration callback.
 */
public class StorageSystemArrayAffinityDiscoveryMigrationTest extends DbSimpleMigrationTestBase {

    private static final Logger log = LoggerFactory.getLogger(StorageSystemArrayAffinityDiscoveryMigrationTest.class);

    private URI systemURI = null;

    @BeforeClass
    public static void setup() throws IOException {
        customMigrationCallbacks.put("3.1", new ArrayList<BaseCustomMigrationCallback>() {
            private static final long serialVersionUID = 2L;
            {
                add(new StorageSystemArrayAffinityDiscoveryMigration());
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
        prepareStorageSystemArrayAffinityDiscoveryData();
    }

    @Override
    protected void verifyResults() throws Exception {
        verifyStorageSystemArrayAffinityDiscoveryResults();
    }

    /**
     * Prepares the data for storage system array affinity discovery tests.
     */
    private void prepareStorageSystemArrayAffinityDiscoveryData() throws Exception {
        log.info("Preparing storage system for StorageSystemArrayAffinityDiscoveryMigration");

        StorageSystem system = new StorageSystem();
        systemURI = URIUtil.createId(StorageSystem.class);
        system.setId(systemURI);
        _dbClient.createObject(system);

        StorageSystem system1 = _dbClient.queryObject(StorageSystem.class, systemURI);
        log.info("{}, {}", system1.getArrayAffinityStatus(), system1.getLastArrayAffinityRunTime());

    }

    /**
     * Verifies the results for storage system array affinity discovery migration.
     */
    private void verifyStorageSystemArrayAffinityDiscoveryResults() throws Exception {
        log.info("Verifying updated storage system results for StorageSystemArrayAffinityDiscoveryMigration.");

        StorageSystem system = _dbClient.queryObject(StorageSystem.class, systemURI);

        Assert.assertNotNull("arrayAffinityStatus shouldn't be null", system.getArrayAffinityStatus());
        Assert.assertNotNull("lastArrayAffinityRunTime shouldn't be null", system.getLastArrayAffinityRunTime());
        Assert.assertNotNull("nextArrayAffinityRunTime shouldn't be null", system.getNextArrayAffinityRunTime());
        Assert.assertNotNull("successArrayAffinityTime shouldn't be null", system.getSuccessArrayAffinityTime());
    }

}
