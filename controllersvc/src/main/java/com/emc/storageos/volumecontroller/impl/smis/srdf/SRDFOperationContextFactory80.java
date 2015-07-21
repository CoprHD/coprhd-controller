/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.smis.srdf;

import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.volumecontroller.impl.smis.SRDFOperations;
import com.emc.storageos.volumecontroller.impl.smis.srdf.collectors.ActiveSynchronizationsOnlyFilter;
import com.emc.storageos.volumecontroller.impl.smis.srdf.collectors.CollectorStrategy;
import com.emc.storageos.volumecontroller.impl.smis.srdf.executors.*;

import java.util.Arrays;

import static com.emc.storageos.volumecontroller.impl.smis.srdf.AbstractSRDFOperationContextFactory.SRDFOperation.*;

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
        switch(operation) {
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
                    executorStrategy = new SuspendStorageSyncsStrategy(helper);
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
                    executorStrategy = new EstablishStorageSyncsStrategy(helper);
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
