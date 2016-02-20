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
import com.emc.storageos.model.application.VolumeGroupSnapshotCreateParam;
import com.emc.vipr.client.Tasks;

public class CreateSnapshotForApplication extends WaitForTasks<TaskResourceRep> {
    private final URI applicationId;
    private final String name;
    private final Boolean readOnly;
    private final List<URI> volumes;
    private final Boolean copyOnHighAvailabilitySide;

    public CreateSnapshotForApplication(URI applicationId, List<URI> volumes, String name, Boolean readOnly,
            Boolean copyOnHighAvailabilitySide) {
        this.applicationId = applicationId;
        this.name = name;
        this.readOnly = readOnly;
        this.volumes = volumes;
        this.copyOnHighAvailabilitySide = copyOnHighAvailabilitySide;
        provideDetailArgs(applicationId, name);
    }

    @Override
    protected Tasks<TaskResourceRep> doExecute() throws Exception {
        VolumeGroupSnapshotCreateParam input = new VolumeGroupSnapshotCreateParam();
        input.setName(name);
        input.setVolumes(volumes);
        input.setPartial(true);
        input.setReadOnly(readOnly);
        input.setCopyOnHighAvailabilitySide(copyOnHighAvailabilitySide);

        TaskList taskList = getClient().application().createSnapshotOfApplication(applicationId, input);

        return new Tasks<TaskResourceRep>(getClient().auth().getClient(), taskList.getTaskList(),
                TaskResourceRep.class);
    }
}
