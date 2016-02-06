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
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume;

public abstract class BlockCGIngestDecorator {

    protected BlockCGIngestDecorator nextCGIngestDecorator = null;

    private DbClient dbClient = null;

    /**
     * 
     * @param umv
     * @return
     */
    public abstract boolean isExecuteDecorator(UnManagedVolume umv, IngestionRequestContext requestContext);

    /**
     * Sets the next decorator.
     * 
     * @param decorator
     */
    public abstract void setNextDecorator(BlockCGIngestDecorator decorator);

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

    /**
     * Decorates the CG with its associated BlockObjects.
     * 
     * @param cg
     * @param associatedObjects
     * @param requestContext
     * @throws Exception
     */
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
    public void decorate(BlockConsistencyGroup cg, UnManagedVolume umv, Collection<BlockObject> allCGBlockObjects,
            IngestionRequestContext requestContext)
            throws Exception {
        if (isExecuteDecorator(umv, requestContext)) {
            Collection<BlockObject> associatedObjects = getAssociatedObjects(cg, allCGBlockObjects, requestContext);
            if (null != cg && !associatedObjects.isEmpty()) {
                decorateCG(cg, associatedObjects, requestContext);
            }
        }
        if (null != this.nextCGIngestDecorator) {
            this.nextCGIngestDecorator.decorate(cg, umv, allCGBlockObjects, requestContext);
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

}