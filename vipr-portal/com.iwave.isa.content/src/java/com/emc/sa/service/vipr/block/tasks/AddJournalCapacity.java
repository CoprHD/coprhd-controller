/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block.tasks;

import java.net.URI;

import com.emc.sa.service.vipr.tasks.WaitForTasks;
import com.emc.storageos.model.block.VolumeCreate;
import com.emc.storageos.model.block.VolumeRestRep;
import com.emc.vipr.client.Tasks;

public class AddJournalCapacity extends WaitForTasks<VolumeRestRep> {
    private final URI vpoolId;
    private final URI varrayId;
    private final URI projectId;
    private final String size;
    private final String copyName;
    private final Integer count;
    private final URI consistencyGroupId;

    public AddJournalCapacity(String vpoolId, String varrayId, String projectId, String size, Integer count,
            String consistencyGroupId, String copyName) {
        this(uri(vpoolId), uri(varrayId), uri(projectId), size, count, uri(consistencyGroupId), copyName);
    }

    public AddJournalCapacity(URI vpoolId, URI varrayId, URI projectId, String size, Integer count,
            URI consistencyGroupId, String copyName) {
        this.vpoolId = vpoolId;
        this.varrayId = varrayId;
        this.projectId = projectId;
        this.size = size;
        this.copyName = copyName;
        this.count = count;
        this.consistencyGroupId = consistencyGroupId;
        provideDetailArgs(size, vpoolId, varrayId, projectId);
    }

    @Override
    public Tasks<VolumeRestRep> doExecute() throws Exception {
        VolumeCreate create = new VolumeCreate();
        create.setVpool(vpoolId);
        create.setVarray(varrayId);
        create.setProject(projectId);
        create.setName(copyName);
        create.setSize(size);
        int numberOfVolumes = 1;
        if ((count != null) && (count > 1)) {
            numberOfVolumes = count;
        }
        create.setCount(numberOfVolumes);
        create.setConsistencyGroup(consistencyGroupId);

        Tasks<VolumeRestRep> tasks = getClient().blockVolumes().addJournalCapacity(create);
        // There should only be as many tasks as is the count
        if (tasks.getTasks().size() != numberOfVolumes) {
            throw stateException("CreateBlockVolume.illegalState.invalid", tasks.getTasks().size());
        }
        return tasks;
    }
}
