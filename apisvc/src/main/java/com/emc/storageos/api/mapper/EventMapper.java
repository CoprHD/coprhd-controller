/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.mapper;

import static com.emc.storageos.api.mapper.DbObjectMapper.mapDataObjectFields;
import static com.emc.storageos.api.mapper.DbObjectMapper.toNamedRelatedResource;
import static com.emc.storageos.api.mapper.DbObjectMapper.toRelatedResource;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import com.emc.storageos.db.client.model.ActionableEvent;
import com.emc.storageos.model.RelatedResourceRep;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.event.EventRestRep;

public class EventMapper {

    private EventMapper() {
    };

    public static EventRestRep map(ActionableEvent from) {
        if (from == null) {
            return null;
        }
        EventRestRep to = new EventRestRep();
        mapDataObjectFields(from, to);
        to.setTenant(toRelatedResource(ResourceTypeEnum.TENANT, from.getTenant()));
        to.setResource(toNamedRelatedResource(from.getResource()));
        to.setEventStatus(from.getEventStatus());
        to.setDescription(from.getDescription());
        to.setWarning(from.getWarning());
        to.setEventCode(from.getEventCode());

        if ((from.getTaskIds() != null) && (!from.getTaskIds().isEmpty())) {
            List<RelatedResourceRep> taskIds = new ArrayList<RelatedResourceRep>();
            for (String task : from.getTaskIds()) {
                taskIds.add(toRelatedResource(ResourceTypeEnum.TASK, URI.create(task)));
            }
            to.setTaskIds(taskIds);
        }

        return to;
    }

}
