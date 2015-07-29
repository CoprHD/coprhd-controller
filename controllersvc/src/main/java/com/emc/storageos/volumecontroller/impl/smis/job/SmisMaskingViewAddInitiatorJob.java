/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.smis.job;

import com.emc.storageos.volumecontroller.TaskCompleter;

import javax.cim.CIMObjectPath;
import java.net.URI;

/**
 * A VMAX Add Initiator job
 */
public class SmisMaskingViewAddInitiatorJob extends SmisJob
{
    public SmisMaskingViewAddInitiatorJob(CIMObjectPath cimJob,
            URI storageSystem,
            TaskCompleter taskCompleter) {
        super(cimJob, storageSystem, taskCompleter, "AddInitiatorToMaskingView");
    }
}