/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.engine.service;

import com.emc.storageos.db.client.model.uimodels.CatalogService;
import com.emc.storageos.db.client.model.uimodels.Order;

public interface ExecutionServiceFactory {
    public ExecutionService createService(Order order, CatalogService catalogService) throws ServiceNotFoundException;
}
