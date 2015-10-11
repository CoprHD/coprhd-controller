/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.core;

import java.util.List;

import com.sun.jersey.api.client.ClientResponse;

import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.dr.*;
import com.emc.vipr.client.ViPRCoreClient;
import com.emc.vipr.client.core.filters.ResourceFilter;
import com.emc.vipr.client.core.impl.PathConstants;
import com.emc.vipr.client.impl.RestClient;

/**
 * Disaster recovery primary/standby sites 
 * <p>
 * Base URL: <tt>/site</tt>
 */
public class Site extends AbstractCoreResources<SiteRestRep> implements TopLevelResources<SiteRestRep> {

    public Site(ViPRCoreClient parent, RestClient client) {
        super(parent, client, SiteRestRep.class, PathConstants.SITE_URL);
    }

    public Site(ViPRCoreClient parent, RestClient client, Class<SiteRestRep> resourceClass, String baseUrl) {
        super(parent, client, resourceClass, baseUrl);
    }

    public SiteRestRep createSite(SiteAddParam input) {
        return client.post(SiteRestRep.class, input, PathConstants.SITE_URL);
    }

    public SiteRestRep deleteSite(String uuid) {
        return client.delete(SiteRestRep.class, PathConstants.SITE_URL + "/" + uuid);
    }

    public ClientResponse syncSite(SiteConfigParam input) {
        return client.put(ClientResponse.class, input, PathConstants.SITE_URL);
    }
    
    public SiteRestRep getSite(String uuid){
        return client.get(SiteRestRep.class, PathConstants.SITE_URL+"/"+uuid);
    }
   
    public SiteList listAllSites() {
        return client.get(SiteList.class, PathConstants.SITE_URL);
    }
    
    public SiteConfigRestRep getStandbyConfig() {
        return client.get(SiteConfigRestRep.class, PathConstants.SITE_URL + "/localconfig");
    }
    
    public DRNatCheckResponse checkIfBehindNat(DRNatCheckParam checkParam) {
        return client.post(DRNatCheckResponse.class, checkParam, PathConstants.SITE_URL + "/natcheck");
    }
    
    public String getSiteError(String uuid) {
        return client.get(String.class, PathConstants.SITE_URL+"/"+uuid+"/error");
    }

    @Override
    public List<SiteRestRep> getAll(ResourceFilter<SiteRestRep> filter) {
        return null;
    }

    @Override
    public List<? extends NamedRelatedResourceRep> list() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public List<SiteRestRep> getAll() {
        // TODO Auto-generated method stub
        return null;
    }
}
