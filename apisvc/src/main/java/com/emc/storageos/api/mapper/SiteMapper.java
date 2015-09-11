/*
 * Copyright (c) 2008-2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.mapper;

import com.emc.storageos.db.client.model.Site;
import com.emc.storageos.model.dr.SiteRestRep;

public class SiteMapper {
    public SiteRestRep map(Site from) {
        if (from == null) {
            return null;
        }
        SiteRestRep to = new SiteRestRep();
        mapDataObjectFields(from, to);
        to.setUuid(from.getUuid());
        to.setName(from.getName());
        to.setVip(from.getVip());
        to.setSecretKey(from.getSecretKey());
        to.setHostIPv4AddressMap(from.getHostIPv4AddressMap());
        to.setHostIPv6AddressMap(from.getHostIPv6AddressMap());
        return to;
    }

    protected void mapDataObjectFields(Site from, SiteRestRep to) {
        DbObjectMapper.mapDataObjectFields(from, to);
    }
}
