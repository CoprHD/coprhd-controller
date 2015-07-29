/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.volumecontroller.impl.block.taskcompleter;

import java.net.URI;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.workflow.WorkflowStepCompleter;

public class VolumeWorkflowCompleter extends VolumeTaskCompleter {

    protected static final Logger _log = LoggerFactory.getLogger(VolumeWorkflowCompleter.class);

    public VolumeWorkflowCompleter(List<URI> volUris, String task) {
        super(Volume.class, volUris, task);
        _log.info("Creating completer for OpId: " + getOpId());
    }

    public VolumeWorkflowCompleter(URI volUri, String task) {
        super(Volume.class, volUri, task);
        _log.info("Creating completer for OpId: " + getOpId());
    }

    @Override
    protected void complete(DbClient dbClient, Operation.Status status, ServiceCoded serviceCoded) {
        switch (status) {
            case error:
                for (URI id : getIds()) {
                    dbClient.error(Volume.class, id, getOpId(), serviceCoded);
                }
                if (isNotifyWorkflow()) {
                    WorkflowStepCompleter.stepFailed(getOpId(), serviceCoded);
                }
                break;
            case ready:
                for (URI id : getIds()) {
                    dbClient.ready(Volume.class, id, getOpId());
                }
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
