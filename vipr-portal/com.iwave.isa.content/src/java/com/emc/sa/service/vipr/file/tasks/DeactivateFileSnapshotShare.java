/**
* Copyright 2012-2015 iWave Software LLC
* All Rights Reserved
 */
package com.emc.sa.service.vipr.file.tasks;

import java.net.URI;

import com.emc.sa.service.vipr.tasks.WaitForTask;
import com.emc.storageos.model.file.FileSnapshotRestRep;
import com.emc.vipr.client.Task;

public class DeactivateFileSnapshotShare extends WaitForTask<FileSnapshotRestRep> {
    private final URI fileSnapshotId;
    private final String shareName;
    
    public DeactivateFileSnapshotShare(String fileSnapshotId, String shareName) {
        this(uri(fileSnapshotId), shareName);
    }

    public DeactivateFileSnapshotShare(URI fileSnapshotId, String shareName) {
        this.fileSnapshotId = fileSnapshotId;
        this.shareName = shareName;
        provideDetailArgs(fileSnapshotId, shareName);
    }

    public URI getFileSnapshotId() {
        return fileSnapshotId;
    }

    @Override
    protected Task<FileSnapshotRestRep> doExecute() throws Exception {
        return getClient().fileSnapshots().removeShare(fileSnapshotId, shareName);
    }
}
