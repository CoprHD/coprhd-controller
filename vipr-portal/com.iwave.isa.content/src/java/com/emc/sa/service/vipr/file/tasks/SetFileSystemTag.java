/*
 * Copyright (c) 2016 EMC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.file.tasks;

import java.net.URI;

import com.emc.sa.machinetags.MachineTagUtils;
import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;

public class SetFileSystemTag extends ViPRExecutionTask<Void> {

    private final String tag;
    private final URI fileSystemId;

    public SetFileSystemTag(String fileSystemId, String tag) {
        this(uri(fileSystemId), tag);
    }

    public SetFileSystemTag(URI fileSystemId, String tag) {
        this.tag = tag;
        this.fileSystemId = fileSystemId;
    }

    @Override
    public Void executeTask() throws Exception {
        setDetail("SetFileSystemTag.title", tag, fileSystemId);
        MachineTagUtils.setFileSystemTag(getClient(), fileSystemId, tag);
        return null;
    }
}
