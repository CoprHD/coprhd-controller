/*
 * Copyright (c) 2016 EMC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.application.tasks;

import java.net.URI;
import java.util.List;

import com.emc.sa.service.vipr.tasks.WaitForTasks;
import com.emc.storageos.model.TaskList;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.application.VolumeGroupSnapshotOperationParam;
import com.emc.vipr.client.Tasks;

public class RestoreSnapshotForApplication extends WaitForTasks<TaskResourceRep> {
    private final URI applicationId;
    private final List<URI> snapshots;

    public RestoreSnapshotForApplication(URI applicationId, List<URI> snapshots) {
        this.applicationId = applicationId;
        this.snapshots = snapshots;
        provideDetailArgs(applicationId, snapshots);
    }

    @Override
    protected Tasks<TaskResourceRep> doExecute() throws Exception {
        VolumeGroupSnapshotOperationParam input = new VolumeGroupSnapshotOperationParam();
        input.setSnapshots(snapshots);
        input.setPartial(true);

        TaskList taskList = getClient().application().restoreApplicationSnapshot(applicationId, input);

        return new Tasks<TaskResourceRep>(getClient().auth().getClient(), taskList.getTaskList(),
                TaskResourceRep.class);
    }
}
