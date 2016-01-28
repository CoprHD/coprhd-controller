/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.smis.srdf;

import static com.emc.storageos.volumecontroller.impl.smis.srdf.AbstractSRDFOperationContextFactory.SRDFOperation.DELETE_PAIR;
import static com.emc.storageos.volumecontroller.impl.smis.srdf.AbstractSRDFOperationContextFactory.SRDFOperation.SPLIT;
import static com.emc.storageos.volumecontroller.impl.smis.srdf.AbstractSRDFOperationContextFactory.SRDFOperation.SUSPEND;
import static com.emc.storageos.volumecontroller.impl.smis.srdf.AbstractSRDFOperationContextFactory.SRDFOperation.SUSPEND_CONS_EXEMPT;

import java.util.Arrays;

import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.volumecontroller.impl.smis.SRDFOperations;
import com.emc.storageos.volumecontroller.impl.smis.SRDFOperations.Mode;
import com.emc.storageos.volumecontroller.impl.smis.srdf.collectors.ActiveSynchronizationsOnlyFilter;
import com.emc.storageos.volumecontroller.impl.smis.srdf.collectors.CollectorStrategy;
import com.emc.storageos.volumecontroller.impl.smis.srdf.executors.ChangeModeToAdaptiveStrategy;
import com.emc.storageos.volumecontroller.impl.smis.srdf.executors.ChangeModeToAsyncStrategy;
import com.emc.storageos.volumecontroller.impl.smis.srdf.executors.ChangeModeToSyncStrategy;
import com.emc.storageos.volumecontroller.impl.smis.srdf.executors.DetachGroupSyncStrategy;
import com.emc.storageos.volumecontroller.impl.smis.srdf.executors.DetachStorageSyncsStrategy;
import com.emc.storageos.volumecontroller.impl.smis.srdf.executors.EstablishGroupSyncStrategy;
import com.emc.storageos.volumecontroller.impl.smis.srdf.executors.EstablishStorageActiveStrategy;
import com.emc.storageos.volumecontroller.impl.smis.srdf.executors.EstablishStorageSyncsStrategy;
import com.emc.storageos.volumecontroller.impl.smis.srdf.executors.ExecutorStrategy;
import com.emc.storageos.volumecontroller.impl.smis.srdf.executors.FailMechanismGroupSyncStrategy;
import com.emc.storageos.volumecontroller.impl.smis.srdf.executors.FailMechanismStorageSyncsStrategy;
import com.emc.storageos.volumecontroller.impl.smis.srdf.executors.FailbackGroupSyncStrategy;
import com.emc.storageos.volumecontroller.impl.smis.srdf.executors.FailbackStorageSyncsStrategy;
import com.emc.storageos.volumecontroller.impl.smis.srdf.executors.FailoverGroupSyncStrategy;
import com.emc.storageos.volumecontroller.impl.smis.srdf.executors.FailoverStorageSyncsStrategy;
import com.emc.storageos.volumecontroller.impl.smis.srdf.executors.RestoreGroupSyncStrategy;
import com.emc.storageos.volumecontroller.impl.smis.srdf.executors.RestoreStorageSyncsStrategy;
import com.emc.storageos.volumecontroller.impl.smis.srdf.executors.SplitGroupSyncStrategy;
import com.emc.storageos.volumecontroller.impl.smis.srdf.executors.SplitStorageSyncsStrategy;
import com.emc.storageos.volumecontroller.impl.smis.srdf.executors.SuspendGroupSyncStrategy;
import com.emc.storageos.volumecontroller.impl.smis.srdf.executors.SuspendStorageActiveStrategy;
import com.emc.storageos.volumecontroller.impl.smis.srdf.executors.SuspendStorageSyncsStrategy;
import com.emc.storageos.volumecontroller.impl.smis.srdf.executors.SuspendWithConsExemptStrategy;
import com.emc.storageos.volumecontroller.impl.smis.srdf.executors.SwapGroupSyncStrategy;
import com.emc.storageos.volumecontroller.impl.smis.srdf.executors.SwapStorageSyncsStrategy;

/**
 * Created by bibbyi1 on 4/8/2015.
 */
public class SRDFOperationContextFactory80 extends AbstractSRDFOperationContextFactory {

