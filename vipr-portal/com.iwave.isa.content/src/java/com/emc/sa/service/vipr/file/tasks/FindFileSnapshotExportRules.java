/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.file.tasks;

import java.net.URI;
import java.util.List;

import org.apache.commons.lang.StringUtils;

import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;
import com.emc.storageos.model.file.ExportRule;

public class FindFileSnapshotExportRules extends ViPRExecutionTask<List<ExportRule>> {
    private final URI fileSnapshotId;
    private final Boolean allDirectory;
    private final String subDirectory;

    public FindFileSnapshotExportRules(String fileSnapshotId, Boolean allDirectory, String subDirectory) {
        this(uri(fileSnapshotId), allDirectory, subDirectory);
    }

    public FindFileSnapshotExportRules(URI fileSnapshotId, Boolean allDirectory, String subDirectory) {
        this.fileSnapshotId = fileSnapshotId;
        this.allDirectory = allDirectory;
        this.subDirectory = subDirectory;
        provideDetailArgs(fileSnapshotId, allDirectory, subDirectory);
    }

    public URI getfileSnapshotId() {
        return fileSnapshotId;
    }
    
    @Override
    public List<ExportRule> executeTask() throws Exception {
        // Don't pass a blank sub-directory to getExport
        String subDir = StringUtils.defaultIfEmpty(subDirectory, null);
        return getClient().fileSnapshots().getExport(fileSnapshotId, allDirectory, subDir);
    }
}