/**
* Copyright 2012-2015 iWave Software LLC
* All Rights Reserved
 */
package com.emc.sa.service.vipr.block.tasks;

import java.net.URI;

import com.emc.sa.service.vipr.tasks.WaitForTasks;
import com.emc.storageos.model.block.VolumeRestRep;
import com.emc.vipr.client.Tasks;

public class RestoreFromFullCopy extends WaitForTasks<VolumeRestRep> {
    private URI fullCopyId;

    public RestoreFromFullCopy(URI fullCopyId) {
        this.fullCopyId = fullCopyId;
        provideDetailArgs(fullCopyId);
    }

    @Override
    protected Tasks<VolumeRestRep> doExecute() throws Exception {
        return getClient().blockFullCopies().restoreFromFullCopy(fullCopyId);
    }
}
