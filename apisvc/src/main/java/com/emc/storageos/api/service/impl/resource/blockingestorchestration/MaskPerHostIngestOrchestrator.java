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
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedExportMask;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume;
import com.emc.storageos.db.client.util.CommonTransformerFunctions;
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
    protected ExportMask getExportMaskAlreadyIngested(UnManagedExportMask mask, IngestionRequestContext requestContext, DbClient dbClient) {
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
                if (potentialMask.getStorageDevice() != null && potentialMask.getStorageDevice().equals(mask.getStorageSystemUri())) {
                	ExportGroup eg = requestContext.getExportGroup();
					if (null != eg && null != eg.getExportMasks() && eg.getExportMasks().contains(eMaskUri.toString())) {
						// COP-36231: Check whether we are ingesting into existing EG's. This will make sure to pick the right ExportMask
						// when there are cluster & exclusive EMs exists.
						_logger.info("Found Mask {} in the export Group {}", eMaskUri, eg.getId());
						eMask = potentialMask;
						maskFound = true;
						break;
					} else {
						_logger.info("Found Mask {} with matching initiator and unmatched Storage System & EG. Skipping mask", eMaskUri);
					}
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

    /*
     * (non-Javadoc)
     *
     * @see
     * com.emc.storageos.api.service.impl.resource.blockingestorchestration.BlockIngestExportOrchestrator#getExportMaskAlreadyCreated(com.
     * emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedExportMask,
     * com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext)
     */
    @Override
    protected ExportMask getExportMaskAlreadyCreated(UnManagedExportMask mask, IngestionRequestContext requestContext, DbClient dbClient) {
        List<URI> initiatorUris = new ArrayList<URI>(Collections2.transform(
                mask.getKnownInitiatorUris(), CommonTransformerFunctions.FCTN_STRING_TO_URI));
        List<ExportMask> exportMasks = requestContext.findAllNewExportMasks();
        for (URI ini : initiatorUris) {
            for (ExportMask createdMask : exportMasks) {
                if (null != createdMask && createdMask.getInitiators() != null
                        && createdMask.getInitiators().contains(ini.toString())) {
                    if (null != createdMask.getStorageDevice() 
                            && createdMask.getStorageDevice().equals(mask.getStorageSystemUri())) {
                        _logger.info("Found already-created ExportMask {} matching UnManagedExportMask initiator {} and storage system {}",
                                createdMask.getMaskName(), ini, mask.getStorageSystemUri());
                        return createdMask;
                    }
                }

            }
        }

        _logger.info("No existing created mask found for UnManagedExportMask {}", mask.getMaskName());
        return null;
    }
}
