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

import com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext;
import com.emc.storageos.api.service.impl.resource.utils.VolumeIngestionUtil;
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

    public <T extends BlockObject> T ingestBlockObjects(IngestionRequestContext requestContext, Class<T> clazz) {

        return ingestResourceOrchestrator.ingestBlockObjects(requestContext, 
                requestContext.getSystemCache(), 
                requestContext.getPoolCache(), 
                requestContext.getStorageSystem(), 
                requestContext.getCurrentUnmanagedVolume(), 
                requestContext.getVpool(), 
                requestContext.getVirtualArray(),
                requestContext.getProject(), 
                requestContext.getTenant(), 
                requestContext.getUnManagedVolumesSuccessfullyProcessed(), 
                requestContext.getCreatedObjectMap(), 
                requestContext.getUpdatedObjectMap(), 
                requestContext.getVolumeContext().isVolumeExported(), 
                clazz,
                requestContext.getTaskStatusMap(), 
                requestContext.getVplexIngestionMethod());

    }
}
