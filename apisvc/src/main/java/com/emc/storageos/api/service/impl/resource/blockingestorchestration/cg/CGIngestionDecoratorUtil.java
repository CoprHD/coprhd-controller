/*
 * Copyright (c) 2008-2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.blockingestorchestration.cg;

import com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext;
import com.emc.storageos.api.service.impl.resource.utils.VolumeIngestionUtil;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume;

/**
 * Util class to get the decorator by unmanaged volume properties.
 *
 */
public class CGIngestionDecoratorUtil {

    private static BlockCGIngestDecorator volumeCGDecorator = null;
    private static BlockCGIngestDecorator vplexCGDecorator = null;
    private static BlockCGIngestDecorator rpCGDecorator = null;

    static {
        vplexCGDecorator = new BlockVplexCGIngestDecorator();
        rpCGDecorator = new BlockRPCGIngestDecorator();
        volumeCGDecorator = new BlockVolumeCGIngestDecorator();
    }

    /**
     * Set the decorators based on the UnManagedVolume properties.
     * 
     * @param umv
     * @param cg
     * @param requestContext
     * @param dbClient
     * @throws Exception
     */
    public static void decorate(UnManagedVolume umv, BlockConsistencyGroup cg, IngestionRequestContext requestContext, DbClient dbClient)
            throws Exception {
        BlockCGIngestDecorator commCGDecorator = null;
        // Check if the UnManagedVolume is RP
        if (VolumeIngestionUtil.checkUnManagedResourceIsRecoverPointEnabled(umv)) {
            commCGDecorator = rpCGDecorator;
            // Check if RP is protecting VPLEX
            if (VolumeIngestionUtil.isRPProtectingVplexVolumes(requestContext, dbClient)) {
                commCGDecorator.setNextDecorator(vplexCGDecorator);
            }
        } else if (VolumeIngestionUtil.isVplexVolume(umv)) {  // Check if the UnManagedVolume is VPLEX
            commCGDecorator = vplexCGDecorator;
            commCGDecorator.setNextDecorator(volumeCGDecorator);
        }
        commCGDecorator.setDbClient(dbClient);
        commCGDecorator.decorate(cg, umv, requestContext);
    }

}