    @Override
    public SRDFOperationContext build(SRDFOperation operation, Volume target) {
        SRDFOperationContext ctx = new SRDFOperationContext();

        // Always set the target volume.
        ctx.setTarget(target);

        // With 8.x Provider, groups are accessible from either Provider.
        ctx.setProviderFinder(findProviderFactory().anyReachable(target));

        // Determine how to collect synchronization instances
        CollectorStrategy collectorStrategy = null;

        if (SUSPEND_CONS_EXEMPT.equals(operation) || DELETE_PAIR.equals(operation)) {
            collectorStrategy = collectorFactory().getSingleCollector();
        } else {
            collectorStrategy = collectorFactory().getCollector(target, true);
        }
        ctx.setCollector(collectorStrategy);

        if (SUSPEND_CONS_EXEMPT.equals(operation) && !isAsync(target)) {
            // suspend+cons_exempt not valid for Sync with/without CG
            operation = SUSPEND;
        }

        if (isPausingOperation(operation)) {
            ctx.appendFilters(new ActiveSynchronizationsOnlyFilter(utils));
        }

        // Determine how to build the SMI-S arguments
        ExecutorStrategy executorStrategy = null;
        switch (operation) {
            case FAIL_MECHANISM:
                if (target.hasConsistencyGroup()) {
                    executorStrategy = new FailMechanismGroupSyncStrategy(helper);
                } else {
                    executorStrategy = new FailMechanismStorageSyncsStrategy(helper);
                }
                break;
            case SUSPEND:
                if (target.hasConsistencyGroup()) {
                    executorStrategy = new SuspendGroupSyncStrategy(helper);
                } else {
                	if (Mode.ACTIVE.equals(Mode.valueOf(target.getSrdfCopyMode()))) {
                		executorStrategy = new SuspendStorageActiveStrategy(helper);
                	} else {
                		executorStrategy = new SuspendStorageSyncsStrategy(helper);
                	}
                }
                break;
            case SUSPEND_CONS_EXEMPT:
                executorStrategy = new SuspendWithConsExemptStrategy(helper);
                break;
            case SPLIT:
                if (target.hasConsistencyGroup()) {
                    executorStrategy = new SplitGroupSyncStrategy(helper);
                } else {
                    executorStrategy = new SplitStorageSyncsStrategy(helper);
                }
                break;
            case ESTABLISH:
                if (target.hasConsistencyGroup()) {
                    executorStrategy = new EstablishGroupSyncStrategy(helper);
                } else {
                    if (Mode.ACTIVE.equals(Mode.valueOf(target.getSrdfCopyMode()))) {
                        executorStrategy = new EstablishStorageActiveStrategy(helper);
                    } else {
                        executorStrategy = new EstablishStorageSyncsStrategy(helper);
                    }
                }
                break;
            case FAIL_OVER:
                if (target.hasConsistencyGroup()) {
                    executorStrategy = new FailoverGroupSyncStrategy(helper);
                } else {
                    executorStrategy = new FailoverStorageSyncsStrategy(helper);
                }
                break;
            case FAIL_BACK:
                if (target.hasConsistencyGroup()) {
                    executorStrategy = new FailbackGroupSyncStrategy(helper);
                } else {
                    executorStrategy = new FailbackStorageSyncsStrategy(helper);
                }
                break;
            case RESTORE:
                if (target.hasConsistencyGroup()) {
                    executorStrategy = new RestoreGroupSyncStrategy(helper);
                } else {
                    executorStrategy = new RestoreStorageSyncsStrategy(helper);
                }
                break;
            case SWAP:
                if (target.hasConsistencyGroup()) {
                    executorStrategy = new SwapGroupSyncStrategy(helper);
                } else {
                    executorStrategy = new SwapStorageSyncsStrategy(helper);
                }
                break;
            case DELETE_GROUP_PAIRS:
                if (target.hasConsistencyGroup()) {
                    executorStrategy = new DetachGroupSyncStrategy(helper);
                } else {
                    executorStrategy = new DetachStorageSyncsStrategy(helper);
                }
                break;
            case DELETE_PAIR:
                executorStrategy = new DetachStorageSyncsStrategy(helper);
                break;
            case RESET_TO_ADAPTIVE:
            	executorStrategy = new ChangeModeToAdaptiveStrategy(helper);
                break;
            case RESET_TO_ASYNC:
            	executorStrategy = new ChangeModeToAsyncStrategy(helper);
                break;
            case RESET_TO_SYNC:
            	executorStrategy = new ChangeModeToSyncStrategy(helper);
                break;
        }
        ctx.setExecutor(executorStrategy);

        return ctx;
    }

    private boolean isAsync(Volume target) {
        return SRDFOperations.Mode.ASYNCHRONOUS.toString().equalsIgnoreCase(target.getSrdfCopyMode());
    }

    private boolean isAsyncWithoutCG(Volume target) {
        return isAsync(target) && !target.hasConsistencyGroup();
    }

    private boolean isPausingOperation(SRDFOperation operation) {
        return Arrays.asList(SPLIT, SUSPEND, SUSPEND_CONS_EXEMPT).contains(operation);
    }
}
