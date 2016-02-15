/*
 * Copyright (c) 2015 EMC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.application.tasks;

import java.net.URI;

import com.emc.sa.service.vipr.tasks.WaitForTasks;
import com.emc.storageos.model.TaskList;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.application.VolumeGroupSnapshotSessionUnlinkTargetsParam;
import com.emc.vipr.client.Tasks;

public class UnlinkSnapshotForApplication extends WaitForTasks<TaskResourceRep> {
    private final URI applicationId;
    private final URI volume;

    public UnlinkSnapshotForApplication(URI applicationId, URI volume) {
        this.applicationId = applicationId;
        this.volume = volume;
        provideDetailArgs(applicationId);
    }

    @Override
    protected Tasks<TaskResourceRep> doExecute() throws Exception {
        VolumeGroupSnapshotSessionUnlinkTargetsParam input = new VolumeGroupSnapshotSessionUnlinkTargetsParam();
        // input.setSnapshotSessions(snapshotSessions)
        input.setPartial(true);

        TaskList taskList = getClient().application().unlinkApplicationSnapshot(applicationId, input);

        return new Tasks<TaskResourceRep>(getClient().auth().getClient(), taskList.getTaskList(),
                TaskResourceRep.class);
    }
}
