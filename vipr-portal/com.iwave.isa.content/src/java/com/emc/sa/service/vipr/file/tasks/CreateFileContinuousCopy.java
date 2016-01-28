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

public class CreateFileContinuousCopy extends WaitForTasks<FileShareRestRep> {
    
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
    protected Tasks<FileShareRestRep> doExecute() throws Exception {
        Copy copy = new Copy();
        
        copy.setType(type);

        FileReplicationParam param = new FileReplicationParam();
        param.getCopies().add(copy);
        return getClient().fileSystems().startFileContinuousCopies(fileId, param);
    }
}
