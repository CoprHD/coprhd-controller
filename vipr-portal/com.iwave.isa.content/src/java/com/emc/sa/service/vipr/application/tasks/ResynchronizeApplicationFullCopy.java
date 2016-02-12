/*
 * Copyright (c) 2015 EMC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.application.tasks;

import java.net.URI;
import java.util.Collections;
import java.util.List;

import com.emc.sa.service.vipr.tasks.WaitForTasks;
import com.emc.storageos.model.TaskList;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.block.VolumeGroupFullCopyResynchronizeParam;
import com.emc.vipr.client.Tasks;

// TODO move to tasks package
public class ResynchronizeApplicationFullCopy extends WaitForTasks<TaskResourceRep> {
    private final URI applicationId;
    private final URI volumeId;

    public ResynchronizeApplicationFullCopy(URI applicationId, URI volumeId, String name) {
        this.applicationId = applicationId;
        this.volumeId = volumeId;
        provideDetailArgs(applicationId, name);
    }

    @Override
    protected Tasks<TaskResourceRep> doExecute() throws Exception {
        List<URI> volList = Collections.singletonList(volumeId);
        VolumeGroupFullCopyResynchronizeParam input = new VolumeGroupFullCopyResynchronizeParam(false, volList);
        TaskList taskList = getClient().application().resynchronizeApplicationFullCopy(applicationId, input);

        return new Tasks<TaskResourceRep>(getClient().auth().getClient(), taskList.getTaskList(),
                TaskResourceRep.class);
    }
}
