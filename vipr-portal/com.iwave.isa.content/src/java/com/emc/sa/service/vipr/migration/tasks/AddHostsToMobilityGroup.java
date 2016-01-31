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

public class AddHostsToMobilityGroup extends WaitForTasks<TaskResourceRep> {
    private final List<URI> hostIds;
    private final URI mobilityGroupId;

    public AddHostsToMobilityGroup(URI mobilityGroupId, List<URI> hostIds) {
        this.hostIds = hostIds;
        this.mobilityGroupId = mobilityGroupId;
        provideDetailArgs(mobilityGroupId, hostIds);
    }

    @Override
    protected Tasks<TaskResourceRep> doExecute() throws Exception {
        VolumeGroupUpdateParam input = new VolumeGroupUpdateParam();
        input.setAddHostsList(hostIds);
        TaskList taskList = getClient().application().updateApplication(mobilityGroupId, input);

        return new Tasks<TaskResourceRep>(getClient().auth().getClient(), taskList.getTaskList(),
                TaskResourceRep.class);
    }
}
