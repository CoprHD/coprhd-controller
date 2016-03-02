/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.coordinator.client.service;

import com.emc.storageos.coordinator.client.model.SiteNetworkState;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang.StringUtils;
import org.apache.curator.framework.recipes.locks.InterProcessLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.client.model.Constants;
import com.emc.storageos.coordinator.client.model.DrOperationStatus;
import com.emc.storageos.coordinator.client.model.PropertyInfoExt;
import com.emc.storageos.coordinator.client.model.Site;
import com.emc.storageos.coordinator.client.model.SiteInfo;
import com.emc.storageos.coordinator.client.model.SiteInfo.ActionScope;
import com.emc.storageos.coordinator.client.model.SiteState;
import com.emc.storageos.coordinator.client.service.impl.CoordinatorClientImpl;
import com.emc.storageos.coordinator.common.Configuration;
import com.emc.storageos.coordinator.common.Service;
import com.emc.storageos.coordinator.common.impl.ConfigurationImpl;
import com.emc.storageos.coordinator.common.impl.ZkPath;
import com.emc.storageos.coordinator.exceptions.CoordinatorException;
import com.emc.storageos.coordinator.exceptions.RetryableCoordinatorException;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;
import com.emc.vipr.model.sys.ClusterInfo;

/**
 * Common utility functions for Disaster Recovery
 */
public class DrUtil {
    private static final List<SiteState> ACTIVE_SITE_STATES = Arrays.asList(SiteState.ACTIVE, SiteState.STANDBY_FAILING_OVER, SiteState.STANDBY_SWITCHING_OVER);

    private static final Logger log = LoggerFactory.getLogger(DrUtil.class);
    
    private static final int COORDINATOR_PORT = 2181;
    private static final int CONNECTION_TIMEOUT = 30*1000;
    public static final String ZOOKEEPER_MODE_OBSERVER = "observer";
    public static final String ZOOKEEPER_MODE_READONLY = "read-only";
    public static final String ZOOKEEPER_MODE_LEADER = "leader";
    public static final String ZOOKEEPER_MODE_FOLLOWER = "follower";
    public static final String ZOOKEEPER_MODE_STANDALONE = "standalone";

    private static final String DR_CONFIG_KIND = "disasterRecoveryConfig";
    private static final String DR_CONFIG_ID = "global";
    private static final String DR_OPERATION_LOCK = "droperation";
    private static final int LOCK_WAIT_TIME_SEC = 5; // 5 seconds

    public static final String KEY_ADD_STANDBY_TIMEOUT = "add_standby_timeout_millis";
    public static final String KEY_REMOVE_STANDBY_TIMEOUT = "remove_standby_timeout_millis";
    public static final String KEY_PAUSE_STANDBY_TIMEOUT = "pause_standby_timout_millis";
    public static final String KEY_RESUME_STANDBY_TIMEOUT = "resume_standby_timeout_millis";
    public static final String KEY_SWITCHOVER_TIMEOUT = "switchover_timeout_millis";
    public static final String KEY_STANDBY_DEGRADE_THRESHOLD = "degrade_standby_threshold_millis";
    public static final String KEY_FAILOVER_STANDBY_SITE_TIMEOUT = "failover_standby_site_timeout_millis";
    public static final String KEY_FAILOVER_ACTIVE_SITE_TIMEOUT = "failover_active_site_timeout_millis";
    
    private CoordinatorClient coordinator;

    public DrUtil() {
    }
    
    public DrUtil(CoordinatorClient coordinator) {
        this.coordinator = coordinator;
    }
    
    public CoordinatorClient getCoordinator() {
        return coordinator;
    }

    public void setCoordinator(CoordinatorClient coordinator) {
        this.coordinator = coordinator;
    }

    /**
     * Record new DR operation
     * 
     * @param site
     */
    public void recordDrOperationStatus(Site site) {
        DrOperationStatus operation = new DrOperationStatus();
        operation.setSiteUuid(site.getUuid());
        operation.setSiteState(site.getState());
        coordinator.persistServiceConfiguration(operation.toConfiguration());
        log.info("DR operation status has been recorded: {}", operation.toString());
    }

