/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block.tasks;

import java.net.URI;

import com.emc.sa.service.vipr.tasks.WaitForTasks;
import com.emc.storageos.model.block.VolumeCreate;
import com.emc.storageos.model.block.VolumeRestRep;
import com.emc.storageos.model.remotereplication.RemoteReplicationParameters;
import com.emc.vipr.client.Tasks;

public class CreateBlockVolume extends WaitForTasks<VolumeRestRep> {
    private URI vpoolId;
    private URI varrayId;
    private URI projectId;
    private String size;
    private Integer count;
    private String name;
    private URI consistencyGroupId;
    private URI remoteReplicationSetId;
    private URI remoteReplicationGroupId;
    private URI computeResource;

    public CreateBlockVolume(String vpoolId, String varrayId, String projectId, String size, Integer count,
            String name, String consistencyGroupId, String remoteReplicationSetId, String remoteReplicationGroupId) {
        this(uri(vpoolId), uri(varrayId), uri(projectId), size, count, name, uri(consistencyGroupId), uri(remoteReplicationSetId),
                uri(remoteReplicationGroupId), null);
    }

    public CreateBlockVolume(URI vpoolId, URI varrayId, URI projectId, String size, Integer count, String name,
            URI consistencyGroupId, URI remoteReplicationSetId, URI remoteReplicationGroupId, URI computeResource) {
        this.vpoolId = vpoolId;
        this.varrayId = varrayId;
        this.projectId = projectId;
        this.size = size;
        this.count = count;
        this.name = name;
        this.consistencyGroupId = consistencyGroupId;
        this.remoteReplicationSetId = remoteReplicationSetId;
        this.remoteReplicationGroupId = remoteReplicationGroupId;
        this.computeResource = computeResource;
        provideDetailArgs(name, size, vpoolId, varrayId, projectId);
    }

    @Override
    public Tasks<VolumeRestRep> doExecute() throws Exception {
        VolumeCreate create = new VolumeCreate();
        create.setVpool(vpoolId);
        create.setVarray(varrayId);
        create.setProject(projectId);
        create.setName(name);
        create.setSize(size);
        int numberOfVolumes = 1;
        if ((count != null) && (count > 1)) {
            numberOfVolumes = count;
        }
        create.setCount(numberOfVolumes);
        create.setConsistencyGroup(consistencyGroupId);
        RemoteReplicationParameters replicationParam = new RemoteReplicationParameters();
        replicationParam.setRemoteReplicationSet(remoteReplicationSetId);
        replicationParam.setRemoteReplicationGroup(remoteReplicationGroupId);
        create.setRemoteReplicationParameters(replicationParam);
        if (computeResource != null) {
            create.setComputeResource(computeResource);
        }

        Tasks<VolumeRestRep> tasks = getClient().blockVolumes().create(create);
        // There should only be as many tasks as is the count
        if (tasks.getTasks().size() != numberOfVolumes) {
            throw stateException("CreateBlockVolume.illegalState.invalid", tasks.getTasks().size());
        }
        return tasks;
    }
}
