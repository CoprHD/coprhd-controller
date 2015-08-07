/*
 * Copyright 2012-2015 EMC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block;

import com.emc.sa.engine.service.Service;

@Service("AddJournalCapacity")
public class AddJournalCapacityService extends CreateVolumeService {
    @Override
    public void execute() throws Exception {
        BlockStorageUtils.addJournalCapacity(project, virtualArray, virtualPool, volumeName, sizeInGb, count,
                consistencyGroup);
    }
}
