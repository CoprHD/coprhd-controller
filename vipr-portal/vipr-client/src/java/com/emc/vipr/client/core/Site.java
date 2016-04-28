/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.core;

import com.emc.storageos.model.dr.DRNatCheckParam;
import com.emc.storageos.model.dr.DRNatCheckResponse;
import com.emc.storageos.model.dr.SiteActive;
import com.emc.storageos.model.dr.SiteAddParam;
import com.emc.storageos.model.dr.SiteConfigParam;
import com.emc.storageos.model.dr.SiteConfigRestRep;
import com.emc.storageos.model.dr.SiteDetailRestRep;
import com.emc.storageos.model.dr.SiteErrorResponse;
import com.emc.storageos.model.dr.SiteIdListParam;
import com.emc.storageos.model.dr.SiteList;
import com.emc.storageos.model.dr.SiteRemoved;
import com.emc.storageos.model.dr.SiteRestRep;
import com.emc.storageos.model.dr.SiteUpdateParam;
import com.emc.vipr.client.core.impl.PathConstants;
import com.emc.vipr.client.impl.RestClient;
import com.sun.jersey.api.client.ClientResponse;

/**
 * Disaster recovery primary/standby sites
 * <p>
 * Base URL: <tt>/site</tt>
 */
public class Site {
    private RestClient client;
    
    public Site(RestClient client) {
        this.client = client;
    }

    public SiteRestRep createSite(SiteAddParam input) {
        return client.post(SiteRestRep.class, input, PathConstants.SITE_URL);
    }

    public boolean isLocalSiteRemoved() {
        SiteRemoved response = client.get(SiteRemoved.class, PathConstants.SITE_URL + "/islocalsiteremoved");
        return response.getIsRemoved();
    }

    public ClientResponse deleteSite(SiteIdListParam uuids) {
        return client.post(ClientResponse.class, uuids, PathConstants.SITE_URL + "/remove");
    }

    public ClientResponse pauseSite(SiteIdListParam uuids) {
        return client.post(ClientResponse.class, uuids, PathConstants.SITE_URL + "/pause/");
    }

    public SiteRestRep resumeSite(String uuid) {
        return client.post(SiteRestRep.class, PathConstants.SITE_URL + "/" + uuid + "/resume/");
    }

    public SiteRestRep retrySite(String uuid) {
        return client.post(SiteRestRep.class, PathConstants.SITE_URL + "/" + uuid + "/retry/");
    }

    public ClientResponse syncSite(String uuid, SiteConfigParam input) {
        return client.put(ClientResponse.class, input, PathConstants.SITE_URL + "/" + uuid + "/initstandby/");
    }

    public SiteRestRep getSite(String uuid) {
        return client.get(SiteRestRep.class, PathConstants.SITE_URL + "/" + uuid);
    }
    
    public SiteRestRep getLocalSite() {
        return client.get(SiteRestRep.class, PathConstants.SITE_URL + "/local");
    }

    public SiteList listAllSites() {
        return client.get(SiteList.class, PathConstants.SITE_URL);
    }

    public SiteActive checkIsActive() {
        return client.get(SiteActive.class, PathConstants.SITE_URL + "/active");
    }

    public SiteConfigRestRep getStandbyConfig() {
        return client.get(SiteConfigRestRep.class, PathConstants.SITE_URL + "/localconfig");
    }

    public DRNatCheckResponse checkIfBehindNat(DRNatCheckParam checkParam) {
        return client.post(DRNatCheckResponse.class, checkParam, PathConstants.SITE_URL + "/natcheck");
    }

    public SiteErrorResponse getSiteError(String uuid) {
        return client.get(SiteErrorResponse.class, PathConstants.SITE_URL + "/" + uuid + "/error");
    }

    public SiteDetailRestRep getSiteDetails(String uuid) {
        return client.get(SiteDetailRestRep.class, PathConstants.SITE_URL + "/" + uuid + "/details");
    }

    public ClientResponse doSwitchover(String uuid) {
        return client.post(ClientResponse.class, PathConstants.SITE_URL + "/" + uuid + "/switchover");
    }

    public ClientResponse doFailover(String uuid) {
        return client.post(ClientResponse.class, PathConstants.SITE_URL + "/" + uuid + "/failover");
    }

    public ClientResponse updateSite(String uuid, SiteUpdateParam updateParam) {
        return client.put(ClientResponse.class, updateParam, PathConstants.SITE_URL + "/" + uuid);
    }
}
