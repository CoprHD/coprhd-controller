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

import java.util.List;

import org.apache.commons.lang.mutable.MutableInt;

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
    public <T extends BlockObject> void ingestExportMasks(UnManagedVolume unManagedVolume, List<UnManagedExportMask> unManagedMasks,
            VolumeExportIngestParam param, ExportGroup exportGroup, T volume, StorageSystem system,
            boolean exportGroupCreated, MutableInt masksIngestedCount, List<Initiator> deviceInitiators) throws IngestionException {
        return;
    }

    @Override
    protected ExportMask getExportsMaskAlreadyIngested(UnManagedExportMask mask, DbClient dbClient) {
        // TODO Auto-generated method stub
        return null;
    }

}
