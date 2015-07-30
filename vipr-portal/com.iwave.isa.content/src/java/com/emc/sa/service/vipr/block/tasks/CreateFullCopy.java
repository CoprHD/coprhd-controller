/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block.tasks;

import java.net.URI;

import com.emc.sa.service.vipr.tasks.WaitForTasks;
import com.emc.storageos.model.block.VolumeFullCopyCreateParam;
import com.emc.storageos.model.block.VolumeRestRep;
import com.emc.vipr.client.Tasks;

public class CreateFullCopy extends WaitForTasks<VolumeRestRep> {

    private URI volumeId;
    private String name;
    private int count;

    public CreateFullCopy(String volumeId, String name, int count) {
        this(uri(volumeId), name, count);
    }

    public CreateFullCopy(URI volumeId, String name, int count) {
        this.volumeId = volumeId;
        this.name = name;
        this.count = count;
        provideDetailArgs(volumeId, name, count);
    }

    @Override
    protected Tasks<VolumeRestRep> doExecute() throws Exception {
        VolumeFullCopyCreateParam param = new VolumeFullCopyCreateParam();
        param.setName(name);
        param.setCount(count);
        return getClient().blockVolumes().createFullCopy(volumeId, param);
    }
}
