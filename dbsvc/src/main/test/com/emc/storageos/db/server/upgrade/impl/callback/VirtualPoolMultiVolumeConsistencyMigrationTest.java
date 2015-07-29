/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.db.server.upgrade.impl.callback;

import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.VpoolProtectionVarraySettings;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.db.client.upgrade.callbacks.VirtualPoolMultiVolumeConsistencyMigration;
import com.emc.storageos.db.server.DbsvcTestBase;
import com.emc.storageos.db.server.upgrade.DbSimpleMigrationTestBase;

import org.junit.BeforeClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;

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
public class VirtualPoolMultiVolumeConsistencyMigrationTest extends DbSimpleMigrationTestBase {

    private static final Logger log = LoggerFactory.getLogger(VirtualPoolMultiVolumeConsistencyMigrationTest.class);
    // Used for migrations tests related to RP VirtualPools.
    private static List<URI> rpTestVirtualPoolURIs = new ArrayList<URI>();

    @BeforeClass
    public static void setup() throws IOException {
        customMigrationCallbacks.put("1.1", new ArrayList<BaseCustomMigrationCallback>() {
            {
                add(new VirtualPoolMultiVolumeConsistencyMigration());
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

    @Override
    protected void prepareData() throws Exception {
        prepareVirtualPoolData();
    }

    @Override
    protected void verifyResults() throws Exception {
        verifyVirtualPoolResults();
    }

    /**
     * Prepares the data for RP volume tests.
     * 
     * @throws Exception When an error occurs preparing the RP volume data.
     */
    private void prepareVirtualPoolData() throws Exception {
        log.info("Preparing VirtualPool Data for VirtualPoolMultiVolumeConsistencyMigration.");
        VirtualArray virtualArray = new VirtualArray();
        URI virtualArrayURI = URIUtil.createId(VirtualArray.class);
        virtualArray.setId(virtualArrayURI);
        virtualArray.setLabel("virtualArray1");
        _dbClient.createObject(virtualArray);

        for (int i = 1; i <= 3; i++) {
            VpoolProtectionVarraySettings protectionSettings = new VpoolProtectionVarraySettings();
            URI protectionSettingsURI = URIUtil.createId(VpoolProtectionVarraySettings.class);
            protectionSettings.setId(protectionSettingsURI);
            protectionSettings.setJournalSize("min");
            _dbClient.createObject(protectionSettings);

            VirtualPool virtualPool = new VirtualPool();
            URI virtualPoolURI = URIUtil.createId(VirtualPool.class);
            rpTestVirtualPoolURIs.add(virtualPoolURI);
            virtualPool.setId(virtualPoolURI);
            virtualPool.setLabel("rpVirtualPool" + i);
            StringMap protectionVarraySettings = new StringMap();
            protectionVarraySettings.put(virtualArrayURI.toString(), protectionSettingsURI.toString());
            virtualPool.setProtectionVarraySettings(protectionVarraySettings);
            _dbClient.createObject(virtualPool);
        }
    }

    /**
     * Verifies the migration results for VirtualPool.
     * 
     * @throws Exception When an error occurs verifying the VirtualPool
     *             migration results.
     */
    private void verifyVirtualPoolResults() throws Exception {
        log.info("Verifying VirtualPoolMultiVolumeConsistencyMigration.");
        for (URI virtualPoolURI : rpTestVirtualPoolURIs) {
            VirtualPool virtualPool = _dbClient.queryObject(VirtualPool.class, virtualPoolURI);

            // The test VirtualPool should have RP protection specified
            Assert.assertTrue("VirtualPool %s does not specify RecoverPoint protection",
                    VirtualPool.vPoolSpecifiesProtection(virtualPool));

            if (VirtualPool.vPoolSpecifiesProtection(virtualPool)) {
                Assert.assertTrue("Multi-volume consistency should be set for a RecoverPoint virtual pool",
                        virtualPool.getMultivolumeConsistency());
            }
        }
    }
}
