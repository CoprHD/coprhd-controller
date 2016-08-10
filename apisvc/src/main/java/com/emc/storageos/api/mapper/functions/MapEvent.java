/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.mapper.functions;

import com.emc.storageos.api.mapper.EventMapper;
import com.emc.storageos.db.client.model.ActionableEvent;
import com.emc.storageos.model.event.EventRestRep;
import com.google.common.base.Function;

public final class MapEvent implements Function<ActionableEvent, EventRestRep> {
    public static final MapEvent instance = new MapEvent();

    public static MapEvent getInstance() {
        return instance;
    }

    private MapEvent() {
    }

    @Override
    public EventRestRep apply(ActionableEvent resource) {
        return EventMapper.map(resource);
    }
}
