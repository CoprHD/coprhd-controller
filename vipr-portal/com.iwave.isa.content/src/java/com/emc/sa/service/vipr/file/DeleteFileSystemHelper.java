/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.file;

import static com.emc.sa.service.ServiceParams.FILESYSTEMS;

import java.net.URI;
import java.util.List;

import com.emc.sa.engine.bind.Param;
import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;
import com.emc.storageos.model.file.FileShareRestRep;

public class DeleteFileSystemHelper {
    @Param(FILESYSTEMS)
    protected List<String> fileSystemIds;

    private List<FileShareRestRep> fileSystems;

    public void precheck() {
        fileSystems = FileStorageUtils.getFileSystems(ViPRExecutionTask.uris(fileSystemIds));
    }

    public void deleteFileSystems() {
        for (FileShareRestRep fs : fileSystems) {
            URI fsId = fs.getId();
            FileStorageUtils.deleteFileSystem(fsId);
        }
    }
}
