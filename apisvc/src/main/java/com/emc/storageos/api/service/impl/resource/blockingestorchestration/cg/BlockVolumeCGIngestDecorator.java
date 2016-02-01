/*
 * Copyright (c) 2008-2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.blockingestorchestration.cg;

import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext;
import com.emc.storageos.api.service.impl.resource.utils.VolumeIngestionUtil;
import com.emc.storageos.db.client.model.AbstractChangeTrackingSet;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.StringSetMap;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedConsistencyGroup;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume.SupportedVolumeInformation;

public class BlockVolumeCGIngestDecorator extends BlockCGIngestDecorator {
    private static final Logger logger = LoggerFactory.getLogger(BlockVolumeCGIngestDecorator.class);

    @Override
    public void decorateCG(BlockConsistencyGroup cg, UnManagedVolume umv, List<BlockObject> associatedObjects,
            IngestionRequestContext requestContext)
            throws Exception {
        if (null != associatedObjects && !associatedObjects.isEmpty()) {
            for (BlockObject blockObj : associatedObjects) {
                StringSetMap systemCGs = cg.getSystemConsistencyGroups();
                if (null != systemCGs && !systemCGs.isEmpty()) {
                    for (Entry<String, AbstractChangeTrackingSet<String>> systemCGEntry : systemCGs.entrySet()) {
                        if (systemCGEntry.getKey().equalsIgnoreCase(blockObj.getStorageController().toString())) {
                            if (systemCGEntry.getValue().contains(blockObj.getReplicationGroupInstance())) {
                                logger.info(String.format("Found BlockObject %s,%s system details in cg %s", blockObj.getNativeGuid(),
                                        blockObj.getReplicationGroupInstance(), cg.getLabel()));
                                continue;
                            } else {
                                logger.info(String.format("Adding BlockObject %s/%s in CG %s", blockObj.getNativeGuid(),
                                        blockObj.getReplicationGroupInstance(), cg.getLabel()));
                                cg.addSystemConsistencyGroup(blockObj.getStorageController().toString(),
                                        blockObj.getReplicationGroupInstance());
                            }
                        } else {
                            logger.info(String.format("BlockObject %s system %s not found in CG %s. Hence adding.",
                                    blockObj.getNativeGuid(),
                                    blockObj.getStorageController(), cg.getLabel()));
                            cg.addSystemConsistencyGroup(blockObj.getStorageController().toString(), blockObj.getReplicationGroupInstance());
                        }
                    }
                }

            }
        }
    }

    @Override
    public void
            decorateCGBlockObjects(BlockConsistencyGroup cg, UnManagedVolume umv, List<BlockObject> associatedObjects,
                    IngestionRequestContext requestContext)
                    throws Exception {
        if (null != associatedObjects && !associatedObjects.isEmpty()) {
            for (BlockObject blockObj : associatedObjects) {
                blockObj.setConsistencyGroup(cg.getId());
            }
        }
    }

    @Override
    protected List<BlockObject> getAssociatedObjects(BlockConsistencyGroup cg, UnManagedVolume umv, IngestionRequestContext requestContext)
            throws Exception {
        // If the CG belongs to VPLEX
        List<BlockObject> associatedObjects = new ArrayList<BlockObject>();
        StringSet backendVolumes = umv.getVolumeInformation().get(SupportedVolumeInformation.VPLEX_BACKEND_VOLUMES.toString());
        if (null != backendVolumes && !backendVolumes.isEmpty()) {
            for (String backendVolumeNativeGuid : backendVolumes) {
                BlockObject blockObject = requestContext.findCreatedBlockObject(backendVolumeNativeGuid);
                if (null == blockObject) {
                    blockObject = VolumeIngestionUtil.getBlockObject(backendVolumeNativeGuid, dbClient);
                    if (null == blockObject) {
                        logger.warn("BlockObject {} is not yet ingested", backendVolumeNativeGuid);
                        continue;
                    }
                }
                associatedObjects.add(blockObject);
            }
        } else {
            UnManagedConsistencyGroup umcg = VolumeIngestionUtil.getUnManagedConsistencyGroup(umv, dbClient);
            if (umcg.getLabel().equalsIgnoreCase(cg.getLabel())) {
                StringMap managedVolumeMap = umcg.getManagedVolumesMap();
                if (null != managedVolumeMap && !managedVolumeMap.isEmpty()) {
                    for (Entry<String, String> managedVolumeMapEntry : managedVolumeMap.entrySet()) {
                        BlockObject blockObject = requestContext.findCreatedBlockObject(managedVolumeMapEntry.getKey());
                        if (null == blockObject) {
                            blockObject = VolumeIngestionUtil.getBlockObject(managedVolumeMapEntry.getKey(), dbClient);
                            if (null == blockObject) {
                                logger.warn("BlockObject {} is not yet ingested", managedVolumeMapEntry.getKey());
                                continue;
                            }
                        }
                        associatedObjects.add(blockObject);
                    }
                }
            }

        }
        return associatedObjects;
    }

}
