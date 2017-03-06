/*
 * Copyright (c) 2016 EMC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.file.tasks;

import java.net.URI;

import com.emc.sa.service.vipr.tasks.WaitForTask;
import com.emc.storageos.model.file.FileShareRestRep;
import com.emc.vipr.client.Task;

public class AssociateFilePolicyToFileSystem extends WaitForTask<FileShareRestRep> {

    private final URI fileSystemId;
    private final URI filePolicyId;
    private final URI targetVArray;

    public AssociateFilePolicyToFileSystem(String fileSystemId, String filePolicyId, String targetVArray) {
        this(uri(fileSystemId), uri(filePolicyId), uri(targetVArray));
    }

    public AssociateFilePolicyToFileSystem(URI fileSystemId, URI filePolicyId, URI targetVarray) {
        this.fileSystemId = fileSystemId;
        this.filePolicyId = filePolicyId;
        this.targetVArray = targetVarray;
        provideDetailArgs(fileSystemId, filePolicyId, targetVarray);
    }

    @Override
    protected Task<FileShareRestRep> doExecute() throws Exception {
        return getClient().fileSystems().associateFilePolicy(fileSystemId, filePolicyId, targetVArray);
    }
}
