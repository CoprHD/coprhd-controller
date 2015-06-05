/**
* Copyright 2015 EMC Corporation
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

public class OrderFinder extends TenantModelFinder<Order> {
    
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
            Order scheduledOrder = client.findById(Order.class, scheduledOrderElem.id);
            if (scheduledOrder.getExecutionWindowId() != null && scheduledOrder.getExecutionWindowId().getURI() != null && executionWindowId.equalsIgnoreCase(scheduledOrder.getExecutionWindowId().getURI().toString())) {
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
     *        the order status.
     * @return the list of orders with the given status.
     * 
     * @see #findByOrderStatus(String, OrderStatus)
     */
    public List<Order> findByOrderStatus(OrderStatus orderStatus) {
        List<NamedElement> orderIds = client.findByAlternateId(Order.class, Order.ORDER_STATUS, orderStatus.name());
        return findByIds(toURIs(orderIds));
    }

    public List<Order> findByUserId(String userId) {
        List<NamedElement> orderIds = findIdsByUserId(userId);
        return findByIds(toURIs(orderIds));
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
}
