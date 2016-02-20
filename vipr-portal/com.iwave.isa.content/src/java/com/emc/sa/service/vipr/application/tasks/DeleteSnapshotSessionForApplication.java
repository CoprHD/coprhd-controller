/*
 * Copyright (c) 2015 EMC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.application.tasks;

import java.net.URI;
import java.util.List;

import com.emc.sa.service.vipr.tasks.WaitForTasks;
import com.emc.storageos.model.TaskList;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.application.VolumeGroupSnapshotSessionDeactivateParam;
import com.emc.vipr.client.Tasks;

public class DeleteSnapshotSessionForApplication extends WaitForTasks<TaskResourceRep> {
    private final URI applicationId;
    private final List<URI> snapshotSessions;

    public DeleteSnapshotSessionForApplication(URI applicationId, List<URI> snapshotSessions) {
        this.applicationId = applicationId;
        this.snapshotSessions = snapshotSessions;
        provideDetailArgs(applicationId, snapshotSessions);
    }

    @Override
    protected Tasks<TaskResourceRep> doExecute() throws Exception {
        VolumeGroupSnapshotSessionDeactivateParam input = new VolumeGroupSnapshotSessionDeactivateParam();
        input.setPartial(true);
        input.setSnapshotSessions(snapshotSessions);

        TaskList taskList = getClient().application().deactivateApplicationSnapshotSession(applicationId, input);

        return new Tasks<TaskResourceRep>(getClient().auth().getClient(), taskList.getTaskList(),
                TaskResourceRep.class);
    }
}
