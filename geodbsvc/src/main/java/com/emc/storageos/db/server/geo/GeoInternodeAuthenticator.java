/*
 * Copyright (c) 2014-2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.server.geo;

import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Collections;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.cassandra.auth.IInternodeAuthenticator;
import org.apache.cassandra.exceptions.ConfigurationException;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.common.DbConfigConstants;
import com.emc.storageos.db.server.impl.DbServiceImpl;
import com.emc.storageos.db.server.impl.DrInternodeAuthenticator;

/**
 * InternnodeAuthenticator for geodb. It maintains a blacklist to refuse gossip connection
 * from nodes in remote vdc. The use case if for vdc disconnect/reconnect, we need block
 * geodb connection from disconnected vdc.
 * 
 * The blacklist is stored in ZK under /config/geodbconfig. The blacklist is reloaded
 * each time during dbservice startup.
 */
public class GeoInternodeAuthenticator extends DrInternodeAuthenticator implements GeoInternodeAuthenticatorMBean {
    private static final Logger log = LoggerFactory.getLogger(GeoInternodeAuthenticator.class);
    private static final String SEPARATOR = ",";
    private Set<InetAddress> blacklist;

    /**
     * Initial Blacklist
     */
    public GeoInternodeAuthenticator() {
        blacklist = new HashSet<InetAddress>();
    }

    @Override
    public boolean authenticate(InetAddress remoteAddress, int remotePort)
    {
        if (!super.authenticate(remoteAddress, remotePort)) {
            return false;
        }

        if (blacklist.contains(remoteAddress)) {
            log.debug("Refuse internode communication for {}", remoteAddress);
            return false;
        }
        log.info("Allow internode communication for {}", remoteAddress);
        return true;
    }

    /**
     * Called by Cassandra startup routine to initialize this instance
     */
    @Override
    public void validateConfiguration() throws ConfigurationException
    {
        super.validateConfiguration();
        log.info("Initialize GeoInternodeAuthenticator");
        reloadBlacklist();

        try {
            MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
            ObjectName name = new ObjectName(MBEAN_NAME);
            mbs.registerMBean(this, name);
        } catch (Exception ex) {
            log.error("Register MBean error ", ex);
            throw new ConfigurationException("Initialize GeoInternodeAuthenticator error", ex);
        }
    }

    @Override
    public void addToBlacklist(List<String> nodeList) {
        log.info("Add nodes {} to blacklist", nodeList);
        for (String nodeIp : nodeList) {
            try {
                InetAddress addr = InetAddress.getByName(nodeIp);
                blacklist.add(addr);

            } catch (UnknownHostException ex) {
                log.error("Unrecognized ip in blacklist", ex);
            }
        }
        saveBlacklist();
    }

    @Override
    public void removeFromBlacklist(List<String> nodeList) {
        log.info("Remove nodes {} from blacklist", nodeList);
        for (String nodeIp : nodeList) {
            try {
                InetAddress addr = InetAddress.getByName(nodeIp);
                blacklist.remove(addr);
            } catch (UnknownHostException ex) {
                log.warn("Unrecognized ip in blacklist", ex);
            }
        }
        saveBlacklist();
    }

    @Override
    public List<String> getBlacklist() {
        List<String> result = new ArrayList<>();
        for (InetAddress addr : blacklist) {
            result.add(addr.getHostAddress());
        }
        return Collections.unmodifiableList(result);
    }

    private void reloadBlacklist() {
        blacklist.clear();
        String peerIPs = DbServiceImpl.instance.getConfigValue(DbConfigConstants.NODE_BLACKLIST);
        if (peerIPs != null) {
            for (String nodeIp : StringUtils.split(peerIPs, SEPARATOR)) {
                try {
                    InetAddress addr = InetAddress.getByName(nodeIp);
                    blacklist.add(addr);
                } catch (UnknownHostException ex) {
                    log.error("Unrecognized ip in blacklist", ex);
                }
            }
        }
        log.info("Reload blacklist from ZK {}", blacklist);
    }

    private void saveBlacklist() {
        List<String> ipList = new ArrayList<String>();
        for (InetAddress addr : blacklist) {
            ipList.add(addr.getHostAddress());
        }
        String value = StringUtils.join(ipList, SEPARATOR);
        DbServiceImpl.instance.setConfigValue(DbConfigConstants.NODE_BLACKLIST, value);
    }

}
