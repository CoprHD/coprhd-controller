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
    public void decorateCG(BlockConsistencyGroup consistencyGroup, Collection<BlockObject> associatedObjects,
            IngestionRequestContext requestContext)
            throws Exception {
        if (null != associatedObjects && !associatedObjects.isEmpty()) {
            for (BlockObject blockObject : associatedObjects) {
                StringSetMap systemCGs = consistencyGroup.getSystemConsistencyGroups();
                if (null != systemCGs && !systemCGs.isEmpty()) {
                    for (Entry<String, AbstractChangeTrackingSet<String>> systemCGEntry : systemCGs.entrySet()) {
                        if (systemCGEntry.getKey().equalsIgnoreCase(blockObject.getStorageController().toString())) {
                            if (checkIfCGNameAlreadyExists(systemCGEntry.getValue(), blockObject.getReplicationGroupInstance())) {
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
        } else {
            logger.info("No associated BlockObject's found for cg {}", consistencyGroup.getLabel());
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
                if (volume.checkForVplexVirtualVolume(getDbClient())) {
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