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
import com.emc.vipr.client.Tasks;

public class RemoveClustersFromMobilityGroup extends WaitForTasks<TaskResourceRep> {
    private final List<URI> clusterIds;
    private final URI mobilityGroupId;

    public RemoveClustersFromMobilityGroup(URI mobilityGroupId, List<URI> clusterIds) {
        this.clusterIds = clusterIds;
        this.mobilityGroupId = mobilityGroupId;
        provideDetailArgs(mobilityGroupId, clusterIds);
    }

    @Override
    protected Tasks<TaskResourceRep> doExecute() throws Exception {
        VolumeGroupUpdateParam input = new VolumeGroupUpdateParam();
        input.setRemoveClustersList(clusterIds);

        TaskList taskList = getClient().application().updateApplication(mobilityGroupId, input);

        return new Tasks<TaskResourceRep>(getClient().auth().getClient(), taskList.getTaskList(),
                TaskResourceRep.class);
    }
}
