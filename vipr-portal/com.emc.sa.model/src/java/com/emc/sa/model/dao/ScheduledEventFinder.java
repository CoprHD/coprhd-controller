/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.model.dao;

import java.net.URI;
import java.util.Date;
import java.util.List;
import java.util.Set;

import com.emc.storageos.db.client.model.uimodels.Order;
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

    public List<ScheduledEvent> findByScheduledEventStatus(String tenant, ScheduledEventStatus eventStatus) {
        if (StringUtils.isBlank(tenant)) {
            return Lists.newArrayList();
        }
        return TenantUtils.filter(findByScheduledEventStatus(eventStatus), tenant);
    }

    /**
     * Finds orders by status. This method is intended for use by the scheduler only, in general use
     * {@link #findByScheduledEventStatus(String, com.emc.storageos.db.client.model.uimodels.ScheduledEventStatus)}.
     *
     * @param eventStatus
     *            the event status.
     * @return the list of events with the given status.
     *
     * @see #findByScheduledEventStatus(String, com.emc.storageos.db.client.model.uimodels.ScheduledEventStatus)
     */
    public List<ScheduledEvent> findByScheduledEventStatus(ScheduledEventStatus eventStatus) {
        List<NamedElement> eventIds = client.findByAlternateId(Order.class, ScheduledEvent.EVENT_STATUS, eventStatus.name());
        return findByIds(toURIs(eventIds));
    }
/*
    public List<ScheduledEvent> findByUserId(String userId) {
        List<NamedElement> eventIds = findIdsByUserId(userId);
        return findByIds(toURIs(eventIds));
    }

    public List<NamedElement> findIdsByUserId(String userId) {
        if (StringUtils.isBlank(userId)) {
            return Lists.newArrayList();
        }

        List<NamedElement> orderIds = client.findByAlternateId(Order.class, Order.SUBMITTED_BY_USER_ID, userId);
        return orderIds;
    }

    public List<NamedElement> findIdsByTimeRange(Date startTime, Date endTime) {
        return client.findByTimeRange(Order.class, "indexed", startTime, endTime);
    }

    public List<Order> findByTimeRange(Date startTime, Date endTime) {
        List<NamedElement> orderIds = findIdsByTimeRange(startTime, endTime);
        return findByIds(toURIs(orderIds));
    }

    public List<Order> findByTimeRange(URI tenantId, Date startTime, Date endTime) {
        if (tenantId == null) {
            return Lists.newArrayList();
        }
        return TenantUtils.filter(findByTimeRange(startTime, endTime), tenantId.toString());
    }
*/
}
