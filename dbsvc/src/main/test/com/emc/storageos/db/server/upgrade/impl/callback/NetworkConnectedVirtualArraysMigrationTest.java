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
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.junit.Assert;

import org.junit.BeforeClass;

import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.Network;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.db.client.upgrade.callbacks.NetworkConnectedVirtualArraysMigration;
import com.emc.storageos.db.server.DbsvcTestBase;
import com.emc.storageos.db.server.upgrade.DbSimpleMigrationTestBase;

/**
 * Test proper population of the new assigned virtual arrays field
 * for Networks.
 */
public class NetworkConnectedVirtualArraysMigrationTest extends DbSimpleMigrationTestBase {

    // The URI of the varray array assigned to the test Network.
    private VirtualArray connectedVarray;
    private VirtualArray connectedAndassignedVarray;
    private VirtualArray assignedVarray;

    @BeforeClass
    public static void setup() throws IOException {
        customMigrationCallbacks.put("1.1", new ArrayList<BaseCustomMigrationCallback>() {
            {
                add(new NetworkConnectedVirtualArraysMigration());
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

    @SuppressWarnings("deprecation")
    @Override
    protected void prepareData() throws Exception {
        Network networkAllNull;
        Network networkAssignedOnly;
        Network networkConnectedOnly;
        Network networkAssignedAndConnected;

        connectedVarray = new VirtualArray();
        connectedVarray.setId(URIUtil.createId(VirtualArray.class));
        connectedVarray.setLabel("connectedVarray");
        _dbClient.createObject(connectedVarray);

        assignedVarray = new VirtualArray();
        assignedVarray.setId(URIUtil.createId(VirtualArray.class));
        assignedVarray.setLabel("assignedVarray");
        _dbClient.createObject(assignedVarray);

        connectedAndassignedVarray = new VirtualArray();
        connectedAndassignedVarray.setId(URIUtil.createId(VirtualArray.class));
        connectedAndassignedVarray.setLabel("connectedAndassignedVarray");
        _dbClient.createObject(connectedAndassignedVarray);

        networkAllNull = new Network();
        networkAllNull.setId(URIUtil.createId(Network.class));
        networkAllNull.setLabel("networkAllNull");
        _dbClient.createObject(networkAllNull);

        networkAssignedOnly = new Network();
        networkAssignedOnly.setId(URIUtil.createId(Network.class));
        networkAssignedOnly.setLabel("networkAssignedOnly");
        networkAssignedOnly.addAssignedVirtualArrays(
                Collections.singletonList(assignedVarray.getId().toString()));
        _dbClient.createObject(networkAssignedOnly);

        networkConnectedOnly = new Network();
        networkConnectedOnly.setId(URIUtil.createId(Network.class));
        networkConnectedOnly.setLabel("networkConnectedOnly");
        networkConnectedOnly.addConnectedVirtualArrays(
                Collections.singletonList(connectedVarray.getId().toString()));
        _dbClient.createObject(networkConnectedOnly);

        networkAssignedAndConnected = new Network();
        networkAssignedAndConnected.setId(URIUtil.createId(Network.class));
        networkAssignedAndConnected.setLabel("networkAssignedAndConnected");
        networkAssignedAndConnected.addAssignedVirtualArrays(
                Collections.singletonList(assignedVarray.getId().toString()));
        networkAssignedAndConnected.addAssignedVirtualArrays(
                Collections.singletonList(connectedAndassignedVarray.getId().toString()));
        networkAssignedAndConnected.addConnectedVirtualArrays(
                Collections.singletonList(connectedVarray.getId().toString()));
        networkAssignedAndConnected.addConnectedVirtualArrays(
                Collections.singletonList(connectedAndassignedVarray.getId().toString()));
        _dbClient.createObject(networkAssignedAndConnected);
    }

    @Override
    protected void verifyResults() throws Exception {
        List<URI> networkURIs = _dbClient.queryByType(Network.class, false);
        Iterator<Network> networksIter =
                _dbClient.queryIterativeObjects(Network.class, networkURIs);
        while (networksIter.hasNext()) {
            Network network = networksIter.next();
            String networkLabel = network.getLabel();
            if (network.getLabel().equals("networkAllNull")) {
                Assert.assertTrue(String.format("Network (label=%s) should have no assigned or connected varrays", networkLabel),
                        ((network.getAssignedVirtualArrays() == null) && (network.getConnectedVirtualArrays() == null)));
            } else if (network.getLabel().equals("networkAssignedOnly")) {
                Assert.assertTrue(String.format("Network (label=%s) should have 1 assigned and 1 connected varray", networkLabel),
                        ((network.getAssignedVirtualArrays().size() == 1) && (network.getConnectedVirtualArrays().size() == 1)));
                Assert.assertTrue(String.format("Network (label=%s) should have the same varray in assigned and connected", networkLabel),
                        (network.getAssignedVirtualArrays().iterator().next().equals(
                                network.getConnectedVirtualArrays().iterator().next())));
                Assert.assertTrue(String.format("Network (label=%s) should have 'assignedVarray' in connected", networkLabel),
                        (assignedVarray.getId().toString().equals(
                                network.getConnectedVirtualArrays().iterator().next())));
            } else if (network.getLabel().equals("networkConnectedOnly")) {
                Assert.assertTrue(String.format("Network (label=%s) should have no assigned varrays", networkLabel),
                        (network.getAssignedVirtualArrays() == null));
                Assert.assertTrue(String.format("Network (label=%s) should have 1 connected varray", networkLabel),
                        (network.getConnectedVirtualArrays().size() == 1));
                Assert.assertTrue(String.format("Network (label=%s) should have 'connectedVarray' in connected", networkLabel),
                        (connectedVarray.getId().toString().equals(
                                network.getConnectedVirtualArrays().iterator().next())));
            } else if (network.getLabel().equals("networkAssignedAndConnected")) {
                Assert.assertTrue(String.format("Network (label=%s) should have 2 assigned varrays", networkLabel),
                        (network.getAssignedVirtualArrays().size() == 2));
                Assert.assertTrue(String.format("Network (label=%s) should have 3 connected varray", networkLabel),
                        (network.getConnectedVirtualArrays().size() == 3));
                Assert.assertTrue(String.format("Network (label=%s) should have 'assignedVarray' in connected", networkLabel),
                        (network.getConnectedVirtualArrays().contains(assignedVarray.getId().toString())));
                Assert.assertTrue(String.format("Network (label=%s) should have 'connectedVarray' in connected", networkLabel),
                        (network.getConnectedVirtualArrays().contains(connectedVarray.getId().toString())));
                Assert.assertTrue(String.format("Network (label=%s) should have 'connectedAndassignedVarray' in connected", networkLabel),
                        (network.getConnectedVirtualArrays().contains(connectedAndassignedVarray.getId().toString())));
            }
        }
    }
}
