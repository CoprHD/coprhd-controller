/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.computesystemcontroller.impl;

import java.net.URI;
import java.util.List;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.Operation.Status;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.volumecontroller.TaskCompleter;

public class ComputeSystemCompleter extends TaskCompleter {

    protected boolean deactivateOnComplete;

    public ComputeSystemCompleter(Class clazz, URI id, String opId) {
        super(clazz, id, opId);
    }

    /**
     * @param clazz
     * @param id
     * @param opId
     */
    public ComputeSystemCompleter(Class clazz, URI id, boolean deactivateOnComplete, String opId) {
        super(clazz, id, opId);
        this.deactivateOnComplete = deactivateOnComplete;
    }

    public ComputeSystemCompleter(Class clazz, List<URI> ids, String opId) {
        super(clazz, ids, opId);
    }

    public ComputeSystemCompleter(Class clazz, List<URI> ids, boolean deactivateOnComplete, String opId) {
        super(clazz, ids, opId);
        this.deactivateOnComplete = deactivateOnComplete;
    }

    @Override
    protected void complete(DbClient dbClient, Status status, ServiceCoded coded) throws DeviceControllerException {
        if (isNotifyWorkflow()) {
            // If there is a workflow, update the step to complete.
            updateWorkflowStatus(status, coded);
        }
    }
}
