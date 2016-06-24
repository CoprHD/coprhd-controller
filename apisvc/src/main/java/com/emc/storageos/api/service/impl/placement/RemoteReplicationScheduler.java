/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.placement;


import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.volumecontroller.Recommendation;
import com.emc.storageos.volumecontroller.impl.utils.VirtualPoolCapabilityValuesWrapper;

import java.util.List;
import java.util.Map;

public class RemoteReplicationScheduler implements Scheduler {
    @Override
    public List getRecommendationsForResources(VirtualArray vArray, Project project, VirtualPool vPool, VirtualPoolCapabilityValuesWrapper capabilities) {
        return null;
    }

    @Override
    public String getSchedulerName() {
        return null;
    }

    @Override
    public boolean handlesVpool(VirtualPool vPool, VpoolUse vPoolUse) {
        return false;
    }

    @Override
    public List<Recommendation> getRecommendationsForVpool(VirtualArray vArray, Project project, VirtualPool vPool, VpoolUse vPoolUse, VirtualPoolCapabilityValuesWrapper capabilities, Map<VpoolUse, List<Recommendation>> currentRecommendations) {
        return null;
    }
}
