/*
 * Copyright (c) 2016 EMC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.file.tasks;

import java.net.URI;
import java.util.HashSet;
import java.util.Set;

import com.emc.sa.service.vipr.tasks.WaitForTask;
import com.emc.storageos.model.file.FileShareRestRep;
import com.emc.storageos.model.file.FileSystemVirtualPoolChangeParam;
import com.emc.vipr.client.Task;

public class ChangeFileVirtualPool extends WaitForTask<FileShareRestRep> {
    private URI fileId;
    private URI targetVirtualPoolId;
    private URI targetVArray;
    private URI filePolicy;

    public ChangeFileVirtualPool(URI fileId, URI targetVirtualPoolId, URI filePolicy, URI targetVArray) {
        this.fileId = fileId;
        this.targetVirtualPoolId = targetVirtualPoolId;
        this.targetVArray = targetVArray;
        this.filePolicy = filePolicy;
        provideDetailArgs(fileId, targetVirtualPoolId, filePolicy, targetVArray);
    }

    @Override
    protected Task<FileShareRestRep> doExecute() throws Exception {
        FileSystemVirtualPoolChangeParam param = new FileSystemVirtualPoolChangeParam();
        param.setVirtualPool(targetVirtualPoolId);
        param.setFilePolicy(filePolicy);
        Set<URI> targetArrays = new HashSet<URI>();
        targetArrays.add(targetVArray);
        param.setTargetVArrays(targetArrays);

        return getClient().fileSystems().changeFileVirtualPool(fileId, param);
    }
}
