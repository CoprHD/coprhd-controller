/*
 * Copyright (c) 2015 EMC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.application.tasks;

import java.net.URI;

import com.emc.sa.service.vipr.tasks.WaitForTasks;
import com.emc.storageos.model.TaskList;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.block.VolumeGroupSnapshotOperationParam;
import com.emc.vipr.client.Tasks;
import com.google.common.collect.Lists;

public class RestoreSnapshotForApplication extends WaitForTasks<TaskResourceRep> {
    private final URI applicationId;
    private final URI volume;

    public RestoreSnapshotForApplication(URI applicationId, URI volume) {
        this.applicationId = applicationId;
        this.volume = volume;
        provideDetailArgs(applicationId);
    }

    @Override
    protected Tasks<TaskResourceRep> doExecute() throws Exception {
        VolumeGroupSnapshotOperationParam input = new VolumeGroupSnapshotOperationParam();
        input.setSnapshots(Lists.newArrayList(volume));
        input.setPartial(true);

        TaskList taskList = getClient().application().restoreApplicationSnapshot(applicationId, input);

        return new Tasks<TaskResourceRep>(getClient().auth().getClient(), taskList.getTaskList(),
                TaskResourceRep.class);
    }
}
