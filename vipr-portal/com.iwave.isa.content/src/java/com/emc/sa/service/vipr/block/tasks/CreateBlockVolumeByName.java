/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block.tasks;

import java.net.URI;
import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;
import com.emc.storageos.model.block.VolumeCreate;
import com.emc.storageos.model.block.VolumeRestRep;
import com.emc.vipr.client.Task;
import com.emc.vipr.client.Tasks;

public class CreateBlockVolumeByName extends ViPRExecutionTask<Task<VolumeRestRep>> {
    private URI vpoolId;
    private URI varrayId;
    private URI projectId;
    private String size;
    private URI consistencyGroupId;
    private String volumeName;

    public CreateBlockVolumeByName(String projectId, String varrayId, String vpoolId, String size,
            String consistencyGroupId, String volumeName) {
        this(uri(projectId), uri(varrayId), uri(vpoolId), size, uri(consistencyGroupId), volumeName);
    }

    public CreateBlockVolumeByName(URI projectId, URI varrayId, URI vpoolId, String size,
            URI consistencyGroupId, String volumeName) {
        this.vpoolId = vpoolId;
        this.varrayId = varrayId;
        this.projectId = projectId;
        this.size = size;
        this.consistencyGroupId = consistencyGroupId;
        this.volumeName = volumeName;
        provideDetailArgs(projectId, varrayId, vpoolId, size, volumeName);

    }

    @Override
    public Task<VolumeRestRep> executeTask() throws Exception {
        VolumeCreate create = new VolumeCreate();
        create.setVpool(vpoolId);
        create.setVarray(varrayId);
        create.setProject(projectId);
        create.setSize(size);
        create.setConsistencyGroup(consistencyGroupId);
        create.setCount(1);
        create.setName(volumeName);

        Tasks<VolumeRestRep> tasksForVolume = getClient().blockVolumes().create(create);
        if (tasksForVolume.getTasks().size() != 1) {
            throw new IllegalStateException("Invalid number of tasks returned from API: " + tasksForVolume.getTasks().size());
        }
        addOrderIdTag(tasksForVolume.firstTask().getTaskResource().getId());
        return tasksForVolume.firstTask();
    }
}
