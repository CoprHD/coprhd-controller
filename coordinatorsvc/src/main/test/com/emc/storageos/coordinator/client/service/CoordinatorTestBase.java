/*
 * Copyright (c) 2008-2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.coordinator.client.service;

import com.emc.storageos.coordinator.client.beacon.ServiceBeacon;
import com.emc.storageos.coordinator.client.beacon.impl.ServiceBeaconImpl;
import com.emc.storageos.coordinator.client.model.Constants;
import com.emc.storageos.coordinator.client.service.impl.CoordinatorClientImpl;
import com.emc.storageos.coordinator.client.service.impl.CoordinatorClientInetAddressMap;
import com.emc.storageos.coordinator.client.service.impl.DualInetAddress;
import com.emc.storageos.coordinator.common.impl.ServiceImpl;
import com.emc.storageos.coordinator.common.impl.ZkConnection;
import com.emc.storageos.coordinator.service.impl.CoordinatorImpl;
import com.emc.storageos.coordinator.service.impl.SpringQuorumPeerConfig;

import org.junit.BeforeClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Coordinator unit test base that contains basic startup / teardown utilities
 */
// Suppress Sonar violation of Lazy initialization of static fields should be synchronized
// There's only one thread initializing and using _dataDir and _coordinator, so it's safe.
@SuppressWarnings("squid:S2444")
public class CoordinatorTestBase {
    private static final Logger _logger = LoggerFactory.getLogger(CoordinatorTestBase.class);

    protected static File _dataDir;
    protected static CoordinatorImpl _coordinator;

    /**
     * Deletes given directory
     * 
     * @param dir
     */
    protected static void cleanDirectory(File dir) {
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file.isDirectory()) {
                cleanDirectory(file);
            } else {
                file.delete();
            }
        }
        dir.delete();
    }

    /**
     * Connects to test coordinator
     * 
     * @return connected client
     * @throws Exception
     */
    protected static CoordinatorClient connectClient() throws Exception {
        CoordinatorClientImpl client = new CoordinatorClientImpl();
        client.setZkConnection(createConnection(10 * 1000));
        client.setInetAddessLookupMap(createLocalInetAddressLookupMap());
        client.start();
        return client;
    }

    protected static CoordinatorClient connectClient(List<URI> server) throws Exception {
        CoordinatorClientImpl client = new CoordinatorClientImpl();

        ZkConnection conn = new ZkConnection();
        conn.setServer(server);
        conn.setTimeoutMs(10 * 1000);
        System.out.println("Connecting with coordinator service...");
        conn.build();
        System.out.println("Connecting with coordinator service.");

        client.setZkConnection(conn);
        client.start();
        return client;
    }

    protected static ZkConnection createConnection(int timeoutMs) throws IOException {
        ZkConnection conn = new ZkConnection();
        conn.setServer(Arrays.asList(URI.create("coordinator://localhost:2181")));
        conn.setTimeoutMs(timeoutMs);
        conn.setSiteId("fake-site-id");
        conn.build();
        return conn;
    }

    protected static ServiceBeacon createBeacon(ServiceImpl service, int timeoutMs) throws IOException {
        ServiceBeaconImpl beacon = new ServiceBeaconImpl();
        beacon.setService(service);
        beacon.setZkConnection(createConnection(timeoutMs));
        return beacon;
    }

    protected static CoordinatorClientInetAddressMap createLocalInetAddressLookupMap() throws UnknownHostException {
        CoordinatorClientInetAddressMap lookup = new CoordinatorClientInetAddressMap();
        lookup.setNodeId("localhost");
        lookup.setDualInetAddress(DualInetAddress.fromAddress("127.0.0.1"));
        Map<String, DualInetAddress> addressMap = new HashMap<>();
        addressMap.put(lookup.getNodeId(), lookup.getDualInetAddress());
        lookup.setControllerNodeIPLookupMap(addressMap);
        return lookup;
    }

    /**
     * Bootstraps test coordinator
     * 
     * @throws Exception
     */
    protected static void startCoordinator() throws Exception {
        SpringQuorumPeerConfig config = new SpringQuorumPeerConfig();
        config.setMachineId(1);
        Properties zkprop = new Properties();
        zkprop.setProperty("tickTime", "2000");
        zkprop.setProperty("dataDir", _dataDir.getAbsolutePath());
        zkprop.setProperty("clientPort", "2181");
        zkprop.setProperty("initLimit", "5");
        zkprop.setProperty("syncLimit", "2");
        zkprop.setProperty("maxClientCnxns", "0");
        zkprop.setProperty("autopurge.purgeInterval", "30");
        zkprop.setProperty("autopurge.snapRetainCount", "16");
        config.setProperties(zkprop);
        config.init();

        _coordinator = new CoordinatorImpl();
        _coordinator.setConfig(config);
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    _coordinator.start();
                } catch (IOException e) {
                    _logger.error("coordinator start failure", e);
                }
            }
        }).start();
    }

    @BeforeClass
    public static void setup() throws Exception {
        _dataDir = new File("./dqtest");
        if (_dataDir.exists() && _dataDir.isDirectory()) {
            cleanDirectory(_dataDir);
        }
        startCoordinator();
    }

}
