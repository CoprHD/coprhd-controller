/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.validators;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.db.client.DbClient;

/**
 * Abstract class to house common plumbing elements of validator factories
 */
public abstract class AbstractValidatorFactory implements StorageSystemValidatorFactory {

    private DbClient dbClient;
    private CoordinatorClient coordinator;

    public void setDbClient(DbClient dbClient) {
        this.dbClient = dbClient;
    }

    public DbClient getDbClient() {
        return dbClient;
    }

    public void setCoordinator(CoordinatorClient coordinator) {
        this.coordinator = coordinator;
    }

    public CoordinatorClient getCoordinator() {
        return coordinator;
    }
}
