/*
 * Copyright (c) 2015 EMC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block.tasks;

import java.net.URI;

import com.emc.sa.service.vipr.tasks.WaitForTasks;
import com.emc.storageos.model.DataObjectRestRep;
import com.emc.storageos.model.application.VolumeGroupUpdateParam;
import com.emc.storageos.model.application.VolumeGroupUpdateParam.VolumeGroupVolumeList;
import com.emc.vipr.client.Tasks;
import com.google.common.collect.Lists;

public class RemoveVolumeFromMobilityGroup extends WaitForTasks<DataObjectRestRep> {
    private final URI volumeId;
    private final URI mobilityGroup;

    public RemoveVolumeFromMobilityGroup(URI mobilityGroup, URI volumeId) {
        this.volumeId = volumeId;
        this.mobilityGroup = mobilityGroup;
        provideDetailArgs(volumeId, mobilityGroup);
    }

    @Override
    protected Tasks<DataObjectRestRep> doExecute() throws Exception {
        VolumeGroupUpdateParam updateParam = new VolumeGroupUpdateParam();
        VolumeGroupVolumeList volumeList = new VolumeGroupVolumeList();
        volumeList.setVolumes(Lists.newArrayList(volumeId));
        updateParam.setRemoveVolumesList(volumeList);
        return new Tasks<DataObjectRestRep>(getClient().auth().getClient(), getClient().application()
                .updateApplication(this.mobilityGroup, updateParam).getTaskList(),
                DataObjectRestRep.class);
    }
}
