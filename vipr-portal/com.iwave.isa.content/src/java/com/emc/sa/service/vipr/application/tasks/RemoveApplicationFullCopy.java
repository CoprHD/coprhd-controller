/*
 * Copyright (c) 2015 EMC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.application.tasks;

import java.net.URI;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.emc.sa.service.vipr.block.BlockStorageUtils;
import com.emc.sa.service.vipr.tasks.WaitForTasks;
import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.TaskList;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.block.NamedVolumesList;
import com.emc.storageos.model.block.VolumeDeleteTypeEnum;
import com.emc.storageos.model.block.VolumeGroupFullCopyDetachParam;
import com.emc.vipr.client.Tasks;

// TODO move to tasks package
public class RemoveApplicationFullCopy extends WaitForTasks<TaskResourceRep> {
    private final URI applicationId;
    private final URI volumeId;

    public RemoveApplicationFullCopy(URI applicationId, URI volumeId, String name) {
        this.applicationId = applicationId;
        this.volumeId = volumeId;
        provideDetailArgs(applicationId, name);
    }

    @Override
    protected Tasks<TaskResourceRep> doExecute() throws Exception {
        NamedVolumesList allFullCopies = getClient().application().getFullCopiesByApplication(applicationId);
        Set<URI> fullCopyIds = new HashSet<URI>();
        for (NamedRelatedResourceRep fullCopy : allFullCopies.getVolumes()) {
            fullCopyIds.add(fullCopy.getId());
        }
        
        List<URI> volList = Collections.singletonList(volumeId);
        VolumeGroupFullCopyDetachParam input = new VolumeGroupFullCopyDetachParam(false, volList);
        TaskList taskList = getClient().application().detachApplicationFullCopy(applicationId, input);
        
        BlockStorageUtils.removeBlockResources(fullCopyIds, VolumeDeleteTypeEnum.FULL);

        return new Tasks<TaskResourceRep>(getClient().auth().getClient(), taskList.getTaskList(),
                TaskResourceRep.class);
    }
}
