/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.util;

import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.emc.sa.model.mock.StubCoordinatorClientImpl;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.coordinator.client.service.impl.CoordinatorClientImpl;
import com.emc.storageos.coordinator.client.service.impl.CoordinatorClientInetAddressMap;
import com.emc.storageos.coordinator.client.service.impl.DualInetAddress;
import com.emc.storageos.coordinator.common.impl.ZkConnection;
import org.apache.curator.test.TestingServer;

/**
 * Starts the CoordinatorSvc for testing
 * 
 * @author dmaddison
 */
public class TestCoordinatorService {
    private TestingServer zkServer;
    private ZkConnection zkConnection;
    private CoordinatorClientImpl coordinatorClient;

    private CoordinatorClientInetAddressMap createInetAddressLookup() throws Exception {
        CoordinatorClientInetAddressMap lookup = new CoordinatorClientInetAddressMap();
        lookup.setNodeId("localhost");
        lookup.setDualInetAddress(DualInetAddress.fromAddress("127.0.0.1"));
        Map<String, DualInetAddress> addressMap = new HashMap<>();
        addressMap.put(lookup.getNodeId(), lookup.getDualInetAddress());
        lookup.setControllerNodeIPLookupMap(addressMap);
        return lookup;
    }

    /** Starts the Coordinator, but first deletes all persisted Zookeeper data */
    public void startClean() throws Exception {
        start();
    }

    public void start() throws Exception {
        zkServer = new TestingServer();

        zkConnection = new ZkConnection();
        zkConnection.setServer(Collections.singletonList(new URI("coordinator://localhost:" + zkServer.getPort())));
        zkConnection.setSiteIdFile("SITEIDFILE");
        zkConnection.setSiteId("1");
        zkConnection.build();
        zkConnection.setSiteId(null);;

        coordinatorClient = new CoordinatorClientImpl();
        coordinatorClient.setZkConnection(zkConnection);
        coordinatorClient.setInetAddessLookupMap(StubCoordinatorClientImpl.createLocalAddressLookupMap());
        coordinatorClient.setVdcShortId("vdc1");
        coordinatorClient.start();
    }

    public void stop() throws Exception {
        zkServer.stop();
    }

    public CoordinatorClient getCoordinatorClient() {
        return coordinatorClient;
    }

}
