/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.smis.job;

import java.net.URI;

import javax.cim.CIMObjectPath;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.volumecontroller.TaskCompleter;

/**
 * This is a job that can be used when running
 * SmisCommandHelper#invokeMethodSynchronously. The job will generate its own
 * completer, which will not tie into a ViPR object's status. Doing this allows
 * multiple invokeMethodSynchronously calls to be made, without affecting the overall
 * task, sub-workflow, or the workflow. The job object can then be use to check if the
 * operation was successful.
 */

public class SmisSynchSubTaskJob extends SmisJob {
    private static Logger log = LoggerFactory.getLogger(SmisSynchSubTaskJob.class);

    public SmisSynchSubTaskJob(CIMObjectPath cimJob,
            URI storageSystem,
            final String name) {
        super(cimJob, storageSystem, new TaskCompleter() {
            @Override
            protected void complete(DbClient dbClient, Operation.Status status,
                    ServiceCoded coded)
                    throws DeviceControllerException {
                log.info(String.format("Completing synchronous sub-task %s with status %s",
                        name, status.toString()));
            }
        }, name);
    }
}