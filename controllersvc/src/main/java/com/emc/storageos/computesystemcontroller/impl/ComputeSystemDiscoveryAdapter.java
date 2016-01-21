/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.computesystemcontroller.impl;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.ModelClient;
import com.emc.storageos.db.client.model.DiscoveredSystemObject;

public interface ComputeSystemDiscoveryAdapter {
    public boolean isSupportedTarget(String targetId);

    public void discoverTarget(String targetId);

    public String getErrorMessage(Throwable t);

    public void setModelClient(ModelClient modelClient);

    public void setDbClient(DbClient dbClient);

    public void setCoordinator(CoordinatorClient coordinator);

    public ComputeSystemDiscoveryVersionValidator getVersionValidator();

    public void discoveryFailure(DiscoveredSystemObject target, String compatibilityStatus, String errorMessage);

}
