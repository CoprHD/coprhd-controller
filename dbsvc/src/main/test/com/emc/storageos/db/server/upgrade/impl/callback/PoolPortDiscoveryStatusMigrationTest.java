/*
 * Copyright (c) 2014 EMC Corporation
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
import com.emc.storageos.db.client.model.DiscoveredDataObject.DiscoveryStatus;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.db.client.upgrade.callbacks.StoragePoolDiscoveryStatusMigration;
import com.emc.storageos.db.client.upgrade.callbacks.StoragePortDiscoveryStatusMigration;
import com.emc.storageos.db.server.DbsvcTestBase;
import com.emc.storageos.db.server.upgrade.DbSimpleMigrationTestBase;

public class PoolPortDiscoveryStatusMigrationTest extends
        DbSimpleMigrationTestBase {

    private final int INSTANCES_TO_CREATE = 5;

    @BeforeClass
    public static void setup() throws IOException {
        customMigrationCallbacks.put("2.1", new ArrayList<BaseCustomMigrationCallback>() {
            {
                add(new StoragePoolDiscoveryStatusMigration());
                add(new StoragePortDiscoveryStatusMigration());
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
        createDataForDiscoveryStatus(DiscoveryStatus.VISIBLE.name());
        createDataForDiscoveryStatus(DiscoveryStatus.NOTVISIBLE.name());
        createDataForDiscoveryStatus(null);

    }

    private void createDataForDiscoveryStatus(String discoveryStatus) {
        // create port data
        for (int i = 0; i < INSTANCES_TO_CREATE; i++) {
            StoragePort port = new StoragePort();
            port.setId(URIUtil.createId(StoragePort.class));
            port.setDiscoveryStatus(discoveryStatus);
            _dbClient.createObject(port);
        }

        // create pool data
        for (int i = 0; i < INSTANCES_TO_CREATE; i++) {
            StoragePool pool = new StoragePool();
            pool.setId(URIUtil.createId(StoragePool.class));
            pool.setDiscoveryStatus(discoveryStatus);
            _dbClient.createObject(pool);
        }
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
            Assert.assertNotNull("DiscoveryStatus shouldn't be null", port.getDiscoveryStatus());
            Assert.assertEquals("DiscoveryStatus should be VISIBLE", DiscoveryStatus.VISIBLE.name(),
                    port.getDiscoveryStatus());
        }
        Assert.assertTrue("We should still have " + 3 * INSTANCES_TO_CREATE + " " + StoragePort.class.getSimpleName()
                + " after migration, not " + portCount, portCount == 3 * INSTANCES_TO_CREATE);

        // verify pools data
        List<URI> poolKeys = _dbClient.queryByType(StoragePool.class, false);
        int poolCount = 0;
        Iterator<StoragePool> poolObjs = _dbClient.queryIterativeObjects(StoragePool.class, poolKeys);
        while (poolObjs.hasNext()) {
            StoragePool pool = poolObjs.next();
            poolCount++;
            Assert.assertNotNull("DiscoveryStatus shouldn't be null", pool.getDiscoveryStatus());
            Assert.assertEquals("DiscoveryStatus should equal VISIBLE", DiscoveryStatus.VISIBLE.name(),
                    pool.getDiscoveryStatus());
        }
        Assert.assertTrue("We should still have " + 3 * INSTANCES_TO_CREATE + " " + StoragePool.class.getSimpleName()
                + " after migration, not " + poolCount, poolCount == 3 * INSTANCES_TO_CREATE);
    }

}