    /**
     * The original purpose of this method is to allow QE engineers to tune DR operation timeout value by inserting timeout settings
     * into ZK via zkCli.sh so they could easily manipulate negative test cases (e.g. generate a add-standby failure in 1 minute)
     * @param key
     * @param defaultValue
     * @return ZK stored configuration item value, or defaultValue if ZNode or configuration item key not found
     */
    public int getDrIntConfig(String key, int defaultValue) {
        try {
            Configuration config = coordinator.queryConfiguration(DR_CONFIG_KIND, DR_CONFIG_ID);
            if (config != null && config.getConfig(key) != null) {
                return Integer.valueOf(config.getConfig(key));
            }
        } catch (Exception e) {
            log.warn("Exception happened when fetching DR config from ZK", e);
        }
        return defaultValue;
    }

    /**
     * Check if current site is active
     * 
     * @return true for active. otherwise false
     */
    public boolean isActiveSite() {
        try {
            SiteState state = getSiteFromLocalVdc(coordinator.getSiteId()).getState();
            return ACTIVE_SITE_STATES.contains(state);
        } catch (RetryableCoordinatorException ex) {
            // Site no initialized yet 
            if  (ServiceCode.COORDINATOR_SITE_NOT_FOUND == ex.getServiceCode()) {
                return true;
            }
            log.error("Unexpected error to check active site", ex);
        }
        return false;
    }
    
    /**
     * Check if current site is a standby site
     * 
     * @return true for standby site. otherwise false
     */
    public boolean isStandby() {
        return !isActiveSite();
    }
    
    public Site getActiveSite() {
        return getActiveSite(getLocalVdcShortId());
    }

    /**
     * Get active site object for specific VDC. 
     * 
     * @param vdcShortId
     * @return site object.
     */
    public Site getActiveSite(String vdcShortId) {
        String siteKind = String.format("%s/%s", Site.CONFIG_KIND, vdcShortId);
        for (Configuration siteConfig : coordinator.queryAllConfiguration(siteKind)) {
            Site site = new Site(siteConfig);
            if (ACTIVE_SITE_STATES.contains(site.getState())) {
                return site;
            }
        }
        
        return Site.DUMMY_ACTIVE_SITE;
    }
    /**
     * Get local site configuration
     *
     * @return local site configuration
     */
    public Site getLocalSite() {
        return getSiteFromLocalVdc(coordinator.getSiteId());
    }

    /**
     * Load site information from local vdc
     *
     * @param siteId
     * @return
     */
    public Site getSiteFromLocalVdc(String siteId) {
        String siteKind = String.format("%s/%s", Site.CONFIG_KIND, getLocalVdcShortId());
        Configuration config = coordinator.queryConfiguration(siteKind, siteId);
        if (config != null) {
            return new Site(config);
        }
        throw CoordinatorException.retryables.cannotFindSite(siteId);
    }


    /**
     * Load site network latency information from zk
     *
     * @param siteId
     * @return
     */
    public SiteNetworkState getSiteNetworkState(String siteId) {
        SiteNetworkState siteNetworkState = coordinator.getTargetInfo(siteId, SiteNetworkState.class);
        if (siteNetworkState != null) {
            return siteNetworkState;
        } else {
            return new SiteNetworkState();
        }
    }
    
    
    /**
     * List all standby sites in current vdc
     * 
     * @return list of standby sites
     */
    public List<Site> listStandbySites() {
        Site activeSite = getActiveSite();
        List<Site> result = new ArrayList<>();
        for(Site site : listSites()) {
            if (!site.getUuid().equals(activeSite.getUuid())) {
                result.add(site);
            }
        }
        return result;
    }

    /**
     * Get a map of all sites of all vdcs.
     * The keys are VDC short ids, the values are lists of sites within each vdc
     *
     * @return map of vdc -> list of sites
     */
    public Map<String, List<Site>> getVdcSiteMap() {
        Map<String, List<Site>> vdcSiteMap = new HashMap<>();
        for(Configuration vdcConfig : coordinator.queryAllConfiguration(Site.CONFIG_KIND)) {
            String siteKind = String.format("%s/%s", Site.CONFIG_KIND, vdcConfig.getId());
            List<Site> sites = new ArrayList<>();
            for (Configuration siteConfig : coordinator.queryAllConfiguration(siteKind)) {
                sites.add(new Site(siteConfig));
            }
            if (sites.size() > 0) {
                vdcSiteMap.put(vdcConfig.getId(), sites);
            }
        }
        return vdcSiteMap;
    }

    /**
     * List all sites in current vdc
     * 
     * @return list of all sites
     */
    public List<Site> listSites() {
        return listSites(getLocalVdcShortId());
    }
    
