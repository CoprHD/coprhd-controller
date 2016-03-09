/*
 * Copyright (c) 2008-2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.blockingestorchestration;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang.mutable.MutableInt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedExportMask;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume;
import com.emc.storageos.db.client.util.CommonTransformerFunctions;
import com.emc.storageos.model.block.VolumeExportIngestParam;
import com.google.common.collect.Collections2;

/*
 * MULTIPLE_MASK_PER_HOST :
 * Arrays whose existing masking containers can be modeled to export mask in ViPR DB
 * are candidates for this multiple mask per host behavior.
 * Here, during provisioning ViPR creates an export mask object for every masking container
 * found in the Array. There is no restriction of one export mask per host , as the export masks created in
 * ViPR DB are actually a replica of what's there in Array.
 * 
 * VMAX,VNX Block are examples
 */
public class MultipleMaskPerHostIngestOrchestrator extends BlockIngestExportOrchestrator {

    private static final Logger _logger = LoggerFactory.getLogger(MultipleMaskPerHostIngestOrchestrator.class);

    @Override
    public <T extends BlockObject> void ingestExportMasks(IngestionRequestContext requestContext, 
            UnManagedVolume unManagedVolume, T blockObject, List<UnManagedExportMask> unManagedMasks, 
            MutableInt masksIngestedCount) throws IngestionException {
        super.ingestExportMasks(requestContext, unManagedVolume, blockObject, unManagedMasks, masksIngestedCount);
    }

    @Override
    protected ExportMask getExportMaskAlreadyIngested(UnManagedExportMask mask, DbClient dbClient) {
        ExportMask exportMask = null;
        @SuppressWarnings("deprecation")
        List<URI> maskUris = dbClient.queryByConstraint(AlternateIdConstraint.Factory.getExportMaskByNameConstraint(mask
                .getMaskName()));
        if (null != maskUris && !maskUris.isEmpty()) {
            for (URI maskUri : maskUris) {
                exportMask = dbClient.queryObject(ExportMask.class, maskUri);
                // COP-18184 : Check if the initiators are also matching
                if (null != exportMask && exportMask.getInitiators() != null
                        && exportMask.getInitiators().containsAll(mask.getKnownInitiatorUris())) {
                    return exportMask;
                }
            }
        }

        return exportMask;
    }

    /* (non-Javadoc)
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.BlockIngestExportOrchestrator#getExportMaskAlreadyCreated(com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedExportMask, com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext)
     */
    @Override
    protected ExportMask getExportMaskAlreadyCreated(UnManagedExportMask mask, IngestionRequestContext requestContext) {
        List<ExportMask> exportMasks = requestContext.findAllNewExportMasks();
        for (ExportMask createdMask : exportMasks) {
            // COP-18184 : Check if the initiators are also matching
            if (null != createdMask && createdMask.getInitiators() != null
                    && createdMask.getInitiators().containsAll(mask.getKnownInitiatorUris())) {
                _logger.info("Found already-created ExportMask {} matching all initiators of UnManagedExportMask {}",
                        createdMask.getMaskName(), mask.getMaskName());
                return createdMask;
            }
        }

        _logger.info("No existing created mask found for UnManagedExportMask {}", mask.getMaskName());
        return null;
    }
}
