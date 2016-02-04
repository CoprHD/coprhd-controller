/*
 * Copyright (c) 2012-2016 EMC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.file.tasks;

import java.net.URI;

import com.emc.sa.service.vipr.tasks.WaitForTask;
import com.emc.storageos.model.file.FileReplicationCreateParam;
import com.emc.storageos.model.file.FileShareRestRep;
import com.emc.vipr.client.Task;

public class CreateFileContinuousCopy extends WaitForTask<FileShareRestRep> {
    
    private URI fileId;
    private String name;
    private String type;

    public CreateFileContinuousCopy(URI fileId, String name, String type) {
        this.fileId = fileId;
        this.name = name;
        this.type = type;
        provideDetailArgs(fileId, name, type);
    }

    @Override
    protected Task<FileShareRestRep> doExecute() throws Exception {
        FileReplicationCreateParam param = new FileReplicationCreateParam();
        param.setCopyName(name);
        param.setType(type);
        
        return getClient().fileSystems().createFileContinuousCopies(fileId, param);
    }
}
