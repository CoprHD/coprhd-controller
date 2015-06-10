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

package com.emc.storageos.db.server.upgrade;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.server.DbServiceTestBase;
import com.emc.storageos.db.server.upgrade.util.DbSchemaChanger;

/**
 * DB migration test framework 
 */
public abstract class DbMigrationTest extends DbServiceTestBase {
    private Logger log = LoggerFactory.getLogger(DbMigrationTest.class);    

    public abstract String getSourceSchemaVersion();
    public abstract String getTargetSchemaVersion();

    protected abstract void changeSourceSchema() throws Exception;
    protected abstract void verifySourceSchema() throws Exception;

    protected abstract void changeTargetSchema() throws Exception;
    protected abstract void verifyTargetSchema() throws Exception;

    protected abstract void prepareData() throws Exception;
    protected abstract void verifyPreparedData() throws Exception;

    protected abstract void verifyResults() throws Exception;

    protected DbSchemaChanger changer;                                                                          

    @BeforeClass
    public static void setup() throws Exception {
        removeDb();
    }

    @Test
    public void runTest() throws Exception {
        changeSourceSchema();
        verifySourceSchema();

        log.info("Calling startDb with schema version {}", getSourceSchemaVersion());
        startDb(getSourceSchemaVersion(), null);

        // prepare data for migration
        prepareData();

        // make sure that the data is created correctly 
        verifyPreparedData();

        stopDb();

        changeTargetSchema();
        verifyTargetSchema();
        
        // trigger migration
        log.info("Calling startDb with schema version {}", getTargetSchemaVersion());        
        startDb(getTargetSchemaVersion(), null);

        verifyResults();
    }

    @After
    public void done() throws Exception {
        if (changer != null)
            changer.restoreClass();

        stopDb();
    }

    @AfterClass
    public static void clean() throws Exception {
        removeDb();
    }
}
