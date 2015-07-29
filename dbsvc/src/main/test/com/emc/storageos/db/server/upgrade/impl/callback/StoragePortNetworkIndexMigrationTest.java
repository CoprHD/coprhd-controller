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
package com.emc.storageos.db.server.upgrade.impl.callback;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.BeforeClass;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.Network;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.db.client.upgrade.callbacks.StoragePortNetworkIndexMigration;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.db.server.upgrade.DbSimpleMigrationTestBase;

/**
 * @author cgarber
 * 
 */
public class StoragePortNetworkIndexMigrationTest extends DbSimpleMigrationTestBase {

    private URI stoPortId1;
    private URI stoPortId2;
    private URI networkId1;

    @BeforeClass
    public static void setup() throws IOException {

        customMigrationCallbacks.put("1.1", new ArrayList<BaseCustomMigrationCallback>() {
            private static final long serialVersionUID = 1L;
            {
                // Add your implementation of migration callback below.
                add(new StoragePortNetworkIndexMigration());
            }
        });

        DbSimpleMigrationTestBase.initialSetup(new AlterSchema() {
            @Override
            protected void process() {
                replaceIndexCf(StoragePort.class, "network", "AltIdIndex");
            }
        });
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.db.server.upgrade.DbSimpleMigrationTestBase#getSourceVersion()
     */
    @Override
    protected String getSourceVersion() {
        return "1.1";
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.db.server.upgrade.DbSimpleMigrationTestBase#getTargetVersion()
     */
    @Override
    protected String getTargetVersion() {
        return "2.0";
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.db.server.upgrade.DbSimpleMigrationTestBase#prepareData()
     */
    @Override
    protected void prepareData() throws Exception {
        DbClient dbClient = getDbClient();

        stoPortId1 = URIUtil.createId(VirtualArray.class);
        stoPortId2 = URIUtil.createId(VirtualArray.class);
        networkId1 = URIUtil.createId(Network.class);

        StoragePort stoPort1 = new StoragePort();
        stoPort1.setId(stoPortId1);
        stoPort1.setLabel("storagePort1");
        stoPort1.setPortNetworkId(networkId1.toString());
        dbClient.createObject(stoPort1);

        StoragePort stoPort2 = new StoragePort();
        stoPort2.setId(stoPortId2);
        stoPort2.setLabel("storagePort2");
        stoPort2.setNetwork(networkId1);

        dbClient.createObject(stoPort2);

        // verify that we've reproduced the issue
        List<StoragePort> stoPorts = CustomQueryUtility.queryActiveResourcesByAltId(dbClient, StoragePort.class, "portNetworkId",
                networkId1.toString());
        Assert.assertEquals(2, stoPorts.size());

        List<StoragePort> stoPorts2 = CustomQueryUtility.queryActiveResourcesByAltId(dbClient, StoragePort.class, "network",
                networkId1.toString());
        Assert.assertEquals(2, stoPorts2.size());
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.db.server.upgrade.DbSimpleMigrationTestBase#verifyResults()
     */
    @Override
    protected void verifyResults() throws Exception {
        DbClient dbClient = getDbClient();
        List<StoragePort> stoPorts = CustomQueryUtility.queryActiveResourcesByAltId(dbClient, StoragePort.class, "portNetworkId",
                networkId1.toString());

        Assert.assertTrue(stoPorts.iterator().hasNext());
        Assert.assertEquals(1, stoPorts.size());

        List<StoragePort> stoPorts2 = CustomQueryUtility.queryActiveResourcesByAltId(dbClient, StoragePort.class, "network",
                networkId1.toString());

        Assert.assertTrue(stoPorts2.iterator().hasNext());
        Assert.assertEquals(1, stoPorts2.size());
        Assert.assertEquals(stoPortId2, stoPorts2.iterator().next().getId());
    }

}
