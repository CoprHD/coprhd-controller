/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.volumecontroller.impl.vnxe.job;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.Operation.Status;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.volumecontroller.TaskCompleter;


public class VNXeFileTaskCompleter extends TaskCompleter{
  
    private static final long serialVersionUID = 1L;
    private static final Logger _logger = LoggerFactory.getLogger(VNXeFileTaskCompleter.class);

    
    /**
     * @param clazz Class type
     * @param id
     * @param opId
     * @param operation operation type
     */
    public VNXeFileTaskCompleter(Class clazz, URI fsId, String opId) {
        super(clazz, fsId, opId);
    }
    
    @Override
    protected void complete(DbClient dbClient, Status status, ServiceCoded coded)
            throws DeviceControllerException {
        _logger.info("VNXeTaskCompleter: set status to {}", status);
        if (isNotifyWorkflow()) {
            // If there is a workflow, update the step to complete.
            updateWorkflowStatus(status, coded);
        }
        super.setStatus(dbClient, status, coded);
        
    }
    
}
