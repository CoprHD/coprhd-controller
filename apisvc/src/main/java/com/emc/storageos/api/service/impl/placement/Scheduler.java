/*
 * Copyright (c) 2008-2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.placement;

import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.model.block.VolumeCreate;
import com.emc.storageos.volumecontroller.impl.utils.VirtualPoolCapabilityValuesWrapper;

import java.util.List;

public interface Scheduler {
    List getRecommendationsForResources(VirtualArray vArray, Project project, VirtualPool vPool,
            VirtualPoolCapabilityValuesWrapper capabilities);

	
}
