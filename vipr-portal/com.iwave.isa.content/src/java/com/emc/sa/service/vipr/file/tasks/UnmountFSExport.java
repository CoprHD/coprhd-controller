/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.file.tasks;

import java.net.URI;

import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;
import com.emc.storageos.model.file.FileShareRestRep;
import com.emc.storageos.model.file.FileSystemUnmountParam;
import com.emc.vipr.client.Task;

/**
 * 
 * @author yelkaa
 * 
 */
public class UnmountFSExport extends ViPRExecutionTask<Task<FileShareRestRep>> {
    private final URI fileSystemId;
    private final FileSystemUnmountParam input;

    public UnmountFSExport(String fileSystemId, FileSystemUnmountParam input) {
        this(uri(fileSystemId), input);
    }

    public UnmountFSExport(URI fileSystemId, FileSystemUnmountParam input) {
        this.fileSystemId = fileSystemId;
        this.input = input;
        setDetail("unmounting FileSystem Id: " + fileSystemId);
    }

    @Override
    public Task<FileShareRestRep> executeTask() throws Exception {
        return getClient().fileSystems().unmountNFS(fileSystemId, input);
    }
}
