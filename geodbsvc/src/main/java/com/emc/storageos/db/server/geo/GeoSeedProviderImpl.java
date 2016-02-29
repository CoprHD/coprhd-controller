/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.server.geo;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.springframework.context.support.ClassPathXmlApplicationContext;
import com.emc.storageos.coordinator.client.service.impl.CoordinatorClientInetAddressMap;
import org.apache.cassandra.locator.SeedProvider;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.coordinator.client.service.DrUtil;
import com.emc.storageos.coordinator.client.service.impl.CoordinatorClientImpl;
import com.emc.storageos.coordinator.client.model.Constants;
import com.emc.storageos.coordinator.client.model.Site;
import com.emc.storageos.coordinator.client.model.SiteState;
import com.emc.storageos.coordinator.common.Configuration;
import com.emc.storageos.coordinator.common.Service;
import com.emc.storageos.coordinator.common.impl.ZkConnection;
import com.emc.storageos.db.common.DbConfigConstants;
import com.emc.storageos.db.server.impl.DbServiceImpl;

/**
 * Custom seed provider for geodb. In single site(or isolated site), first boot node is thought as seed, so that
 * rolling upgrade from 1.1 to 2.0 could move ahead.
 * In multiple connected sites, it reads remote seed list from arguments(template expansion from genconfig)
 */
public class GeoSeedProviderImpl implements SeedProvider {
    private static final Logger log = LoggerFactory.getLogger(GeoSeedProviderImpl.class);

    private static final String SEEDS = "seeds";
    private static final String COORDINATORS = "coordinators";

    private CoordinatorClient coordinator;
    private List<String> seeds = new ArrayList<>();
    private boolean useSeedsInLocalSite;
    /**
     * 
     * @param args
     * @throws Exception
     */
    public GeoSeedProviderImpl(Map<String, String> args) throws Exception {
        initCoordinatorClient(args);
        initSeedList(args);
        log.info("Geo seed provider initialized successfully with seeds {}",
                StringUtils.join(seeds.toArray(), ","));
    }

