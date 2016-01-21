package com.emc.sa.service.vipr.file.tasks;

import java.net.URI;

import com.emc.sa.service.vipr.tasks.WaitForTasks;
import com.emc.storageos.model.block.CopiesParam;
import com.emc.storageos.model.block.Copy;
import com.emc.storageos.model.file.FileShareRestRep;
import com.emc.vipr.client.Tasks;

public class FailoverFileSystem extends WaitForTasks<FileShareRestRep> {
    public static final String REMOTE_TARGET = "remote";
    private URI fileId;
    private URI failoverTarget;
    private String type;

    public FailoverFileSystem(URI fileId, URI failoverTarget) {
        this(fileId, failoverTarget, "rp");
    }

    public FailoverFileSystem(URI fileId, URI failoverTarget, String type) {
        this.fileId = fileId;
        this.failoverTarget = failoverTarget;
        this.type = type;
        provideDetailArgs(fileId, failoverTarget, type);
    }

    @Override
    protected Tasks<FileShareRestRep> doExecute() throws Exception {
        Copy copy = new Copy();
        copy.setType(type);
        copy.setCopyID(failoverTarget);

        CopiesParam param = new CopiesParam();
        param.getCopies().add(copy);
        return getClient().fileSystems().failover(fileId, param);
    }
}
