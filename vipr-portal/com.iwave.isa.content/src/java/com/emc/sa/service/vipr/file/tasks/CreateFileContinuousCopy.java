/*
 * Copyright (c) 2012-2016 EMC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.file.tasks;

import java.net.URI;

import com.emc.sa.service.vipr.tasks.WaitForTask;
import com.emc.storageos.model.file.Copy;
import com.emc.storageos.model.file.FileReplicationParam;
import com.emc.storageos.model.file.FileShareRestRep;
import com.emc.vipr.client.Task;

public class CreateFileContinuousCopy extends WaitForTask<FileShareRestRep> {
    
    private URI fileId;
    private String name;

    public CreateFileContinuousCopy(URI fileId, String name) {
        this.fileId = fileId;
        this.name = name;
        provideDetailArgs(fileId, name);
    }

    @Override
    protected Task<FileShareRestRep> doExecute() throws Exception {
        Copy copy = new Copy();

        FileReplicationParam param = new FileReplicationParam();
        param.getCopies().add(copy);
        return getClient().fileSystems().startFileContinuousCopies(fileId, param);
    }
}
