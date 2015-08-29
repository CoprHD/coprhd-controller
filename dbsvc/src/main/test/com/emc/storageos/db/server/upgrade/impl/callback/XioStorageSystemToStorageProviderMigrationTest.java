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
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.StorageProvider;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.db.client.upgrade.callbacks.XtremioStorageSystemToStorageProviderMigration;
import com.emc.storageos.db.server.DbsvcTestBase;
import com.emc.storageos.db.server.upgrade.DbSimpleMigrationTestBase;

public class XioStorageSystemToStorageProviderMigrationTest extends
        DbSimpleMigrationTestBase {

    private static final Logger log = LoggerFactory.getLogger(XioStorageSystemToStorageProviderMigrationTest.class);

    @BeforeClass
    public static void setup() throws IOException {
        customMigrationCallbacks.put("2.3", new ArrayList<BaseCustomMigrationCallback>() {
            {
                add(new XtremioStorageSystemToStorageProviderMigration());
            }
        });

        DbsvcTestBase.setup();
        log.info("completed setup");
    }

    @Override
    protected String getSourceVersion() {
        return "2.3";
    }

    @Override
    protected String getTargetVersion() {
        return "2.4";
    }

    @Override
    protected void prepareData() throws Exception {
        createStorageSystems();
    }

    @Override
    protected void verifyResults() throws Exception {
        List<URI> storageSystemKeys = _dbClient.queryByType(StorageSystem.class, true);
        Iterator<StorageSystem> iter = _dbClient.queryIterativeObjects(StorageSystem.class, storageSystemKeys);
        while (iter.hasNext()) {
            StorageSystem storageSystem = iter.next();
            if (DiscoveredDataObject.Type.xtremio.name().equals(storageSystem.getSystemType())) {
                URI providerURI = storageSystem.getActiveProviderURI();
                Assert.assertNotNull("XtremIO storage system should have an active provider associated.", providerURI);
                StorageProvider provider = _dbClient.queryObject(StorageProvider.class, providerURI);
                Assert.assertTrue("Storage provider is not associated with the xtremio storage system", provider.getStorageSystems()
                        .contains(storageSystem.getId().toString()));
            }
        }
    }

    private void createStorageSystems() {
        StorageSystem xioStorageSystem = new StorageSystem();
        xioStorageSystem.setId(URIUtil.createId(StorageSystem.class));
        xioStorageSystem.setSystemType(DiscoveredDataObject.Type.xtremio.name());
        xioStorageSystem.setNativeGuid("XTREMIO+APM00141017919");
        xioStorageSystem.setSerialNumber("APM00141017919");
        xioStorageSystem.setIpAddress("10.247.63.50");
        xioStorageSystem.setFirmwareVersion("3.0.2-14");
        _dbClient.createObject(xioStorageSystem);
    }

}
