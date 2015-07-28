/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.smis.job;

import com.emc.storageos.volumecontroller.TaskCompleter;

import javax.cim.CIMObjectPath;
import java.net.URI;

/**
 * A VMAX Delete Masking View job
 */
public class SmisDeleteMaskingViewJob extends SmisJob
{
    public SmisDeleteMaskingViewJob(CIMObjectPath cimJob,
            URI storageSystem,
            TaskCompleter taskCompleter) {
        super(cimJob, storageSystem, taskCompleter, "DeleteMaskingView");
    }
}