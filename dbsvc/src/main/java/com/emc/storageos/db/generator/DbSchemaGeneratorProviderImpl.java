/*
 * Copyright (c) 2012-2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.generator;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.cassandra.locator.SeedProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import com.emc.storageos.coordinator.client.service.impl.CoordinatorClientInetAddressMap;

public class DbSchemaGeneratorProviderImpl implements SeedProvider {
    private static final Logger _logger = LoggerFactory.getLogger(DbSchemaGeneratorProviderImpl.class);

    CoordinatorClientInetAddressMap inetAddressMap;

    public DbSchemaGeneratorProviderImpl(Map<String, String> args) throws Exception {
        ClassPathXmlApplicationContext ctx = new ClassPathXmlApplicationContext("/nodeaddrmap-var.xml");
        inetAddressMap = (CoordinatorClientInetAddressMap) ctx.getBean("inetAddessLookupMap");
        if (inetAddressMap == null) {
            _logger.error("CoordinatorClientInetAddressMap is not initialized. Node address lookup will fail.");
        }
    }

    @Override
    public List<InetAddress> getSeeds() {
        try {
            CoordinatorClientInetAddressMap nodeMap = inetAddressMap;
            List<InetAddress> seeds = new ArrayList<>();
            InetAddress ip = null;
            String ipAddress = nodeMap.getConnectableInternalAddress("standalone");
            _logger.debug("ip: " + ipAddress);
            ip = InetAddress.getByName(ipAddress);
            seeds.add(ip);
            _logger.info("Seed {}", ip);
            return seeds;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
