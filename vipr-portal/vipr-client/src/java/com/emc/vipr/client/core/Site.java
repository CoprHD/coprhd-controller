package com.emc.vipr.client.core;

import java.util.List;

import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.dr.SiteAddParam;
import com.emc.storageos.model.dr.SiteConfigRestRep;
import com.emc.storageos.model.dr.SiteList;
import com.emc.storageos.model.dr.SiteRestRep;
import com.emc.vipr.client.ViPRCoreClient;
import com.emc.vipr.client.core.filters.ResourceFilter;
import com.emc.vipr.client.core.impl.PathConstants;
import com.emc.vipr.client.impl.RestClient;

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
        return client.delete(SiteRestRep.class, PathConstants.SITE_URL+"/"+uuid);
    }
    
    public SiteRestRep getSite(String uuid){
        return client.get(SiteRestRep.class, PathConstants.SITE_URL+"/"+uuid);
    }
   
    public SiteList listAllSites() {
        return client.get(SiteList.class, PathConstants.SITE_URL);
    }
    
    public SiteConfigRestRep getStandbyConfig() {
        return client.get(SiteConfigRestRep.class, PathConstants.SITE_URL + "/standby/config");
    }
    
    public SiteRestRep addPrimary(SiteAddParam input) {
        return client.post(SiteRestRep.class, input, PathConstants.SITE_URL + "/standby/config");
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
