/**
* Copyright 2012-2015 iWave Software LLC
* All Rights Reserved
 */
package com.emc.sa.service.vipr.file.tasks;

import java.net.URI;

import org.apache.commons.lang3.StringUtils;

import com.emc.sa.service.vipr.tasks.WaitForTask;
import com.emc.storageos.model.file.FileSnapshotRestRep;
import com.emc.vipr.client.Task;

public class DeactivateFileSnapshotExportRule extends WaitForTask<FileSnapshotRestRep> {
    private final URI fileSnapshotId;
    private final Boolean allDirectory;
    private final String subDirectory;

    public DeactivateFileSnapshotExportRule(String fileSnapshotId, Boolean allDirectory, String subDirectory) {
        this(uri(fileSnapshotId), allDirectory, subDirectory);
    }

    public DeactivateFileSnapshotExportRule(URI fileSnapshotId, Boolean allDirectory, String subDirectory) {
        this.fileSnapshotId = fileSnapshotId;
        this.allDirectory = allDirectory;
        this.subDirectory = subDirectory;
        provideDetailArgs(fileSnapshotId, allDirectory, subDirectory);
    }

    public URI getfileSnapshotId() {
        return fileSnapshotId;
    }

    @Override
    protected Task<FileSnapshotRestRep> doExecute() throws Exception {
        if (StringUtils.isBlank(subDirectory)) {
            return getClient().fileSnapshots().deleteAllExport(fileSnapshotId, allDirectory);
        }
        return getClient().fileSnapshots().deleteExport(fileSnapshotId, allDirectory, subDirectory);
    }
}
