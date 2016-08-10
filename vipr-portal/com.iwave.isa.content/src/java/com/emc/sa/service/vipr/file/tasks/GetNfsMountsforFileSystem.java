/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.file.tasks;

import java.net.URI;

import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;
import com.emc.storageos.model.file.MountInfoList;

/**
 * 
 * @author yelkaa
 * 
 */
public class GetNfsMountsforFileSystem extends ViPRExecutionTask<MountInfoList> {
    private final URI fileSystemId;

    public GetNfsMountsforFileSystem(String fileSystemId) {
        this(uri(fileSystemId));
    }

    public GetNfsMountsforFileSystem(URI fileSystemId) {
        this.fileSystemId = fileSystemId;
        setDetail("Getting all mounts for FileSystem Id: " + fileSystemId);
    }

    @Override
    public MountInfoList executeTask() throws Exception {
        return getClient().fileSystems().getNFSMounts(fileSystemId);
    }
}
