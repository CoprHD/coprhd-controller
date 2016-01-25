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
 * MASK_PER_HOST :
 * Arrays whose existing masking containers cannot be modeled as export mask in ViPR DB
 * are candidates for this mask per host behavior.
 * Here, during provisioning ViPR creates a logical container Export mask for each Host to get exported
 * through ViPR.Its guaranteed that there will be always only 1 export mask available in ViPR Db at any
 * point of time.
 * 
 * XtremIO,HDS are examples.
 */
public class MaskPerHostIngestOrchestrator extends BlockIngestExportOrchestrator {

    private static final Logger _logger = LoggerFactory.getLogger(MaskPerHostIngestOrchestrator.class);

    @Override
    public <T extends BlockObject> void ingestExportMasks(IngestionRequestContext requestContext, 
            UnManagedVolume unManagedVolume, T blockObject, List<UnManagedExportMask> unManagedMasks, 
            MutableInt masksIngestedCount) throws IngestionException {
        super.ingestExportMasks(requestContext, unManagedVolume, blockObject, unManagedMasks, masksIngestedCount);
    }

    /**
     * maskPerHost Mode guaranteed to have initiators in only 1 export mask
     * always.
     * 
     */
    @Override
    protected ExportMask getExportsMaskAlreadyIngested(UnManagedExportMask mask, DbClient dbClient) {
        ExportMask eMask = null;
        boolean maskFound = false;
        List<URI> initiatorUris = new ArrayList<URI>(Collections2.transform(
                mask.getKnownInitiatorUris(), CommonTransformerFunctions.FCTN_STRING_TO_URI));
        for (URI ini : initiatorUris) {
            List<URI> exportMaskUris = _dbClient.queryByConstraint(AlternateIdConstraint.Factory
                    .getExportMaskInitiatorConstraint(ini.toString()));
            if (null == exportMaskUris) {
                return eMask;
            }
            for (URI eMaskUri : exportMaskUris) {
                ExportMask potentialMask = _dbClient.queryObject(ExportMask.class, eMaskUri);
                if (potentialMask.getStorageDevice().equals(mask.getStorageSystemUri())) {
                    _logger.info("Found Mask {} with matching initiator and matching Storage System", eMaskUri);
                    eMask = potentialMask;
                    maskFound = true;
                    break;
                } else {
                    _logger.info("Found Mask {} with matching initiator and unmatched Storage System. Skipping mask", eMaskUri);
                }
            }
            if (maskFound) {
                break;
            }
        }
        return eMask;
    }
}
