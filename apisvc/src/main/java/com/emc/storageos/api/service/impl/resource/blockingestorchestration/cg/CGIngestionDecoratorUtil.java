/**
 * 
 */
package com.emc.storageos.api.service.impl.resource.blockingestorchestration.cg;

import com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.BlockConsistencyGroup.Types;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume;

/**
 * @author gangak
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

    public static void decorateVplexCG(BlockConsistencyGroup cg, UnManagedVolume umv, IngestionRequestContext requestContext)
            throws Exception {
        vplexCGDecorator.decorate(cg, umv, requestContext);
    }

    public static void decorateRPCG(BlockConsistencyGroup cg, UnManagedVolume umv, IngestionRequestContext requestContext) throws Exception {
        rpCGDecorator.decorate(cg, umv, requestContext);
    }

    public static void decorateVolumeCG(BlockConsistencyGroup cg, UnManagedVolume umv, IngestionRequestContext requestContext)
            throws Exception {
        volumeCGDecorator.decorate(cg, umv, requestContext);
    }

    public static void decorate(UnManagedVolume umv, BlockConsistencyGroup cg, IngestionRequestContext requestContext) throws Exception {
        if (cg.getRequestedTypes().contains(Types.VPLEX.toString())) {
            decorateVplexCG(cg, umv, requestContext);
        } else if (cg.getRequestedTypes().contains(Types.RP.toString())) {
            decorateRPCG(cg, umv, requestContext);
        }
        decorateVolumeCG(cg, umv, requestContext);
    }

}
