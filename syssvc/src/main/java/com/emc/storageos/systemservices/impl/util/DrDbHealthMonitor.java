/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.systemservices.impl.util;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

import com.emc.storageos.coordinator.client.model.*;
import com.emc.storageos.coordinator.client.model.DrOperationStatus.InterState;
import org.apache.curator.framework.recipes.locks.InterProcessLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.coordinator.client.service.DrUtil;
import com.emc.storageos.management.jmx.recovery.DbManagerOps;
import com.emc.storageos.svcs.errorhandling.resources.APIException;

/**
 * A thread scheduled in syssvc of the active site to monitor the db quorum availability of each standby site.
 * If a STANDBY_SYNCED site has lost db quorum for more than STANDBY_DEGRADED_THRESHOLD, it will be degraded.
 * If a STANDBY_DEGRADED site has all db instances running, it will be rejoined.
 */
public class DrDbHealthMonitor extends DrHealthMonitor {
    private static final Logger log = LoggerFactory.getLogger(DrDbHealthMonitor.class);

    private static final int STANDBY_DEGRADED_THRESHOLD = 1000 * 60 * 15; // 15 minutes, in milliseconds

    private static final String FMTURL = "service:jmx:rmi://%s:7300/jndi/rmi://%s:%d/jmxrmi";
    private static final int DEFAULTPORT = 7199;
    private static final int DEFAULTGEOPORT = 7299;
    
    private DrUtil drUtil;
    private CoordinatorClient coordinatorClient;

    public DrDbHealthMonitor() {
    }

    @Override
    public void tick() {
        if (!drUtil.isLeaderNode()) {
            log.info("Current node is not ZK leader. Do nothing");
            return;
        }

        boolean hasConnectedStandbySite = false;

        List<Site> standbySites = drUtil.listStandbySites();
        List<Site> sitesToDegrade = new ArrayList<>();
        for (Site standbySite : standbySites) {
            SiteState siteState = standbySite.getState();
            if (siteState.equals(SiteState.STANDBY_DEGRADED)) {
                checkAndRejoinSite(standbySite);
            }

            if (siteState.equals(SiteState.STANDBY_INCR_SYNCING)) {
                checkIncrementalSyncingSite(standbySite, sitesToDegrade);
            }
            
            if (siteState.equals(SiteState.STANDBY_SYNCED)) {
                SiteMonitorResult monitorResult = updateSiteMonitorResult(standbySite);
                if (monitorResult.getDbQuorumLostSince() == 0) {
                    log.info("Standby site {} is connected now", standbySite.getUuid());
                    hasConnectedStandbySite = true;
                }
                checkEligibleForDegrade(monitorResult, standbySite, sitesToDegrade);
            }
        }

        // update local site's monitor result
        // if local site is active and there's connected standby site
        Site localSite = drUtil.getLocalSite();
        if (localSite.getState() == SiteState.ACTIVE && hasConnectedStandbySite) {
            updateSiteMonitorResult(localSite);
        }

        // degrade all standby sites in a single batch
        if (!sitesToDegrade.isEmpty()) {
            degradeSites(sitesToDegrade);
        }
    }
    
    private void checkEligibleForDegrade(SiteMonitorResult monitorResult, Site standbySite, List<Site> sitesToDegrade) {
        long quorumLostTime = monitorResult.getDbQuorumLostSince();
        int degradeThreshold = drUtil.getDrIntConfig(DrUtil.KEY_STANDBY_DEGRADE_THRESHOLD, STANDBY_DEGRADED_THRESHOLD);
        if (quorumLostTime != 0 && System.currentTimeMillis() - quorumLostTime >= degradeThreshold) {
            log.info("Db quorum lost over {} ms, degrading site {}", degradeThreshold, standbySite.getUuid());
            standbySite.setLastLostQuorumTime(quorumLostTime);
            sitesToDegrade.add(standbySite);
        }
    }
    
