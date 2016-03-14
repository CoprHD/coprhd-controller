/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.smis.job;

import com.emc.storageos.volumecontroller.TaskCompleter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.cim.CIMObjectPath;
import java.net.URI;

public class SmisBlockResyncSnapshotJob extends SmisJob {
    private static final Logger _log = LoggerFactory.getLogger(SmisBlockResyncSnapshotJob.class);

    public SmisBlockResyncSnapshotJob(CIMObjectPath cimJob,
            URI storageSystem,
            TaskCompleter taskCompleter) {
        super(cimJob, storageSystem, taskCompleter, "ResyncBlockSnapshot");
    }
}
