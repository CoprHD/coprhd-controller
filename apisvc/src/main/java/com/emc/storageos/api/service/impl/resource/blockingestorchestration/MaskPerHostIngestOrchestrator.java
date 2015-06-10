/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**  Copyright (c) 2008-2015 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.api.service.impl.resource.blockingestorchestration;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang.mutable.MutableInt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.ExportMask;
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
    public <T extends BlockObject> void ingestExportMasks(UnManagedVolume unManagedVolume,
            List<UnManagedExportMask> unManagedMasks, VolumeExportIngestParam param, ExportGroup exportGroup, T volume,
            StorageSystem system, boolean exportGroupCreated, MutableInt masksIngestedCount) throws IngestionException {
        super.ingestExportMasks(unManagedVolume, unManagedMasks, param, exportGroup, volume, system, exportGroupCreated, masksIngestedCount);
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
            if (null == exportMaskUris)
                return eMask;
            for (URI eMaskUri : exportMaskUris) {
                eMask = _dbClient.queryObject(ExportMask.class, eMaskUri);
                if (eMask.getStorageDevice().equals(mask.getStorageSystemUri())) {
                    _logger.info("Found Mask {} with matching initiator and matching Storage System", eMaskUri);
                    maskFound = true;
                    break;
                } else {
                    _logger.info("Found Mask {} with matching initiator and unmatched Storage System. Skipping mask", eMaskUri);
                }
            }
            if (maskFound)
                break;
        }
        return eMask;
    }
}
