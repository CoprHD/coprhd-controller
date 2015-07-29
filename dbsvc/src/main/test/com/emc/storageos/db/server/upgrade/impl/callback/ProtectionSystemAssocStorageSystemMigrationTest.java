/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2013-2015 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.db.server.upgrade.impl.callback;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.ProtectionSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.db.client.upgrade.callbacks.ProtectionSystemAssocStorageSystemMigration;
import com.emc.storageos.db.server.DbsvcTestBase;
import com.emc.storageos.db.server.upgrade.DbSimpleMigrationTestBase;

/**
 * Test proper population of the new DataObject.internalFlags field
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
public class ProtectionSystemAssocStorageSystemMigrationTest extends DbSimpleMigrationTestBase {
    private static final Logger log = LoggerFactory.getLogger(ProtectionSystemAssocStorageSystemMigrationTest.class);

    // Used for migrations tests related to RP Protection System Assoc Storage System
    private static List<URI> protectionSystemURIs = new ArrayList<URI>();

    @BeforeClass
    public static void setup() throws IOException {
        customMigrationCallbacks.put("2.1", new ArrayList<BaseCustomMigrationCallback>() {
            {
                add(new ProtectionSystemAssocStorageSystemMigration());
            }
        });

        DbsvcTestBase.setup();
    }

    @Override
    protected String getSourceVersion() {
        return "2.1";
    }

    @Override
    protected String getTargetVersion() {
        return "2.2";
    }

    @Override
    protected void prepareData() throws Exception {
        prepareProtectionSystemData();
    }

    @Override
    protected void verifyResults() throws Exception {
        verifyProtectionSystemResults();
    }

    /**
     * Prepares the ProtectionSystem data.
     * 
     * @throws Exception
     */
    private void prepareProtectionSystemData() throws Exception {
        ProtectionSystem protectionSystem = new ProtectionSystem();
        URI protectionSystemURI = URIUtil.createId(ProtectionSystem.class);
        protectionSystem.setId(protectionSystemURI);

        StringSet associatedStorageSystems = new StringSet();
        associatedStorageSystems.add("FAKE DATA");
        associatedStorageSystems.add("FAKE DATA");
        associatedStorageSystems.add("FAKE DATA");
        associatedStorageSystems.add("FAKE DATA");
        associatedStorageSystems.add("FAKE DATA");

        protectionSystem.setAssociatedStorageSystems(associatedStorageSystems);

        _dbClient.createObject(protectionSystem);

        ProtectionSystem protectionSystem2 = new ProtectionSystem();
        URI protectionSystemURI2 = URIUtil.createId(ProtectionSystem.class);
        protectionSystem2.setId(protectionSystemURI2);

        StringSet associatedStorageSystems2 = new StringSet();
        associatedStorageSystems2.add("FAKE DATA2");
        associatedStorageSystems2.add("FAKE DATA2");
        associatedStorageSystems2.add("FAKE DATA2");
        associatedStorageSystems2.add("FAKE DATA2");
        associatedStorageSystems2.add("FAKE DATA2");

        protectionSystem2.setAssociatedStorageSystems(associatedStorageSystems2);

        _dbClient.createObject(protectionSystem2);

        // Make sure our test data made it into the database as expected
        List<URI> protectionSystemURIs = _dbClient.queryByType(ProtectionSystem.class, false);
        int count = 0;
        for (@SuppressWarnings("unused")
        URI ignore : protectionSystemURIs) {
            count++;
        }
        Assert.assertTrue("Expected 2 ProtectionSystems, found: " + count, count == 2);
    }

    /**
     * Verifies the migration results for volumes.
     * 
     * @throws Exception When an error occurs verifying the volume migration
     *             results.
     */
    private void verifyProtectionSystemResults() throws Exception {
        log.info("Verifying migration of ProtectionSystems.");
        List<URI> protectionSystemURIs = _dbClient.queryByType(ProtectionSystem.class, false);
        int count = 0;
        Iterator<ProtectionSystem> protectionSystemIter =
                _dbClient.queryIterativeObjects(ProtectionSystem.class, protectionSystemURIs);
        while (protectionSystemIter.hasNext()) {
            ProtectionSystem protectionSystem = protectionSystemIter.next();
            count++;
            Assert.assertTrue("ProtectionSystem associated storage systems should be empty", protectionSystem.getAssociatedStorageSystems()
                    .isEmpty());
        }
        Assert.assertTrue("Should still have 2 ProtectionSystems after migration, not " + count, count == 2);
    }
}
