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
import com.emc.storageos.model.application.VolumeGroupFullCopyResynchronizeParam;
import com.emc.vipr.client.Tasks;

public class ResynchronizeApplicationFullCopy extends WaitForTasks<TaskResourceRep> {
    private final URI applicationId;
    private final List<URI> volumeIds;

    public ResynchronizeApplicationFullCopy(URI applicationId, List<URI> volumeIds) {
        this.applicationId = applicationId;
        this.volumeIds = volumeIds;
        provideDetailArgs(applicationId);
    }

    @Override
    protected Tasks<TaskResourceRep> doExecute() throws Exception {
        VolumeGroupFullCopyResynchronizeParam input = new VolumeGroupFullCopyResynchronizeParam(false, volumeIds);
        TaskList taskList = getClient().application().resynchronizeApplicationFullCopy(applicationId, input);

        return new Tasks<TaskResourceRep>(getClient().auth().getClient(), taskList.getTaskList(),
                TaskResourceRep.class);
    }
}
