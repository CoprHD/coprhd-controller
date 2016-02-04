/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.plugins.tasks;

import java.net.URI;

import com.emc.sa.service.vipr.tasks.WaitForTasks;
import com.emc.storageos.model.block.VolumeCreate;
import com.emc.storageos.model.block.VolumeRestRep;
import com.emc.vipr.client.Tasks;

public class CustomSample extends WaitForTasks<VolumeRestRep> {
    private URI vpoolId;
    private URI varrayId;
    private URI projectId;
    private String size;
    private Integer count;
    private String name;
    private URI consistencyGroupId;

    public CustomSample(String vpoolId, String varrayId, String projectId, String size, Integer count,
            String name, String consistencyGroupId) {
        this(uri(vpoolId), uri(varrayId), uri(projectId), size, count, name, uri(consistencyGroupId));
    }

    public CustomSample(URI vpoolId, URI varrayId, URI projectId, String size, Integer count, String name,
            URI consistencyGroupId) {
        this.vpoolId = vpoolId;
        this.varrayId = varrayId;
        this.projectId = projectId;
        this.size = size;
        this.count = count;
        this.name = name;
        this.consistencyGroupId = consistencyGroupId;
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

        Tasks<VolumeRestRep> tasks = getClient().blockVolumes().create(create);
        // There should only be as many tasks as is the count
        if (tasks.getTasks().size() != numberOfVolumes) {
            throw stateException("CreateBlockVolume.illegalState.invalid", tasks.getTasks().size());
        }
        return tasks;
    }
}
