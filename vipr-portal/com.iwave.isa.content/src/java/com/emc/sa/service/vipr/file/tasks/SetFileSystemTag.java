/*
 * Copyright (c) 2016 EMC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.file.tasks;

import java.util.HashSet;
import java.util.Set;

import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;
import com.emc.storageos.model.file.FileShareRestRep;

public class SetFileSystemTag extends ViPRExecutionTask<FileShareRestRep> {

    private final String tag;
    private final String fileSystemId;

    public SetFileSystemTag(String fileSystemId, String tag) {
        this.tag = tag;
        this.fileSystemId = fileSystemId;
    }

    @Override
    public FileShareRestRep executeTask() throws Exception {
        Set<String> tags = new HashSet<String>();
        tags.add(tag);
        getClient().fileSystems().addTags(uri(fileSystemId), tags);
        return null;
    }
}
