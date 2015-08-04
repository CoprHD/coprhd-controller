/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block.tasks;

import java.net.URI;
import java.util.List;

import org.apache.commons.lang.StringUtils;

import com.emc.sa.service.vipr.tasks.WaitForTasks;
import com.emc.storageos.model.block.VolumeRestRep;
import com.emc.storageos.model.block.VolumeVirtualArrayChangeParam;
import com.emc.vipr.client.Tasks;
import com.google.common.collect.Lists;

public class ChangeBlockVolumeVirtualArray extends WaitForTasks<VolumeRestRep> {
    private List<URI> volumeIds;

    private URI targetVirtualArrayId;

    public ChangeBlockVolumeVirtualArray(List<String> volumeIds, String targetVirtualArrayId) {
        this(uris(volumeIds), uri(targetVirtualArrayId));
    }

    public ChangeBlockVolumeVirtualArray(List<URI> volumeIds, URI targetVirtualArrayId) {
        this.volumeIds = volumeIds;
        this.targetVirtualArrayId = targetVirtualArrayId;
        provideDetailArgs(targetVirtualArrayId, getVolumesDisplayString());
    }

    @Override
    protected Tasks<VolumeRestRep> doExecute() throws Exception {
        VolumeVirtualArrayChangeParam param = new VolumeVirtualArrayChangeParam();
        param.setVirtualArray(targetVirtualArrayId);
        param.setVolumes(volumeIds);
        return getClient().blockVolumes().changeVirtualArrayForVolumes(param);
    }

    private String getVolumesDisplayString() {
        List<String> volumes = Lists.newArrayList();
        for (URI volumeId : volumeIds) {
            volumes.add(volumeId.toString());
        }
        return StringUtils.join(volumes, ",");
    }

}
