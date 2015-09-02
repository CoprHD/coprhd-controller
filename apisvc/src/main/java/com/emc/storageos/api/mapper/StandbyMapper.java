package com.emc.storageos.api.mapper;

import static com.emc.storageos.api.mapper.DbObjectMapper.mapDataObjectFields;

import com.emc.storageos.db.client.model.Standby;
import com.emc.storageos.model.dr.StandbyRestRep;

public class StandbyMapper {
    public static StandbyRestRep map(Standby from) {
        if (from == null) {
            return null;
        }
        StandbyRestRep to = new StandbyRestRep();
        mapDataObjectFields(from, to);
        to.setUuid(from.getUuid());
        to.setName(from.getName());
        to.setVip(from.getVip());
        to.setHostIPv4AddressMap(from.getHostIPv4AddressMap());
        to.setHostIPv6AddressMap(from.getHostIPv6AddressMap());
        return to;
    }
}
