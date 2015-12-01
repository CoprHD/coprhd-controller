/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.block.taskcompleter;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.Application;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.volumecontroller.TaskCompleter;
public class ApplicationTaskCompleter extends TaskCompleter{

    private static final long serialVersionUID = -9188670003331949130L;
    private static final Logger log = LoggerFactory.getLogger(ApplicationTaskCompleter.class);
    
    public ApplicationTaskCompleter(URI applicationId, String opId) {
        super(Application.class, applicationId, opId);
    }
    
    @Override
    protected void complete(DbClient dbClient, Operation.Status status, ServiceCoded coded)
            throws DeviceControllerException {
        super.setStatus(dbClient, status, coded);
        updateWorkflowStatus(status, coded);
        log.info("END ApplicationCompleter complete");
    }
}
