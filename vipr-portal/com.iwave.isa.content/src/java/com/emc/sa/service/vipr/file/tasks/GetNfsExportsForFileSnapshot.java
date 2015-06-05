/**
* Copyright 2012-2015 iWave Software LLC
* All Rights Reserved
 */
package com.emc.sa.service.vipr.file.tasks;

import java.net.URI;
import java.util.List;

import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;
import com.emc.storageos.model.file.FileSystemExportParam;

public class GetNfsExportsForFileSnapshot extends ViPRExecutionTask<List<FileSystemExportParam>> {
    private final URI fileSnapshotId;

    public GetNfsExportsForFileSnapshot(String fileSnapshotId) {
        this(uri(fileSnapshotId));
    }

    public GetNfsExportsForFileSnapshot(URI fileSnapshotId) {
        this.fileSnapshotId = fileSnapshotId;
        provideDetailArgs(fileSnapshotId);
    }

    @Override
    public List<FileSystemExportParam> executeTask() throws Exception {
        return getClient().fileSnapshots().getExports(fileSnapshotId);
    }
}
