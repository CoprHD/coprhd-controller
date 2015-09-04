/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2013-2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.db.server.upgrade.util;

import java.io.IOException;
import java.util.ArrayList;

import org.junit.Assert;
import org.junit.BeforeClass;

import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.db.server.DbsvcTestBase;
import com.emc.storageos.db.server.upgrade.DbSimpleMigrationTestBase;

/**
 * Template class for writing a migration unit test case.
 * 
 * Copy this file into a classname appropriate for your test
 * and supply an appropriate implementation at the //TODO: markers
 * 
 * Here's the basic execution flow for the test case:
 * - setup() runs, bringing up a "pre-migration" version
 * of the database. Also initializes the list of custom migration
 * callbacks that will be executed later.
 * - Your implementation of prepareData() is called, allowing
 * you to use the internal _dbClient reference to create any
 * needed pre-migration test data.
 * - The database is then shutdown and restarted with the target
 * schema version.
 * - The dbsvc detects the diffs in schema version and executes the
 * migration callbacks as part of the startup process.
 * - Your implementation of verifyResults() is called to
 * allow you to confirm that the migration of your prepared
 * data went as expected.
 */
public abstract class DbMigrationTestTemplate extends DbSimpleMigrationTestBase {

    @BeforeClass
    public static void setup() throws IOException {

        /**
         * Define a custom migration callback map.
         * The key should be the source version from getSourceVersion().
         * The value should be a list of migration callbacks under test.
         */
        customMigrationCallbacks.put("1.1", new ArrayList<BaseCustomMigrationCallback>() {
            {
                // Add your implementation of migration callback below.
                // add(new CustomMigrationCallback1());
                // add(new CustomMigrationCallback2());
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
}
