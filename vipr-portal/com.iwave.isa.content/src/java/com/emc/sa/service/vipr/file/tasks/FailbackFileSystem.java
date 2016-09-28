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

public class FailbackFileSystem extends WaitForTasks<FileShareRestRep> {

    public static final String REMOTE_TARGET = "remote";
    private URI failbackSource;
    private URI failbackTarget;
    private String type;
    private boolean replicationConf;

    public FailbackFileSystem(URI failbackSource, URI failbackTarget, String type, boolean replicationConf) {
        this.failbackSource = failbackSource;
        this.failbackTarget = failbackTarget;
        this.type = type;
        this.replicationConf = replicationConf;
        provideDetailArgs(failbackSource, failbackTarget, type);
    }

    @Override
    protected Tasks<FileShareRestRep> doExecute() throws Exception {
        FileCopy copy = new FileCopy();
        copy.setType(type);
        copy.setCopyID(failbackTarget);

        FileReplicationParam param = new FileReplicationParam();
        param.setReplicateConfiguration(replicationConf);
        param.getCopies().add(copy);
        return getClient().fileSystems().failBackContinousCopies(failbackSource, param);
    }

}

