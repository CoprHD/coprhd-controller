/*
 * Copyright (c) 2008-2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.placement;

import com.emc.storageos.api.service.authorization.PermissionsHelper;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.model.block.VirtualPoolChangeParam;
import com.emc.storageos.volumecontroller.Recommendation;
import com.emc.storageos.volumecontroller.impl.utils.VirtualPoolCapabilityValuesWrapper;

import java.util.List;

public interface Scheduler {
    List getRecommendationsForResources(VirtualArray vArray, Project project, VirtualPool vPool,
            VirtualPoolCapabilityValuesWrapper capabilities);
    
    public List<Recommendation> scheduleStorageForCosChangeUnprotected(final Volume volume,
            final VirtualPool vpool, final List<VirtualArray> targetVarrays,
            final VirtualPoolChangeParam param);
    
    public List<VirtualArray> getTargetVirtualArraysForVirtualPool(final Project project,
            final VirtualPool vpool, final DbClient dbClient,
            final PermissionsHelper permissionHelper);
}
