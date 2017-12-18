/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.tasks;

import com.emc.sa.engine.ExecutionTask;
import com.emc.sa.engine.ExecutionUtils;
import com.emc.storageos.model.compute.ComputeSystemRestRep;

public class ReleaseComputeSystemLock extends ExecutionTask<Void> {
    private ComputeSystemRestRep computeSystem;

    public ReleaseComputeSystemLock(ComputeSystemRestRep computeSystem) {
        this.computeSystem = computeSystem;
        provideDetailArgs(computeSystem.getIpAddress(), computeSystem.getId());
    }

    @Override
    public void execute() throws Exception {
        String lockName = computeSystem.getId().toString();
        ExecutionUtils.releaseLock(lockName);
    }
}