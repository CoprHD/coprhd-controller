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
package com.emc.storageos.db.server.upgrade.impl.callback;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.junit.Assert;
import org.junit.BeforeClass;

import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.Network;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.db.client.upgrade.callbacks.StoragePortConnectedVirtualArraysInitializer;
import com.emc.storageos.db.server.DbsvcTestBase;
import com.emc.storageos.db.server.upgrade.DbSimpleMigrationTestBase;

/**
 * Test proper population of the new connected and tagged virtual arrays fields
 * for StoragePorts.
 */
public class StoragePortConnectedVirtualArraysMigrationTest extends DbSimpleMigrationTestBase {

    // The URI of the varray array assigned to the test Network.
    private static URI varrayURI = null;

    @BeforeClass
    public static void setup() throws IOException {
        customMigrationCallbacks.put("1.0", new ArrayList<BaseCustomMigrationCallback>() {
            {
                add(new StoragePortConnectedVirtualArraysInitializer());
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

    @SuppressWarnings("deprecation")
    @Override
    protected void prepareData() throws Exception {

        // Create a virtual array.
        VirtualArray varray = new VirtualArray();
        varrayURI = URIUtil.createId(VirtualArray.class);
        varray.setId(varrayURI);
        _dbClient.createObject(varray);

        // Create a network and set the virtual array.
        Network networkWithVArray = new Network();
        URI networkWithVArrayURI = URIUtil.createId(Network.class);
        networkWithVArray.setId(networkWithVArrayURI);
        networkWithVArray.setLabel("NetworkWithVarray");
        networkWithVArray.setVirtualArray(varrayURI);
        _dbClient.createObject(networkWithVArray);

        // Create another network without a virtual array.
        Network networkWithoutVArray = new Network();
        URI networkWithoutVArrayURI = URIUtil.createId(Network.class);
        networkWithoutVArray.setId(networkWithoutVArrayURI);
        networkWithoutVArray.setLabel("NetworkWithoutVArray");
        _dbClient.createObject(networkWithoutVArray);

        // Create a storage port and set the network for
        // storage port to the network assigned to the
        // virtual array.
        StoragePort storagePortWithConnectedVArray = new StoragePort();
        storagePortWithConnectedVArray.setId(URIUtil.createId(Network.class));
        storagePortWithConnectedVArray.setLabel("StoragePortWithConnectedVArray");
        storagePortWithConnectedVArray.setNetwork(networkWithVArrayURI);
        _dbClient.createObject(storagePortWithConnectedVArray);

        // Create a storage port and set the network for
        // storage port to the network that is not assigned
        // to the virtual array.
        StoragePort storagePortWithoutConnectedVArray = new StoragePort();
        storagePortWithoutConnectedVArray.setId(URIUtil.createId(Network.class));
        storagePortWithoutConnectedVArray.setLabel("StoragePortWithoutConnectedVArray");
        storagePortWithoutConnectedVArray.setNetwork(networkWithoutVArrayURI);
        _dbClient.createObject(storagePortWithoutConnectedVArray);

        // Create s storage port without and assigned network.
        StoragePort storagePortWithoutNetwork = new StoragePort();
        storagePortWithoutNetwork.setId(URIUtil.createId(Network.class));
        storagePortWithoutNetwork.setLabel("StoragePortWithoutNetwork");
        _dbClient.createObject(storagePortWithoutNetwork);
    }

    @Override
    protected void verifyResults() throws Exception {
        List<URI> storagePortURIs = _dbClient.queryByType(StoragePort.class, false);
        Iterator<StoragePort> storagePortsIter =
                _dbClient.queryIterativeObjects(StoragePort.class, storagePortURIs);
        while (storagePortsIter.hasNext()) {
            StoragePort storagePort = storagePortsIter.next();
            String storagePortId = storagePort.getId().toString();
            StringSet connectedVArrayIds = storagePort.getConnectedVirtualArrays();
            StringSet taggedVArrayIds = storagePort.getTaggedVirtualArrays();
            if (storagePort.getLabel().equals("StoragePortWithConnectedVArray")) {
                Assert.assertTrue(String.format("StoragePort (id=%s) should have a connected virtual array", storagePortId),
                        ((connectedVArrayIds != null) && (!connectedVArrayIds.isEmpty())));
                int count = 0;
                for (String connectedVArrayId : connectedVArrayIds) {
                    Assert.assertTrue("StoragePort has unexpected connected varray", connectedVArrayId.equals(varrayURI.toString()));
                    count++;
                }
                Assert.assertTrue("StoragePort has incorrect connected varray count", count == 1);
                Assert.assertTrue(String.format("StoragePort (id=%s) should have a tagged virtual array", storagePortId),
                        ((taggedVArrayIds != null) && (!taggedVArrayIds.isEmpty())));
                count = 0;
                for (String taggedVArrayId : taggedVArrayIds) {
                    Assert.assertTrue("StoragePort has unexpected tagged varray", taggedVArrayId.equals(varrayURI.toString()));
                    count++;
                }
                Assert.assertTrue("StoragePort has incorrect tagged varray count", count == 1);
            } else {
                Assert.assertTrue(String.format("StoragePort (id=%s) should NOT have a connected virtual array", storagePortId),
                        ((connectedVArrayIds == null) || (connectedVArrayIds.isEmpty())));
                Assert.assertTrue(String.format("StoragePort (id=%s) should NOT have a tagged virtual array", storagePortId),
                        ((taggedVArrayIds == null) || (taggedVArrayIds.isEmpty())));
            }
        }
    }
}
