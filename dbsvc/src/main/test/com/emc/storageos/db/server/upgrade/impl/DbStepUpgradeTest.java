/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
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
        setupDB(initalVersion, "com.emc.storageos.db.server.upgrade.util.models.old");
        prepareData1();
        stopAll();
        setupDB(firstUpgradeVersion, "com.emc.storageos.db.server.upgrade.util.models.updated");
        // restart with same version
        stopAll();
        setupDB(firstUpgradeVersion, "com.emc.storageos.db.server.upgrade.util.models.updated");
        firstUpgradeVerifyResults();
        prepareData2();
        stopAll();
        setupDB(secondUpgradeVersion, "com.emc.storageos.db.server.upgrade.util.models.updated2");
        secondUpgradeVerifyResults();
        stop();
    }
}
