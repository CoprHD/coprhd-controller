/**
* Copyright 2012-2015 iWave Software LLC
* All Rights Reserved
 */
package com.emc.sa.service.vipr.block.tasks;

import java.net.URI;

import com.emc.sa.service.vipr.tasks.WaitForTask;
import com.emc.storageos.model.block.VolumeExpandParam;
import com.emc.storageos.model.block.VolumeRestRep;
import com.emc.vipr.client.Task;

public class ExpandVolume extends WaitForTask<VolumeRestRep> {
    private URI volumeId;
    private String newSize;

    public ExpandVolume(String volumeId, String newSize) {
        this(uri(volumeId), newSize);
    }

    public ExpandVolume(URI volumeId, String newSize) {
        super();
        this.volumeId = volumeId;
        this.newSize = newSize;
        provideDetailArgs(volumeId, newSize);
    }

    @Override
    protected Task<VolumeRestRep> doExecute() throws Exception {
        return getClient().blockVolumes().expand(volumeId, new VolumeExpandParam(newSize));
    }
}
