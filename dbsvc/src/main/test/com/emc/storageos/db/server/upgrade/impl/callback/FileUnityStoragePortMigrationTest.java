/*
 * Copyright (c) 2013-2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.server.upgrade.impl.callback;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.junit.BeforeClass;

import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.Network;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.VirtualPool.SystemType;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.db.client.upgrade.callbacks.FileUnityStoragePortMigration;
import com.emc.storageos.db.server.DbsvcTestBase;
import com.emc.storageos.db.server.upgrade.DbSimpleMigrationTestBase;
import com.emc.storageos.volumecontroller.impl.NativeGUIDGenerator;

/**
 * Test proper population of the new assigned virtual arrays field
 * for Networks.
 */
public class FileUnityStoragePortMigrationTest extends DbSimpleMigrationTestBase {

    @BeforeClass
    public static void setup() throws IOException {
        customMigrationCallbacks.put("3.0", new ArrayList<BaseCustomMigrationCallback>() {
            {
                add(new FileUnityStoragePortMigration());
            }
        });

        DbsvcTestBase.setup();
    }

    @Override
    protected void prepareData() throws Exception {
        StorageSystem ss;
        StoragePort sp1, sp2, sp3;
        Network net;

        ss = new StorageSystem();
        ss.setId(URIUtil.createId(StorageSystem.class));
        ss.setSystemType(SystemType.unity.name());
        ss.setLabel("unity");
        ss.setSerialNumber("VIRT1638GJ6DJM");
        _dbClient.createObject(ss);

        net = new Network();
        StringMap map = new StringMap();
        map.put("10.247.142.243", "false");
        net.setEndpointsMap(map);
        net.setId(URIUtil.createId(Network.class));

        sp1 = new StoragePort();
        sp1.setStorageDevice(ss.getId());
        sp1.setTransportType("IP");
        sp1.setPortNetworkId("10.247.142.243");
        sp1.setPortGroup("nas_1");
        sp1.setNetwork(net.getId());
        sp1.setNativeGuid(NativeGUIDGenerator.generateNativeGuid(ss, sp1.getPortNetworkId(), NativeGUIDGenerator.PORT));
        _dbClient.createObject(sp1);
    }

    @Override
    protected void verifyResults() throws Exception {
        List<URI> sportlist = _dbClient.queryByType(StoragePort.class, true);
        for (URI sportURI : sportlist) {
            StoragePort sport = _dbClient.queryObject(StoragePort.class, sportURI);
            System.out.println(sport.getPortNetworkId());
            System.out.println(sport.getIpAddress());
            System.out.println(sport.getNativeGuid());
        }
    }
}
