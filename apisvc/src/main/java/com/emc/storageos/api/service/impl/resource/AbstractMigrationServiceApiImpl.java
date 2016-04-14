/*
 * Copyright (c) 2016 Intel Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource;

import java.net.URI;
import java.util.List;
import java.util.Set;

import com.emc.storageos.db.client.DbClient;

public abstract class AbstractMigrationServiceApiImpl implements
        MigrationServiceApi {

    protected DbClient _dbClient;

    // Db client getter/setter

    public void setDbClient(DbClient dbClient) {
        _dbClient = dbClient;
    }

    public DbClient getDbClient() {
        return _dbClient;
    }

}
