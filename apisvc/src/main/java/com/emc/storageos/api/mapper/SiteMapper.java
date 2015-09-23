/*
 * Copyright (c) 2008-2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.mapper;

import com.emc.storageos.db.client.model.Site;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.model.dr.SiteRestRep;
import com.emc.storageos.model.dr.SiteSyncParam;

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
        to.setVdcId(from.getVdc());
        to.setName(from.getName());
        to.setVip(from.getVip());
    }
    
    public void map(SiteSyncParam siteParam, Site site) {
        site.setUuid(siteParam.getUuid());
        site.setName(siteParam.getName());
        site.setVip(siteParam.getVip());
        site.getHostIPv4AddressMap().putAll(new StringMap(siteParam.getHostIPv4AddressMap()));
        site.getHostIPv6AddressMap().putAll(new StringMap(siteParam.getHostIPv6AddressMap()));
        site.setSecretKey(siteParam.getSecretKey());
    }
}
