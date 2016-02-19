/*
 * Copyright (c) 2015 EMC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.application.tasks;

import java.net.URI;

import com.emc.sa.service.vipr.tasks.WaitForTasks;
import com.emc.storageos.model.TaskList;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.application.VolumeGroupSnapshotSessionDeactivateParam;
import com.emc.vipr.client.Tasks;
import com.google.common.collect.Lists;

public class DeleteSnapshotSessionForApplication extends WaitForTasks<TaskResourceRep> {
    private final URI applicationId;
    private final URI snapshotSession;

    public DeleteSnapshotSessionForApplication(URI applicationId, URI snapshotSession) {
        this.applicationId = applicationId;
        this.snapshotSession = snapshotSession;
        provideDetailArgs(applicationId, snapshotSession);
    }

    @Override
    protected Tasks<TaskResourceRep> doExecute() throws Exception {
        VolumeGroupSnapshotSessionDeactivateParam input = new VolumeGroupSnapshotSessionDeactivateParam();
        input.setPartial(true);
        input.setSnapshotSessions(Lists.newArrayList(snapshotSession));

        TaskList taskList = getClient().application().deactivateApplicationSnapshotSession(applicationId, input);

        return new Tasks<TaskResourceRep>(getClient().auth().getClient(), taskList.getTaskList(),
                TaskResourceRep.class);
    }
}
