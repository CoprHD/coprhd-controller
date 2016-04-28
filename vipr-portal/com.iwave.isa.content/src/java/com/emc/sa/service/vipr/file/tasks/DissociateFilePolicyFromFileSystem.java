/*
 * Copyright (c) 2016 EMC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.file.tasks;

import java.net.URI;

import com.emc.sa.service.vipr.tasks.WaitForTask;
import com.emc.storageos.model.file.FileShareRestRep;
import com.emc.vipr.client.Task;

public class DissociateFilePolicyFromFileSystem extends WaitForTask<FileShareRestRep> {

    private final URI fileSystemId;
    private final URI filePolicyId;

    public DissociateFilePolicyFromFileSystem(String fileSystemId, String filePolicyId) {
        this(uri(fileSystemId), uri(filePolicyId));
    }

    public DissociateFilePolicyFromFileSystem(URI fileSystemId, URI filePolicyId) {
        this.fileSystemId = fileSystemId;
        this.filePolicyId = filePolicyId;
        provideDetailArgs(fileSystemId, filePolicyId);
    }

    @Override
    protected Task<FileShareRestRep> doExecute() throws Exception {
        return getClient().fileSystems().dissociateFilePolicy(fileSystemId, filePolicyId);
    }
}
