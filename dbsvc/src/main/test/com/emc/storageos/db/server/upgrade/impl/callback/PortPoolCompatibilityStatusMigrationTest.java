/*
 * Copyright (c) 2013-2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.server.upgrade.impl.callback;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.junit.Assert;
import org.junit.BeforeClass;

import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.DiscoveredDataObject.CompatibilityStatus;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.server.DbsvcTestBase;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.db.server.upgrade.DbSimpleMigrationTestBase;
import com.emc.storageos.db.client.upgrade.callbacks.PoolsCompatibilityStatusMigration;
import com.emc.storageos.db.client.upgrade.callbacks.PortsCompatibilityStatusMigration;

public class PortPoolCompatibilityStatusMigrationTest extends
        DbSimpleMigrationTestBase {

    private final int INSTANCES_TO_CREATE = 5;

    @BeforeClass
    public static void setup() throws IOException {
        customMigrationCallbacks.put("1.0", new ArrayList<BaseCustomMigrationCallback>() {
            {
                add(new PoolsCompatibilityStatusMigration());
                add(new PortsCompatibilityStatusMigration());
            }
        });

        DbsvcTestBase.setup();
    }

    @Override
    protected String getSourceVersion() {
        return "1.0";
    }

    @Override
    protected String getTargetVersion() {
        return "1.1";
    }

    @Override
    protected void prepareData() throws Exception {
        createDataForCompatibilityStatus(CompatibilityStatus.COMPATIBLE.toString());
        createDataForCompatibilityStatus(CompatibilityStatus.INCOMPATIBLE.toString());
        createDataForCompatibilityStatus(CompatibilityStatus.UNKNOWN.toString());
        createDataForCompatibilityStatus(null);

    }

    @Override
    protected void verifyResults() throws Exception {
        // verify ports data
        List<URI> portKeys = _dbClient.queryByType(StoragePort.class, false);
        int portCount = 0;
        Iterator<StoragePort> portObjs = _dbClient.queryIterativeObjects(StoragePort.class, portKeys);
        while (portObjs.hasNext()) {
            StoragePort port = portObjs.next();
            portCount++;
            URI storageSystemURI = port.getStorageDevice();
            StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class, storageSystemURI);
            Assert.assertNotNull("CompatibilityStatus shouldn't be null", port.getCompatibilityStatus());
            Assert.assertEquals("CompatibilityStatus should equal parent storage system's compatibility status",
                    storageSystem.getCompatibilityStatus(),
                    port.getCompatibilityStatus());
        }
        Assert.assertTrue("We should still have " + 4 * INSTANCES_TO_CREATE + " " + StoragePort.class.getSimpleName()
                + " after migration, not " + portCount, portCount == 4 * INSTANCES_TO_CREATE);
        // verify pools data
        List<URI> poolKeys = _dbClient.queryByType(StoragePool.class, false);
        int poolCount = 0;
        Iterator<StoragePool> poolObjs =
                _dbClient.queryIterativeObjects(StoragePool.class, poolKeys);
        while (poolObjs.hasNext()) {
            StoragePool pool = poolObjs.next();
            poolCount++;
            URI storageSystemURI = pool.getStorageDevice();
            StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class, storageSystemURI);
            Assert.assertNotNull("CompatibilityStatus shouldn't be null", pool.getCompatibilityStatus());
            Assert.assertEquals("CompatibilityStatus should equal parent storage system's compatibility status",
                    storageSystem.getCompatibilityStatus(),
                    pool.getCompatibilityStatus());
        }
        Assert.assertTrue("We should still have " + 4 * INSTANCES_TO_CREATE + " " + StoragePort.class.getSimpleName()
                + " after migration, not " + poolCount, poolCount == 4 * INSTANCES_TO_CREATE);

    }

    private void createDataForCompatibilityStatus(String compatibilityStatus) {
        // create storage system.
        StorageSystem parentSystem = new StorageSystem();
        parentSystem.setId(URIUtil.createId(StorageSystem.class));
        parentSystem.setCompatibilityStatus(compatibilityStatus);
        _dbClient.createObject(parentSystem);

        // create port data
        for (int i = 0; i < INSTANCES_TO_CREATE; i++) {
            StoragePort port = new StoragePort();
            port.setId(URIUtil.createId(StoragePort.class));
            port.setStorageDevice(parentSystem.getId());
            port.setCompatibilityStatus("UNDEFINED");
            _dbClient.createObject(port);
        }

        // create pool data
        for (int i = 0; i < INSTANCES_TO_CREATE; i++) {
            StoragePool port = new StoragePool();
            port.setId(URIUtil.createId(StoragePool.class));
            port.setStorageDevice(parentSystem.getId());
            port.setCompatibilityStatus("UNDEFINED");
            _dbClient.createObject(port);
        }
    }

}
