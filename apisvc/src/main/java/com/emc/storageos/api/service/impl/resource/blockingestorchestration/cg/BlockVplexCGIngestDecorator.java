/*
 * Copyright (c) 2008-2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.blockingestorchestration.cg;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.service.impl.resource.blockingestorchestration.IngestionException;
import com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext;
import com.emc.storageos.api.service.impl.resource.utils.PropertySetterUtil;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
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
        boolean isRP = false;
        if (isRP) {
            // @TODO RP logic to get VPLEX volumes goes here..
        } else {
            BlockObject blockObject = requestContext.findCreatedBlockObject(umv.getNativeGuid());
            associatedObjects.add(blockObject);
        }
        return associatedObjects;

    }

}
