/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.mapper;

import static com.emc.storageos.api.mapper.DbObjectMapper.mapDataObjectFields;
import static com.emc.storageos.api.mapper.DbObjectMapper.toRelatedResource;

import com.emc.storageos.db.client.model.ActionableEvent;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.host.EventRestRep;

public class EventMapper {

    public static EventRestRep map(ActionableEvent from) {
        if (from == null) {
            return null;
        }
        EventRestRep to = new EventRestRep();
        mapDataObjectFields(from, to);
        to.setTenant(toRelatedResource(ResourceTypeEnum.TENANT, from.getTenant()));
        return to;
    }

}
