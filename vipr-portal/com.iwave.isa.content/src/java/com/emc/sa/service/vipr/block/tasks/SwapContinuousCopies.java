/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block.tasks;

import java.net.URI;

import com.emc.sa.service.vipr.tasks.WaitForTasks;
import com.emc.storageos.model.block.CopiesParam;
import com.emc.storageos.model.block.Copy;
import com.emc.storageos.model.block.VolumeRestRep;
import com.emc.vipr.client.Tasks;

public class SwapContinuousCopies extends WaitForTasks<VolumeRestRep> {

    private URI targetVolumeId;
    private String type;

    public SwapContinuousCopies(URI targetVolumeId, String type) {
        this.targetVolumeId = targetVolumeId;
        this.type = type;
        provideDetailArgs(targetVolumeId, type);
    }

    @Override
    protected Tasks<VolumeRestRep> doExecute() throws Exception {
        Copy copy = new Copy();
        copy.setCopyID(targetVolumeId);
        copy.setType(type);

        CopiesParam param = new CopiesParam();
        param.getCopies().add(copy);
        return getClient().blockVolumes().swapContinuousCopies(targetVolumeId, param);
    }
}
