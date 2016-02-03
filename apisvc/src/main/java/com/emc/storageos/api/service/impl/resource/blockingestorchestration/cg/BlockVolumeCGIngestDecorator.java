/*
 * Copyright (c) 2008-2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.blockingestorchestration.cg;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext;
import com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.impl.RecoverPointVolumeIngestionContext;
import com.emc.storageos.api.service.impl.resource.utils.VolumeIngestionUtil;
import com.emc.storageos.db.client.model.AbstractChangeTrackingSet;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.BlockConsistencyGroup.Types;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.StringSetMap;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedConsistencyGroup;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume.SupportedVolumeInformation;
import com.emc.storageos.util.VPlexUtil;

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
                        if (!cg.getTypes().contains(Types.LOCAL.toString())) {
                            cg.getTypes().add(Types.LOCAL.toString());
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
            for (String backendUmvNativeGuid : backendVolumes) {
                String volumeNativeGuid = backendUmvNativeGuid.replace(VolumeIngestionUtil.UNMANAGEDVOLUME, VolumeIngestionUtil.VOLUME);
                BlockObject blockObject = requestContext.findCreatedBlockObject(volumeNativeGuid);
                if (null == blockObject) {
                    blockObject = VolumeIngestionUtil.getBlockObject(volumeNativeGuid, dbClient);
                    if (null == blockObject) {
                        logger.warn("BlockObject {} is not yet ingested", volumeNativeGuid);
                        continue;
                    }
                }
                associatedObjects.add(blockObject);
            }
        } else {
            UnManagedConsistencyGroup umcg = VolumeIngestionUtil.getUnManagedConsistencyGroup(umv, dbClient);
            if (umcg != null) {
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
            } else {
                // In the case of RP, we want the block decorator to care about:
                // Non-VPLEX volumes in the protection set
                // VPLEX volume's backing volumes only
                Collection<BlockObject> blockObjects = BlockRPCGIngestDecorator.getAssociatedObjectsStatic(cg, umv, requestContext);
                if (blockObjects != null) {
                    Iterator<BlockObject> blockObjectItr = blockObjects.iterator();
                    while (blockObjectItr.hasNext()) {
                        // Is this a VPLEX volume?
                        Volume volume = (Volume)blockObjectItr.next();
                        if (!volume.checkForVplexVirtualVolume(dbClient)) {
                            associatedObjects.add(volume);
                        } else {
                            // Get the back-end volume(s)
                            StringSet associatedVolumeIds = volume.getAssociatedVolumes();
                            if (associatedVolumeIds != null) {
                                for (String associatedVolumeId : associatedVolumeIds) {
                                    // First look in created block objects for the associated volumes.  This would be the latest version.
                                    BlockObject blockObject = requestContext.findCreatedBlockObject(associatedVolumeId);
                                    if (blockObject == null) {
                                        // Next look in the updated objects.
                                        blockObject = (BlockObject)requestContext.findInUpdatedObjects(URI.create(associatedVolumeId));
                                    }
                                    if (blockObject == null) {
                                        // Finally look in the DB itself.  It may be from a previous ingestion operation.
                                        blockObject = dbClient.queryObject(Volume.class, URI.create(associatedVolumeId));
                                        // Since I pulled this in from the database, we need to add it to the list of objects to update.
                                        ((RecoverPointVolumeIngestionContext)requestContext.getVolumeContext()).getObjectsToBeUpdatedMap().put(blockObject.getNativeGuid(), Arrays.asList(blockObject));
                                    }
                                    if (blockObject != null) {
                                        associatedObjects.add(blockObject);
                                    }
                                }
                            }
                        }
                    }                            
                }
            }

        }
        return associatedObjects;
    }

}
