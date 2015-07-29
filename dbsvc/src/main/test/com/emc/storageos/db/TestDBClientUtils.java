/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db;

import com.emc.storageos.coordinator.client.model.DbVersionInfo;
import com.emc.storageos.coordinator.client.service.impl.CoordinatorClientImpl;
import com.emc.storageos.coordinator.client.service.impl.CoordinatorClientInetAddressMap;
import com.emc.storageos.coordinator.client.service.impl.DualInetAddress;
import com.emc.storageos.coordinator.common.impl.ZkConnection;
import com.emc.storageos.db.client.impl.DbClientContext;
import com.emc.storageos.db.client.impl.DbClientImpl;
import com.emc.storageos.db.common.VdcUtil;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import java.net.URI;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 */
public class TestDBClientUtils {
    private static final Logger log = LoggerFactory.getLogger(TestDBClientUtils.class);

    public static DbClientImpl newDBClient() throws Exception {
        ZkConnection zkConnection = new ZkConnection();
        zkConnection.setServer(Lists.newArrayList(new URI("coordinator://localhost:2181")));
        zkConnection.build();

        DualInetAddress dualInetAddress = DualInetAddress.fromAddresses("127.0.0.1", "::1");
        Map<String, DualInetAddress> addresses = Maps.newHashMap();
        addresses.put("localhost", dualInetAddress);

        CoordinatorClientInetAddressMap map = new CoordinatorClientInetAddressMap();
        map.setNodeName("standalone");
        map.setDualInetAddress(dualInetAddress);
        map.setControllerNodeIPLookupMap(addresses);

        CoordinatorClientImpl coordinatorClient = new CoordinatorClientImpl();
        coordinatorClient.setZkConnection(zkConnection);
        coordinatorClient.setInetAddessLookupMap(map);
        coordinatorClient.start();

        DbClientContext localContext = new DbClientContext();
        localContext.setKeyspaceName("StorageOS");
        localContext.setClusterName("StorageOs");

        DbClientContext geoContext = new DbClientContext();
        geoContext.setKeyspaceName("GeoStorageOs");
        geoContext.setClusterName("GeoStorageOs");

        DbVersionInfo versionInfo = new DbVersionInfo();
        versionInfo.setSchemaVersion("2.0");

        DbClientImpl client = new DbClientImpl();
        client.setDbVersionInfo(versionInfo);
        client.setLocalContext(localContext);
        client.setGeoContext(geoContext);
        client.setCoordinatorClient(coordinatorClient);
        client.setLocalContext(new DbClientContext());

        client.start();

        VdcUtil.setDbClient(client);

        return client;
    }

    public static void stop() {
        System.exit(0);
    }

    public static int size(List<URI> ids) {
        int count = 0;
        for (URI id : ids) {
            count++;
        }
        return count;
    }
}