    private void degradeSites(List<Site> sitesToDegrade) {
        InterProcessLock lock;
        try {
            lock = drUtil.getDROperationLock();
        } catch (APIException e) {
            log.warn("There are ongoing dr operations. Try again later.");
            return;
        }

        try {
            long vdcVersion = DrUtil.newVdcConfigVersion();

            // Update degraded sites
            for (Site standbySite : sitesToDegrade) {
                standbySite.setState(SiteState.STANDBY_DEGRADING);
                coordinatorClient.persistServiceConfiguration(standbySite.toConfiguration());
                drUtil.updateVdcTargetVersion(standbySite.getUuid(), SiteInfo.DR_OP_DEGRADE_STANDBY, vdcVersion);
                drUtil.recordDrOperationStatus(standbySite.getUuid(), InterState.DEGRADING_STANDBY);
            }

            // Update all other connected sites
            List<Site> connectedSites = getOtherConnectedSites(sitesToDegrade);
            for (Site site: connectedSites) {
                drUtil.updateVdcTargetVersion(site.getUuid(), SiteInfo.NONE, vdcVersion);
            }

            // Update local site
            drUtil.updateVdcTargetVersion(coordinatorClient.getSiteId(), SiteInfo.DR_OP_DEGRADE_STANDBY, vdcVersion);
        } catch (Exception e) {
            log.error("Failed to initiate degrade standby operation. Try again later", e);
        } finally {
            try {
                lock.release();
            } catch (Exception e) {
                log.error("Failed to release the dr operation lock", e);
            }
        }
    }

    private List<Site> getOtherConnectedSites(List<Site> excludedSites) {
        List<Site> sites = new ArrayList<>();

        for (Site site : drUtil.listStandbySites()) {
            if (!excludedSites.contains(site)) {
                sites.add(site);
            }

        }
        return sites;
    }

    private void checkAndRejoinSite(Site standbySite) {
        String siteId = standbySite.getUuid();
        int nodeCount = standbySite.getNodeCount();
        // We must wait until all the dbsvc/geodbsvc instances are back
        // or the data sync will fail anyways
        if (drUtil.getNumberOfLiveServices(siteId, Constants.DBSVC_NAME) == nodeCount ||
                drUtil.getNumberOfLiveServices(siteId, Constants.GEODBSVC_NAME) == nodeCount) {
            log.info("All the dbsvc/geodbsvc instances are back. Rejoining site {}", standbySite.getUuid());

            InterProcessLock lock;
            try {
                lock = drUtil.getDROperationLock();
            } catch (APIException e) {
                log.warn("There are ongoing dr operations. Try again later.");
                return;
            }

            try {
                long vdcVersion = DrUtil.newVdcConfigVersion();

                standbySite.setState(SiteState.STANDBY_RESUMING);
                coordinatorClient.persistServiceConfiguration(standbySite.toConfiguration());
                long dataRevision = vdcVersion;
                drUtil.updateVdcTargetVersion(standbySite.getUuid(), SiteInfo.DR_OP_CHANGE_DATA_REVISION, vdcVersion, dataRevision);

                // Update version on other connected standby sites if any
                for (Site site : drUtil.listSites()) {
                    if (site.equals(standbySite) ||
                            site.getUuid().equals(coordinatorClient.getSiteId())) { // target site or local site
                        continue;
                    }
                    drUtil.updateVdcTargetVersion(site.getUuid(), SiteInfo.NONE, vdcVersion);
                }

                // Update version on active site but do nothing
                drUtil.updateVdcTargetVersion(coordinatorClient.getSiteId(), SiteInfo.NONE, vdcVersion);
            } catch (Exception e) {
                log.error("Failed to initiate rejoin standby operation. Try again later", e);
            } finally {
                try {
                    lock.release();
                } catch (Exception e) {
                    log.error("Failed to release the dr operation lock", e);
                }
            }
        }
    }
    
