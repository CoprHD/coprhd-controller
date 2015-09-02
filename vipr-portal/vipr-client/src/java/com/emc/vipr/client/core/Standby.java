package com.emc.vipr.client.core;

import java.util.List;

import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.dr.StandbyRestRep;
import com.emc.storageos.model.vdc.VirtualDataCenterRestRep;
import com.emc.vipr.client.ViPRCoreClient;
import com.emc.vipr.client.core.filters.ResourceFilter;
import com.emc.vipr.client.core.impl.PathConstants;
import com.emc.vipr.client.impl.RestClient;

public class Standby extends AbstractCoreResources<StandbyRestRep> implements TopLevelResources<VirtualDataCenterRestRep>{
    
    public Standby(ViPRCoreClient parent, RestClient client) {
        super(parent, client, StandbyRestRep.class, "/standby");
    }
    
    public Standby(ViPRCoreClient parent, RestClient client, Class<StandbyRestRep> resourceClass, String baseUrl) {
        super(parent, client, resourceClass, baseUrl);
    }
    
    public void testUpdate(){
        postTask("/standby");
    }

    @Override
    public List<? extends NamedRelatedResourceRep> list() {
        // TODO Auto-generated method stub
        return null;
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
