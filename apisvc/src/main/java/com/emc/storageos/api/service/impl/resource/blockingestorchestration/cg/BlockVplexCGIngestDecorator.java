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
    public void decorateCG(BlockConsistencyGroup consistencyGroup, UnManagedVolume umv, Collection<BlockObject> associatedObjects,
            IngestionRequestContext requestContext)
            throws Exception {
        if (null != associatedObjects && !associatedObjects.isEmpty()) {
            for (BlockObject blockObject : associatedObjects) {
                StringSetMap systemCGs = consistencyGroup.getSystemConsistencyGroups();
                if (null != systemCGs && !systemCGs.isEmpty()) {
                    for (Entry<String, AbstractChangeTrackingSet<String>> systemCGEntry : systemCGs.entrySet()) {
                        if (systemCGEntry.getKey().equalsIgnoreCase(blockObject.getStorageController().toString())) {
                            if (systemCGEntry.getValue().contains(blockObject.getReplicationGroupInstance())) {
                                logger.info(String.format("Found BlockObject %s,%s system details in cg %s", blockObject.getNativeGuid(),
                                        blockObject.getReplicationGroupInstance(), consistencyGroup.getLabel()));
                                continue;
                            } else {
                                logger.info(String.format("Adding BlockObject %s/%s in CG %s", blockObject.getNativeGuid(),
                                        blockObject.getReplicationGroupInstance(), consistencyGroup.getLabel()));
                                consistencyGroup.addSystemConsistencyGroup(blockObject.getStorageController().toString(),
                                        blockObject.getReplicationGroupInstance());
                            }
                        } else {
                            logger.info(String.format("BlockObject %s system %s not found in CG %s. Hence adding.",
                                    blockObject.getNativeGuid(),
                                    blockObject.getStorageController(), consistencyGroup.getLabel()));
                            consistencyGroup.addSystemConsistencyGroup(blockObject.getStorageController().toString(),
                                    blockObject.getReplicationGroupInstance());
                        }
                        if (!consistencyGroup.getTypes().contains(Types.VPLEX.toString())) {
                            consistencyGroup.getTypes().add(Types.VPLEX.toString());
                        }
                    }
                }
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
