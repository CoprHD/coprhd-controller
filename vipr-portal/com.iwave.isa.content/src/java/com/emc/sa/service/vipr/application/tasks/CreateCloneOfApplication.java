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
import com.emc.storageos.model.application.VolumeGroupFullCopyCreateParam;
import com.emc.vipr.client.Tasks;

public class CreateCloneOfApplication extends WaitForTasks<TaskResourceRep> {
    private final URI applicationId;
    private final String name;
    private final List<URI> volumeIds;

    public CreateCloneOfApplication(URI applicationId, String name, List<URI> volumeIds) {
        this.applicationId = applicationId;
        this.name = name;
        this.volumeIds = volumeIds;
        provideDetailArgs(applicationId, name);
    }

    @Override
    protected Tasks<TaskResourceRep> doExecute() throws Exception {
        VolumeGroupFullCopyCreateParam input = new VolumeGroupFullCopyCreateParam();
        input.setName(name);
        input.setCreateInactive(false);
        input.setPartial(true);
        input.setVolumes(volumeIds);
        TaskList taskList = getClient().application().createFullCopyOfApplication(applicationId, input);

        return new Tasks<TaskResourceRep>(getClient().auth().getClient(), taskList.getTaskList(),
                TaskResourceRep.class);
    }
}
