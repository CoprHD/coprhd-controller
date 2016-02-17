/*
 * Copyright (c) 2015 EMC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.application.tasks;

import java.net.URI;

import com.emc.sa.service.vipr.tasks.WaitForTasks;
import com.emc.storageos.model.TaskList;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.application.VolumeGroupFullCopyCreateParam;
import com.emc.vipr.client.Tasks;

// TODO move to tasks package
public class CreateCloneOfApplication extends WaitForTasks<TaskResourceRep> {
    private final URI applicationId;
    private final String name;
    private final URI virtualArrayId;
    private final Integer count;

    public CreateCloneOfApplication(URI applicationId, String name, URI virtualArrayId, Integer count) {
        this.applicationId = applicationId;
        this.name = name;
        this.virtualArrayId = virtualArrayId;
        this.count = count;
        provideDetailArgs(applicationId, name);
    }

    @Override
    protected Tasks<TaskResourceRep> doExecute() throws Exception {
        VolumeGroupFullCopyCreateParam input = new VolumeGroupFullCopyCreateParam();
        input.setName(name);
        input.setVarrayId(virtualArrayId);
        input.setCount(count);
        input.setCreateInactive(false);
        input.setPartial(false);
        TaskList taskList = getClient().application().createFullCopyOfApplication(applicationId, input);

        return new Tasks<TaskResourceRep>(getClient().auth().getClient(), taskList.getTaskList(),
                TaskResourceRep.class);
    }
}
