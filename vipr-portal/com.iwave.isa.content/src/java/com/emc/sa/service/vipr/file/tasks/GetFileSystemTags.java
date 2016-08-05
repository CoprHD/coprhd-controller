/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.file.tasks;

import java.util.Set;

import com.emc.sa.machinetags.MachineTagUtils;
import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;

/**
 * 
 * @author yelkaa
 * 
 */
public class GetFileSystemTags extends ViPRExecutionTask<Set<String>> {

    private final String fileSystemId;

    public GetFileSystemTags(String fileSystemId) {
        this.fileSystemId = fileSystemId;
    }

    @Override
    public Set<String> executeTask() throws Exception {
        return MachineTagUtils.getFileSystemTags(getClient(), uri(fileSystemId));
    }
}
