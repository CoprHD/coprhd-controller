/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.file.tasks;

import java.net.URI;

import org.apache.commons.lang.StringUtils;

import com.emc.sa.service.vipr.tasks.WaitForTask;
import com.emc.storageos.model.file.FileSnapshotRestRep;
import com.emc.storageos.model.file.SnapshotExportUpdateParams;
import com.emc.vipr.client.Task;

public class UpdateFileSnapshotExport extends WaitForTask<FileSnapshotRestRep> {
    private final URI fileSystemId;
    private final String subDirectory;
    private final SnapshotExportUpdateParams input;

    public UpdateFileSnapshotExport(String fileSystemId, String subDirectory, SnapshotExportUpdateParams input) {
        this(uri(fileSystemId), subDirectory, input);
    }

    public UpdateFileSnapshotExport(URI fileSystemId, String subDirectory, SnapshotExportUpdateParams input) {
        this.fileSystemId = fileSystemId;
        this.subDirectory = subDirectory;
        this.input = input;
        provideDetailArgs(fileSystemId, subDirectory, input);
    }

    @Override
    protected Task<FileSnapshotRestRep> doExecute() throws Exception {
        // Don't pass a blank sub-directory to getExport
        String subDir = StringUtils.defaultIfEmpty(subDirectory, null);
        return getClient().fileSnapshots().updateExport(fileSystemId, subDir, input);
    }

}