/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.model.catalog;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.BulkRestRep;

@XmlRootElement(name = "bulk_orders")
public class OrderBulkRep extends BulkRestRep {

    private List<OrderRestRep> orders;

    public OrderBulkRep() {

    }

    public OrderBulkRep(List<OrderRestRep> orders) {
        this.orders = orders;
    }

    /**
     * List of projects
     * 
     * @valid none
     * @return List of projects
     */
    @XmlElement(name = "order")
    public List<OrderRestRep> getOrders() {
        if (orders == null) {
            orders = new ArrayList<>();
        }
        return orders;
    }

    public void setOrders(List<OrderRestRep> orders) {
        this.orders = orders;
    }

}
