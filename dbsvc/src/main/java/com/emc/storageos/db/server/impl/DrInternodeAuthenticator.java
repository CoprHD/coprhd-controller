/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.server.impl;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.emc.storageos.model.property.PropertyConstants;
import org.apache.cassandra.auth.IInternodeAuthenticator;
import org.apache.cassandra.exceptions.ConfigurationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.client.model.Site;
import com.emc.storageos.coordinator.client.model.SiteState;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.coordinator.client.service.DrUtil;

/**
 * InternnodeAuthenticator for db/geodb in a DR environment.
 */
public class DrInternodeAuthenticator implements IInternodeAuthenticator {
    private static final Logger log = LoggerFactory.getLogger(DrInternodeAuthenticator.class);

    private boolean isDegraded; // default to false
    private List<InetAddress> localAddresses = new ArrayList<>();

    @Override
    public boolean authenticate(InetAddress remoteAddress, int remotePort)
    {
        if (!isDegraded || localAddresses.contains(remoteAddress)) {
            log.info("Allow internode communication for {}", remoteAddress);
            return true;
        } else {
            log.debug("Refuse internode communication for {} since current site is degraded", remoteAddress);
            return false;
        }
    }

    /**
     * Called by Cassandra startup routine to initialize this instance
     */
    @Override
    public void validateConfiguration() throws ConfigurationException
    {
        log.info("Initializing DrInternodeAuthenticator");
        CoordinatorClient coordinatorClient = DbServiceImpl.instance.getCoordinator();
        DrUtil drUtil = new DrUtil(coordinatorClient);
        Site localSite = drUtil.getLocalSite();

        isDegraded = localSite.getState().equals(SiteState.STANDBY_DEGRADED)
                || localSite.getState().equals(SiteState.STANDBY_DEGRADING);
        Collection<String> nodeAddrList = localSite.getHostIPv4AddressMap().values();
        if (!localSite.isUsingIpv4()) {
            nodeAddrList = localSite.getHostIPv6AddressMap().values();
        }
        for (String nodeAddr : nodeAddrList) {
            try {
                localAddresses.add(InetAddress.getByName(nodeAddr));
            } catch (UnknownHostException e) {
                log.error("Invalid IP address {}", nodeAddr);
            }
        }
    }
}
