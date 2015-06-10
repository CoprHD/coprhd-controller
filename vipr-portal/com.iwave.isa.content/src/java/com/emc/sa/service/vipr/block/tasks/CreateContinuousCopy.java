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

public class CreateContinuousCopy extends WaitForTasks<VolumeRestRep> {

    private URI volumeId;
    private String name;
    private int count;
    private String type;

    public CreateContinuousCopy(URI volumeId, String name, int count, String type) {
        this.volumeId = volumeId;
        this.name = name;
        this.count = count;
        this.type = type;
        provideDetailArgs(volumeId, name, count, type);
    }

    @Override
    protected Tasks<VolumeRestRep> doExecute() throws Exception {
        Copy copy = new Copy();
        copy.setName(name);
        copy.setCount(count);
        copy.setType(type);

        CopiesParam param = new CopiesParam();
        param.getCopies().add(copy);
        return getClient().blockVolumes().startContinuousCopies(volumeId, param);
    }
}
