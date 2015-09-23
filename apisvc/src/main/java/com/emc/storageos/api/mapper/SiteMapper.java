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
        
        mapDataObjectFields(from, to);
        to.setUuid(from.getUuid());
        to.setVdcId(from.getVdc());
        to.setName(from.getName());
        to.setVip(from.getVip());
    }

    protected void mapDataObjectFields(Site from, SiteRestRep to) {
        DbObjectMapper.mapDataObjectFields(from, to);
    }
}
