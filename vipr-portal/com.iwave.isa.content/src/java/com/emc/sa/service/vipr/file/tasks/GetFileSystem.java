/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.file.tasks;

import java.net.URI;

import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;
import com.emc.storageos.model.file.FileShareRestRep;

public class GetFileSystem extends ViPRExecutionTask<FileShareRestRep> {
    private URI fileSystemId;

    public GetFileSystem(String fileSystemId) {
        this(uri(fileSystemId));
    }

    public GetFileSystem(URI fileSystemId) {
        this.fileSystemId = fileSystemId;
        provideDetailArgs(fileSystemId);
    }

    @Override
    public FileShareRestRep executeTask() throws Exception {
        return getClient().fileSystems().get(fileSystemId);
    }
}