/*
 * Copyright (c) 2017 EMC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.application.tasks;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import com.emc.sa.service.vipr.tasks.WaitForTasks;
import com.emc.storageos.model.TaskList;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.application.VolumeGroupSnapshotOperationParam;
import com.emc.vipr.client.Tasks;

public class CreateVplexVolumeFromAppSnapshot extends WaitForTasks<TaskResourceRep> {
    private final URI applicationId;
    private String copySetName;
    private List<String> subGroups;

    public CreateVplexVolumeFromAppSnapshot(URI applicationId, String copySetName, List<String> subGroups) {
        this.applicationId = applicationId;
        this.copySetName = copySetName;
        this.subGroups = new ArrayList<String>(subGroups);
    }

    @Override
    protected Tasks<TaskResourceRep> doExecute() throws Exception {
        VolumeGroupSnapshotOperationParam input = new VolumeGroupSnapshotOperationParam();
        input.setCopySetName(copySetName);
        input.setSubGroups(new ArrayList<String>(subGroups));

        TaskList taskList = getClient().application().exposeApplicationSnapshot(applicationId, input);

        return new Tasks<TaskResourceRep>(getClient().auth().getClient(), taskList.getTaskList(),
                TaskResourceRep.class);
    }
}
