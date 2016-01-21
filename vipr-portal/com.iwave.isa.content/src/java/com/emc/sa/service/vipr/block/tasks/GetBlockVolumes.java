/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block.tasks;

import java.net.URI;
import java.util.List;

import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;
import com.emc.storageos.model.block.VolumeRestRep;

public class GetBlockVolumes extends ViPRExecutionTask<List<VolumeRestRep>> {

    private List<URI> resourceIds;

    public GetBlockVolumes(List<URI> resourceIds) {
        this.resourceIds = resourceIds;
        provideDetailArgs(resourceIds);
    }

    @Override
    public List<VolumeRestRep> executeTask() throws Exception {
        return getClient().blockVolumes().getByIds(resourceIds);
    }
}