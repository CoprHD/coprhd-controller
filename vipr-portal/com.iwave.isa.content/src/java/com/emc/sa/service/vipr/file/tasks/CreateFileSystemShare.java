/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.file.tasks;

import java.net.URI;

import org.apache.commons.lang.StringUtils;

import com.emc.sa.service.vipr.tasks.WaitForTask;
import com.emc.storageos.model.file.FileShareRestRep;
import com.emc.storageos.model.file.FileSystemShareParam;
import com.emc.vipr.client.Task;

public class CreateFileSystemShare extends WaitForTask<FileShareRestRep> {
    private final String shareName;
    private final String shareComment;
    private final URI fileSystemId;
    private final String subDirectory;
    private final String directoryAcls;

    public CreateFileSystemShare(String shareName, String shareComment, String fileSystemId, String subDirectory, String directoryAcls) {
        this(shareName, shareComment, uri(fileSystemId), subDirectory, directoryAcls);
    }

    public CreateFileSystemShare(String shareName, String shareComment, URI fileSystemId, String subDirectory,
            String directoryAcls) {
        this.shareName = shareName;
        this.shareComment = shareComment;
        this.fileSystemId = fileSystemId;
        this.subDirectory = subDirectory;
        this.directoryAcls = directoryAcls;
        provideDetailArgs(fileSystemId, shareName, shareComment, subDirectory);
    }

    @Override
    protected Task<FileShareRestRep> doExecute() throws Exception {
        FileSystemShareParam fileShare = new FileSystemShareParam();
        fileShare.setShareName(shareName);
        if (StringUtils.isNotBlank(shareComment)) {
            fileShare.setDescription(shareComment);
        }
        if (StringUtils.isNotBlank(subDirectory)) {
            fileShare.setSubDirectory(subDirectory);
        }

        if (StringUtils.isNotBlank(directoryAcls)) {
            fileShare.setDirectoryAclsOptions(directoryAcls);
        }

        return getClient().fileSystems().share(fileSystemId, fileShare);
    }
}
