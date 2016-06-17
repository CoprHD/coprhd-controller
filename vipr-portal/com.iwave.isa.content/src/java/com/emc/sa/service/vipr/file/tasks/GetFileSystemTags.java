/*
 * Copyright (c) 2016 EMC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.file.tasks;

import java.util.Set;

import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;

public class GetFileSystemTags extends ViPRExecutionTask<Set<String>> {

    private final String fileSystemId;

    public GetFileSystemTags(String fileSystemId) {
        this.fileSystemId = fileSystemId;
    }

    @Override
    public Set<String> executeTask() throws Exception {
        return getClient().fileSystems().getTags(uri(fileSystemId));
    }
}
