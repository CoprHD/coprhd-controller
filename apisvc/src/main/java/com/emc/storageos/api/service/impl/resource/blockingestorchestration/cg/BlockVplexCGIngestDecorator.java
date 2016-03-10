/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.blockingestorchestration.cg;

import java.util.ArrayList;
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
import com.emc.storageos.db.client.model.util.BlockConsistencyGroupUtils;

/**
 * This Decorator is responsible for decorating CG with the VPLEX Volume properties.
 * 
 * Ex:-
 * In case of RP + VPLEX, BlockConsistencyGroup should belongs to RP and this class is responsible
 * for decorating properties of VPLEX volumes protected by RP in RP consistencyGroup.
 * 
 * In case of VPLEX + XIO, BlockConsistencyGroup belongs to VPLEX and it will be decorated with VPLEX
 * Virtual volumes.
 *
 */
public class BlockVplexCGIngestDecorator extends BlockCGIngestDecorator {
    private static final Logger logger = LoggerFactory.getLogger(BlockVplexCGIngestDecorator.class);

    @Override
    public void decorateCG(BlockConsistencyGroup cg, Collection<BlockObject> associatedObjects,
            IngestionRequestContext requestContext)
            throws Exception {

        if (null == associatedObjects || associatedObjects.isEmpty()) {
            logger.info("No associated BlockObject's found for cg {}", cg.getLabel());
            return;
        }

        for (BlockObject blockObj : associatedObjects) {
            StringSetMap systemCGs = cg.getSystemConsistencyGroups();

            // No entries yet in the system consistency groups list. That's OK, we'll create it.
            if (null == systemCGs || systemCGs.isEmpty()) {
                cg.setSystemConsistencyGroups(new StringSetMap());
            }

            // This volume is not in a CG of this type
            if (blockObj.getReplicationGroupInstance() == null) {
                logger.info("BlockObject {} doesn't have replicationGroup name {}. No need to set system cg information.",
                        blockObj.getNativeGuid());
                continue;
            }

            boolean found = false;
            // Look through the existing entries in the CG and see if we find a match.
            for (Entry<String, AbstractChangeTrackingSet<String>> systemCGEntry : systemCGs.entrySet()) {
                if (systemCGEntry.getKey().equalsIgnoreCase(blockObj.getStorageController().toString())) {
                    if (checkIfCGNameAlreadyExists(systemCGEntry.getValue(), blockObj.getReplicationGroupInstance())) {
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
                if (blockObj instanceof Volume) {
                    Volume volume = (Volume) blockObj;
                    if (volume.getAssociatedVolumes() != null                             
                            && volume.getAssociatedVolumes().size() > 1) {                        
                        // Since this is a distributed volume, ensure there is a CG entry for each cluster
                        String cgName = BlockConsistencyGroupUtils.fetchCgName(volume.getReplicationGroupInstance());
                        cg.addSystemConsistencyGroup(volume.getStorageController().toString(),
                                BlockConsistencyGroupUtils.buildClusterCgName(BlockConsistencyGroupUtils.CLUSTER_1, cgName));
                        cg.addSystemConsistencyGroup(volume.getStorageController().toString(),
                                BlockConsistencyGroupUtils.buildClusterCgName(BlockConsistencyGroupUtils.CLUSTER_2, cgName));
                        logger.info(String.format("Found BlockObject [%s] is a Distributed VPLEX volume. "
                                + "Adding cg entry [%s] for both cluster1 and cluster2.", blockObj.getNativeGuid(), cgName));
                    } else {
                        cg.addSystemConsistencyGroup(volume.getStorageController().toString(),
                                volume.getReplicationGroupInstance());
                    }
                }
            }

            if (!cg.getTypes().contains(Types.VPLEX.toString())) {
                cg.getTypes().add(Types.VPLEX.toString());
            }
        }
    }

    @Override
    public Collection<BlockObject> getAssociatedObjects(BlockConsistencyGroup cg, Collection<BlockObject> allCGBlockObjects,
            IngestionRequestContext requestContext) {
        Collection<BlockObject> cgVplexAssocBlockObjects = new ArrayList<BlockObject>();

        // Filter in vplex volumes
        Iterator<BlockObject> allCGBlockObjectItr = allCGBlockObjects.iterator();
        while (allCGBlockObjectItr.hasNext()) {
            BlockObject cgBlockObject = allCGBlockObjectItr.next();
            if (cgBlockObject instanceof Volume) {
                Volume volume = (Volume) cgBlockObject;
                if (volume.isVPlexVolume(getDbClient())) {
                    cgVplexAssocBlockObjects.add(volume);
                }
            }
        }
        return cgVplexAssocBlockObjects;
    }

    /**
     * This utility verifies whether cg Name already exists in the list or not.
     * 
     * Since VPLEX Ingestion already populates the cluster & cg name, we don't need to add again here.
     * 
     * @param cgExistingNamesSet
     * @param replicationGroupInstance
     * @return
     */
    private boolean checkIfCGNameAlreadyExists(AbstractChangeTrackingSet<String> cgExistingNamesSet, String replicationGroupInstance) {
        if (null != cgExistingNamesSet && !cgExistingNamesSet.isEmpty()) {
            for (String existingCgName : cgExistingNamesSet) {
                if (existingCgName.contains(replicationGroupInstance)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public void setNextDecorator(BlockCGIngestDecorator decorator) {
        this.nextCGIngestDecorator = decorator;
    }

    @Override
    public boolean isExecuteDecorator(UnManagedVolume umv, IngestionRequestContext requestContext) {
        return VolumeIngestionUtil.isRPProtectingVplexVolumes(umv, requestContext, getDbClient()) || VolumeIngestionUtil.isVplexVolume(umv);
    }

}