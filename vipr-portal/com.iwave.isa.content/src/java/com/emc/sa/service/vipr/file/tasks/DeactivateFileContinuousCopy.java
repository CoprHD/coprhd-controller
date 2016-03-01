/*
 * Copyright (c) 2016 EMC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.file.tasks;

import java.net.URI;

import com.emc.sa.service.vipr.tasks.WaitForTask;
import com.emc.storageos.model.file.FileShareRestRep;
import com.emc.storageos.model.file.FileSystemDeleteParam;
import com.emc.vipr.client.Task;

public class DeactivateFileContinuousCopy extends WaitForTask<FileShareRestRep> {
    
    private URI fileId;
    private URI continuousCopyId;
    private String type;

    public DeactivateFileContinuousCopy(URI fileId, URI continuousCopyId, String type) {
        this.fileId = fileId;
        this.continuousCopyId = continuousCopyId;
        this.type = type;
        provideDetailArgs(fileId, continuousCopyId, type);
    }

    @Override
    protected Task<FileShareRestRep> doExecute() throws Exception {
        FileSystemDeleteParam param = new FileSystemDeleteParam();
        param.setDeleteType(type);
        
        return getClient().fileSystems().deactivateFileContinuousCopies(fileId, param);
    }
}
