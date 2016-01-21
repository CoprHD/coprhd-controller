package com.emc.storageos.api.service.impl.resource.blockingestorchestration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.service.impl.resource.blockingestorchestration.IngestStrategyFactory.IngestStrategyEnum;
import com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext;
import com.emc.storageos.api.service.impl.resource.utils.VolumeIngestionUtil;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume;

public class BlockRpVplexIngestOrchestrator extends BlockVolumeIngestOrchestrator {
    private static final Logger _logger = LoggerFactory.getLogger(BlockVolumeIngestOrchestrator.class);

    @SuppressWarnings("unchecked")
    @Override
    protected <T extends BlockObject> T ingestBlockObjects(IngestionRequestContext requestContext, Class<T> clazz)
            throws IngestionException {

        UnManagedVolume currentRpVplexVolume = requestContext.getCurrentUnmanagedVolume();
        _logger.info("ingesting RP/VPLEX volume: " + currentRpVplexVolume.forDisplay());

        _logger.info("ingesting VPLEX nature of RP/VPLEX UnManagedVolume " + currentRpVplexVolume.forDisplay());
        IngestStrategy ingestStrategy = ingestStrategyFactory.getIngestStrategy(IngestStrategyEnum.VPLEX_VOLUME);
        BlockObject blockObject = ingestStrategy.ingestBlockObjects(requestContext, 
                VolumeIngestionUtil.getBlockObjectClass(currentRpVplexVolume));
        _logger.info("RP/VPLEX BlockObject returned " + blockObject.forDisplay());

        _logger.info("ingesting RecoverPoint nature of RP/VPLEX UnManagedVolume " + currentRpVplexVolume.forDisplay());
        ingestStrategy = ingestStrategyFactory.getIngestStrategy(IngestStrategyEnum.RP_VOLUME);
        blockObject = ingestStrategy.ingestBlockObjects(requestContext, 
                VolumeIngestionUtil.getBlockObjectClass(currentRpVplexVolume));
        _logger.info("RP/VPLEX BlockObject returned " + blockObject.forDisplay());

        return clazz.cast(blockObject);
    }

}
