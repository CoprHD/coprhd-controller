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
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.db.client.upgrade.callbacks.NetworkAssignedVirtualArraysInitializer;
import com.emc.storageos.db.server.DbsvcTestBase;
import com.emc.storageos.db.server.upgrade.DbSimpleMigrationTestBase;

/**
 * Test proper population of the new assigned virtual arrays field
 * for Networks.
 */
public class NetworkAssignedVirtualArraysMigrationTest extends DbSimpleMigrationTestBase {
    
    // The URI of the varray array assigned to the test Network.
    private static volatile URI varrayURI = null;
    
    @BeforeClass
    public static void setup() throws IOException {
        customMigrationCallbacks.put("1.0", new ArrayList<BaseCustomMigrationCallback>() {{
            add(new NetworkAssignedVirtualArraysInitializer());
        }});

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
        Network network = new Network();
        network.setId(URIUtil.createId(Network.class));
        network.setLabel("NetworkWithVarray");
        network.setVirtualArray(varrayURI);
        _dbClient.createObject(network);
        
        // Create another network without a virtual array.
        network = new Network();
        network.setId(URIUtil.createId(Network.class)); 
        network.setLabel("NetworkWithoutVArray");
        _dbClient.createObject(network);
    }

    @Override
    protected void verifyResults() throws Exception {
        List<URI> networkURIs = _dbClient.queryByType(Network.class, false);
        Iterator<Network> networksIter =
                _dbClient.queryIterativeObjects(Network.class, networkURIs);
        while (networksIter.hasNext()) {
            Network network = networksIter.next();
            String networkId = network.getId().toString();
            StringSet assignedVArrayIds = network.getAssignedVirtualArrays();
            if (network.getLabel().equals("NetworkWithVarray")) {
                Assert.assertTrue(String.format("Network (id=%s) should have an assigned virtual array", networkId), ((assignedVArrayIds != null) && (!assignedVArrayIds.isEmpty())));
                int count = 0;
                for (String assignedVArrayId : assignedVArrayIds) {
                    Assert.assertTrue("Network has unexpected varray assignment", assignedVArrayId.equals(varrayURI.toString()));
                    count++;
                }
                Assert.assertTrue("Network has incorrect varray count", count == 1);
            } else {
                Assert.assertTrue(String.format("Network (id=%s) should NOT have an assigned virtual array", networkId), ((assignedVArrayIds == null) || (assignedVArrayIds.isEmpty())));
            }
        }
    }
}
