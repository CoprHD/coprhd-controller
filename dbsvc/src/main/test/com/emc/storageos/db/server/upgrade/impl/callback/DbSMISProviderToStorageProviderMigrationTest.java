/*
 * Copyright (c) 2008-2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.server.upgrade.impl.callback;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.DiscoveredDataObject.CompatibilityStatus;
import com.emc.storageos.db.client.model.DiscoveredDataObject.RegistrationStatus;
import com.emc.storageos.db.client.model.SMISProvider;
import com.emc.storageos.db.client.model.StorageProvider;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.db.client.upgrade.callbacks.SMISProviderToStorageProviderMigration;
import com.emc.storageos.db.server.DbsvcTestBase;
import com.emc.storageos.db.server.upgrade.DbSimpleMigrationTestBase;

public class DbSMISProviderToStorageProviderMigrationTest extends DbSimpleMigrationTestBase {
    private static final Logger log = LoggerFactory.getLogger(DbSMISProviderToStorageProviderMigrationTest.class);

    // Constants for testing VPLEX storage systems.
    private static volatile URI VPLEX_SYS_ID;
    private static final int VPLEX_SYS_PORT = 443;
    private static final String VPLEX_SYS_LABEL = "vplex";
    private static final String VPLEX_SYS_IP = "10.247.96.154";
    private static final String VPLEX_SYS_USER = "user";
    private static final String VPLEX_SYS_PW = "password";
    private static final String VPLEX_SYS_VERSION = "vplex";
    private static final RegistrationStatus VPLEX_SYS_REG_STATUS = RegistrationStatus.REGISTERED;
    private static final CompatibilityStatus VPLEX_SYS_COMP_STATUS = CompatibilityStatus.COMPATIBLE;

    @BeforeClass
    public static void setup() throws IOException {
        customMigrationCallbacks.put("1.1", new ArrayList<BaseCustomMigrationCallback>() {
            {
                add(new SMISProviderToStorageProviderMigration());
            }
        });

        DbsvcTestBase.setup();
        log.info("completed setup");
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
        log.info("started prepareData");
        /**
         * Mock data for migration test
         * 
         */
        StorageSystem storageSystem1 = new StorageSystem();
        StorageSystem storageSystem2 = new StorageSystem();
        StorageSystem storageSystem3 = new StorageSystem();

        SMISProvider smisProvider1 = new SMISProvider();
        SMISProvider smisProvider2 = new SMISProvider();
        SMISProvider smisProvider3 = new SMISProvider();

        storageSystem1.setId(URIUtil.createId(StorageSystem.class));
        storageSystem2.setId(URIUtil.createId(StorageSystem.class));
        storageSystem3.setId(URIUtil.createId(StorageSystem.class));
        storageSystem1.setLabel("st1");
        storageSystem2.setLabel("st2");
        storageSystem3.setLabel("st3");
        _dbClient.createObject(storageSystem1);
        _dbClient.createObject(storageSystem2);
        _dbClient.createObject(storageSystem3);

        smisProvider1.setId(URIUtil.createId(SMISProvider.class));
        smisProvider2.setId(URIUtil.createId(SMISProvider.class));
        smisProvider3.setId(URIUtil.createId(SMISProvider.class));
        smisProvider1.setLabel("smis1");
        smisProvider2.setLabel("smis2");
        smisProvider3.setLabel("smis3");

        _dbClient.createObject(smisProvider1);
        _dbClient.createObject(smisProvider2);
        _dbClient.createObject(smisProvider3);

        smisProvider1.addStorageSystem(_dbClient, storageSystem1, true);
        smisProvider1.addStorageSystem(_dbClient, storageSystem2, true);

        smisProvider2.addStorageSystem(_dbClient, storageSystem2, false);

        smisProvider3.addStorageSystem(_dbClient, storageSystem3, true);

        // Prepare VPLEX test data
        prepareVPlexTestData();

        validateSMISProviderInstances(false);
        validateStorageSystemInstances(false);
        validateStorageProviderInstances(false);
        log.info("completed prepareData");
    }

    @Override
    protected void verifyResults() throws Exception {
        validateSMISProviderInstances(true);
        validateStorageSystemInstances(true);
        validateStorageProviderInstances(true);
    }

    private void validateSMISProviderInstances(boolean isAfterMigration) {
        int size = 0;
        List<URI> keys = _dbClient.queryByType(SMISProvider.class, true);
        Iterator<SMISProvider> iter = _dbClient.queryIterativeObjects(SMISProvider.class, keys);
        while (iter.hasNext()) {
            size++;
            SMISProvider smisProvider = iter.next();
            StringSet storageSystems = smisProvider.getStorageSystems();
            Assert.assertNotNull(storageSystems);
            int storageSystemCount = 0;
            for (String storageSystemURI : storageSystems) {
                storageSystemCount++;
            }
            if (!isAfterMigration) {
                if ("smis1".equals(smisProvider.getLabel())) {
                    Assert.assertEquals("StorageSystems count is mismatch for " + smisProvider.getLabel()
                            , 2, storageSystemCount);
                } else if ("smis3".equals(smisProvider.getLabel()) || "smis2".equals(smisProvider.getLabel())) {
                    Assert.assertEquals("StorageSystems count is mismatch for " + smisProvider.getLabel(),
                            1, storageSystemCount);
                }
            }

        }
        if (isAfterMigration) {
            Assert.assertEquals("SMISProvider instance should be removed after successful migration", 0, size);
        } else {
            Assert.assertEquals("SMISProvider instance size should be 3 before migration", 3, size);
        }

    }

    private void validateStorageSystemInstances(boolean isAfterMigration) throws URISyntaxException {
        int size = 0;
        List<URI> keys = _dbClient.queryByType(StorageSystem.class, true);
        Iterator<StorageSystem> iter = _dbClient.queryIterativeObjects(StorageSystem.class, keys);
        Class dbModelCLass = null;
        if (isAfterMigration) {
            dbModelCLass = StorageProvider.class;
        } else {
            dbModelCLass = SMISProvider.class;
        }
        while (iter.hasNext()) {
            size++;
            StorageSystem storageSystem = iter.next();
            if (DiscoveredDataObject.Type.vplex.name().equals(storageSystem.getSystemType())) {
                verifyVPlexSystem(storageSystem, isAfterMigration);
            } else {
                Assert.assertTrue("Active Provider URI should point to " + dbModelCLass
                        , URIUtil.isType(storageSystem.getActiveProviderURI(), dbModelCLass));
                StringSet providers = storageSystem.getProviders();
                for (String provider : providers) {
                    URI providerURI = new URI(provider);
                    Assert.assertTrue("Provider URI should point to " + dbModelCLass
                            , URIUtil.isType(providerURI, dbModelCLass));
                }
            }
        }
        Assert.assertEquals("StorageSystem size should be 4", 4, size);
    }

    private void validateStorageProviderInstances(boolean isAfterMigration) {
        int size = 0;
        List<URI> keys = _dbClient.queryByType(StorageProvider.class, true);
        Iterator<StorageProvider> iter = _dbClient.queryIterativeObjects(StorageProvider.class, keys);
        while (iter.hasNext()) {
            size++;
            StorageProvider storageProvider = iter.next();
            if (StorageProvider.InterfaceType.vplex.name().equals(storageProvider.getInterfaceType())) {
                verifyVPlexStorageProvider(storageProvider);
            } else {
                Assert.assertEquals("Interface type should be smis", StorageProvider.InterfaceType.smis.name()
                        , storageProvider.getInterfaceType());
                StringSet storageSystems = storageProvider.getStorageSystems();
                int storageSystemCount = 0;
                for (String storageSystemURI : storageSystems) {
                    storageSystemCount++;
                }
                if (isAfterMigration) {
                    if ("smis1".equals(storageProvider.getLabel())) {
                        Assert.assertEquals("StorageSystems count is mismatch for " + storageProvider.getLabel()
                                , 2, storageSystemCount);
                    } else if ("smis3".equals(storageProvider.getLabel()) || "smis2".equals(storageProvider.getLabel())) {
                        Assert.assertEquals("StorageSystems count is mismatch for " + storageProvider.getLabel(),
                                1, storageSystemCount);
                    }
                }
            }
        }
        if (isAfterMigration) {
            Assert.assertEquals("StorageProvider size should be 4", 4, size);
        } else {
            Assert.assertEquals("StorageProvider size should be 0", 0, size);
        }
    }

    /**
     * Prepare the test data for testing migration of VPLEX storage systems
     * to the StorageProvider discovery model.
     */
    private void prepareVPlexTestData() {
        StorageSystem vplexStorageSystem = new StorageSystem();
        VPLEX_SYS_ID = URIUtil.createId(StorageSystem.class);
        vplexStorageSystem.setId(VPLEX_SYS_ID);
        vplexStorageSystem.setSystemType(DiscoveredDataObject.Type.vplex.name());
        vplexStorageSystem.setLabel(VPLEX_SYS_LABEL);
        vplexStorageSystem.setIpAddress(VPLEX_SYS_IP);
        vplexStorageSystem.setPortNumber(VPLEX_SYS_PORT);
        vplexStorageSystem.setUsername(VPLEX_SYS_USER);
        vplexStorageSystem.setPassword(VPLEX_SYS_PW);
        vplexStorageSystem.setFirmwareVersion(VPLEX_SYS_VERSION);
        vplexStorageSystem.setRegistrationStatus(VPLEX_SYS_REG_STATUS.name());
        vplexStorageSystem.setCompatibilityStatus(VPLEX_SYS_COMP_STATUS.name());
        _dbClient.createObject(vplexStorageSystem);
    }

    /**
     * Verify the changes to the VPLEX storage system after the migration.
     * 
     * @param vplexSystem Reference to the VPLEX test system.
     * 
     * @param isAfterMigration true if being called after migration completes.
     */
    private void verifyVPlexSystem(StorageSystem vplexSystem, boolean isAfterMigration) {
        if (isAfterMigration) {
            URI activeProviderURI = vplexSystem.getActiveProviderURI();
            Assert.assertNotNull("The active provider is null", activeProviderURI);
            StringSet providerIds = vplexSystem.getProviders();
            Assert.assertNotNull("The providers for the VPLEX system are null", providerIds);
            Assert.assertEquals("There should only be a single provider for the VPLEX system", 1, providerIds.size());
            String providerId = providerIds.iterator().next();
            Assert.assertEquals("The VPLEX system provider should be the active provider", activeProviderURI.toString(), providerId);
        }
    }

    /**
     * Verify the storage provider created for the VPLEX system after migration.
     * 
     * @param storageProvider The VPLEX storage provider.
     */
    private void verifyVPlexStorageProvider(StorageProvider storageProvider) {
        Assert.assertEquals("The VPLEX provider IP is not correct", VPLEX_SYS_IP,
                storageProvider.getIPAddress());
        Assert.assertEquals("The VPLEX provider Port is not correct", VPLEX_SYS_PORT,
                storageProvider.getPortNumber().intValue());
        Assert.assertEquals("The VPLEX provider use ssl is not correct", Boolean.TRUE,
                storageProvider.getUseSSL());
        Assert.assertEquals("The VPLEX provider user is not correct", VPLEX_SYS_USER,
                storageProvider.getUserName());
        Assert.assertEquals("The VPLEX provider password is not correct", VPLEX_SYS_PW,
                storageProvider.getPassword());
        Assert.assertEquals("The VPLEX provider label is not correct", VPLEX_SYS_LABEL,
                storageProvider.getLabel());
        Assert.assertEquals("The VPLEX provider version is not correct", VPLEX_SYS_VERSION,
                storageProvider.getVersionString());
        Assert.assertEquals("The VPLEX provider IP is not correct", VPLEX_SYS_IP,
                storageProvider.getIPAddress());
        Assert.assertEquals("The VPLEX provider compatibility status is not correct", VPLEX_SYS_COMP_STATUS.toString(),
                storageProvider.getCompatibilityStatus());
        Assert.assertEquals("The VPLEX provider registration status is not correct", VPLEX_SYS_REG_STATUS.toString(),
                storageProvider.getRegistrationStatus());
        StringSet managedSystemIds = storageProvider.getStorageSystems();
        Assert.assertEquals("The VPLEX provider should have one managed system", 1, managedSystemIds.size());
        String vplexSystemId = managedSystemIds.iterator().next();
        Assert.assertEquals("The VPLEX system provider managed system id is not correct", vplexSystemId, VPLEX_SYS_ID.toString());
    }
}
