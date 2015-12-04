/*
 * Copyright (c) 2008-2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.blockingestorchestration;

import java.util.List;

import org.apache.commons.lang.mutable.MutableInt;

import com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedExportMask;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume;
import com.emc.storageos.model.block.VolumeExportIngestParam;

public class UnexportedVolumeIngestOrchestrator extends BlockIngestExportOrchestrator {

    @Override
    public <T extends BlockObject> void ingestExportMasks(IngestionRequestContext requestContext, 
            UnManagedVolume unManagedVolume, T blockObject, List<UnManagedExportMask> unManagedMasks, 
            MutableInt masksIngestedCount) throws IngestionException {
        return;
    }

    @Override
    protected ExportMask getExportsMaskAlreadyIngested(UnManagedExportMask mask, DbClient dbClient) {
        // TODO Auto-generated method stub
        return null;
    }

}
