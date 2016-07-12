/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.vnxe.job;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.Snapshot;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.workflow.WorkflowStepCompleter;

public class VNXeFSSnapshotTaskCompleter extends TaskCompleter {

    private static final long serialVersionUID = -1491254877900460861L;
    private static final Logger _logger = LoggerFactory.getLogger(VNXeFSSnapshotTaskCompleter.class);

    public VNXeFSSnapshotTaskCompleter(Class clazz, URI snapId, String opId) {
        super(clazz, snapId, opId);
    }

    @Override
    protected void complete(DbClient dbClient, Operation.Status status, ServiceCoded coded) throws DeviceControllerException {

        Snapshot snapshot = dbClient.queryObject(Snapshot.class, getId());
        FileShare fsObj = dbClient.queryObject(FileShare.class, snapshot.getParent());
        switch (status) {
            case error:
                dbClient.error(Snapshot.class, getId(), getOpId(), coded);
                if (fsObj != null) {
                    dbClient.error(FileShare.class, fsObj.getId(), getOpId(), coded);
                }
                if (isNotifyWorkflow()) {
                    WorkflowStepCompleter.stepFailed(getOpId(), coded);
                }
                break;
            case ready:
                dbClient.ready(Snapshot.class, getId(), getOpId());
                if (fsObj != null) {
                    dbClient.ready(FileShare.class, fsObj.getId(), getOpId());
                }
                if (isNotifyWorkflow()) {
                    WorkflowStepCompleter.stepSucceded(getOpId());
                }
                _logger.info("Done Snapshot operation {}, with Status: {}", getOpId(), status.name());
                break;
            default:
                if (isNotifyWorkflow()) {
                    WorkflowStepCompleter.stepExecuting(getOpId());
                }
                break;
        }
    }
}
