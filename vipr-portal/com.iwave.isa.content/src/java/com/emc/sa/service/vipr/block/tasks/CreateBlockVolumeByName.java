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
    private URI portGroupId;
    private URI computeResourceId;
    private String volumeName;

    public CreateBlockVolumeByName(String projectId, String varrayId, String vpoolId, String size,
            String consistencyGroupId, String volumeName, String portGroupId, String computeResourceId) {
        this(uri(projectId), uri(varrayId), uri(vpoolId), size, uri(consistencyGroupId), volumeName, uri(portGroupId), uri(computeResourceId));
    }

    public CreateBlockVolumeByName(URI projectId, URI varrayId, URI vpoolId, String size,
            URI consistencyGroupId, String volumeName, URI portGroupId, URI computeResourceId) {
        this.vpoolId = vpoolId;
        this.varrayId = varrayId;
        this.projectId = projectId;
        this.size = size;
        this.consistencyGroupId = consistencyGroupId;
        this.volumeName = volumeName;
        this.portGroupId = portGroupId;
        this.computeResourceId = computeResourceId;
        provideDetailArgs(projectId, varrayId, vpoolId, size, volumeName, portGroupId);
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
        create.setPortGroup(portGroupId);
        create.setComputeResource(computeResourceId);

        Tasks<VolumeRestRep> tasksForVolume = getClient().blockVolumes().create(create);
        if (tasksForVolume.getTasks().size() != 1) {
            throw new IllegalStateException("Invalid number of tasks returned from API: " + tasksForVolume.getTasks().size());
        }
        addOrderIdTag(tasksForVolume.firstTask().getTaskResource().getId());
        return tasksForVolume.firstTask();
    }
}
