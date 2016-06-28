/*
 * Copyright (c) 2016 EMC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.file.tasks;

import java.net.URI;

import com.emc.sa.machinetags.MachineTagUtils;
import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;

public class RemoveFileSystemTag extends ViPRExecutionTask<Void> {

    private final String removeTag;
    private final URI fileSystemId;

    public RemoveFileSystemTag(String fileSystemId, String removeTag) {
        this(uri(fileSystemId), removeTag);
    }

    public RemoveFileSystemTag(URI fileSystemId, String removeTag) {
        this.removeTag = removeTag;
        this.fileSystemId = fileSystemId;
    }

    @Override
    public Void executeTask() throws Exception {
        setDetail("RemoveFileSystemTag.detail", removeTag, fileSystemId);
        MachineTagUtils.removeFileSystemTag(getClient(), fileSystemId, removeTag);
        return null;
    }
}
