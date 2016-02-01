/*
 * Copyright (c) 2008-2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.blockingestorchestration.cg;

import java.util.List;

import com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume;

public abstract class BlockCGIngestDecorator {

    BlockCGIngestDecorator nextCGIngestDecorator = null;

    DbClient dbClient = null;

    public abstract void decorateCG(BlockConsistencyGroup cg, UnManagedVolume umv, List<BlockObject> associatedObjects,
            IngestionRequestContext requestContext)
            throws Exception;

    public abstract void decorateCGBlockObjects(BlockConsistencyGroup cg, UnManagedVolume umv, List<BlockObject> associatedObjects,
            IngestionRequestContext requestContext) throws Exception;

    /**
     * Decorate the given CG with respective attributes.
     * 
     * @param cg
     * @param associatedObjects
     */
    public void decorate(BlockConsistencyGroup cg, UnManagedVolume umv, IngestionRequestContext requestContext)
            throws Exception {
        List<BlockObject> associatedObjects = getAssociatedObjects(cg, umv, requestContext);
        if (null != cg && !associatedObjects.isEmpty()) {
            decorateCG(cg, umv, associatedObjects, requestContext);
            decorateCGBlockObjects(cg, umv, associatedObjects, requestContext);
        }
        if (null != nextCGIngestDecorator) {
            nextCGIngestDecorator.decorate(cg, umv, requestContext);
        }
    }

    /**
     * @param dbClient the dbClient to set
     */
    public void setDbClient(DbClient dbClient) {
        this.dbClient = dbClient;
    }

    protected void setNextDecorator(BlockCGIngestDecorator decorator) {
        this.nextCGIngestDecorator = decorator;
    }

    protected abstract List<BlockObject> getAssociatedObjects(BlockConsistencyGroup cg, UnManagedVolume umv,
            IngestionRequestContext requestContext)
            throws Exception;

}
