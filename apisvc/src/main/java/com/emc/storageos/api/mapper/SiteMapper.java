/*
 * Copyright (c) 2008-2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.mapper;

import com.emc.storageos.db.client.model.Site;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.model.dr.SiteAddParam;
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
        }
        
        mapDataObjectFields(from, to);
        to.setUuid(from.getUuid());
        to.setName(from.getName());
        to.setVip(from.getVip());
        to.setHostIPv4AddressMap(from.getHostIPv4AddressMap());
        to.setHostIPv6AddressMap(from.getHostIPv6AddressMap());
        to.setSecretKey(from.getSecretKey());
    }
    
    public void map(SiteAddParam siteAddParam, Site site) {
        site.setUuid(siteAddParam.getUuid());
        site.setName(siteAddParam.getName());
        site.setVip(siteAddParam.getVip());
        site.getHostIPv4AddressMap().putAll(new StringMap(siteAddParam.getHostIPv4AddressMap()));
        site.getHostIPv6AddressMap().putAll(new StringMap(siteAddParam.getHostIPv6AddressMap()));
        site.setSecretKey(siteAddParam.getSecretKey());
    }

    protected void mapDataObjectFields(Site from, SiteRestRep to) {
        DbObjectMapper.mapDataObjectFields(from, to);
    }
}
