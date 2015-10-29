/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block.consistency.tasks;

import java.net.URI;

import com.emc.sa.service.vipr.tasks.WaitForTasks;
import com.emc.storageos.model.block.BlockConsistencyGroupRestRep;
import com.emc.storageos.model.block.VolumeFullCopyCreateParam;
import com.emc.vipr.client.Tasks;

public class CreateConsistencyGroupFullCopy extends
        WaitForTasks<BlockConsistencyGroupRestRep> {

    private URI consistencyGroupId;
    private String name;
    private int count;

    public CreateConsistencyGroupFullCopy(URI consistencyGroupId, String name,
            int count) {
        this.consistencyGroupId = consistencyGroupId;
        this.name = name;
        this.count = count;
    }

    @Override
    protected Tasks<BlockConsistencyGroupRestRep> doExecute() throws Exception {

        VolumeFullCopyCreateParam param = new VolumeFullCopyCreateParam();
        param.setName(name);
        param.setCount(count);

        return getClient().blockConsistencyGroups().createFullCopy(
                consistencyGroupId, param);
    }
}
