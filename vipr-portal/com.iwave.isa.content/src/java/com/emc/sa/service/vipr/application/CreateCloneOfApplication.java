/*
 * Copyright (c) 2015 EMC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.application;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import com.emc.sa.service.vipr.tasks.WaitForTasks;
import com.emc.storageos.model.TaskList;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.block.VolumeGroupFullCopyCreateParam;
import com.emc.vipr.client.Tasks;

// TODO move to tasks package
public class CreateCloneOfApplication extends WaitForTasks<TaskResourceRep> {
    private final URI applicationId;
    private final URI volumeId;
    private final String name;
    private final Integer count;

    public CreateCloneOfApplication(URI applicationId, URI volumeId, String name, Integer count) {
        this.applicationId = applicationId;
        this.volumeId = volumeId;
        this.name = name;
        this.count = count;
        provideDetailArgs(applicationId, name);
    }

    @Override
    protected Tasks<TaskResourceRep> doExecute() throws Exception {
        VolumeGroupFullCopyCreateParam input = new VolumeGroupFullCopyCreateParam();
        input.setName(name);
        input.setCount(count);
        List<URI> volList = new ArrayList<URI>();
        volList.add(volumeId);
        input.setVolumes(volList);
        input.setCreateInactive(false);
        input.setPartial(false);
        TaskList taskList = getClient().application().createFullCopyOfApplication(applicationId, input);

        return new Tasks<TaskResourceRep>(getClient().auth().getClient(), taskList.getTaskList(),
                TaskResourceRep.class);
    }
}
