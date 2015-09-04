/*
 * Copyright 2012-2015 EMC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block.tasks;

import java.net.URI;
import java.util.List;

import com.emc.sa.service.vipr.tasks.WaitForTask;
import com.emc.storageos.model.block.BlockConsistencyGroupRestRep;
import com.emc.storageos.model.block.BlockConsistencyGroupUpdate;
import com.emc.storageos.model.block.BlockConsistencyGroupUpdate.BlockConsistencyGroupVolumeList;
import com.emc.vipr.client.Task;

public class AddVolumesToConsistencyGroup extends WaitForTask<BlockConsistencyGroupRestRep> {
    private URI consistencyGroupId;
    private List<URI> volumeIds;

    public AddVolumesToConsistencyGroup(URI consistencyGroupId, List<URI> volumeIds) {
        super();
        this.consistencyGroupId = consistencyGroupId;
        this.volumeIds = volumeIds;
        provideDetailArgs(consistencyGroupId, volumeIds);
    }

    @Override
    protected Task<BlockConsistencyGroupRestRep> doExecute() throws Exception {
        BlockConsistencyGroupUpdate blockConsistencyGroupUpdate = new BlockConsistencyGroupUpdate();
        BlockConsistencyGroupVolumeList volumeList = new BlockConsistencyGroupVolumeList();
        volumeList.setVolumes(volumeIds);

        blockConsistencyGroupUpdate.setAddVolumesList(volumeList);

        return getClient().blockConsistencyGroups().update(consistencyGroupId, blockConsistencyGroupUpdate);
    }
}
