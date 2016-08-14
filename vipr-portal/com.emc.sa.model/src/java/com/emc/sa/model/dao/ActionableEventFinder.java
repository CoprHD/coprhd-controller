/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.model.dao;

import java.net.URI;
import java.util.List;

import com.emc.storageos.db.client.constraint.NamedElementQueryResultList.NamedElement;
import com.emc.storageos.db.client.model.ActionableEvent;
import com.google.common.collect.Lists;

public class ActionableEventFinder extends TenantResourceFinder<ActionableEvent> {
    protected static final String RESOURCE_COLUMN_NAME = "resource";

    public ActionableEventFinder(DBClientWrapper client) {
        super(ActionableEvent.class, client);
    }

    public List<ActionableEvent> findPendingByResource(URI resourceId) {
        List<NamedElement> events = findIdsByResource(resourceId);
        List<ActionableEvent> result = Lists.newArrayList();
        for (ActionableEvent event : findByIds(toURIs(events))) {
            if (event != null && event.getEventStatus().equalsIgnoreCase(ActionableEvent.Status.pending.name().toString())) {
                result.add(event);
            }
        }
        return result;
    }

    public List<NamedElement> findIdsByResource(URI resourceId) {
        return client.findBy(ActionableEvent.class, RESOURCE_COLUMN_NAME, resourceId);
    }

}
