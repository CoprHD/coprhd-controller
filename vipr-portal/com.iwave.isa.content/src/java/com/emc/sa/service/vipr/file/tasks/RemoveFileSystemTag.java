/*
 * Copyright (c) 2016 EMC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.file.tasks;

import java.net.URI;
import java.util.HashSet;
import java.util.Set;

import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;
import com.emc.storageos.model.file.FileShareRestRep;

public class RemoveFileSystemTag extends ViPRExecutionTask<FileShareRestRep> {

    private final String removeTag;
    private final URI fileSystemId;

    public RemoveFileSystemTag(URI fileSystemId, String removeTag) {
        this.removeTag = removeTag;
        this.fileSystemId = fileSystemId;
    }

    @Override
    public FileShareRestRep executeTask() throws Exception {
        Set<String> removeTags = new HashSet<String>();
        removeTags.add(removeTag);
        setDetail("RemoveFileSystemTag.detail", removeTag, fileSystemId);
        getClient().fileSystems().removeTags(fileSystemId, removeTags);
        return null;
    }
}
