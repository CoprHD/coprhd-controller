/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.hds.prov.job;

import java.net.URI;

import com.emc.storageos.volumecontroller.TaskCompleter;

public class HDSBlockMirrorDeleteJob extends HDSDeleteVolumeJob {

    public HDSBlockMirrorDeleteJob(String hdsJob, URI storageSystem,
            TaskCompleter taskCompleter) {
        super(hdsJob, storageSystem, taskCompleter, "DeleteMirror");
    }

}
