/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.computesystemcontroller.hostmountadapters;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.computesystemcontroller.impl.ComputeSystemCompleter;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.Operation.Status;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.workflow.WorkflowStepCompleter;

public class MountCompleter extends ComputeSystemCompleter {

    /**
     * Reference to logger
     */
    private static final Logger _logger = LoggerFactory.getLogger(MountCompleter.class);

    public MountCompleter(URI id, String opId) {
        super(FileShare.class, id, false, opId);
    }

    @Override
    protected void complete(DbClient dbClient, Status status, ServiceCoded coded) throws DeviceControllerException {
        switch (status) {
            case error:
                dbClient.error(FileShare.class, this.getId(), getOpId(), coded);
                if (isNotifyWorkflow()) {
                    WorkflowStepCompleter.stepFailed(getOpId(), coded);
                }
                break;
            case ready:
                dbClient.ready(FileShare.class, getId(), getOpId());
                if (isNotifyWorkflow()) {
                    WorkflowStepCompleter.stepSucceded(getOpId());
                }
                break;
            default:
                if (isNotifyWorkflow()) {
                    WorkflowStepCompleter.stepExecuting(getOpId());
                }
                break;
        }
    }

}