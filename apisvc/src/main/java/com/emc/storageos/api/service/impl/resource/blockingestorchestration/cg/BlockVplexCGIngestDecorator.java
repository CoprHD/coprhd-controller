/*
 * Copyright (c) 2008-2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.blockingestorchestration.cg;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.service.impl.resource.blockingestorchestration.IngestionException;
import com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext;
import com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.impl.RecoverPointVolumeIngestionContext;
import com.emc.storageos.api.service.impl.resource.utils.PropertySetterUtil;
import com.emc.storageos.api.service.impl.resource.utils.VolumeIngestionUtil;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.ProtectionSet;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume.SupportedVolumeInformation;
import com.emc.storageos.db.client.model.util.BlockConsistencyGroupUtils;
import com.emc.storageos.vplexcontroller.VPlexControllerUtils;

/**
 * 
 *
 */
public class BlockVplexCGIngestDecorator extends BlockCGIngestDecorator {
    private static final Logger logger = LoggerFactory.getLogger(BlockVplexCGIngestDecorator.class);

    @Override
    public void decorateCG(BlockConsistencyGroup consistencyGroup, UnManagedVolume umv, List<BlockObject> associatedObjects,
            IngestionRequestContext requestContext)
            throws Exception {
        String cgName = PropertySetterUtil.extractValueFromStringSet(
                SupportedVolumeInformation.VPLEX_CONSISTENCY_GROUP_NAME.toString(), umv.getVolumeInformation());

        // Add a system consistency group mapping for the varray the cluster is connected to
        try {
            StorageSystem system = dbClient.queryObject(StorageSystem.class, umv.getStorageSystemUri());
            Volume volume = (Volume)requestContext.findCreatedBlockObject(umv.getNativeGuid());
            consistencyGroup.setVirtualArray(volume.getVirtualArray());
            String vplexClusterName = VPlexControllerUtils.getVPlexClusterName(
                    dbClient, consistencyGroup.getVirtualArray(), system.getId());
            if (vplexClusterName != null) {

                StringSet unmanagedVolumeClusters = umv.getVolumeInformation().get(
                        SupportedVolumeInformation.VPLEX_CLUSTER_IDS.toString());
                // Add a ViPR CG mapping for each of the VPlex clusters the VPlex CG
                // belongs to.
                if (unmanagedVolumeClusters != null && !unmanagedVolumeClusters.isEmpty()) {
                    Iterator<String> unmanagedVolumeClustersItr = unmanagedVolumeClusters.iterator();
                    String cgCluster = null;
                    while (unmanagedVolumeClustersItr.hasNext()) {
                        if (vplexClusterName.equals(unmanagedVolumeClustersItr.next())) {
                            cgCluster = vplexClusterName;
                            break;
                        }
                    }
                    if (cgCluster != null) {
                        consistencyGroup.addSystemConsistencyGroup(system.getId().toString(),
                                BlockConsistencyGroupUtils.buildClusterCgName(cgCluster, cgName));
                    } else {
                        throw new Exception(
                                "could not determine VPLEX cluster name for consistency group virtual array "
                                        + consistencyGroup.getVirtualArray());
                    }
                } else {
                    throw new Exception(
                            "no VPLEX cluster(s) set on unmanaged volume "
                                    + umv.getLabel());
                }
            } else {
                throw new Exception(
                        "could not determine VPLEX cluster name for virtual array "
                                + consistencyGroup.getVirtualArray());
            }
        } catch (Exception ex) {
            String message = "could not determine VPLEX cluster placement for consistency group "
                    + consistencyGroup.getLabel() + " configured on UnManagedVolume " + umv.getLabel();
            logger.error(message, ex);
            throw IngestionException.exceptions.generalVolumeException(umv.getLabel(), message);
        }
    }

    @Override
    public void
            decorateCGBlockObjects(BlockConsistencyGroup cg, UnManagedVolume umv, List<BlockObject> associatedObjects,
                    IngestionRequestContext requestContext)
                    throws Exception {
        if (!associatedObjects.isEmpty()) {
            for (BlockObject blockObject : associatedObjects) {
                blockObject.setConsistencyGroup(cg.getId());
            }
        }
    }

    @Override
    public List<BlockObject> getAssociatedObjects(BlockConsistencyGroup cg, UnManagedVolume umv, IngestionRequestContext requestContext) {
        // If the UMV is RP, then this should return all RP => VPLEX volumes else just return vplex volume
        List<BlockObject> associatedObjects = new ArrayList<BlockObject>();

        // If this is RP, filter out only the VPLEX volumes
        if (requestContext.getVolumeContext() instanceof RecoverPointVolumeIngestionContext) {
            // Get all of the block objects that are in the protection set
            RecoverPointVolumeIngestionContext rpContext = (RecoverPointVolumeIngestionContext)requestContext.getVolumeContext();
            ProtectionSet pset = rpContext.getManagedProtectionSet();
            
            if (pset == null) {
                return null;
            }
            
            // All of the volumes in the CG are in the "objects to be updated" map in the RP context.
            // Only grab the VPLEX ones in this case.
            List<BlockObject> boList = new ArrayList<BlockObject>();
            for (String volumeIdStr : pset.getVolumes()) {
                for (List<DataObject> dataObjList : rpContext.getObjectsToBeUpdatedMap().values()) {
                    for (DataObject dataObj : dataObjList) {
                        if (URIUtil.identical(dataObj.getId(), URI.create(volumeIdStr))) {
                            Volume volume = (Volume)dataObj;
                            if (volume.checkForVplexVirtualVolume(dbClient)) {
                                boList.add((BlockObject)dataObj);
                            }
                        }
                    }
                }
                
                Collection<BlockObject> dataObjList = rpContext.getObjectsToBeCreatedMap().values();
                for (DataObject dataObj : dataObjList) {
                    if (URIUtil.identical(dataObj.getId(), URI.create(volumeIdStr))) {
                        Volume volume = (Volume)dataObj;
                        if (volume.checkForVplexVirtualVolume(dbClient)) {
                            boList.add((BlockObject)dataObj);
                        }
                    }
                }
            }
            return boList;
        } else {
            BlockObject blockObject = requestContext.findCreatedBlockObject(umv.getNativeGuid());
            associatedObjects.add(blockObject);
        }
        return associatedObjects;

    }

}
