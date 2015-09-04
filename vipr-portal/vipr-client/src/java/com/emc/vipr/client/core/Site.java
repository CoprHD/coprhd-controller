package com.emc.vipr.client.core;

import static com.emc.vipr.client.core.util.ResourceUtils.defaultList;

import java.net.URI;
import java.util.List;

import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.dr.SiteAddParam;
import com.emc.storageos.model.dr.SiteList;
import com.emc.storageos.model.dr.SiteRestRep;
import com.emc.storageos.model.vdc.VirtualDataCenterRestRep;
import com.emc.vipr.client.ViPRCoreClient;
import com.emc.vipr.client.core.filters.ResourceFilter;
import com.emc.vipr.client.core.impl.PathConstants;
import com.emc.vipr.client.impl.RestClient;

public class Site extends AbstractCoreResources<SiteRestRep> implements TopLevelResources<VirtualDataCenterRestRep> {

    public Site(ViPRCoreClient parent, RestClient client) {
        super(parent, client, SiteRestRep.class, PathConstants.SITE_URL);
    }

    public Site(ViPRCoreClient parent, RestClient client, Class<SiteRestRep> resourceClass, String baseUrl) {
        super(parent, client, resourceClass, baseUrl);
    }

    public SiteRestRep create(SiteAddParam input) {
        return client.post(SiteRestRep.class, input, PathConstants.SITE_URL);
    }

    @Override
    public List<? extends NamedRelatedResourceRep> list() {
        SiteList response = client.get(SiteList.class, PathConstants.SITE_URL);
        return defaultList(response.getSites());
    }

    public SiteRestRep delete(URI id) {
        return client.delete(SiteRestRep.class, getIdUrl(), id);
    }

    @Override
    public List<VirtualDataCenterRestRep> getAll() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public List<VirtualDataCenterRestRep> getAll(ResourceFilter<VirtualDataCenterRestRep> filter) {
        // TODO Auto-generated method stub
        return null;
    }
}
