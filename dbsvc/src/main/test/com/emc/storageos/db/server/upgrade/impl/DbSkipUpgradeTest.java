/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.server.upgrade.impl;

import org.junit.Test;

import com.emc.storageos.db.server.upgrade.DbStepSkipUpgradeTestBase;

/**
 * tests default/custom migration callbacks in skip upgrade scenarios
 */
public class DbSkipUpgradeTest extends DbStepSkipUpgradeTestBase {

    @Test
    public void runSkipUpgradeTest() throws Exception {

        // actual test run
        stopAll();
        setupDB(initalVersion, initalVersion, "com.emc.storageos.db.server.upgrade.util.models.old");
        prepareData1();
        prepareData2();
        stopAll();
        setupDB(initalVersion, secondUpgradeVersion, "com.emc.storageos.db.server.upgrade.util.models.updated2");
        verifyAll();
        stop();
    }
}
