/*
 * Copyright (c) 2015 EMC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.migration.tasks;

import java.net.URI;
import java.util.List;

import com.emc.sa.service.vipr.tasks.WaitForTasks;
import com.emc.storageos.model.TaskList;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.application.VolumeGroupUpdateParam;
import com.emc.storageos.model.application.VolumeGroupUpdateParam.VolumeGroupVolumeList;
import com.emc.vipr.client.Tasks;

public class RemoveVolumesFromMobilityGroup extends WaitForTasks<TaskResourceRep> {
    private final List<URI> volumeIds;
    private final URI mobilityGroupId;

    public RemoveVolumesFromMobilityGroup(URI mobilityGroupId, List<URI> volumeIds) {
        this.volumeIds = volumeIds;
        this.mobilityGroupId = mobilityGroupId;
        provideDetailArgs(mobilityGroupId, volumeIds);
    }

    @Override
    protected Tasks<TaskResourceRep> doExecute() throws Exception {
        VolumeGroupUpdateParam input = new VolumeGroupUpdateParam();
        VolumeGroupVolumeList removeVolumesList = new VolumeGroupVolumeList();
        removeVolumesList.setVolumes(volumeIds);
        input.setRemoveVolumesList(removeVolumesList);

        TaskList taskList = getClient().application().updateApplication(mobilityGroupId, input);

        return new Tasks<TaskResourceRep>(getClient().auth().getClient(), taskList.getTaskList(),
                TaskResourceRep.class);
    }
}
