/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.file.tasks;

import java.net.URI;

import com.emc.sa.service.vipr.tasks.WaitForTask;
import com.emc.storageos.model.file.FileDeleteTypeEnum;
import com.emc.storageos.model.file.FileShareRestRep;
import com.emc.storageos.model.file.FileSystemDeleteParam;
import com.emc.vipr.client.Task;

public class DeactivateFileSystem extends WaitForTask<FileShareRestRep> {
    private final URI fileSystemId;
    
    private FileDeleteTypeEnum fileDeletionType;

    public DeactivateFileSystem(String fileSystemId, FileDeleteTypeEnum fileDeletionType) {
        this(uri(fileSystemId), fileDeletionType);
    }

    public DeactivateFileSystem(URI fileSystemId, FileDeleteTypeEnum fileDeletionType) {
        this.fileSystemId = fileSystemId;
        this.fileDeletionType = fileDeletionType;
        provideDetailArgs(fileSystemId, fileDeletionType.toString());
    }

    public URI getFileSystemId() {
        return fileSystemId;
    }

    @Override
    protected Task<FileShareRestRep> doExecute() throws Exception {
        FileSystemDeleteParam param = new FileSystemDeleteParam();
        param.setForceDelete(true);
        param.setDeleteType(fileDeletionType.toString());
        return getClient().fileSystems().deactivate(fileSystemId, param);
    }
}
