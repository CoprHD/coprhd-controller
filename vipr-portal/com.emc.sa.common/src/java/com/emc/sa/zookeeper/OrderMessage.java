/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.zookeeper;

import java.io.Serializable;

public class OrderMessage implements Serializable {
    private static final long serialVersionUID = 2510247618783741565L;
    private String orderId;

    public OrderMessage(String orderId) {
        this.orderId = orderId;
    }

    public String getOrderId() {
        return orderId;
    }
}
