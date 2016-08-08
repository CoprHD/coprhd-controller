/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.model.dao;

import java.net.URI;
import java.util.Date;
import java.util.List;
import java.util.Set;

import com.emc.storageos.db.client.model.uimodels.ScheduledEventType;
import org.apache.commons.lang.StringUtils;

import com.emc.sa.model.util.TenantUtils;
import com.emc.storageos.db.client.constraint.NamedElementQueryResultList.NamedElement;
import com.emc.storageos.db.client.model.uimodels.ScheduledEvent;
import com.emc.storageos.db.client.model.uimodels.ScheduledEventStatus;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

public class ScheduledEventFinder extends TenantModelFinder<ScheduledEvent> {

    public ScheduledEventFinder(DBClientWrapper client) {
        super(ScheduledEvent.class, client);
    }

    /**
     * Finds scheduled events by type. This method is intended for use by the scheduler only, in general use
     *
     * @param scheduledEventType
     *            the scheduled event type
     * @return the list of scheduled events with the given type.
     */
    public List<ScheduledEvent> findByScheduledEventType(ScheduledEventType scheduledEventType) {
        List<NamedElement> scheduledEventIds = client.findByAlternateId(ScheduledEvent.class, ScheduledEvent.EVENT_TYPE, scheduledEventType.name());
        return findByIds(toURIs(scheduledEventIds));
    }

    public List<ScheduledEvent> findScheduledEventsByExecutionWindow(String executionWindowId) {

        List<ScheduledEvent> results = Lists.newArrayList();

        if (StringUtils.isBlank(executionWindowId)) {
            return results;
        }

        Set<URI> eventIds = Sets.newHashSet();
        List<NamedElement> scheduledEventElems = client.findByAlternateId(ScheduledEvent.class, ScheduledEvent.EVENT_STATUS, ScheduledEventStatus.APPROVED.name());
        for (NamedElement scheduledEventElem : scheduledEventElems) {
            ScheduledEvent scheduledEvent = client.findById(ScheduledEvent.class, scheduledEventElem.getId());
            if (scheduledEvent.getExecutionWindowId() != null && scheduledEvent.getExecutionWindowId().getURI() != null
                    && executionWindowId.equalsIgnoreCase(scheduledEvent.getExecutionWindowId().getURI().toString())) {
                results.add(scheduledEvent);
            }
        }

        results.addAll(findByIds(Lists.newArrayList(eventIds)));

        return results;
    }


}
