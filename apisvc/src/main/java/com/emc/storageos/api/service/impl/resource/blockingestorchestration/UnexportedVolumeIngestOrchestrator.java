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
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedExportMask;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume;


public class UnexportedVolumeIngestOrchestrator extends BlockIngestExportOrchestrator {

    @Override
    public <T extends BlockObject> void ingestExportMasks(IngestionRequestContext requestContext, 
            UnManagedVolume unManagedVolume, T blockObject, List<UnManagedExportMask> unManagedMasks, 
            MutableInt masksIngestedCount) throws IngestionException {
        return;
    }

    @Override
    protected ExportMask getExportMaskAlreadyIngested(UnManagedExportMask mask, IngestionRequestContext requestContext, DbClient dbClient) {
        // TODO Auto-generated method stub
        return null;
    }

    /* (non-Javadoc)
     * @see com.emc.storageos.api.service.impl.resource.blockingestorchestration.BlockIngestExportOrchestrator#getExportMaskAlreadyCreated(com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedExportMask, com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext)
     */
    @Override
    protected ExportMask getExportMaskAlreadyCreated(UnManagedExportMask mask, IngestionRequestContext requestContext, DbClient dbClient) {
        // TODO Auto-generated method stub
        return null;
    }
}
