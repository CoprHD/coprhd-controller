/*
 * Copyright (c) 2016 EMC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.file.tasks;

import java.net.URI;

import com.emc.sa.service.vipr.tasks.WaitForTasks;
import com.emc.storageos.model.file.FileCopy;
import com.emc.storageos.model.file.FileReplicationParam;
import com.emc.storageos.model.file.FileShareRestRep;
import com.emc.vipr.client.Tasks;

public class FailoverFileSystem extends WaitForTasks<FileShareRestRep> {
    public static final String REMOTE_TARGET = "remote";
    private URI failoverSource;
    private URI failoverTarget;
    private String type;
    private boolean replicationConf;

    public FailoverFileSystem(URI failoverSource, URI failoverTarget, String type, boolean replicationConf) {
        this.failoverSource = failoverSource;
        this.failoverTarget = failoverTarget;
        this.type = type;
        this.replicationConf = replicationConf;
        provideDetailArgs(failoverSource, failoverTarget, type);
    }

    @Override
    protected Tasks<FileShareRestRep> doExecute() throws Exception {
        FileCopy copy = new FileCopy();
        copy.setType(type);
        copy.setCopyID(failoverTarget);

        FileReplicationParam param = new FileReplicationParam();
        param.getCopies().add(copy);
        param.setReplicateConfiguration(replicationConf);
        return getClient().fileSystems().failover(failoverSource, param);
    }
}
