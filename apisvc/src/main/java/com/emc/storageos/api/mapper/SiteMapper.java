/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.mapper;

import com.emc.storageos.coordinator.client.model.Site;
import com.emc.storageos.coordinator.client.model.SiteState;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.model.dr.SiteParam;
import com.emc.storageos.model.dr.SiteRestRep;

public class SiteMapper {
    public SiteRestRep map(Site from) {
        if (from == null) {
            return null;
        }
        SiteRestRep to = new SiteRestRep();
        map(from, to);
        return to;
    }
    
    public void map(Site from, SiteRestRep to) {
        if (from == null) {
            return;
        }
        
        to.setUuid(from.getUuid());
        to.setVdcShortId(from.getVdcShortId());
        to.setName(from.getName());
        to.setVip(from.getVip());
        to.setDescription(from.getDescription());
        to.setState(from.getState().toString());
        to.setNetworkHealth(from.getNetworkHealth().toString());
    }

    public void map(Site from, SiteParam to) {
        to.setHostIPv4AddressMap(new StringMap(from.getHostIPv4AddressMap()));
        to.setHostIPv6AddressMap(new StringMap(from.getHostIPv6AddressMap()));
        to.setName(from.getName()); // this is the name for the standby site
        to.setUuid(from.getUuid());
        to.setVip(from.getVip());
        to.setShortId(from.getSiteShortId());
        to.setState(from.getState().toString());
        to.setNodeCount(from.getNodeCount());
        to.setCreationTime(from.getCreationTime());
    }

    public void map(SiteParam from, Site to) {
        to.setUuid(from.getUuid());
        to.setVip(from.getVip());
        to.getHostIPv4AddressMap().putAll(from.getHostIPv4AddressMap());
        to.getHostIPv6AddressMap().putAll(from.getHostIPv6AddressMap());
        to.setSiteShortId(from.getShortId());
        to.setState(SiteState.valueOf(from.getState()));
        to.setNodeCount(from.getNodeCount());
        to.setCreationTime(from.getCreationTime());
    }

}
