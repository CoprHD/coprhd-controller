package com.emc.sa.service.vipr.file.tasks;

import java.net.URI;

import com.emc.sa.service.vipr.tasks.WaitForTask;
import com.emc.storageos.model.block.CopiesParam;
import com.emc.storageos.model.block.Copy;
import com.emc.storageos.model.block.VolumeRestRep;
import com.emc.storageos.model.file.FileShareRestRep;
import com.emc.vipr.client.Task;

public class CreateFileContinuousCopy extends WaitForTask<FileShareRestRep> {
    
    private URI fileId;
    private String name;

    public CreateFileContinuousCopy(URI fileId, String name) {
        this.fileId = fileId;
        this.name = name;
        provideDetailArgs(fileId, name);
    }

    @Override
    protected Task<FileShareRestRep> doExecute() throws Exception {
        Copy copy = new Copy();
        if (name != null) {
            copy.setName(name);
        }
        copy.setName(name);

        CopiesParam param = new CopiesParam();
        param.getCopies().add(copy);
        return getClient().fileSystems().startFileContinuousCopies(fileId, param);
    }
}
