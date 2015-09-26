/*
 * Copyright (c) 2008-2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.mapper;

import com.emc.storageos.db.client.model.Site;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.model.dr.SiteRestRep;
import com.emc.storageos.model.dr.SiteParam;

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
        
        mapDataObjectFields(from, to);
        to.setUuid(from.getUuid());
        to.setVdcId(from.getVdc());
        to.setName(from.getName());
        to.setVip(from.getVip());
    }

    public void map(Site from, SiteParam to) {
        to.setHostIPv4AddressMap(new StringMap(from.getHostIPv4AddressMap()));
        to.setHostIPv6AddressMap(new StringMap(from.getHostIPv6AddressMap()));
        to.setName(from.getName()); // this is the name for the standby site
        to.setSecretKey(from.getSecretKey());
        to.setUuid(from.getUuid());
        to.setVip(from.getVip());
        to.setShortId(from.getStandbyShortId());
    }

    public void map(SiteParam from, Site to) {
        to.setUuid(from.getUuid());
        to.setVip(from.getVip());
        to.getHostIPv4AddressMap().putAll(from.getHostIPv4AddressMap());
        to.getHostIPv6AddressMap().putAll(from.getHostIPv6AddressMap());
        to.setSecretKey(from.getSecretKey());
        to.setStandbyShortId(from.getShortId());
    }

    protected void mapDataObjectFields(Site from, SiteRestRep to) {
        DbObjectMapper.mapDataObjectFields(from, to);
    }
}
