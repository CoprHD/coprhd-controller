/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.file.tasks;

import java.net.URI;
import java.util.List;

import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;
import com.emc.storageos.model.file.FileSnapshotRestRep;

public class GetActiveSnapshotsForFileSystem extends ViPRExecutionTask<List<FileSnapshotRestRep>> {
    private URI fileSystemId;

    public GetActiveSnapshotsForFileSystem(String fileSystemId) {
        this(uri(fileSystemId));
    }

    public GetActiveSnapshotsForFileSystem(URI fileSystemId) {
        this.fileSystemId = fileSystemId;
        provideDetailArgs(fileSystemId);
    }

    @Override
    public List<FileSnapshotRestRep> executeTask() throws Exception {
        return getClient().fileSnapshots().getByFileSystem(fileSystemId);
    }
}