    @Override
    public List<InetAddress> getSeeds() {
        try {
            List<InetAddress> result = new ArrayList<>();

            for (String seed : seeds) {
                if (StringUtils.isNotEmpty(seed)) {
                    result.add(InetAddress.getByName(seed));
                }
            }

            log.info("Seeds list {}", StringUtils.join(result.toArray(), ","));
            return result;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Construct coordinator client from argument. Seed provider instance is created by cassandra
     * on demand. Not from spring context.
     * 
     * @param args
     */
    private void initCoordinatorClient(Map<String, String> args) throws IOException {
        // endpoints for coordinator in local site
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
        String siteIdFile= args.get(Constants.SITE_ID_FILE);
        connection.setSiteIdFile(siteIdFile);
        connection.build();

        CoordinatorClientImpl client = new CoordinatorClientImpl();
        client.setZkConnection(connection);
        
        ClassPathXmlApplicationContext ctx = new ClassPathXmlApplicationContext("/nodeaddrmap-var.xml");
        CoordinatorClientInetAddressMap inetAddressMap = (CoordinatorClientInetAddressMap) ctx.getBean("inetAddessLookupMap");
        if (inetAddressMap == null) {
            log.error("CoordinatorClientInetAddressMap is not initialized. Node address lookup will fail.");
        }
        client.setInetAddessLookupMap(inetAddressMap); // HARCODE FOR NOW
        client.start();
        
        DrUtil drUtil = new DrUtil(client);
        useSeedsInLocalSite = drUtil.isActiveSite() ||  SiteState.ACTIVE_DEGRADED.equals(drUtil.getLocalSite().getState());
        
        coordinator = client;
    }

    /**
     *  We select seeds based on the following rules -
     *  For DR - 
     *     Standby sites, use all nodes in active site as seeds
     *     Active site, use local nodes as seeds. The rule to select local seed is
     *        - first boot node(AUTOBOOT = false) uses itself as seed nodes so that it could boot and initialize schema
     *        - subsequent node(AUTOBOOT = true) uses other successfully booted(JOINED = true) nodes as seeds
     *  For GEO(a.k.a multivdc) -
     *     Use first node of all other vdc, and other active nodes in local vdc as seed nodes
     */
    private void initSeedList(Map<String, String> args) {
        // seed nodes in sites
        String seedsArg = args.get(SEEDS);
        String[] seedIPs = null;
        if (seedsArg != null && !seedsArg.trim().isEmpty()) {
            seedIPs = seedsArg.split(",", -1);
        }

        if (seedIPs != null) {
            // multiple site - assume seeds in other site is available
            // so just pick from config file
            for (String ip : seedIPs) {
                seeds.add(ip);
            }
        }
        // On DR standby site, only use seeds from active site. On active site
        // we use local seeds
        if (useSeedsInLocalSite) {
            // add local seed(s):
            // -For fresh install and upgraded system from 1.1,
            // get the first started node via the AUTOBOOT flag.
            // -For geodb restore/recovery,
            // get the active nodes by checking geodbsvc beacon in zk,
            // successfully booted node will register geodbsvc beacon in zk and remove the REINIT flag.
            List<Configuration> configs = getAllConfigZNodes();
            if (hasRecoveryReinitFlag(configs)) {
                seeds.addAll(getAllActiveNodes(configs));
            }
            else {
                seeds.add(getNonAutoBootNode(configs));
            }
        }
    }

    private List<Configuration> getAllConfigZNodes() {
        List<Configuration> configs = coordinator.queryAllConfiguration(coordinator.getSiteId(), Constants.GEODB_CONFIG);
        List<Configuration> result = new ArrayList<>();

        List<Configuration> leftoverConfig = coordinator.queryAllConfiguration(Constants.GEODB_CONFIG);
        configs.addAll(leftoverConfig);
        
        // filter out non config ZNodes: 2.0 and global
        for (Configuration config : configs) {
            if (isConfigZNode(config)) {
                result.add(config);
            }
        }
        return result;
    }

    private List<String> getAllActiveNodes(List<Configuration> configs) {
        List<String> ipAddrs = new ArrayList<>();
        for (Configuration config : configs) {
            // if a node has the STARTUPMODE_RESTORE_REINIT flag, it has not yet been restored.
            if (isRestoreReinit(config)) {
                continue;
            }
            if (isGeodbsvcStarted(config)) {
                ipAddrs.add(getIpAddrFromConfig(config));
            }
        }
        if (ipAddrs.isEmpty()) {
            log.warn("All the nodes in local site are inactive. This could either" +
                    " be the first started node or something went wrong");
        }
        return ipAddrs;
    }

    private String getNonAutoBootNode(List<Configuration> configs) {
        for (Configuration config : configs) {
            if (isAutoBootNode(config)) {
                continue;
            }

            return getIpAddrFromConfig(config);
        }
        throw new IllegalStateException("Cannot find a node with autoboot set to false");
    }

    private boolean isAutoBootNode(Configuration config) {
        String value = config.getConfig(DbConfigConstants.AUTOBOOT);
        return value != null && Boolean.parseBoolean(value);
    }

    private String getIpAddrFromConfig(Configuration config) {
        String nodeId = config.getConfig(DbConfigConstants.NODE_ID);
        String ipAddress;
        if (coordinator.getInetAddessLookupMap() != null) {
            ipAddress = coordinator.getInetAddessLookupMap().getConnectableInternalAddress(nodeId);
        } else {
            try {
                ipAddress = InetAddress.getByName(nodeId).getHostAddress();
            } catch (UnknownHostException e) {
                throw new IllegalStateException(e);
            }
        }
        log.info("Seed {} in local site", ipAddress);
        return ipAddress;
    }

    private boolean hasRecoveryReinitFlag(List<Configuration> configs) {
        for (Configuration config : configs) {
            if (isRestoreReinit(config) || isHibernateMode()) {
                return true;
            }
        }
        return false;
    }

    private boolean isRestoreReinit(Configuration config) {
        String value = config.getConfig(Constants.STARTUPMODE_RESTORE_REINIT);
        return value != null && Boolean.parseBoolean(value);
    }

    private boolean isHibernateMode() {
        String modeType = getDbStartupMode();
        return Constants.STARTUPMODE_HIBERNATE.equalsIgnoreCase(modeType);
    }

    private String getDbStartupMode() {
        String modeType = null;
        try {
            modeType = DbServiceImpl.instance.readStartupModeFromDisk();
        } catch (Exception ex) {
            log.error("Read start up mode file failed", ex);
        }
        return modeType;
    }

    private boolean isGeodbsvcStarted(Configuration config) {
        List<String> geodbsvcIds = getStartedGeodbsvcList();
        for (String geodbId : geodbsvcIds) {
            if (geodbId.equals(config.getId())) {
                return true;
            }
        }
        return false;
    }

    private List<String> getStartedGeodbsvcList() {
        List<String> geodbsvcIds = new ArrayList<String>();
        try {
            final String schemaVersion = coordinator.getCurrentDbSchemaVersion();
            final List<Service> serviceList = coordinator.locateAllServices(
                    Constants.GEODBSVC_NAME, schemaVersion,
                    (String) null, null);
            for (Service getdbsvc : serviceList) {
                geodbsvcIds.add(getdbsvc.getId());
            }
            log.info("Geodbsvc started status: {}", geodbsvcIds);
        } catch (Exception ex) {
            log.warn("Check geodbsvc beacon error", ex);
        }
        return geodbsvcIds;
    }

    private boolean isConfigZNode(Configuration config) {
        if (config.getId() == null || config.getId().equals(Constants.GLOBAL_ID)) {
            return false;
        }
        return true;
    }
}
