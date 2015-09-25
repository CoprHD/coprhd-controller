/*
 * Copyright (c) 2008-2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.mapper;

import com.emc.storageos.coordinator.client.model.Site;
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
        to.setVdcId(from.getVdc());
        to.setName(from.getName());
        to.setVip(from.getVip());
    }
}
