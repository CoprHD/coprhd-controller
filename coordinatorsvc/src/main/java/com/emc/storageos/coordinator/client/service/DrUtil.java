/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.coordinator.client.service;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.client.model.Constants;
import com.emc.storageos.coordinator.client.model.Site;
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
    private static final Logger log = LoggerFactory.getLogger(DrUtil.class);
    
    private CoordinatorClient coordinator;

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
     * Check if current site is primary
     * 
     * @return true for primary. otherwise false
     */
    public boolean isPrimary() {
        return getPrimarySiteId().equals(coordinator.getSiteId());
    }
    
    /**
     * Check if current site is a standby site
     * 
     * @return true for standby site. otherwise false
     */
    public boolean isStandby() {
        return !isPrimary();
    }
    
    /**
     * Get primary site in current vdc
     * 
     * @return
     */
    public String getPrimarySiteId() {
        return getPrimarySiteId(getLocalVdcShortId());
    }

    /**
     * Get primary site in a specific vdc
     *
     * @param vdcShortId short id of the vdc
     * @return uuid of the primary site
     */
    public String getPrimarySiteId(String vdcShortId) {
        Configuration config = coordinator.queryConfiguration(Constants.CONFIG_DR_PRIMARY_KIND, vdcShortId);
        if (config == null) {
            log.warn("primary site not set in ZK. Assuming local site for now");
            return coordinator.getSiteId();
        }
        return config.getConfig(Constants.CONFIG_DR_PRIMARY_SITEID);
    }

    /**
     * Load site information from coordinator 
     * 
     * @param siteId
     * @return
     */
    public Site getSite(String siteId) {
        Configuration config = coordinator.queryConfiguration(Site.CONFIG_KIND, siteId);
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
        List<Site> result = new ArrayList<>();
        for(Site site : listSites()) {
            if (site.getState() != SiteState.PRIMARY) {
                result.add(site);
            }
        }
        return result;
    }

    /**
     * List all sites in all vdc
     *
     * @return list of all sites
     */
    public List<Site> listAllVdcSites() {
        List<Site> result = new ArrayList<>();
        for(Configuration config : coordinator.queryAllConfiguration(Site.CONFIG_KIND)) {
            result.add(new Site(config));
        }
        return result;
    }

    /**
     * List all sites in current vdc
     * 
     * @return list of all sites
     */
    public List<Site> listSites() {
        String vdcId = getLocalVdcShortId();
        List<Site> result = new ArrayList<>();
        for(Configuration config : coordinator.queryAllConfiguration(Site.CONFIG_KIND)) {
            Site site = new Site(config);
            if (site.getVdcShortId().equals(vdcId)) {
                result.add(site);
            }
        }
        return result;
    }
    
    /**
     * Get number of running services in given site
     * 
     * @return number to indicate servers 
     */
    public int getNumberOfLiveServices(String siteUuid, String svcName, String svcVersion) {
        try {
            List<Service> svcs = coordinator.locateAllServices(siteUuid, svcName, svcVersion, null, null);
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
     * Check if a specific site is the local site
     * @param site
     * @return true if the specified site is the local site
     */
    public boolean isLocalSite(Site site) {
        return site.getUuid().equals(coordinator.getSiteId());
    }

    /**
     * Get the short id of local VDC
     */
    public String getLocalVdcShortId() {
        Configuration localVdc = coordinator.queryConfiguration(Constants.CONFIG_GEO_LOCAL_VDC_KIND,
                Constants.CONFIG_GEO_LOCAL_VDC_ID);
        if (localVdc == null) {
            log.warn("local VDC not set in ZK. Assuming vdc1 for now");
            return "vdc1";
        }
        return localVdc.getConfig(Constants.CONFIG_GEO_LOCAL_VDC_SHORT_ID);
    }
}
