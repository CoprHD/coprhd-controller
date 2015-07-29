/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.sa.model.migration;

import com.emc.storageos.coordinator.common.Configuration;
import com.emc.storageos.db.client.model.uimodels.InitialSetup;
import com.emc.storageos.db.client.model.uimodels.migration.InitialSetupDeprecationCallback;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.db.server.DbsvcTestBase;
import com.emc.storageos.db.server.upgrade.DbSimpleMigrationTestBase;
import org.junit.Assert;
import org.junit.BeforeClass;

import java.io.IOException;
import java.util.ArrayList;

/**
 * Test migration of InitialSetup flag from DB to ZK
 * 
 * Here's the basic execution flow for the test case:
 * - setup() runs, bringing up a "pre-migration" version
 * of the database, using the DbSchemaScannerInterceptor
 * you supply to hide your new field or column family
 * when generating the "before" schema.
 * - Your implementation of prepareData() is called, allowing
 * you to use the internal _dbClient reference to create any
 * needed pre-migration test data.
 * - The database is then shutdown and restarted (without using
 * the interceptor this time), so the full "after" schema
 * is available.
 * - The dbsvc detects the diffs in the schema and executes the
 * migration callbacks as part of the startup process.
 * - Your implementation of verifyResults() is called to
 * allow you to confirm that the migration of your prepared
 * data went as expected.
 * 
 */
@SuppressWarnings("deprecation")
public class InitialSetupMigrationTest extends DbSimpleMigrationTestBase {

    @BeforeClass
    public static void setup() throws IOException {
        customMigrationCallbacks.put("1.1", new ArrayList<BaseCustomMigrationCallback>() {
            {
                add(new InitialSetupDeprecationCallback());
            }
        });
        // turn off ignoring of the sa model
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
        InitialSetup setup = new InitialSetup();
        setup.setId(InitialSetup.SINGLETON_ID);
        setup.setComplete(true);
        _dbClient.createObject(setup);

        setup = _dbClient.queryObject(InitialSetup.class, InitialSetup.SINGLETON_ID);
        Assert.assertNotNull(setup);
    }

    @Override
    protected void verifyResults() throws Exception {
        InitialSetup setup = _dbClient.queryObject(InitialSetup.class, InitialSetup.SINGLETON_ID);
        Assert.assertTrue("the InitialSetup singleton should be gone after migration",
                ((setup == null) || (setup.getInactive() == Boolean.TRUE)));

        Configuration config = getCoordinator().queryConfiguration(InitialSetup.CONFIG_KIND, InitialSetup.CONFIG_ID);
        Assert.assertNotNull("coordinator config  setup object should not be null", config);
        String complete = config.getConfig(InitialSetup.COMPLETE);
        Assert.assertTrue("coordinator config setup object should be marked comoplete",
                complete != null && complete.equals(Boolean.TRUE.toString()));
    }
}
