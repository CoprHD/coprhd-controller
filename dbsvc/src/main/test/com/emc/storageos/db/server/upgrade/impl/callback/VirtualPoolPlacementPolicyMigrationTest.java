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
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.db.client.upgrade.callbacks.VirtualPoolPlacementPolicyMigration;
import com.emc.storageos.db.server.DbsvcTestBase;
import com.emc.storageos.db.server.upgrade.DbSimpleMigrationTestBase;

/**
 * Test Class for VirtualPoolPlacementPolicyMigration migration callback.
 */
public class VirtualPoolPlacementPolicyMigrationTest extends DbSimpleMigrationTestBase {

    private static final Logger log = LoggerFactory.getLogger(VirtualPoolPlacementPolicyMigrationTest.class);

    private URI blockVpoolURI = null;
    private URI fileVpoolURI = null;

    @BeforeClass
    public static void setup() throws IOException {
        customMigrationCallbacks.put("3.1", new ArrayList<BaseCustomMigrationCallback>() {
            private static final long serialVersionUID = 2L;
            {
                add(new VirtualPoolPlacementPolicyMigration());
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
        prepareVpoolPlacementPolicyData();
    }

    @Override
    protected void verifyResults() throws Exception {
        verifyVpoolPlacementPolicyResults();
    }

    /**
     * Prepares the data for vPool placement policy tests.
     */
    private void prepareVpoolPlacementPolicyData() throws Exception {
        log.info("Preparing Virtual pools for VirtualPoolPlacementPolicyMigration");

        VirtualPool blockVpool = new VirtualPool();
        blockVpoolURI = URIUtil.createId(VirtualPool.class);
        blockVpool.setId(blockVpoolURI);
        blockVpool.setType(VirtualPool.Type.block.name());
        _dbClient.createObject(blockVpool);

        VirtualPool fileVpool = new VirtualPool();
        fileVpoolURI = URIUtil.createId(VirtualPool.class);
        fileVpool.setId(fileVpoolURI);
        fileVpool.setType(VirtualPool.Type.file.name());
        _dbClient.createObject(fileVpool);
    }

    /**
     * Verifies the results for vPool placement policy migration.
     */
    private void verifyVpoolPlacementPolicyResults() throws Exception {
        log.info("Verifying updated virtual pool results for VirtualPoolPlacementPolicyMigration.");

        VirtualPool blockVpool = _dbClient.queryObject(VirtualPool.class, blockVpoolURI);
        VirtualPool fileVpool = _dbClient.queryObject(VirtualPool.class, fileVpoolURI);

        Assert.assertNotNull("placementPolicy shouldn't be null", blockVpool.getPlacementPolicy());
        Assert.assertNull("PlacementPolicy should be null", fileVpool.getPlacementPolicy());
    }

}
