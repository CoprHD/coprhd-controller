/**
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
import com.emc.storageos.model.block.VolumeExportIngestParam;
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
    public <T extends BlockObject> void ingestExportMasks(UnManagedVolume unManagedVolume,
            List<UnManagedExportMask> unManagedMasks, VolumeExportIngestParam param, ExportGroup exportGroup, T volume,
            StorageSystem system, boolean exportGroupCreated, MutableInt masksIngestedCount) throws IngestionException {
        super.ingestExportMasks(unManagedVolume, unManagedMasks, param, exportGroup, volume, system, exportGroupCreated, masksIngestedCount);
    }

    @Override
    protected ExportMask getExportsMaskAlreadyIngested(UnManagedExportMask mask, DbClient dbClient) {
        ExportMask exportMask = null;
        @SuppressWarnings("deprecation")
        List<URI> maskUris = dbClient.queryByConstraint(AlternateIdConstraint.Factory.getExportMaskByNameConstraint(mask
                .getMaskName()));
        if (null != maskUris && !maskUris.isEmpty()) {
            return dbClient.queryObject(ExportMask.class, maskUris.get(0));
        }

        return exportMask;
    }

}
