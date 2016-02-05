package com.emc.sa.service.vipr.file.tasks;

import java.net.URI;

import com.emc.sa.service.vipr.tasks.WaitForTask;
import com.emc.storageos.model.file.FileShareRestRep;
import com.emc.storageos.model.file.FileSystemVirtualPoolChangeParam;
import com.emc.vipr.client.Task;

public class ChangeFileVirtualPool  extends WaitForTask<FileShareRestRep> {
    private URI fileId;
    private URI targetVirtualPoolId;

    public ChangeFileVirtualPool(URI fileId, URI targetVirtualPoolId) {
        this.fileId = fileId;
        this.targetVirtualPoolId = targetVirtualPoolId;
        provideDetailArgs(fileId, targetVirtualPoolId);
    }

    @Override
    protected Task<FileShareRestRep> doExecute() throws Exception {
        FileSystemVirtualPoolChangeParam param = new FileSystemVirtualPoolChangeParam();
        param.setVirtualPool(targetVirtualPoolId);

        return getClient().fileSystems().changeFileVirtualPool(fileId, param);
    }
}
