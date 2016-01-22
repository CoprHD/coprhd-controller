/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.utils;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.client.model.Site;
import com.emc.storageos.model.dr.FailoverPrecheckResponse;
import com.emc.storageos.model.dr.SiteConfigParam;
import com.emc.storageos.model.dr.SiteList;
import com.emc.storageos.security.helpers.BaseServiceClient;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.vipr.model.sys.recovery.DbRepairStatus;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.UniformInterfaceException;
import com.sun.jersey.api.client.WebResource;

/**
 * Internal API for communication among sites (within one specific VDC)
 */
public class InternalSiteServiceClient extends BaseServiceClient {

    private static final String INTERNAL_SITE_ROOT = "/site/internal";
    private static final String INTERNAL_SITE_INIT_STANDBY = INTERNAL_SITE_ROOT + "/initstandby";
    private static final String SITE_INTERNAL_FAILOVER = INTERNAL_SITE_ROOT + "/failover?newActiveSiteUUid=%s&vdcVersion=%d";
    private static final String SITE_INTERNAL_FAILOVERPRECHECK = INTERNAL_SITE_ROOT + "/failoverprecheck";
    private static final String SITE_INTERNAL_LIST = INTERNAL_SITE_ROOT + "/list";
    private static final String SITE_REPAIR_STATUS = "/control/cluster/dbrepair-status";

    final private Logger log = LoggerFactory
            .getLogger(InternalSiteServiceClient.class);

    private Site site;
    
    /**
     * Client without target hosts
     */
    public InternalSiteServiceClient() {
    }
    
    /**
     * Initialize site client with site information
     * @param site
     */
    public InternalSiteServiceClient(Site site) {
        this.site = site;
        setServer(site.getVip());
    }

    /**
     * Client with specific host
     *
     * @param server
     */
    public InternalSiteServiceClient(String server) {
        setServer(server);
    }

    /**
     * Make client associated with this api server host (IP)
     * 
     * @param server IP
     */
    @Override
    public void setServer(String server) {
        setServiceURI(URI.create("https://" + server + ":4443"));
    }

    /**
     * Initialize a to-be resumed target standby
     * 
     * @param configParam the sites configuration
     * @return
     */
    public ClientResponse initStandby(SiteConfigParam configParam) {
        WebResource rRoot = createRequest(INTERNAL_SITE_INIT_STANDBY);
        ClientResponse resp = null;
        try {
            resp = addSignature(rRoot)
                    .put(ClientResponse.class, configParam);
        } catch (UniformInterfaceException e) {
            log.warn("could not initialize target standby site. Err:{}", e);
        }
        return resp;
    }

    /**
     * Get db repair status
     * @return
     */
    public DbRepairStatus getSiteDbrepairStatus() {
        WebResource rRoot = createRequest(SITE_REPAIR_STATUS);
        DbRepairStatus status = null;
        try {
            status = addSignature(rRoot)
                    .get(DbRepairStatus.class);
        } catch (UniformInterfaceException e) {
            log.warn("could not get site db repair status from site {}. Err:", site.toBriefString(), e);
        }
        return status;
    }
    public FailoverPrecheckResponse failoverPrecheck() {
        WebResource rRoot = createRequest(SITE_INTERNAL_FAILOVERPRECHECK);
        ClientResponse resp = null;
        try {
            resp = addSignature(rRoot).post(ClientResponse.class);
        } catch (Exception e) {
            log.error("Fail to send request to precheck failover", e);
            //throw APIException.internalServerErrors.failoverPrecheckFailed(site.getName(), String.format("Can't connect to standby to do precheck for failover, %s", e.getMessage()));
            return null;
        }
        
        FailoverPrecheckResponse response = resp.getEntity(FailoverPrecheckResponse.class);
        
        if (FailoverPrecheckResponse.isErrorResponse(response)) {
            throw APIException.internalServerErrors.failoverPrecheckFailed(site.getName(), response.getErrorMessage());
        }
        
        return response;
    }
    
    public void failover(String newActiveSiteUUID, long vdcVersion) {
        String getVdcPath = String.format(SITE_INTERNAL_FAILOVER, newActiveSiteUUID, vdcVersion);
        WebResource rRoot = createRequest(getVdcPath);
        
        try {
            addSignature(rRoot).post(ClientResponse.class);
        } catch (Exception e) {
            log.error("Fail to send request to failover", e);
            throw APIException.internalServerErrors.failoverFailed(site.getName(), e.getMessage());
        }
        
    }
    
    public SiteList getSiteList() {
        WebResource rRoot = createRequest(SITE_INTERNAL_LIST);
        ClientResponse resp = null;
        
        resp = addSignature(rRoot).get(ClientResponse.class);
        SiteList response = resp.getEntity(SiteList.class);
        return response;
    }
}