    /**
     * List all sites in given vdc
     * 
     * @return list of all sites
     */
    public List<Site> listSites(String vdcShortId) {
        List<Site> result = new ArrayList<>();
        String siteKind = String.format("%s/%s", Site.CONFIG_KIND, vdcShortId);
        for (Configuration siteConfig : coordinator.queryAllConfiguration(siteKind)) {
            result.add(new Site(siteConfig));
        }
        return result;
    }
    
    /**
     * List sites with given state
     * 
     * @param state
     * @return
     */
    public List<Site> listSitesInState(SiteState state) {
        return listSitesInState(getLocalVdcShortId(), state);
    }
    
    /**
     * List sites in given vdc with given state
     * 
     * @param state
     * @return
     */
    public List<Site> listSitesInState(String vdcShortId, SiteState state) {
        List<Site> result = new ArrayList<Site>();
        for(Site site : listSites(vdcShortId)) {
            if (site.getState().equals(state)) {
                result.add(site);
            }
        }
        return result;
    }

    /**
     * Return true if we have sites in given state
     * 
     * @param state
     * @return
     */
    public boolean hasSiteInState(SiteState state) {
         return !listSitesInState(state).isEmpty();
    }
    
    /**
     * Get the total number of nodes in all sites of a VDC
     * @return
     */
    public int getNodeCountWithinVdc() {
        int vdcNodeCount = 0;
        for (Site site : listSites()) {
            vdcNodeCount += site.getNodeCount();
        }
        return vdcNodeCount;
    }
    
    /**
     * Get number of running services in given site
     * 
     * @return number to indicate servers 
     */
    public int getNumberOfLiveServices(String siteUuid, String svcName) {
        try {
            List<Service> svcs = coordinator.locateAllSvcsAllVers(siteUuid, svcName);
            return svcs.size();
        } catch (RetryableCoordinatorException ex) {
            if (ex.getServiceCode() == ServiceCode.COORDINATOR_SVC_NOT_FOUND) {
                return 0;
            }
            throw ex;
        }
    }
    
    /**
     * Check if site is up and running
     * 
     * @param siteId
     * @return true if any syssvc is running on this site
     */
    public boolean isSiteUp(String siteId) {
        // Get service beacons for given site - - assume syssvc on all sites share same service name in beacon
        try {
            String syssvcName = ((CoordinatorClientImpl)coordinator).getSysSvcName();
            String syssvcVersion = ((CoordinatorClientImpl)coordinator).getSysSvcVersion();
            List<Service> svcs = coordinator.locateAllServices(siteId, syssvcName, syssvcVersion, null, null);

            List<String> nodeList = new ArrayList<>();
            for(Service svc : svcs) {
                nodeList.add(svc.getNodeId());
            }
            log.info("Site {} is up. active nodes {}", siteId, StringUtils.join(nodeList, ","));
            return true;
        } catch (CoordinatorException ex) {
            if (ex.getServiceCode() == ServiceCode.COORDINATOR_SVC_NOT_FOUND) {
                return false; // no service beacon found for given site
            }
            log.error("Unexpected error when checking site service becons", ex);
            return true;
        }
    }
    
    /**
     * Update SiteInfo's action and version for specified site id 
     * @param siteId site UUID
     * @param action action to take
     */
    public void updateVdcTargetVersion(String siteId, String action, long vdcTargetVersion) throws Exception {
        updateVdcTargetVersion(siteId, action, vdcTargetVersion, null, null);
    }
    
    /**
     * Update SiteInfo's action and version for specified site id 
     * @param siteId site UUID
     * @param action action to take
     * @param sourceSiteUUID source site UUID
     * @param targetSiteUUID target site UUID
     */
    public void updateVdcTargetVersion(String siteId, String action, long vdcTargetVersion, String sourceSiteUUID, String targetSiteUUID) throws Exception {
        SiteInfo siteInfo;
        SiteInfo currentSiteInfo = coordinator.getTargetInfo(siteId, SiteInfo.class);
        String targetDataRevision = null;
        
        if (currentSiteInfo != null) {
            targetDataRevision = currentSiteInfo.getTargetDataRevision();
        } else {
            targetDataRevision = SiteInfo.DEFAULT_TARGET_VERSION;
        }
        
        siteInfo = new SiteInfo(vdcTargetVersion, action, targetDataRevision, ActionScope.SITE, sourceSiteUUID, targetSiteUUID);
        coordinator.setTargetInfo(siteId, siteInfo);
        log.info("VDC target version updated to {} for site {}", siteInfo, siteId);
    }

