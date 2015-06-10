/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.file.tasks;

import java.net.URI;

import org.apache.commons.lang.StringUtils;

import com.emc.sa.service.vipr.tasks.WaitForTask;
import com.emc.storageos.model.file.FileShareRestRep;
import com.emc.vipr.client.Task;

public class DeactivateFileSystemExportRule extends WaitForTask<FileShareRestRep> {
    private final URI fileSystemId;
    private final Boolean allDirectory;
    private final String subDirectory;

    public DeactivateFileSystemExportRule(String fileSystemId, Boolean allDirectory, String subDirectory) {
        this(uri(fileSystemId), allDirectory, subDirectory);
    }

    public DeactivateFileSystemExportRule(URI fileSystemId, Boolean allDirectory, String subDirectory) {
        this.fileSystemId = fileSystemId;
        this.allDirectory = allDirectory;
        this.subDirectory = subDirectory;
        provideDetailArgs(fileSystemId, allDirectory, subDirectory);
    }

    public URI getFileSystemId() {
        return fileSystemId;
    }

    @Override
    protected Task<FileShareRestRep> doExecute() throws Exception {
        if (StringUtils.isBlank(subDirectory)) {
            return getClient().fileSystems().deleteAllExport(fileSystemId, allDirectory);
        }
        return getClient().fileSystems().deleteExport(fileSystemId, allDirectory, subDirectory);
    }
}