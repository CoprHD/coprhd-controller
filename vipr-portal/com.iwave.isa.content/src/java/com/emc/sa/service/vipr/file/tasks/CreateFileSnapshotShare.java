/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.file.tasks;

import java.net.URI;

import org.apache.commons.lang.StringUtils;

import com.emc.sa.service.vipr.tasks.WaitForTask;
import com.emc.storageos.model.file.FileSnapshotRestRep;
import com.emc.storageos.model.file.FileSystemShareParam;
import com.emc.vipr.client.Task;

public class CreateFileSnapshotShare extends WaitForTask<FileSnapshotRestRep> {
    private URI snapshotId;
    private final String shareName;
    private final String shareComment;

    public CreateFileSnapshotShare(String snapshotId, String shareName, String shareComment) {
        this(uri(snapshotId), shareName, shareComment);
    }

    public CreateFileSnapshotShare(URI snapshotId, String shareName, String shareComment) {
        this.snapshotId = snapshotId;
        this.shareName = shareName;
        this.shareComment = shareComment;
        provideDetailArgs(snapshotId, shareName, shareComment);
    }

    @Override
    protected Task<FileSnapshotRestRep> doExecute() throws Exception {
	 FileSystemShareParam fileShare = new FileSystemShareParam();
         fileShare.setShareName(shareName);
         if (StringUtils.isNotBlank(shareComment)) {
             fileShare.setDescription(shareComment);
         }

         return getClient().fileSnapshots().share(snapshotId, fileShare);
    }
}
