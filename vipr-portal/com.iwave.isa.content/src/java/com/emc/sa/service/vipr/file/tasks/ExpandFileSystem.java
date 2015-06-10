/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.file.tasks;

import java.net.URI;

import com.emc.sa.service.vipr.tasks.WaitForTask;
import com.emc.storageos.model.file.FileShareRestRep;
import com.emc.storageos.model.file.FileSystemExpandParam;
import com.emc.vipr.client.Task;

public class ExpandFileSystem extends WaitForTask<FileShareRestRep> {
    private URI fileSystemId;
    private String newSize;

    public ExpandFileSystem(String fileSystemId, String newSize) {
        this(uri(fileSystemId), newSize);
    }

    public ExpandFileSystem(URI fileSystemId, String newSize) {
        super();
        this.fileSystemId = fileSystemId;
        this.newSize = newSize;
        provideDetailArgs(fileSystemId, newSize);
    }

    @Override
    protected Task<FileShareRestRep> doExecute() throws Exception {
        FileSystemExpandParam expand = new FileSystemExpandParam();
        expand.setNewSize(newSize);
        return getClient().fileSystems().expand(fileSystemId, expand);
    }
}
