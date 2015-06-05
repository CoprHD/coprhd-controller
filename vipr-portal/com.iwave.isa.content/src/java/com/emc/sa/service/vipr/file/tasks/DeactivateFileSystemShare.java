/**
* Copyright 2012-2015 iWave Software LLC
* All Rights Reserved
 */
package com.emc.sa.service.vipr.file.tasks;

import java.net.URI;

import com.emc.sa.service.vipr.tasks.WaitForTask;
import com.emc.storageos.model.file.FileShareRestRep;
import com.emc.vipr.client.Task;

public class DeactivateFileSystemShare extends WaitForTask<FileShareRestRep> {
    private final URI fileSystemId;
    private final String shareName;

    public DeactivateFileSystemShare(String fileSystemId, String shareName) {
        this(uri(fileSystemId), shareName);
    }

    public DeactivateFileSystemShare(URI fileSystemId, String shareName) {
        this.fileSystemId = fileSystemId;
        this.shareName = shareName;
        provideDetailArgs(fileSystemId, shareName);
    }

    public URI getFileSystemId() {
        return fileSystemId;
    }

    @Override
    protected Task<FileShareRestRep> doExecute() throws Exception {
        return getClient().fileSystems().removeShare(fileSystemId, shareName);
    }
}
