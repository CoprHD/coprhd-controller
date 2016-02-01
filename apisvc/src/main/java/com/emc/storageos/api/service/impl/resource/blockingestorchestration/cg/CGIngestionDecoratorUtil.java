/*
 * Copyright (c) 2008-2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.blockingestorchestration.cg;

import com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.BlockConsistencyGroup.Types;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume;

/**
 *
 *
 */
public class CGIngestionDecoratorUtil {

    private static BlockVolumeCGIngestDecorator volumeCGDecorator = null;
    private static BlockVplexCGIngestDecorator vplexCGDecorator = null;
    private static BlockRPCGIngestDecorator rpCGDecorator = null;

    static {
        vplexCGDecorator = new BlockVplexCGIngestDecorator();
        rpCGDecorator = new BlockRPCGIngestDecorator();
        volumeCGDecorator = new BlockVolumeCGIngestDecorator();

        rpCGDecorator.setNextDecorator(vplexCGDecorator);
        vplexCGDecorator.setNextDecorator(volumeCGDecorator);
    }

    public static void decorateVplexCG(BlockConsistencyGroup cg, UnManagedVolume umv, IngestionRequestContext requestContext,
            DbClient dbClient)
            throws Exception {
        vplexCGDecorator.setDbClient(dbClient);
        vplexCGDecorator.decorate(cg, umv, requestContext);
    }

    public static void
            decorateRPCG(BlockConsistencyGroup cg, UnManagedVolume umv, IngestionRequestContext requestContext, DbClient dbClient)
                    throws Exception {
        rpCGDecorator.setDbClient(dbClient);
        rpCGDecorator.decorate(cg, umv, requestContext);
    }

    public static void decorateVolumeCG(BlockConsistencyGroup cg, UnManagedVolume umv, IngestionRequestContext requestContext,
            DbClient dbClient)
            throws Exception {
        volumeCGDecorator.setDbClient(dbClient);
        volumeCGDecorator.decorate(cg, umv, requestContext);
    }

    public static void decorate(UnManagedVolume umv, BlockConsistencyGroup cg, IngestionRequestContext requestContext, DbClient dbClient)
            throws Exception {
        if (cg.getRequestedTypes().contains(Types.VPLEX.toString())) {
            decorateVplexCG(cg, umv, requestContext, dbClient);
        } else if (cg.getRequestedTypes().contains(Types.RP.toString())) {
            decorateRPCG(cg, umv, requestContext, dbClient);
        }
        decorateVolumeCG(cg, umv, requestContext, dbClient);
    }

}
