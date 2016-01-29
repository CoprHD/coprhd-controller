/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.upgrade;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.svcs.errorhandling.resources.MigrationCallbackException;

/**
 * Base class for custom migration handlers
 */
public abstract class BaseCustomMigrationCallback implements MigrationCallback {
    protected DbClient dbClient;
    protected CoordinatorClient coordinatorClient;
    protected String name;

    public DbClient getDbClient() {
        return dbClient;
    }

    public void setDbClient(DbClient dbClient) {
        this.dbClient = dbClient;
    }

    public void setCoordinatorClient(CoordinatorClient coordinatorClient) {
        this.coordinatorClient = coordinatorClient;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    abstract public void process() throws MigrationCallbackException;

    @Override
    public String getName() {
        return name;
    }
}
