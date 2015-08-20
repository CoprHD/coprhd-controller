/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.engine;

import com.emc.storageos.db.client.model.uimodels.Order;

public interface ExecutionEngine {
    public void executeOrder(Order order);
}