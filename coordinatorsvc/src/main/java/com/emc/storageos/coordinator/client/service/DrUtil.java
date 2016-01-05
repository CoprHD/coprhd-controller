/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.coordinator.client.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.client.model.Constants;
import com.emc.storageos.coordinator.client.model.Site;
import com.emc.storageos.coordinator.client.model.SiteInfo;
import com.emc.storageos.coordinator.client.model.SiteState;
import com.emc.storageos.coordinator.client.service.impl.CoordinatorClientImpl;
import com.emc.storageos.coordinator.common.Configuration;
import com.emc.storageos.coordinator.common.Service;
import com.emc.storageos.coordinator.exceptions.CoordinatorException;
import com.emc.storageos.coordinator.exceptions.RetryableCoordinatorException;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

/**
 * Common utility functions for Disaster Recovery
 */
public class DrUtil {
    private static final List<SiteState> ACTIVE_SITE_STATES = Arrays.asList(SiteState.ACTIVE, SiteState.STANDBY_FAILING_OVER, SiteState.STANDBY_SWITCHING_OVER);

    private static final Logger log = LoggerFactory.getLogger(DrUtil.class);
    
    private static final int COORDINATOR_PORT = 2181;
    public static final String ZOOKEEPER_MODE_LEADER = "leader";
    public static final String ZOOKEEPER_MODE_OBSERVER = "observer";
    public static final String ZOOKEEPER_MODE_READONLY = "read-only";

    private static final String DR_CONFIG_KIND = "disasterRecoveryConfig";
    private static final String DR_CONFIG_ID = "global";

    public static final String KEY_ADD_STANDBY_TIMEOUT = "add_standby_timeout_millis";
    public static final String KEY_REMOVE_STANDBY_TIMEOUT = "remove_standby_timeout_millis";
    public static final String KEY_PAUSE_STANDBY_TIMEOUT = "pause_standby_timout_millis";
    public static final String KEY_RESUME_STANDBY_TIMEOUT = "resume_standby_timeout_millis";
    public static final String KEY_DATA_SYNC_TIMEOUT = "data_sync_timeout_millis";
    public static final String KEY_SWITCHOVER_TIMEOUT = "switchover_timeout_millis";
    public static final String KEY_STANDBY_DEGRADE_THRESHOLD = "degrade_standby_threshold_millis";

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
    
    /**
     * Get active site in current vdc
     * 
     * @return
     */
    public String getActiveSiteId() {
        return getActiveSiteId(getLocalVdcShortId());
    }
    
    /**
     * Get active site in a specific vdc
     *
     * @param vdcShortId short id of the vdc
     * @return uuid of the active site
     */
    public String getActiveSiteId(String vdcShortId) {
        String siteKind = String.format("%s/%s", Site.CONFIG_KIND, vdcShortId);
        for (Configuration siteConfig : coordinator.queryAllConfiguration(siteKind)) {
            Site site = new Site(siteConfig);
            if (ACTIVE_SITE_STATES.contains(site.getState())) {
                return site.getUuid();
            }
        }
        return null;
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
     * List all standby sites in current vdc
     * 
     * @return list of standby sites
     */
    public List<Site> listStandbySites() {
        String activeSiteId = getActiveSiteId();
        List<Site> result = new ArrayList<>();
        for(Site site : listSites()) {
            if (!site.getUuid().equals(activeSiteId)) {
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
            vdcSiteMap.put(vdcConfig.getId(), sites);
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
    public void updateVdcTargetVersion(String siteId, String action) {
        SiteInfo siteInfo;
        SiteInfo currentSiteInfo = coordinator.getTargetInfo(siteId, SiteInfo.class);
        if (currentSiteInfo != null) {
            siteInfo = new SiteInfo(System.currentTimeMillis(), action, currentSiteInfo.getTargetDataRevision());
        } else {
            siteInfo = new SiteInfo(System.currentTimeMillis(), action);
        }
        coordinator.setTargetInfo(siteId, siteInfo);
        log.info("VDC target version updated to {} for site {}", siteInfo.getVdcConfigVersion(), siteId);
    }

    public void updateVdcTargetVersion(String siteId, String action, long dataRevision) {
        SiteInfo siteInfo = new SiteInfo(System.currentTimeMillis(), action, String.valueOf(dataRevision));
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
        if (StringUtils.isEmpty(site.getStandbyShortId()) || site.getVdcShortId().equals(site.getStandbyShortId())) {
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
     * Use Zookeeper 4 letter command to check status of local coordinatorsvc. The return value could 
     * be one of the following - follower, leader, observer, read-only
     * 
     * @return zookeeper mode
     */
    public String getLocalCoordinatorMode(String nodeId) {
        Socket sock = null;
        try {
            log.info("get local coordinator mode from {}:{}", nodeId, COORDINATOR_PORT);
            sock = new Socket(nodeId, COORDINATOR_PORT);
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
    
    public void removeSiteConfiguration(Site site) {
        coordinator.removeServiceConfiguration(site.toConfiguration());
        log.info("Removed site {} configuration from ZK", site.getUuid());
    }
}
