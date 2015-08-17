/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.file.tasks;

import java.net.URI;
import java.util.List;

import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;
import com.emc.storageos.model.file.FileSystemExportParam;

public class GetNfsExportsForFileSystem extends ViPRExecutionTask<List<FileSystemExportParam>> {
    private final URI fileSystemId;

    public GetNfsExportsForFileSystem(String fileSystemId) {
        this(uri(fileSystemId));
    }

    public GetNfsExportsForFileSystem(URI fileSystemId) {
        this.fileSystemId = fileSystemId;
        provideDetailArgs(fileSystemId);
    }

    @Override
    public List<FileSystemExportParam> executeTask() throws Exception {
        return getClient().fileSystems().getExports(fileSystemId);
    }
}
