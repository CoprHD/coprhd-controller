/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.management.backup;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.coordinator.client.service.impl.CoordinatorClientImpl;
import com.emc.storageos.coordinator.client.service.impl.CoordinatorClientInetAddressMap;
import com.emc.storageos.coordinator.client.service.impl.DualInetAddress;
import com.emc.storageos.coordinator.common.impl.ZkConnection;
import com.emc.storageos.coordinator.service.impl.SpringQuorumPeerConfig;
import org.apache.zookeeper.server.ServerConfig;
import org.apache.zookeeper.server.ZooKeeperServerMain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.HashMap;

/**
 * Zookeeper simulator to start Zookeeper server on localhost with specified configurations.
 */
public class ZkSimulator {
    private static final Logger log = LoggerFactory.getLogger(ZkSimulator.class);

    private SpringQuorumPeerConfig config;
    private CoordinatorClient coordinatorClient;

    public ZkSimulator() {
    }

    /**
     * Sets Zookeeper configuration file
     * 
     * @param config The instance of SpringQuorumPeerConfig
     */
    public void setConfig(SpringQuorumPeerConfig config) {
        this.config = config;
    }

    /**
     * Starts standalone Zookeeper service
     */
    public void start() throws Exception {
        final ServerConfig serverConfig = new ServerConfig();
        serverConfig.readFrom(config);
        final ZooKeeperServerMain server = new ZooKeeperServerMain();
        Thread zkService = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    server.runFromConfig(serverConfig);
                } catch (IOException e) {
                    log.error("coordinator start failure", e);
                }
            }
        });
        zkService.setDaemon(true);
        zkService.start();
        coordinatorClient = connectClient();
    }

    /**
     * Gets coordinatorClient and connect client if client is not exist or connected.
     * 
     * @return The instance of CoordinatorClient
     * @throws IOException
     */
    public CoordinatorClient getCoordinatorClient() throws IOException {
        if (coordinatorClient == null || !coordinatorClient.isConnected()) {
            coordinatorClient = connectClient();
        }
        return coordinatorClient;
    }

    /**
     * Connects to Zookeeper server
     * 
     * @return The instance of CoordinatorClient
     * @throws IOException
     */
    private CoordinatorClient connectClient() throws IOException {
        CoordinatorClientImpl client = new CoordinatorClientImpl();

        ZkConnection conn = new ZkConnection();
        URI zkUri = URI.create(
                String.format("coordinator://localhost:%s", config.getClientPortAddress().getPort()));
        conn.setServer(Arrays.asList(zkUri));
        conn.setTimeoutMs(10 * 1000);
        log.info("Connecting with coordinator service...");
        conn.build();
        log.info("Connecting with coordinator service.");

        client.setZkConnection(conn);
        CoordinatorClientInetAddressMap inetAddressMap = new CoordinatorClientInetAddressMap();
        inetAddressMap.setNodeId("standalone");
        inetAddressMap.setDualInetAddress(DualInetAddress.fromAddress("127.0.0.1"));
        inetAddressMap.setCoordinatorClient(client);
        inetAddressMap.setControllerNodeIPLookupMap(new HashMap<String, DualInetAddress>());
        client.setInetAddessLookupMap(inetAddressMap);
        client.setSysSvcName("syssvc");
        client.setSysSvcVersion("1");
        client.start();
        return client;
    }

}
