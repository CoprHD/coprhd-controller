/*
 * Copyright (c) 2012-2016 EMC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.file.tasks;

import java.net.URI;

import com.emc.sa.service.vipr.tasks.WaitForTasks;
import com.emc.storageos.model.file.Copy;
import com.emc.storageos.model.file.FileReplicationParam;
import com.emc.storageos.model.file.FileShareRestRep;
import com.emc.vipr.client.Tasks;

public class FailoverFileSystem extends WaitForTasks<FileShareRestRep> {
    public static final String REMOTE_TARGET = "remote";
    private URI failoverSource;
    private URI failoverTarget;
    private String type;

    public FailoverFileSystem(URI failoverSource, URI failoverTarget, String type) {
        this.failoverSource = failoverSource;
        this.failoverTarget = failoverTarget;
        this.type = type;
        provideDetailArgs(failoverSource, failoverTarget, type);
    }

    @Override
    protected Tasks<FileShareRestRep> doExecute() throws Exception {
        Copy copy = new Copy();
        copy.setType(type);
        copy.setCopyID(failoverTarget);

        FileReplicationParam param = new FileReplicationParam();
        param.getCopies().add(copy);
        return getClient().fileSystems().failover(failoverSource, param);
    }
}