    public void updateVdcTargetVersion(String siteId, String action, long vdcConfigVersion, long dataRevision) throws Exception {
        SiteInfo siteInfo = new SiteInfo(vdcConfigVersion, action, String.valueOf(dataRevision));
        coordinator.setTargetInfo(siteId, siteInfo);
        log.info("VDC target version updated to {} for site {}", siteInfo.getVdcConfigVersion(), siteId);
    }

    /**
     * Check if a specific site is the local site
     * @param site
     * @return true if the specified site is the local site
     */
    public boolean isLocalSite(Site site) {
        return site.getUuid().equals(coordinator.getSiteId());
    }
    
    /**
     * Generate Cassandra data center name for given site.
     * 
     * @param site
     * @return
     */
    public String getCassandraDcId(Site site) {
        String dcId = null;
        // Use vdc short id as Cassandra Data cener name for first site in a DR config. 
        // To keep the backward compatibility with geo
        if (site.getSiteShortId().equals(Constants.CONFIG_DR_FIRST_SITE_SHORT_ID)) {
            dcId = site.getVdcShortId();
        } else {
            dcId = site.getUuid();
        }

        log.info("Cassandra DC Name is {}", dcId);
        return dcId;
    }

    /**
     * Get the short id of local VDC
     */
    public String getLocalVdcShortId() {
        Configuration localVdc = coordinator.queryConfiguration(Constants.CONFIG_GEO_LOCAL_VDC_KIND,
                Constants.CONFIG_GEO_LOCAL_VDC_ID);
        if (localVdc == null) {
            return Constants.CONFIG_GEO_FIRST_VDC_SHORT_ID; // return default vdc1 in case of localVdc is not initialized yet
        }
        return localVdc.getConfig(Constants.CONFIG_GEO_LOCAL_VDC_SHORT_ID);
    }
    
    /**
     * Update current vdc short id to zk
     * 
     * @param vdcShortId
     */
    public void setLocalVdcShortId(String vdcShortId) {
        ConfigurationImpl localVdc = new ConfigurationImpl();
        localVdc.setKind(Constants.CONFIG_GEO_LOCAL_VDC_KIND);
        localVdc.setId(Constants.CONFIG_GEO_LOCAL_VDC_ID);
        localVdc.setConfig(Constants.CONFIG_GEO_LOCAL_VDC_SHORT_ID, vdcShortId);
        coordinator.persistServiceConfiguration(localVdc);
    }
    
    /**
     * Use Zookeeper 4 letter command to check status of local coordinatorsvc. The return value could 
     * be one of the following - follower, leader, observer, read-only
     * 
     * @return zookeeper mode
     */
    public String getLocalCoordinatorMode(String nodeId) {
        Socket sock = null;
        try {
            log.info("get local coordinator mode from {}:{}", nodeId, COORDINATOR_PORT);
            sock = new Socket();
            sock.connect(new InetSocketAddress(nodeId, COORDINATOR_PORT),CONNECTION_TIMEOUT);
            OutputStream output = sock.getOutputStream();
            output.write("mntr".getBytes());
            sock.shutdownOutput();
            
            BufferedReader input =
                new BufferedReader(new InputStreamReader(sock.getInputStream()));
            String answer;
            while ((answer = input.readLine()) != null) {
                if (answer.startsWith("zk_server_state")){
                    String state = StringUtils.trim(answer.substring("zk_server_state".length()));
                    log.info("Get current zookeeper mode {}", state);
                    return state;
                }
            }
            input.close();
        } catch(IOException ex) {
            log.warn("Unexpected IO errors when checking local coordinator state {}", ex.toString());
        } finally {
            try {
                if (sock != null) sock.close();
            } catch (Exception ex) {}
        }
        return null;
    }

    private String getSitePath(String siteId) {
        StringBuilder builder = new StringBuilder(ZkPath.SITES.toString());
        builder.append("/");
        builder.append(siteId);
        return builder.toString();
    }

    /**
     * Will remove 3 ZNodes:
     *     1. /config/disasterRecoverySites/${vdc_shortid}/${uuid} node
     *     2. /sites/${uuid} node
     *     3. /config/upgradetargetpropertyoverride/${uuid} node
     * @param site
     */
    public void removeSite(Site site) {
        coordinator.removeServiceConfiguration(site.toConfiguration());

        coordinator.deletePath(getSitePath(site.getUuid()));

        ConfigurationImpl sitePropsCfg = new ConfigurationImpl();
        sitePropsCfg.setId(site.getUuid());
        sitePropsCfg.setKind(PropertyInfoExt.TARGET_PROPERTY);
        coordinator.removeServiceConfiguration(sitePropsCfg);

        log.info("Removed site {} configuration from ZK", site.getUuid());
    }