    private void checkIncrementalSyncingSite(Site standbySite, List<Site> sitesToDegrade) {
        String siteId = standbySite.getUuid();
        int nodeCount = standbySite.getNodeCount();
        // We must wait until all the dbsvc/geodbsvc instances are back
        // or the data sync will fail anyways
        int liveDbsvcNodeCount = drUtil.getNumberOfLiveServices(siteId, Constants.DBSVC_NAME);
        int liveGeodbsvcNodeCount = drUtil.getNumberOfLiveServices(siteId, Constants.GEODBSVC_NAME);
        if ( liveDbsvcNodeCount != nodeCount ||  liveGeodbsvcNodeCount != nodeCount) {
            log.info("Not all the dbsvc/geodbsvc instances are back. dbsvc active nodes {}, geodbsvc live nodes {}", 
                    liveDbsvcNodeCount, liveGeodbsvcNodeCount);
            
            boolean quorumLost = (liveDbsvcNodeCount <= nodeCount / 2) || (liveGeodbsvcNodeCount <= nodeCount / 2);
            if (quorumLost) {
                SiteMonitorResult monitorResult = coordinatorClient.getTargetInfo(siteId, SiteMonitorResult.class);
                checkEligibleForDegrade(monitorResult, standbySite, sitesToDegrade);
            }
            return;
        }
        
        log.info("All the dbsvc/geodbsvc instances are back. {}. Check if we need reset STANDBY_INCR_SYNCING state", standbySite.getUuid());

        InterProcessLock lock;
        try {
            lock = drUtil.getDROperationLock();
        } catch (APIException e) {
            log.warn("There are ongoing DR operations. Try again later.");
            return;
        }
        
        try {
            Site site = drUtil.getSiteFromLocalVdc(standbySite.getUuid()); // reload site from zookeeper
            if (site.getState() != SiteState.STANDBY_INCR_SYNCING) {
                log.info("Skip incremental syncing state check. Site {} current state is {}", site.getUuid(), site.getState());
                return;
            }
            
            String dcName = drUtil.getCassandraDcId(site);
            Collection<String> ipAddrs = drUtil.getLocalSite().getHostIPv4AddressMap().values();
            if (ipAddrs.isEmpty()) {
                ipAddrs = drUtil.getLocalSite().getHostIPv6AddressMap().values();
            }
            for (String host : ipAddrs) {
                boolean isLocaldbsvcSynced = isDataCenterSynced(host, DEFAULTPORT, dcName);
                if (!isLocaldbsvcSynced){
                    return;
                }
                boolean isGeodbsvcSynced = isDataCenterSynced(host, DEFAULTGEOPORT, dcName);
                if (!isGeodbsvcSynced){
                    return;
                }
            }
            log.info("Data synced for dbsvc/geodbsvc of standby site {}", site.getUuid());
            site.setState(SiteState.STANDBY_SYNCED);
            drUtil.getCoordinator().persistServiceConfiguration(site.toConfiguration());
            updateSiteMonitorResult(standbySite);
        } catch (Exception e) {
            log.error("Failed to initiate reset STATNDBY_INCR_SYNCING standby operation. Try again later", e);
        } finally {
            try {
                lock.release();
            } catch (Exception e) {
                log.error("Failed to release the dr operation lock", e);
            }
        }

    }
    
    private boolean isDataCenterSynced(String host, int port, String dcName) throws MalformedObjectNameException, IOException {
        JMXServiceURL jmxUrl = new JMXServiceURL(String.format(FMTURL, host, host, port));
        Map<String, Object> env = new HashMap<String, Object>();
        JMXConnector jmxc = JMXConnectorFactory.connect(jmxUrl, env);
        MBeanServerConnection mbeanServerConn = jmxc.getMBeanServerConnection();
    
        try (DbManagerOps dbOps = new DbManagerOps(mbeanServerConn)) {
            boolean isDbSynced = dbOps.isDataCenterSynced(dcName);
            if (!isDbSynced) {
                log.info("Data is not synced to standby site on {}. JMX port {}", host, port);
                return false;
            }
        }
        return true;
    }
    
    private SiteMonitorResult updateSiteMonitorResult(Site standbySite) {
        String siteId = standbySite.getUuid();
        SiteMonitorResult monitorResult = coordinatorClient.getTargetInfo(siteId, SiteMonitorResult.class);
        if (monitorResult == null) {
            monitorResult = new SiteMonitorResult();
        }

        int nodeCount = standbySite.getNodeCount();
        boolean quorumLost = drUtil.getNumberOfLiveServices(siteId, Constants.DBSVC_NAME) <= nodeCount / 2 ||
                drUtil.getNumberOfLiveServices(siteId, Constants.GEODBSVC_NAME) <= nodeCount / 2;
        long current = System.currentTimeMillis();
        if (quorumLost && monitorResult.getDbQuorumLostSince() == 0) {
            log.warn("Db quorum lost for site {}", siteId);
            monitorResult.setDbQuorumLostSince(current);
        } else if (!quorumLost && monitorResult.getDbQuorumLostSince() != 0) {
            // reset the timer
            log.info("Db quorum restored for site {}", siteId);
            monitorResult.setDbQuorumLostSince(0);
        }
        if (!quorumLost) {
            monitorResult.setDbQuorumLastActive(current);
        }
        coordinatorClient.setTargetInfo(siteId, monitorResult);
        log.info(String.format("Update db quorum monitor result: quorum lost since %s, last active at %s, for site %s",
                monitorResult.getDbQuorumLostSince(), monitorResult.getDbQuorumLastActive(), siteId));
        return monitorResult;
    }

    public DrUtil getDrUtil() {
        return drUtil;
    }

    public void setDrUtil(DrUtil drUtil) {
        this.drUtil = drUtil;
    }

    public CoordinatorClient getCoordinatorClient() {
        return coordinatorClient;
    }

    public void setCoordinatorClient(CoordinatorClient coordinatorClient) {
        this.coordinatorClient = coordinatorClient;
    }
    
}
