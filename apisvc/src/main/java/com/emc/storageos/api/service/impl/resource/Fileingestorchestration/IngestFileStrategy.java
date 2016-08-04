package com.emc.storageos.api.service.impl.resource.Fileingestorchestration;

import com.emc.storageos.api.service.impl.resource.Fileingestorchestration.context.IngestionFileRequestContext;
import com.emc.storageos.db.client.DbClient;

import com.emc.storageos.db.client.model.FileObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by bonduj on 8/3/2016.
 */
public class IngestFileStrategy {
    private static final Logger _logger = LoggerFactory.getLogger(IngestFileStrategy.class);

    private DbClient _dbClient;
    private FileIngestOrchestrator ingestResourceOrchestrator;

    public void setDbClient(DbClient dbClient) {
        _dbClient = dbClient;
    }

    public void setIngestResourceOrchestrator(FileIngestOrchestrator ingestResourceOrchestrator) {
        this.ingestResourceOrchestrator = ingestResourceOrchestrator;
    }

    public <T extends FileObject> T ingestBlockObjects(IngestionFileRequestContext requestContext, Class<T> clazz) {

        return ingestResourceOrchestrator.ingestFileObjects(requestContext, clazz);

    }
}
