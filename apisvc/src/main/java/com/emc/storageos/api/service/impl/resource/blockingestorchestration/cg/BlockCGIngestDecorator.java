/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.blockingestorchestration.cg;

import java.util.Collection;

import com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.BlockObject;

public abstract class BlockCGIngestDecorator {

    private BlockCGIngestDecorator nextCGIngestDecorator = null;

    private DbClient dbClient = null;

    public abstract void decorateCG(BlockConsistencyGroup cg, Collection<BlockObject> associatedObjects,
            IngestionRequestContext requestContext)
            throws Exception;

    /**
     * Decorate the given CG with its associated block objects properties.
     * 
     * In BlockConsistencyGroup, we should populate the system info & type of the system it is handling.
     * 
     * Till next decorator exists, we should decorate CG with respective decorator associated objects.
     * 
     * @param cg - ConsistencyGroup to decorator
     * @param allCGBlockObjects - All CG block objects to process.
     */
    public void decorate(BlockConsistencyGroup cg, Collection<BlockObject> allCGBlockObjects, IngestionRequestContext requestContext)
            throws Exception {
        Collection<BlockObject> associatedObjects = getAssociatedObjects(cg, allCGBlockObjects, requestContext);
        if (null != cg && !associatedObjects.isEmpty()) {
            decorateCG(cg, associatedObjects, requestContext);
        }
        if (null != nextCGIngestDecorator) {
            nextCGIngestDecorator.setDbClient(dbClient);
            nextCGIngestDecorator.decorate(cg, allCGBlockObjects, requestContext);
        }
    }

    /**
     * @param dbClient the dbClient to set
     */
    public void setDbClient(DbClient dbClient) {
        this.dbClient = dbClient;
    }

    /**
     * @return the dbClient
     */
    public DbClient getDbClient() {
        return dbClient;
    }

    /**
     * Sets the next decorator.
     * 
     * @param decorator
     */
    protected void setNextDecorator(BlockCGIngestDecorator decorator) {
        this.nextCGIngestDecorator = decorator;
    }

    /**
     * Returns the Decorator associated blockObjects.
     * 
     * @param cg - ConsistencyGroup to decorate
     * @param allCGBlockObjects - All CG blockObjects
     * @param requestContext - current unManagedVolume Ingestion context.
     * @return
     * @throws Exception
     */
    protected abstract Collection<BlockObject> getAssociatedObjects(BlockConsistencyGroup cg, Collection<BlockObject> allCGBlockObjects,
            IngestionRequestContext requestContext)
            throws Exception;

}
