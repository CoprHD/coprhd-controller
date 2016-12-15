/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.computesystemcontroller.impl;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.Operation.Status;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.workflow.WorkflowStepCompleter;

/**
 * @author sanjes
 *
 */
public class VolumeCompleter extends ComputeSystemCompleter {


    protected static final Logger _log = LoggerFactory.getLogger(VolumeCompleter.class);

    public VolumeCompleter(Class clazz, URI id, boolean deactivateOnComplete, String opId) {
        super(clazz, id, deactivateOnComplete, opId);
        _log.info("Creating completer for OpId: " + getOpId());
    }
    
    public VolumeCompleter(URI id, String opId) {
        super(Volume.class, id, false, opId);
        _log.info("Creating completer for OpId: " + getOpId());
    }
    
    @Override
    protected void complete(DbClient dbClient, Status status, ServiceCoded coded) throws DeviceControllerException {
        switch (status) {
            case error:
                dbClient.error(Volume.class, this.getId(), getOpId(), coded);
                break;
            case ready:
                dbClient.ready(Volume.class, this.getId(), getOpId());
                if (isNotifyWorkflow()) {
                    WorkflowStepCompleter.stepSucceded(getOpId());
                }
                break;
            case suspended_error:
                    dbClient.suspended_error(Volume.class, this.getId(), getOpId(), coded);
                if (isNotifyWorkflow()) {
                    WorkflowStepCompleter.stepSuspendedError(getOpId(), coded);
                }
                break;
            case suspended_no_error:
                    dbClient.suspended_no_error(Volume.class, this.getId(), getOpId());
                if (isNotifyWorkflow()) {
                    WorkflowStepCompleter.stepSuspendedNoError(getOpId());
                }
                break;
            default:
                dbClient.ready(Volume.class, this.getId(), getOpId());
        }
    }

}
