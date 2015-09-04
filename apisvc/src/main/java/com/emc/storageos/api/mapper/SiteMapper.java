package com.emc.storageos.api.mapper;

import static com.emc.storageos.api.mapper.DbObjectMapper.mapDataObjectFields;

import com.emc.storageos.db.client.model.Site;
import com.emc.storageos.model.dr.SiteRestRep;

public class SiteMapper {
    public static SiteRestRep map(Site from) {
        if (from == null) {
            return null;
        }
        SiteRestRep to = new SiteRestRep();
        mapDataObjectFields(from, to);
        to.setUuid(from.getUuid());
        to.setName(from.getName());
        to.setVip(from.getVip());
        to.setHostIPv4AddressMap(from.getHostIPv4AddressMap());
        to.setHostIPv6AddressMap(from.getHostIPv6AddressMap());
        return to;
    }
}
