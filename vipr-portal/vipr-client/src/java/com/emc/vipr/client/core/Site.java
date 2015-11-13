/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.core;

import java.util.List;

import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.dr.DRNatCheckParam;
import com.emc.storageos.model.dr.DRNatCheckResponse;
import com.emc.storageos.model.dr.SiteAddParam;
import com.emc.storageos.model.dr.SiteConfigParam;
import com.emc.storageos.model.dr.SiteConfigRestRep;
import com.emc.storageos.model.dr.SiteErrorResponse;
import com.emc.storageos.model.dr.SiteIdListParam;
import com.emc.storageos.model.dr.SiteList;
import com.emc.storageos.model.dr.SiteRestRep;
import com.emc.vipr.client.ViPRCoreClient;
import com.emc.vipr.client.core.filters.ResourceFilter;
import com.emc.vipr.client.core.impl.PathConstants;
import com.emc.vipr.client.impl.RestClient;
import com.sun.jersey.api.client.ClientResponse;

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

    public ClientResponse deleteSite(SiteIdListParam uuids) {
        return client.post(ClientResponse.class, uuids, PathConstants.SITE_URL + "/remove");
    }

    public SiteRestRep pauseSite(String uuid) {
        return client.post(SiteRestRep.class, PathConstants.SITE_URL + "/" + uuid + "/pause/");
    }

    public SiteRestRep resumeSite(String uuid) {
        return client.post(SiteRestRep.class, PathConstants.SITE_URL + "/" + uuid + "/resume/");
    }


    public ClientResponse syncSite(SiteConfigParam input) {
        return client.put(ClientResponse.class, input, PathConstants.SITE_URL);
    }

    public SiteRestRep getSite(String uuid) {
        return client.get(SiteRestRep.class, PathConstants.SITE_URL + "/" + uuid);
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
    
    public SiteErrorResponse getSiteError(String uuid) {
        return client.get(SiteErrorResponse.class, PathConstants.SITE_URL+"/"+uuid+"/error");
    }
    
    public ClientResponse doSwitchover(String uuid) {
        return client.post(ClientResponse.class, PathConstants.SITE_URL+"/"+uuid+"/switchover");
    }
    
    public ClientResponse doFailover(String uuid) {
        return client.post(ClientResponse.class, PathConstants.SITE_URL+"/"+uuid+"/failover");
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
