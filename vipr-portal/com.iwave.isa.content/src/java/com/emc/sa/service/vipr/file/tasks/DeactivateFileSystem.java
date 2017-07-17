/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.file.tasks;

import java.net.URI;

import com.emc.sa.service.vipr.tasks.WaitForTask;
import com.emc.storageos.model.file.FileShareRestRep;
import com.emc.storageos.model.file.FileSystemDeleteParam;
import com.emc.storageos.volumecontroller.FileControllerConstants;
import com.emc.vipr.client.Task;

public class DeactivateFileSystem extends WaitForTask<FileShareRestRep> {
    private final URI fileSystemId;
    
    private FileControllerConstants.DeleteTypeEnum fileDeletionType;

    public DeactivateFileSystem(String fileSystemId, FileControllerConstants.DeleteTypeEnum fileDeletionType) {
        this(uri(fileSystemId), fileDeletionType);
    }

    public DeactivateFileSystem(URI fileSystemId, FileControllerConstants.DeleteTypeEnum fileDeletionType) {
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
        // Delete file system
        // force delete is applicable only for Inventory delete only!!
        if (fileDeletionType != null && fileDeletionType.equals(FileControllerConstants.DeleteTypeEnum.VIPR_ONLY)) {
            param.setForceDelete(true);
        } else {
            param.setForceDelete(false);
        }
        param.setDeleteType(fileDeletionType.toString());
        return getClient().fileSystems().deactivate(fileSystemId, param);
    }
}
