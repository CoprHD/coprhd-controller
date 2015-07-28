/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.file.tasks;

import static com.emc.sa.util.ArrayUtil.safeArrayCopy;

import java.net.URI;

import com.emc.sa.service.vipr.file.FileStorageUtils;
import com.emc.sa.service.vipr.file.FileStorageUtils.FileSystemACLs;
import com.emc.sa.service.vipr.tasks.WaitForTask;
import com.emc.storageos.model.file.FileCifsShareACLUpdateParams;
import com.emc.storageos.model.file.FileShareRestRep;
import com.emc.storageos.model.file.ShareACLs;
import com.emc.vipr.client.Task;

public class SetFileSystemShareACL extends WaitForTask<FileShareRestRep> {
    private final String shareName;
    private final URI fileSystemId;
    private final FileSystemACLs[] acls;
    
    public SetFileSystemShareACL(URI fileSystemId, String shareName, FileSystemACLs[] acls) {
        this.shareName = shareName;
        this.fileSystemId = fileSystemId;
        this.acls = safeArrayCopy(acls);
        provideDetailArgs(fileSystemId, shareName);
    }

    @Override
    protected Task<FileShareRestRep> doExecute() throws Exception {
        FileCifsShareACLUpdateParams aclUpdate = new FileCifsShareACLUpdateParams();
        ShareACLs aclsToAdd = FileStorageUtils.createShareACLs(acls);
        aclUpdate.setAclsToAdd(aclsToAdd);
        return getClient().fileSystems().updateShareACL(fileSystemId,shareName,aclUpdate);
    }
}
