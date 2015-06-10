/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.file.tasks;

import java.net.URI;
import com.emc.sa.service.vipr.tasks.WaitForTask;
import com.emc.storageos.model.file.FileSnapshotRestRep;
import com.emc.storageos.model.file.FileSystemSnapshotParam;
import com.emc.vipr.client.Task;

public class CreateFileSnapshot extends WaitForTask<FileSnapshotRestRep> {
    private URI fileSystemId;
    private String name;

    public CreateFileSnapshot(String fileSystemId, String name) {
        this(uri(fileSystemId), name);
    }

    public CreateFileSnapshot(URI fileSystemId, String name) {
        this.fileSystemId = fileSystemId;
        this.name = name;
        provideDetailArgs(fileSystemId, name);
    }

    @Override
    protected Task<FileSnapshotRestRep> doExecute() throws Exception {
        FileSystemSnapshotParam snapshot = new FileSystemSnapshotParam();
        snapshot.setLabel(name);
        return getClient().fileSnapshots().createForFileSystem(fileSystemId, snapshot);
    }
}
