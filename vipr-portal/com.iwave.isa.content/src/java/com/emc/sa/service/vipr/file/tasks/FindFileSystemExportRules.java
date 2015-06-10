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

public class FindFileSystemExportRules extends ViPRExecutionTask<List<ExportRule>> {
    private final URI fileSystemId;
    private final Boolean allDirectory;
    private final String subDirectory;

    public FindFileSystemExportRules(String fileSystemId, Boolean allDirectory, String subDirectory) {
        this(uri(fileSystemId), allDirectory, subDirectory);
    }

    public FindFileSystemExportRules(URI fileSystemId, Boolean allDirectory, String subDirectory) {
        this.fileSystemId = fileSystemId;
        this.allDirectory = allDirectory;
        this.subDirectory = subDirectory;
        provideDetailArgs(fileSystemId, allDirectory, subDirectory);
    }

    public URI getFileSystemId() {
        return fileSystemId;
    }
    
    @Override
    public List<ExportRule> executeTask() throws Exception {
        // Don't pass a blank sub-directory to getExport
        String subDir = StringUtils.defaultIfEmpty(subDirectory, null);
        return getClient().fileSystems().getExport(fileSystemId, allDirectory, subDir);
    }
}