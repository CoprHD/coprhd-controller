/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.file.tasks;

import java.net.URI;

import com.emc.sa.service.vipr.tasks.WaitForTask;
import com.emc.storageos.model.file.FileShareRestRep;
import com.emc.storageos.model.file.FileSystemDeleteParam;
import com.emc.vipr.client.Task;

public class DeactivateFileSystem extends WaitForTask<FileShareRestRep> {
    private final URI fileSystemId;

    public DeactivateFileSystem(String fileSystemId) {
        this(uri(fileSystemId));
    }

    public DeactivateFileSystem(URI fileSystemId) {
        this.fileSystemId = fileSystemId;
        provideDetailArgs(fileSystemId);
    }

    public URI getFileSystemId() {
        return fileSystemId;
    }

    @Override
    protected Task<FileShareRestRep> doExecute() throws Exception {
        FileSystemDeleteParam param = new FileSystemDeleteParam();
        param.setForceDelete(true);
        return getClient().fileSystems().deactivate(fileSystemId, param);
    }
}
