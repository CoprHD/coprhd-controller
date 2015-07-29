/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.util;

import java.net.URI;

import org.apache.commons.collections.Predicate;
import org.apache.commons.lang.StringUtils;

import com.emc.storageos.db.client.model.uimodels.Order;

public class ServiceIdPredicate implements Predicate {

    private final String serviceId;

    public ServiceIdPredicate(String serviceId) {
        this.serviceId = serviceId;
    }

    @Override
    public boolean evaluate(Object obj) {
        URI serviceURI = getOrder(obj).getCatalogServiceId();
        String orderServiceId = serviceURI != null ? serviceURI.toString() : StringUtils.EMPTY;
        return StringUtils.equals(orderServiceId, serviceId);
    }

    private Order getOrder(Object obj) {
        if (obj instanceof Order) {
            return (Order) obj;
        }
        throw new IllegalArgumentException(String.format("All orders must be of type %s.", Order.class.getName()));
    }

}
