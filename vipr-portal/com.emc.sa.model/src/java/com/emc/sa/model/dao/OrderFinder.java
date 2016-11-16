/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.model.dao;

import java.net.URI;
import java.util.Date;
import java.util.List;
import java.util.Set;
import org.apache.commons.lang.StringUtils;
import com.emc.storageos.db.client.model.uimodels.Order;
import com.emc.storageos.db.client.model.uimodels.OrderStatus;
import com.emc.sa.model.util.TenantUtils;
import com.emc.storageos.db.client.constraint.NamedElementQueryResultList.NamedElement;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OrderFinder extends TenantModelFinder<Order> {
    private static final Logger log = LoggerFactory.getLogger(OrderFinder.class);

    public OrderFinder(DBClientWrapper client) {
        super(Order.class, client);
    }

    public List<Order> findScheduledByExecutionWindow(String executionWindowId) {

        List<Order> results = Lists.newArrayList();

        if (StringUtils.isBlank(executionWindowId)) {
            return results;
        }

        Set<URI> orderIds = Sets.newHashSet();
        List<NamedElement> scheduledOrderElems = client.findByAlternateId(Order.class, Order.ORDER_STATUS, OrderStatus.SCHEDULED.name());
        for (NamedElement scheduledOrderElem : scheduledOrderElems) {
            Order scheduledOrder = client.findById(Order.class, scheduledOrderElem.getId());
            if (scheduledOrder.getExecutionWindowId() != null && scheduledOrder.getExecutionWindowId().getURI() != null
                    && executionWindowId.equalsIgnoreCase(scheduledOrder.getExecutionWindowId().getURI().toString())) {
                results.add(scheduledOrder);
            }
        }

        results.addAll(findByIds(Lists.newArrayList(orderIds)));

        return results;
    }

    public List<Order> findByOrderStatus(String tenant, OrderStatus orderStatus) {
        if (StringUtils.isBlank(tenant)) {
            return Lists.newArrayList();
        }
        return TenantUtils.filter(findByOrderStatus(orderStatus), tenant);
    }

    /**
     * Finds orders by status. This method is intended for use by the scheduler only, in general use
     * {@link #findByOrderStatus(String, OrderStatus)}.
     * 
     * @param orderStatus
     *            the order status.
     * @return the list of orders with the given status.
     * 
     * @see #findByOrderStatus(String, OrderStatus)
     */
    public List<Order> findByOrderStatus(OrderStatus orderStatus) {
        List<NamedElement> orderIds = client.findByAlternateId(Order.class, Order.ORDER_STATUS, orderStatus.name());
        return findByIds(toURIs(orderIds));
    }

    public List<Order> findByUserId(String userId, long startTime, long endTime, int maxCount) {
        List<NamedElement> orderIds = findIdsByUserId(userId, startTime, endTime, maxCount);
        return findByIds(toURIs(orderIds));
    }

    public List<NamedElement> findIdsByUserId(String userId, long startTime, long endTime, int maxCount) {
        if (StringUtils.isBlank(userId)) {
            return Lists.newArrayList();
        }


        List<NamedElement> orderIds = client.findByAlternateId2(Order.class, Order.SUBMITTED_BY_USER_ID, userId,
                startTime, endTime, maxCount);
        return orderIds;
    }

    public List<NamedElement> findIdsByTimeRange(Date startTime, Date endTime, int maxCount) {
        //return client.findAllOrdersByTimeRange(Order.class, "indexed", startTime, endTime, maxCount);
        return client.findAllOrdersByTimeRange("indexed", startTime, endTime, maxCount);
        // return client.findAllOrdersByTimeRange(Order.SUBMITTED_BY_USER_ID, startTime, endTime, maxCount);
    }

    public List<Order> findByTimeRange(Date startTime, Date endTime, int maxCount) {
        List<NamedElement> orderIds = findIdsByTimeRange(startTime, endTime, maxCount);
        return findByIds(toURIs(orderIds));
    }

    public List<Order> findByTimeRange(URI tenantId, Date startTime, Date endTime, int maxCount) {
        if (tenantId == null) {
            return Lists.newArrayList();
        }
        return TenantUtils.filter(findByTimeRange(startTime, endTime, maxCount), tenantId.toString());
    }
}
