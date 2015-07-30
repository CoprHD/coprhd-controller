/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model.uimodels;

import java.util.List;

public class OrderAndParams {

    private Order order;
    private List<OrderParameter> parameters;

    public Order getOrder() {
        return order;
    }

    public void setOrder(Order order) {
        this.order = order;
    }

    public List<OrderParameter> getParameters() {
        return parameters;
    }

    public void setParameters(List<OrderParameter> parameters) {
        this.parameters = parameters;
    }

}
