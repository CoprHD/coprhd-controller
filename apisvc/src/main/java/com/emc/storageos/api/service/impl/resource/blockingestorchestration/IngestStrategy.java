/*
 * Copyright (c) 2008-2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.blockingestorchestration;

import java.net.URI;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.TenantOrg;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume;

public class IngestStrategy {

    private static final Logger _logger = LoggerFactory.getLogger(IngestStrategy.class);

    private DbClient _dbClient;
    private BlockIngestOrchestrator ingestResourceOrchestrator;

    public void setDbClient(DbClient dbClient) {
        _dbClient = dbClient;
    }

    public void setIngestResourceOrchestrator(BlockIngestOrchestrator ingestResourceOrchestrator) {
        this.ingestResourceOrchestrator = ingestResourceOrchestrator;
    }

    public <T extends BlockObject> T ingestBlockObjects(List<URI> systemCache, List<URI> poolCache, StorageSystem system,
            UnManagedVolume unManagedVolume,
            VirtualPool vPool, VirtualArray virtualArray, Project project, TenantOrg tenant,
            List<UnManagedVolume> unManagedVolumesToBeDeleted,
            Map<String, BlockObject> createdObjectMap, Map<String, List<DataObject>> updatedObjectMap, boolean unManagedVolumeExported,
            Class<T> clazz,
            Map<String, StringBuffer> taskStatusMap) {
        return ingestResourceOrchestrator.ingestBlockObjects(systemCache, poolCache, system, unManagedVolume, vPool, virtualArray,
                project, tenant, unManagedVolumesToBeDeleted, createdObjectMap, updatedObjectMap, unManagedVolumeExported, clazz,
                taskStatusMap);

    }
}