    public static long newVdcConfigVersion() {
        return System.currentTimeMillis();
    }

    /**
     * @return DR operation lock only when successfully acquired lock and there's no ongoing DR operation on any site, throw Exception otherwise
     */
    public InterProcessLock getDROperationLock() {
        return getDROperationLock(true);
    }

    /**
     * @param checkAllSitesOperations
     * @return DR operation lock only when successfully acquired lock and there's no ongoing DR operation, throw Exception otherwise
     */
    public InterProcessLock getDROperationLock(boolean checkAllSitesOperations) {
        // Try to acquire lock, succeed or throw Exception
        InterProcessLock lock = coordinator.getLock(DR_OPERATION_LOCK);
        boolean acquired;
        try {
            acquired = lock.acquire(LOCK_WAIT_TIME_SEC, TimeUnit.SECONDS);
        } catch (Exception e) {
            try {
                lock.release();
            } catch (Exception ex) {
                log.error("Fail to release DR operation lock", ex);
            }
            throw APIException.internalServerErrors.failToAcquireDROperationLock();
        }
        if (!acquired) {
            throw APIException.internalServerErrors.failToAcquireDROperationLock();
        }

        // Has successfully acquired lock
        if (!checkAllSitesOperations) {
            return lock;
        }

        // Check if there's ongoing DR operation on any site, if there is, release lock and throw exception
        Site ongoingSite = null;
        List<Site> sites = listSites();
        for (Site site : sites) {
            if (site.getState().isDROperationOngoing()) {
                ongoingSite = site;
                break;
            }
        }

        if (ongoingSite != null) {
            try {
                lock.release();
            } catch (Exception e) {
                log.error("Fail to release DR operation lock", e);
            }
            throw APIException.internalServerErrors.concurrentDROperationNotAllowed(ongoingSite.getName(), ongoingSite.getState()
                    .toString());
        }

        return lock;

    }
    
    /**
     * Check if it is a multi-vdc configuration 
     * 
     * @return true if there are more than 1 vdc
     */
    public boolean isMultivdc() {
        return getVdcSiteMap().keySet().size() > 1;
    }

    /**
     * Get all vdc ids except local vdc
     * 
     * @return list of vdc ids
     */
    public List<String> getOtherVdcIds() {
        Set<String> vdcIdSet = getVdcSiteMap().keySet();
        String localVdcId = this.getLocalVdcShortId();
        vdcIdSet.remove(localVdcId);
        List<String> vdcIds = new ArrayList<>();
        vdcIds.addAll(vdcIdSet);
        return vdcIds;
    }

    /**
     * Check if all sites of local vdc are
     */
    public boolean isAllSitesStable() {
        boolean bStable = true;

        for (Site site : listSites()) {
            if (site.getState().isDROperationOngoing()) {
                return false;
            }

            int nodeCount = site.getNodeCount();
            ClusterInfo.ClusterState state = coordinator.getControlNodesState(site.getUuid(), nodeCount);
            if (state != ClusterInfo.ClusterState.STABLE) {
                log.info("Site {} is not stable {}", site.getUuid(), state);
                bStable = false;
            }
        }
        return bStable;
    }

    /**
     * ping target host with port to check connectivity
     *
     * @param hostAddress host address 
     * @param port port number
     * @param timeout timeout value as ms
     * @return delay in ms if the specified host responded, -1 if failed
     */
    public double testPing(String hostAddress, int port, int timeout) {
        InetAddress inetAddress = null;
        InetSocketAddress socketAddress = null;
        Socket socket = new Socket();
        long timeToRespond = -1;
        long start, stop;
    
        try {
            inetAddress = InetAddress.getByName(hostAddress);
    
            socketAddress = new InetSocketAddress(inetAddress, port);
    
            start = System.nanoTime();
            socket.connect(socketAddress, timeout);
            stop = System.nanoTime();
            timeToRespond = (stop - start);
        } catch (Exception e) {
            log.error(String.format("Fail to check cross-site network latency to node {} with Exception: ",hostAddress),e);
            return -1;
        } finally {
            try {
                if (socket.isConnected()) {
                    socket.close();
                }
            } catch (Exception e) {
                log.error(String.format("Fail to close connection to node {} with Exception: ",hostAddress),e);
            }
        }
    
        //the ping suceeded, convert from ns to ms
        return timeToRespond/1000000.0;
    }
}
