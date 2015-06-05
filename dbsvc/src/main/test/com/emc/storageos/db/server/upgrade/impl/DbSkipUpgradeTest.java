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

import com.emc.storageos.db.server.upgrade.DbStepSkipUpgradeTestBase;

/**
 * tests default/custom migration callbacks in skip upgrade scenarios
 */
public class DbSkipUpgradeTest extends DbStepSkipUpgradeTestBase {

    @Test
    public void runSkipUpgradeTest() throws Exception {
        
        // actual test run
        stopAll();
        setupDB(initalVersion, "com.emc.storageos.db.server.upgrade.util.models.old");
        prepareData1();
        prepareData2();
        stopAll();
        setupDB(secondUpgradeVersion, "com.emc.storageos.db.server.upgrade.util.models.updated2");
        verifyAll();
        stop();
    }
}
