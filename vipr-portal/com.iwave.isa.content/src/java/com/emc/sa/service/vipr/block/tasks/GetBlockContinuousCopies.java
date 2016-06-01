/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block.tasks;

import java.net.URI;
import java.util.List;

import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;
import com.emc.storageos.model.block.BlockMirrorRestRep;
import com.google.common.collect.Lists;

/**
 * Task to return a list of Block Mirrors based on the resource ids provided. 
 *
 * @author cormij4
 *
 */
public class GetBlockContinuousCopies extends ViPRExecutionTask<List<BlockMirrorRestRep>> {
    private List<URI> resourceIds;
    private URI parentId;

    public GetBlockContinuousCopies(List<URI> resourceIds, URI parentId) {
        this.resourceIds = resourceIds;
        this.parentId = parentId;
        provideDetailArgs(resourceIds, parentId);
    }

    @Override
    public List<BlockMirrorRestRep> executeTask() throws Exception {
        List<BlockMirrorRestRep> mirrors = Lists.newArrayList();
        for (URI id : resourceIds) {
            mirrors.add(getClient().blockVolumes().getContinuousCopy(parentId, id));
        }
        return mirrors;
    }
}
