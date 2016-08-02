/*
 * Copyright (c) 2008-2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.Fileingestorchestration;

import com.emc.storageos.api.service.impl.resource.Fileingestorchestration.context.IngestionFileRequestContext;
import com.emc.storageos.api.service.impl.resource.blockingestorchestration.IngestionException;
import com.emc.storageos.db.client.impl.DbClientImpl;
import com.emc.storageos.db.client.model.FileObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by bonduj on 8/1/2016.
 */
public class FileSystemIngestOrchestrator extends FileIngestOrchestrator {
    private static final Logger _logger = LoggerFactory.getLogger(FileSystemIngestOrchestrator.class);
    private DbClientImpl dbClient;
    private IngestFileStrategyFactory ingestFileStrategyFactory;

    @Override
    protected <T extends FileObject> T ingestFileObjects(IngestionFileRequestContext requestContext, Class<T> clazz) throws IngestionFileException {
        return null;
    }

    public void setDbClient(DbClientImpl dbClient) {
        this.dbClient = dbClient;
    }

    public DbClientImpl getDbClient() {
        return dbClient;
    }

    public void setIngestFileStrategyFactory(IngestFileStrategyFactory ingestFileStrategyFactory) {
        this.ingestFileStrategyFactory = ingestFileStrategyFactory;
    }

    public IngestFileStrategyFactory getIngestFileStrategyFactory() {
        return ingestFileStrategyFactory;
    }
}
