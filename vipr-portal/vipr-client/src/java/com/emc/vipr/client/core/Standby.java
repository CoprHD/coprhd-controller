package com.emc.vipr.client.core;

import static com.emc.vipr.client.core.util.ResourceUtils.defaultList;

import java.net.URI;
import java.util.List;

import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.dr.StandbyAddParam;
import com.emc.storageos.model.dr.StandbyList;
import com.emc.storageos.model.dr.StandbyRestRep;
import com.emc.storageos.model.vdc.VirtualDataCenterRestRep;
import com.emc.vipr.client.ViPRCoreClient;
import com.emc.vipr.client.core.filters.ResourceFilter;
import com.emc.vipr.client.core.impl.PathConstants;
import com.emc.vipr.client.impl.RestClient;

public class Standby extends AbstractCoreResources<StandbyRestRep> implements TopLevelResources<VirtualDataCenterRestRep> {

    public Standby(ViPRCoreClient parent, RestClient client) {
        super(parent, client, StandbyRestRep.class, PathConstants.SITE_URL);
    }

    public Standby(ViPRCoreClient parent, RestClient client, Class<StandbyRestRep> resourceClass, String baseUrl) {
        super(parent, client, resourceClass, baseUrl);
    }

    public StandbyRestRep create(StandbyAddParam input) {
        return client.post(StandbyRestRep.class, input, PathConstants.SITE_URL);
    }

    @Override
    public List<? extends NamedRelatedResourceRep> list() {
        StandbyList response = client.get(StandbyList.class, PathConstants.SITE_URL);
        return defaultList(response.getStandbys());
    }

    public StandbyRestRep delete(URI id) {
        return client.delete(StandbyRestRep.class, getIdUrl(), id);
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
