/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.model.catalog;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.NamedRelatedResourceRep;

@XmlRootElement(name = "orders")
public class OrderList {

    private List<NamedRelatedResourceRep> orders;
    
    public OrderList() {}
    
    public OrderList(List<NamedRelatedResourceRep> orders) {
        this.orders = orders;
    }

    /**
     * List of orders
     * @valid none
     */
    @XmlElement(name = "order")
    public List<NamedRelatedResourceRep> getOrders() {
        if (orders == null) {
            orders = new ArrayList<>();
        }
        return orders;
    }

    public void setOrders(List<NamedRelatedResourceRep> orders) {
        this.orders = orders;
    }        
    
}
