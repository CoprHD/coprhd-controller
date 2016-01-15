/*
 * Copyright (c) 2015 EMC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block.tasks;

import java.net.URI;
import java.util.List;

import com.emc.sa.service.vipr.tasks.WaitForTasks;
import com.emc.storageos.model.DataObjectRestRep;
import com.emc.storageos.model.application.VolumeGroupUpdateParam;
import com.emc.storageos.model.application.VolumeGroupUpdateParam.VolumeGroupVolumeList;
import com.emc.vipr.client.Tasks;

public class RemoveVolumesFromMobilityGroup extends WaitForTasks<DataObjectRestRep> {
    private final List<URI> volumeIds;
    private final URI mobilityGroup;

    public RemoveVolumesFromMobilityGroup(URI mobilityGroup, List<URI> volumeIds) {
        this.volumeIds = volumeIds;
        this.mobilityGroup = mobilityGroup;
        provideDetailArgs(volumeIds, mobilityGroup);
    }

    @Override
    protected Tasks<DataObjectRestRep> doExecute() throws Exception {
        VolumeGroupUpdateParam updateParam = new VolumeGroupUpdateParam();
        VolumeGroupVolumeList volumeList = new VolumeGroupVolumeList();
        volumeList.setVolumes(this.volumeIds);
        updateParam.setRemoveVolumesList(volumeList);
        return new Tasks<DataObjectRestRep>(getClient().auth().getClient(), getClient().application()
                .updateApplication(this.mobilityGroup, updateParam).getTaskList(),
                DataObjectRestRep.class);
    }
}
