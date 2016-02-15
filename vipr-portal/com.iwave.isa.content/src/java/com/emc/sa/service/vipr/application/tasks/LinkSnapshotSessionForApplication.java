/*
 * Copyright (c) 2015 EMC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.application.tasks;

import java.net.URI;

import com.emc.sa.service.vipr.tasks.WaitForTasks;
import com.emc.storageos.model.TaskList;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.application.VolumeGroupSnapshotSessionLinkTargetsParam;
import com.emc.vipr.client.Tasks;

public class LinkSnapshotSessionForApplication extends WaitForTasks<TaskResourceRep> {
    private final URI applicationId;
    private final URI volume;

    public LinkSnapshotSessionForApplication(URI applicationId, URI volume) {
        this.applicationId = applicationId;
        this.volume = volume;
        provideDetailArgs(applicationId);
    }

    @Override
    protected Tasks<TaskResourceRep> doExecute() throws Exception {
        VolumeGroupSnapshotSessionLinkTargetsParam input = new VolumeGroupSnapshotSessionLinkTargetsParam();
        // input.setSnapshotSessions(snapshotSessions)
        // TODO fix the inputs
        input.setPartial(true);

        TaskList taskList = getClient().application().linkApplicationSnapshotSession(applicationId, input);

        return new Tasks<TaskResourceRep>(getClient().auth().getClient(), taskList.getTaskList(),
                TaskResourceRep.class);
    }
}
