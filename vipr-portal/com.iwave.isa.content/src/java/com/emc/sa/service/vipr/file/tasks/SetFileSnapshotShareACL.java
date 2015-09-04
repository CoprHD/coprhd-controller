/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.file.tasks;

import java.net.URI;

import com.emc.sa.service.vipr.file.FileStorageUtils;
import com.emc.sa.service.vipr.file.FileStorageUtils.FileSystemACLs;
import com.emc.sa.service.vipr.tasks.WaitForTask;
import com.emc.storageos.model.file.FileSnapshotRestRep;
import com.emc.storageos.model.file.ShareACLs;
import com.emc.storageos.model.file.SnapshotCifsShareACLUpdateParams;
import com.emc.vipr.client.Task;

public class SetFileSnapshotShareACL extends WaitForTask<FileSnapshotRestRep> {
    private final String shareName;
    private final URI fileSystemId;
    private final FileSystemACLs[] acls;

    public SetFileSnapshotShareACL(URI fileSystemId, String shareName, FileSystemACLs[] acls) {
        this.shareName = shareName;
        this.fileSystemId = fileSystemId;
        this.acls = acls;
        provideDetailArgs(fileSystemId, shareName);
    }

    @Override
    protected Task<FileSnapshotRestRep> doExecute() throws Exception {
        SnapshotCifsShareACLUpdateParams aclUpdate = new SnapshotCifsShareACLUpdateParams();
        ShareACLs shareACLs = FileStorageUtils.createShareACLs(acls);
        aclUpdate.setAclsToAdd(shareACLs);
        return getClient().fileSnapshots().updateShareACL(fileSystemId, shareName, aclUpdate);
    }
}