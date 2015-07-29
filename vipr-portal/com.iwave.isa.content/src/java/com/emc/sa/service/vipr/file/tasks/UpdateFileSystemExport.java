/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.file.tasks;

import java.net.URI;

import org.apache.commons.lang.StringUtils;

import com.emc.sa.service.vipr.tasks.WaitForTask;
import com.emc.storageos.model.file.FileShareExportUpdateParams;
import com.emc.storageos.model.file.FileShareRestRep;
import com.emc.vipr.client.Task;

public class UpdateFileSystemExport extends WaitForTask<FileShareRestRep> {
    private final URI fileSystemId;
    private final String subDirectory;
    private final FileShareExportUpdateParams input;

    public UpdateFileSystemExport(String fileSystemId, String subDirectory, FileShareExportUpdateParams input) {
        this(uri(fileSystemId), subDirectory, input);
    }

    public UpdateFileSystemExport(URI fileSystemId, String subDirectory, FileShareExportUpdateParams input) {
        this.fileSystemId = fileSystemId;
        this.subDirectory = subDirectory;
        this.input = input;
        provideDetailArgs(fileSystemId, subDirectory, input);
    }

    @Override
    protected Task<FileShareRestRep> doExecute() throws Exception {
        // Don't pass a blank sub-directory to getExport
        String subDir = StringUtils.defaultIfEmpty(subDirectory, null);
        return getClient().fileSystems().updateExport(fileSystemId, subDirectory, input);
    }

}