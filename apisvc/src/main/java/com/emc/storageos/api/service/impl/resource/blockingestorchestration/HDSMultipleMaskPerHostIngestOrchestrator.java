/*
 * Copyright (c) 2008-2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.blockingestorchestration;

import java.net.URI;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.lang.mutable.MutableInt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedExportMask;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume;

/*
 * MULTIPLE_MASKS_PER_HOST :
 * Arrays whose existing masking containers can be modeled to export mask in ViPR DB
 * are candidates for this multiple mask per host behavior.
 * Here, during provisioning ViPR creates an export mask object for every masking container
 * found in the Array. There is no restriction of one export mask per host , as the export masks created in
 * ViPR DB are actually a replica of what's there in Array.
 * 
 * This is applicable to HDS Arrays.
 */
public class HDSMultipleMaskPerHostIngestOrchestrator extends BlockIngestExportOrchestrator {

    private static final Logger logger = LoggerFactory.getLogger(HDSMultipleMaskPerHostIngestOrchestrator.class);

    @Override
    protected <T extends BlockObject> void ingestExportMasks(IngestionRequestContext requestContext, UnManagedVolume unManagedVolume,
            T blockObject, List<UnManagedExportMask> unManagedMasks, MutableInt masksIngestedCount) throws IngestionException {
        logger.info("Ingestion exportMasks for volume: {}", unManagedVolume.getNativeGuid());
        validateUmvVPool(requestContext, unManagedVolume, unManagedMasks, blockObject);
        super.ingestExportMasks(requestContext, unManagedVolume, blockObject, unManagedMasks, masksIngestedCount);
    }

    private <T> void validateUmvVPool(IngestionRequestContext requestContext, UnManagedVolume unManagedVolume,
            List<UnManagedExportMask> unManagedMasks, T blockObject) {
        Iterator<UnManagedExportMask> itr = unManagedMasks.iterator();
        while (itr.hasNext()) {
            // Iterator through each UnManagedExportMask and validate the vpool path parameters.
            UnManagedExportMask umask = itr.next();

        }
    }

    @Override
    protected ExportMask getExportsMaskAlreadyIngested(UnManagedExportMask mask, DbClient dbClient) {
        ExportMask exportMask = null;
        @SuppressWarnings("deprecation")
        List<URI> maskUris = dbClient.queryByConstraint(AlternateIdConstraint.Factory.getExportMaskByNameConstraint(mask
                .getMaskName()));
        if (null != maskUris && !maskUris.isEmpty()) {
            for (URI maskUri : maskUris) {
                exportMask = dbClient.queryObject(ExportMask.class, maskUri);
                if (null != exportMask) {
                    return exportMask;
                }
            }
        }

        return exportMask;
    }

}
