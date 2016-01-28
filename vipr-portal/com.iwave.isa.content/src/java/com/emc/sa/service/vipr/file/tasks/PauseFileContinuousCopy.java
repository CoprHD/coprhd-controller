/*
 * Copyright (c) 2012-2016 EMC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.file.tasks;

import java.net.URI;

import com.emc.sa.service.vipr.tasks.WaitForTasks;
import com.emc.storageos.model.block.CopiesParam;
import com.emc.storageos.model.block.Copy;
import com.emc.storageos.model.file.FileShareRestRep;
import com.emc.vipr.client.Tasks;

public class PauseFileContinuousCopy extends WaitForTasks<FileShareRestRep> {

    private URI fileId;
    private URI continuousCopyId;
    private String type;

    public PauseFileContinuousCopy(URI fileId, URI continuousCopyId, String type) {
        this.continuousCopyId = continuousCopyId;
        this.fileId = fileId;
        this.type = type;
        provideDetailArgs(fileId, continuousCopyId);
    }

    @Override
    protected Tasks<FileShareRestRep> doExecute() throws Exception {
        Copy copy = new Copy();
        copy.setCopyID(continuousCopyId);
        copy.setType(type);

        CopiesParam param = new CopiesParam();
        param.getCopies().add(copy);
        return getClient().fileSystems().pauseFileContinuousCopies(fileId, param);
    }
}
