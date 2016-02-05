/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.blockingestorchestration.cg;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext;
import com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.impl.RecoverPointVolumeIngestionContext;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.ProtectionSet;

/**
 * This Decorator is responsible for decorating CG with the RP Volume properties.
 * 
 * Ex:-
 * In case of RP + VPLEX, BlockConsistencyGroup should belongs to RP and it will be decorated with RP Volume properties.
 * 
 * In case of RP + XIO, BlockConsistencyGroup belongs to RP and it will be decorated with XIO volumes.
 *
 */
public class BlockRPCGIngestDecorator extends BlockCGIngestDecorator {

    @Override
    public void decorateCG(BlockConsistencyGroup cg, Collection<BlockObject> associatedObjects,
            IngestionRequestContext requestContext)
            throws Exception {
        // This information is already set in the RP ingestion orchestrator, however in case anyone ever writes a decorator
        // above us, this will ensure we put the right information in their CG to represent our RP CG.
        RecoverPointVolumeIngestionContext rpContext = (RecoverPointVolumeIngestionContext) requestContext.getVolumeContext();
        ProtectionSet pset = rpContext.getManagedProtectionSet();
        cg.getTypes().add(BlockConsistencyGroup.Types.RP.toString());
        cg.addSystemConsistencyGroup(pset.getProtectionSystem().toString(), pset.getLabel());
    }

    @Override
    protected List<BlockObject> getAssociatedObjects(BlockConsistencyGroup cg, Collection<BlockObject> allCGBlockObjects,
            IngestionRequestContext requestContext)
            throws Exception {
        // Get all of the block objects that are in the protection set
        RecoverPointVolumeIngestionContext rpContext = (RecoverPointVolumeIngestionContext) requestContext.getVolumeContext();
        ProtectionSet pset = rpContext.getManagedProtectionSet();

        if (pset == null) {
            return null;
        }

        // All of the volumes in the CG are in the "objects to be updated" map in the RP context.
        List<BlockObject> boList = new ArrayList<BlockObject>();
        for (String volumeIdStr : pset.getVolumes()) {
            for (DataObject dataObj : allCGBlockObjects) {
                if (URIUtil.identical(dataObj.getId(), URI.create(volumeIdStr))) {
                    boList.add((BlockObject) dataObj);
                }
            }
        }
        return boList;
    }

}
