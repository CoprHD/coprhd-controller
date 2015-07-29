/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/*
 * Copyright (c) $today_year. EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.volumecontroller.impl.block.taskcompleter;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.volumecontroller.TaskCompleter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;

public class SimpleTaskCompleter extends TaskCompleter {

    private static final Logger _log = LoggerFactory.getLogger(SimpleTaskCompleter.class);

    /**
     * @param clazz
     * @param id
     * @param opId
     */
    public SimpleTaskCompleter(Class clazz, URI id, String opId) {
        super(clazz, id, opId);
    }

    /**
     * This method will be called upon the job execution finished
     * 
     * @param dbClient
     * @param status
     * @param coded
     * @throws com.emc.storageos.exceptions.DeviceControllerException
     */
    public void complete(DbClient dbClient, Operation.Status status, ServiceCoded coded) throws DeviceControllerException
    {
        _log.info("SimpleTaskCompleter: set status to {}", status);

        if (isNotifyWorkflow()) {
            // If there is a workflow, update the step to complete.
            updateWorkflowStatus(status, coded);
        }

        setStatus(dbClient, status, coded);
    }

}
