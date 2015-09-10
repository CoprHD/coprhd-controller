/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.file.tasks;

import java.net.URI;

import com.emc.sa.service.vipr.tasks.WaitForTask;
import com.emc.storageos.model.file.FileSnapshotRestRep;
import com.emc.vipr.client.Task;

public class DeactivateFileSnapshotExport extends WaitForTask<FileSnapshotRestRep> {
    private final URI fileSnapshotId;
    private final String protocol;
    private final String sectype;
    private final String perm;
    private final String rootMapping;

    public DeactivateFileSnapshotExport(String fileSnapshotId, String protocol, String sectype, String perm,
            String rootMapping) {
        this(uri(fileSnapshotId), protocol, sectype, perm, rootMapping);
    }

    public DeactivateFileSnapshotExport(URI fileSnapshotId, String protocol, String sectype, String perm, String rootMapping) {
        this.fileSnapshotId = fileSnapshotId;
        this.protocol = protocol;
        this.sectype = sectype;
        this.perm = perm;
        this.rootMapping = rootMapping;
        provideDetailArgs(fileSnapshotId, protocol, sectype, perm, rootMapping);
    }

    public URI getFileSnapshotId() {
        return fileSnapshotId;
    }

    @Override
    protected Task<FileSnapshotRestRep> doExecute() throws Exception {
        return getClient().fileSnapshots().removeExport(fileSnapshotId, protocol, sectype, perm, rootMapping);
    }
}
