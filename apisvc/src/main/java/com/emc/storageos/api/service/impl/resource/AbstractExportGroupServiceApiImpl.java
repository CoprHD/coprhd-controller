/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource;

import java.net.URI;
import java.util.List;
import java.util.Set;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;

public abstract class AbstractExportGroupServiceApiImpl implements
        ExportGroupServiceApi {

    protected DbClient _dbClient;

    // Db client getter/setter

    public void setDbClient(DbClient dbClient) {
        _dbClient = dbClient;
    }

    public DbClient getDbClient() {
        return _dbClient;
    }

    @Override
    public void validateVarrayStoragePorts(Set<URI> storageSystemURIs,
            VirtualArray varray, List<URI> allHosts)
            throws InternalException {
    }

}
