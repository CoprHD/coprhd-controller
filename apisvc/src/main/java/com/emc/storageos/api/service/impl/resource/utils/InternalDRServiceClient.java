package com.emc.storageos.api.service.impl.resource.utils;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.client.model.Site;
import com.emc.storageos.model.dr.SiteErrorResponse;
import com.emc.storageos.security.helpers.BaseServiceClient;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;

public class InternalDRServiceClient extends BaseServiceClient {
    
    final private static Logger log = LoggerFactory.getLogger(InternalDRServiceClient.class);
    
    final private static String URI_FORMAT = "https://%s:4443";
    private static final String SITE_INTERNAL_FAILOVER = "/site/internal/failover?newActiveSiteUUid=%s";
    private static final String SITE_INTERNAL_FAILOVERPRECHECK = "/site/internal/failoverprecheck";
    private Site site;

    public InternalDRServiceClient(Site site) {
        this.site = site;
        setServer(site.getVip());
    }
    
    @Override
    public void setServer(String server) {
        setServiceURI(URI.create(String.format(URI_FORMAT, server)));
    }
    
    
    public SiteErrorResponse failoverPrecheck() {
        WebResource rRoot = createRequest(SITE_INTERNAL_FAILOVERPRECHECK);
        ClientResponse resp = null;
        try {
            resp = addSignature(rRoot).post(ClientResponse.class);
        } catch (Exception e) {
            log.error("Fail to send request to precheck failover", e);
            throw APIException.internalServerErrors.failoverPrecheckFailed(site.getName(), String.format("Can't connect to standby to do precheck for failover, %s", e.getMessage()));
        }
        
        SiteErrorResponse errorResponse = resp.getEntity(SiteErrorResponse.class);
        
        if (SiteErrorResponse.isErrorResponse(errorResponse)) {
            throw APIException.internalServerErrors.failoverPrecheckFailed(site.getName(), errorResponse.getErrorMessage());
        }
        
        return SiteErrorResponse.noError();
    }
    
    public void failover(String newActiveSiteUUID) {
        String getVdcPath = String.format(SITE_INTERNAL_FAILOVER, newActiveSiteUUID);
        WebResource rRoot = createRequest(getVdcPath);
        
        try {
            addSignature(rRoot).post(ClientResponse.class);
        } catch (Exception e) {
            log.error("Fail to send request to failover", e);
            throw APIException.internalServerErrors.failoverFailed(site.getName(), e.getMessage());
        }
        
    }
}