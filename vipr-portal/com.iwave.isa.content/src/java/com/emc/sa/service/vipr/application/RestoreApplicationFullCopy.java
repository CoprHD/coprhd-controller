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
import com.emc.storageos.model.block.VolumeGroupFullCopyRestoreParam;
import com.emc.vipr.client.Tasks;

// TODO move to tasks package
public class RestoreApplicationFullCopy extends WaitForTasks<TaskResourceRep> {
    private final URI applicationId;
    private final URI volumeId;

    public RestoreApplicationFullCopy(URI applicationId, URI volumeId, String name) {
        this.applicationId = applicationId;
        this.volumeId = volumeId;
        provideDetailArgs(applicationId, name);
    }

    @Override
    protected Tasks<TaskResourceRep> doExecute() throws Exception {
        List<URI> volList = new ArrayList<URI>();
        volList.add(volumeId);
        VolumeGroupFullCopyRestoreParam input = new VolumeGroupFullCopyRestoreParam(false, volList);
        TaskList taskList = getClient().application().restoreApplicationFullCopy(applicationId, input);

        return new Tasks<TaskResourceRep>(getClient().auth().getClient(), taskList.getTaskList(),
                TaskResourceRep.class);
    }
}
