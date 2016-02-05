/*
 * Copyright (c) 2008-2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.blockingestorchestration.cg;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map.Entry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext;
import com.emc.storageos.api.service.impl.resource.utils.VolumeIngestionUtil;
import com.emc.storageos.db.client.model.AbstractChangeTrackingSet;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.BlockConsistencyGroup.Types;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.StringSetMap;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume;

/**
 * Decorates the VPLEX Volumes in a CG.
 *
 */
public class BlockVplexCGIngestDecorator extends BlockCGIngestDecorator {
    private static final Logger logger = LoggerFactory.getLogger(BlockVplexCGIngestDecorator.class);

    @Override
    public void decorateCG(BlockConsistencyGroup cg, UnManagedVolume umv, Collection<BlockObject> associatedObjects,
            IngestionRequestContext requestContext)
            throws Exception {
        if (null == associatedObjects || associatedObjects.isEmpty()) {
            return;
        }

        for (BlockObject blockObj : associatedObjects) {
            StringSetMap systemCGs = cg.getSystemConsistencyGroups();
     
            // No entries yet in the system consistency groups list.  That's OK, we'll create it.
            if (null == systemCGs || systemCGs.isEmpty()) {
                cg.setSystemConsistencyGroups(new StringSetMap());
            }

            // This volume is not in a CG of this type
            if (blockObj.getReplicationGroupInstance() == null) {
                continue;
            }
            
            boolean found = false;
            // Look through the existing entries in the CG and see if we find a match.
            for (Entry<String, AbstractChangeTrackingSet<String>> systemCGEntry : systemCGs.entrySet()) {
                if (systemCGEntry.getKey().equalsIgnoreCase(blockObj.getStorageController().toString())) {
                    if (systemCGEntry.getValue().contains(blockObj.getReplicationGroupInstance())) {
                        logger.info(String.format("Found BlockObject %s,%s system details in cg %s", blockObj.getNativeGuid(),
                                blockObj.getReplicationGroupInstance(), cg.getLabel()));
                        found = true;
                        break;
                    }
                }
            }
            
            // If we didn't find this storage:cg combo, let's add it.
            if (!found) {
                logger.info(String.format("Adding BlockObject %s/%s in CG %s", blockObj.getNativeGuid(),
                        blockObj.getReplicationGroupInstance(), cg.getLabel()));
                            cg.addSystemConsistencyGroup(blockObj.getStorageController().toString(),
                                    blockObj.getReplicationGroupInstance());
            }
            
            if (!cg.getTypes().contains(Types.VPLEX.toString())) {
                cg.getTypes().add(Types.VPLEX.toString());
            }
        }
    }

    @Override
    public Collection<BlockObject> getAssociatedObjects(BlockConsistencyGroup cg, UnManagedVolume umv,
            IngestionRequestContext requestContext) {
        Collection<BlockObject> blockObjects = VolumeIngestionUtil.findBlockObjectsInCg(cg, requestContext);

        // Filter in vplex volumes
        Iterator<BlockObject> blockObjectItr = blockObjects.iterator();
        while (blockObjectItr.hasNext()) {
            BlockObject blockObject = blockObjectItr.next();
            if (blockObject instanceof Volume) {
                Volume volume = (Volume) blockObject;
                if (!volume.checkForVplexVirtualVolume(dbClient)) {
                    blockObjectItr.remove();
                }
            }
        }
        return blockObjects;
    }

}
