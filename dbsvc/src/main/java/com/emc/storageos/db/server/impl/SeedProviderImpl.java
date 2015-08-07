/*
 * Copyright (c) 2012-2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.server.impl;

import java.net.InetAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.cassandra.locator.SeedProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.util.StringUtils;

import com.emc.storageos.coordinator.client.model.Constants;
import com.emc.storageos.coordinator.client.service.impl.CoordinatorClientImpl;
import com.emc.storageos.coordinator.client.service.impl.CoordinatorClientInetAddressMap;
import com.emc.storageos.coordinator.common.Configuration;
import com.emc.storageos.coordinator.common.impl.ZkConnection;
import com.emc.storageos.db.common.DbConfigConstants;

/**
 * Custom seed provider that uses coordinator cluster. This is used by Cassandra
 * to located other nodes to discover cluster information when joining
 */
public class SeedProviderImpl implements SeedProvider {
    private static final Logger _logger = LoggerFactory.getLogger(SeedProviderImpl.class);

    private static final String ID = "id";
    private static final String COORDINATORS = "coordinators";

    private String _id;
    private CoordinatorClientImpl _client;

    /**
     * This constructor's argument is from cassandral's yaml configuration. Here is an example
     * seed_provider:
     * - class_name: com.emc.storageos.db.server.impl.SeedProviderImpl
     * parameters:
     * - coordinators: "coordinator://127.0.0.1:2181, coordinator://127.0.0.1:3181, coordinator://127.0.0.1:4181"
     * id: "db-one
     * 
     * 
     * @param args
     * @throws Exception
     */
    public SeedProviderImpl(Map<String, String> args) throws Exception {
        _id = args.get(ID);
        if (_id == null) {
            throw new IllegalArgumentException(ID);
        }

        if (_id.contains(":")) {
            _id = StringUtils.delimitedListToStringArray(_id, ":")[1];
        }

        String coordinatorArg = args.get(COORDINATORS);
        if (coordinatorArg == null || coordinatorArg.trim().isEmpty()) {
            throw new IllegalArgumentException(COORDINATORS);
        }

        String[] coordinators = coordinatorArg.split(",", -1);
        List<URI> uri = new ArrayList<URI>(coordinators.length);
        for (String coord : coordinators) {
            if (!coord.trim().isEmpty()) {
                uri.add(URI.create(coord.trim()));
            }
        }

        ZkConnection connection = new ZkConnection();
        connection.setServer(uri);
        connection.build();

        CoordinatorClientImpl client = new CoordinatorClientImpl();
        client.setZkConnection(connection);
        ClassPathXmlApplicationContext ctx = new ClassPathXmlApplicationContext("/nodeaddrmap-var.xml");
        CoordinatorClientInetAddressMap inetAddressMap = (CoordinatorClientInetAddressMap) ctx.getBean("inetAddessLookupMap");
        if (inetAddressMap == null) {
            _logger.error("CoordinatorClientInetAddressMap is not initialized. Node address lookup will fail.");
        }
        client.setInetAddessLookupMap(inetAddressMap); // HARCODE FOR NOW
        client.start();
        _client = client;
    }

    @Override
    public List<InetAddress> getSeeds() {
        try {
            CoordinatorClientInetAddressMap nodeMap = _client.getInetAddessLookupMap();
            List<Configuration> configs = _client.queryAllConfiguration(Constants.DB_CONFIG);
            List<InetAddress> seeds = new ArrayList<>(configs.size());
            for (int i = 0; i < configs.size(); i++) {
                Configuration config = configs.get(i);
                // Bypasses item of "global" and folders of "version", just check db configurations.
                if (config.getId() == null || config.getId().equals(Constants.GLOBAL_ID)) {
                    continue;
                }
                String nodeIndex = config.getId().split("-")[1];
                String nodeId = config.getConfig(DbConfigConstants.NODE_ID);
                if (nodeId == null) {
                    // suppose that they are existing znodes from a previous version
                    // set the NODE_ID config, id is like db-x
                    nodeId = "vipr" + nodeIndex;
                    config.setConfig(DbConfigConstants.NODE_ID, nodeId);
                    config.removeConfig(DbConfigConstants.DB_IP);
                    _client.persistServiceConfiguration(config);
                }
                if (!Boolean.parseBoolean(config.getConfig(DbConfigConstants.AUTOBOOT)) ||
                        (!config.getId().equals(_id) && Boolean.parseBoolean(config.getConfig(DbConfigConstants.JOINED)))) {
                    // all non autobootstrap nodes + other nodes are used as seeds
                    InetAddress ip = null;
                    if (nodeMap != null) {
                        String ipAddress = nodeMap.getConnectableInternalAddress(nodeId);
                        _logger.debug("ip[" + i + "]: " + ipAddress);
                        ip = InetAddress.getByName(ipAddress);
                    } else {
                        ip = InetAddress.getByName(nodeId);
                    }
                    seeds.add(ip);
                    _logger.info("Seed {}", ip);
                }
            }
            return seeds;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
