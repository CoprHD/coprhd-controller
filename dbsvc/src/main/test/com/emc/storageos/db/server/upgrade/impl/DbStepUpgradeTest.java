/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.server.upgrade.impl;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.server.upgrade.DbStepSkipUpgradeTestBase;

/**
 * tests default/custom migration callbacks in step upgrade scenario
 */
public class DbStepUpgradeTest extends DbStepSkipUpgradeTestBase {
    private static final Logger log = LoggerFactory.getLogger(DbStepUpgradeTest.class);

    @Test
    public void runStepUpgradeTest() throws Exception {

        stopAll();
        setupDB(initalVersion, initalVersion, "com.emc.storageos.db.server.upgrade.util.models.old");
        prepareData1();
        stopAll();
        setupDB(initalVersion,firstUpgradeVersion, "com.emc.storageos.db.server.upgrade.util.models.updated");
        // restart with same version
        stopAll();
        setupDB(firstUpgradeVersion, firstUpgradeVersion, "com.emc.storageos.db.server.upgrade.util.models.updated");
        firstUpgradeVerifyResults();
        prepareData2();
        stopAll();
        setupDB(firstUpgradeVersion, secondUpgradeVersion, "com.emc.storageos.db.server.upgrade.util.models.updated2");
        secondUpgradeVerifyResults();
        stop();
    }
}
