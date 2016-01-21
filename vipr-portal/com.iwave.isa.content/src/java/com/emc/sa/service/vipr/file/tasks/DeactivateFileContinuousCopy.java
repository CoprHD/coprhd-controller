package com.emc.sa.service.vipr.file.tasks;

import java.net.URI;

import com.emc.sa.service.vipr.tasks.WaitForTasks;
import com.emc.storageos.model.block.CopiesParam;
import com.emc.storageos.model.block.Copy;
import com.emc.storageos.model.file.FileShareRestRep;
import com.emc.vipr.client.Tasks;

public class DeactivateFileContinuousCopy extends WaitForTasks<FileShareRestRep> {
    
    private URI fileId;
    private URI continuousCopyId;

    public DeactivateFileContinuousCopy(URI fileId, URI continuousCopyId) {
        this.fileId = fileId;
        this.continuousCopyId = continuousCopyId;
        provideDetailArgs(fileId, continuousCopyId);
    }

    @Override
    protected Tasks<FileShareRestRep> doExecute() throws Exception {
        Copy copy = new Copy();
        copy.setCopyID(continuousCopyId);
        CopiesParam param = new CopiesParam();
        param.getCopies().add(copy);
        return getClient().fileSystems().deactivateFileContinuousCopies(fileId, param);
    }
}
