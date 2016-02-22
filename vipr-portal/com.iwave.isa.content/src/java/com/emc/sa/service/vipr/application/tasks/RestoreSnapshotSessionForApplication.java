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
import com.emc.storageos.model.application.VolumeGroupSnapshotSessionRestoreParam;
import com.emc.vipr.client.Tasks;

public class RestoreSnapshotSessionForApplication extends WaitForTasks<TaskResourceRep> {
    private final URI applicationId;
    private final List<URI> snapSessions;

    public RestoreSnapshotSessionForApplication(URI applicationId, List<URI> snapSessions) {
        this.applicationId = applicationId;
        this.snapSessions = snapSessions;
        provideDetailArgs(applicationId, snapSessions);
    }

    @Override
    protected Tasks<TaskResourceRep> doExecute() throws Exception {
        VolumeGroupSnapshotSessionRestoreParam input = new VolumeGroupSnapshotSessionRestoreParam();
        input.setSnapshotSessions(snapSessions);
        input.setPartial(true);

        TaskList taskList = getClient().application().restoreApplicationSnapshotSession(applicationId, input);

        return new Tasks<TaskResourceRep>(getClient().auth().getClient(), taskList.getTaskList(),
                TaskResourceRep.class);
    }
}
