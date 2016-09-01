/*
 * Copyright (c) 2008-2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.blockingestorchestration;

import java.net.URI;
import java.util.List;

import org.apache.commons.lang.mutable.MutableInt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedExportMask;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume;
import com.emc.storageos.util.ConnectivityUtil;

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
        @SuppressWarnings("deprecation")
        List<URI> maskUris = dbClient.queryByConstraint(AlternateIdConstraint.Factory.getExportMaskByNameConstraint(mask
                .getMaskName()));
        if (null != maskUris && !maskUris.isEmpty()) {
            boolean checkMaskPathForVplex = false;
            if (maskUris.size() > 1) {
                // if more than one mask was returned by name and this is a vplex system,
                // it could be the case that there are storage views on each vplex cluster
                // with the same name, so we need to check the full unmanaged export mask path
                StorageSystem storageSystem = dbClient.queryObject(StorageSystem.class, mask.getStorageSystemUri());
                checkMaskPathForVplex = storageSystem != null && ConnectivityUtil.isAVPlex(storageSystem);
            }
            for (URI maskUri : maskUris) {
                ExportMask exportMask = dbClient.queryObject(ExportMask.class, maskUri);
                if (null == exportMask) {
                    continue;
                }
                if (checkMaskPathForVplex) {
                    if (exportMask.getNativeId() != null 
                            && !exportMask.getNativeId().equalsIgnoreCase(mask.getNativeId())){
                        // this is not the right mask
                        _logger.info("found a mask with the same name {}, but the mask view paths are different. "
                                + "UnManagedExportMask: {} Existing ExportMask: {}", mask.getMaskName(),
                                mask.getNativeId(), exportMask.getNativeId());
                        continue;
                    }
                }
                // COP-18184 : Check if the initiators are also matching
                if (exportMask.getInitiators() != null
                        && exportMask.getInitiators().containsAll(mask.getKnownInitiatorUris())) {
                    return exportMask;
                }
            }
        }

        return null;
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
