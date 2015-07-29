/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.hds.prov.job;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.volumecontroller.TaskCompleter;

public class HDSBlockCreateMirrorJob extends HDSCreateVolumeJob {

    private static final Logger log = LoggerFactory.getLogger(HDSBlockCreateMirrorJob.class);

    public HDSBlockCreateMirrorJob(String hdsJob, URI storageSystem,
            URI storagePool, TaskCompleter taskCompleter) {
        super(hdsJob, storageSystem, storagePool, taskCompleter, "CreateBlockMirror");
    }

}
