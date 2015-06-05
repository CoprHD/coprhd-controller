/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package com.emc.storageos.api.mapper;

import static com.emc.storageos.api.mapper.DbObjectMapper.mapDataObjectFields;

import com.emc.storageos.db.client.model.VirtualDataCenter;
import com.emc.storageos.model.vdc.VirtualDataCenterRestRep;

public class VirtualDataCenterMapper {
    public static VirtualDataCenterRestRep map(VirtualDataCenter from) {
        if (from == null) {
            return null;
        }
        VirtualDataCenterRestRep to = new VirtualDataCenterRestRep();
        mapDataObjectFields(from, to);
        to.setDescription(from.getDescription());
        to.setApiEndpoint(from.getApiEndpoint());
        to.setStatus(from.getConnectionStatus().name());
        to.setLocal(from.getLocal());
        to.setShortId(from.getShortId());
        to.setGeoCommandEndpoint(from.getGeoCommandEndpoint());
        to.setGeoDataEndpoint(from.getGeoDataEndpoint());
        to.setLastSeenTimeInMillis(from.getLastSeenTimeInMillis());
        return to;        
    }    
}
