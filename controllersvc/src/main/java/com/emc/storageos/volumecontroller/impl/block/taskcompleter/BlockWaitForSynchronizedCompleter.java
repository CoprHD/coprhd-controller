/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.block.taskcompleter;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.workflow.WorkflowStepCompleter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.List;

public class BlockWaitForSynchronizedCompleter<T extends BlockObject> extends TaskCompleter {

    private static final Logger log = LoggerFactory.getLogger(BlockWaitForSynchronizedCompleter.class);

    public BlockWaitForSynchronizedCompleter(Class<T> clazz, URI target, String opId) {
        super(clazz, target, opId);
    }

    public BlockWaitForSynchronizedCompleter(Class<T> clazz, List<URI> targets, String opId) {
        super(clazz, targets, opId);
    }
    
    @Override
    protected void complete(DbClient dbClient, Operation.Status status,
            ServiceCoded serviceCoded) throws DeviceControllerException {
        log.info("START BlockWaitForSynchronizedCompleter " + status + " for {}", getId());
        switch (status) {
        case error:
            WorkflowStepCompleter.stepFailed(getOpId(), serviceCoded);
            break;
        case ready:
            WorkflowStepCompleter.stepSucceded(getOpId());
            break;
        default:
            WorkflowStepCompleter.stepExecuting(getOpId());
            break;
        }
    }
}
