/*
 * Copyright (c) 2016-2017
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.tasks;

import com.emc.sa.engine.ExecutionTask;
import com.emc.sa.engine.ExecutionUtils;
import com.emc.storageos.model.compute.ComputeSystemRestRep;

public class AcquireComputeSystemLock extends ExecutionTask<Void> {
    private ComputeSystemRestRep computeSystem;

    public AcquireComputeSystemLock(ComputeSystemRestRep computeSystem) {
        this.computeSystem = computeSystem;
        provideDetailArgs(computeSystem.getIpAddress(), computeSystem.getId());
    }

    @Override
    public void execute() throws Exception {
        acquireComputeSystemLock();
    }

    private void acquireComputeSystemLock() {
        if (computeSystem != null) {
            String lockName = computeSystem.getId().toString();
            if (!ExecutionUtils.acquireLock(lockName)) {
                throw stateException("AcquireComputeSystemLock.illegalState.failedComputeSystemLock", lockName);
            }
        }
    }
}