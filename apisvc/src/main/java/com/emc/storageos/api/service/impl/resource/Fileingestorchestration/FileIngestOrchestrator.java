/*
 * Copyright (c) 2008-2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.Fileingestorchestration;

import com.emc.storageos.api.service.impl.resource.Fileingestorchestration.context.IngestionFileRequestContext;
import com.emc.storageos.api.service.impl.resource.blockingestorchestration.IngestionException;
import com.emc.storageos.db.client.model.FileObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by bonduj on 8/1/2016.
 */
public abstract class FileIngestOrchestrator {
    private static final Logger _logger = LoggerFactory.getLogger(FileIngestOrchestrator.class);
    protected abstract <T extends FileObject> T ingestFileObjects(IngestionFileRequestContext requestContext, Class<T> clazz)
            throws IngestionFileException;
}
