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
import com.google.common.collect.Lists;

public class UnlinkSnapshotSessionForApplication extends WaitForTasks<TaskResourceRep> {
    private final URI applicationId;
    private final URI snapshotSession;

    public UnlinkSnapshotSessionForApplication(URI applicationId, URI snapshotSession) {
        this.applicationId = applicationId;
        this.snapshotSession = snapshotSession;
        provideDetailArgs(applicationId);
    }

    @Override
    protected Tasks<TaskResourceRep> doExecute() throws Exception {
        VolumeGroupSnapshotSessionUnlinkTargetsParam input = new VolumeGroupSnapshotSessionUnlinkTargetsParam();
        input.setSnapshotSessions(Lists.newArrayList(snapshotSession));
        input.setPartial(true);

        TaskList taskList = getClient().application().unlinkApplicationSnapshotSession(applicationId, input);

        return new Tasks<TaskResourceRep>(getClient().auth().getClient(), taskList.getTaskList(),
                TaskResourceRep.class);
    }
}
