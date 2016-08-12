/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.file.tasks;

import java.net.URI;

import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;
import com.emc.storageos.model.file.FileShareRestRep;
import com.emc.storageos.model.file.FileSystemMountParam;
import com.emc.vipr.client.Task;

/**
 * 
 * @author yelkaa
 * 
 */
public class MountFSExport extends ViPRExecutionTask<Task<FileShareRestRep>> {
    private final URI fileSystemId;
    private final FileSystemMountParam input;

    public MountFSExport(String fileSystemId, FileSystemMountParam input) {
        this(uri(fileSystemId), input);
    }

    public MountFSExport(URI fileSystemId, FileSystemMountParam input) {
        this.fileSystemId = fileSystemId;
        this.input = input;
        setDetail("mounting FileSystem Id: " + fileSystemId);
    }

    @Override
    public Task<FileShareRestRep> executeTask() throws Exception {
        return getClient().fileSystems().mountNFS(fileSystemId, input);
    }
}
