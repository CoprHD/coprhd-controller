/*
 * Copyright (c) 2016 EMC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.application.tasks;

import java.net.URI;

import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;
import com.emc.storageos.model.application.VolumeGroupCopySetParam;
import com.emc.storageos.model.block.BlockSnapshotSessionList;

public class GetBlockSnapshotSessionList extends ViPRExecutionTask<BlockSnapshotSessionList> {
    private final URI applicationId;
    private final String copySet;

    public GetBlockSnapshotSessionList(URI applicationId, String copySet) {
        this.applicationId = applicationId;
        this.copySet = copySet;
        provideDetailArgs(applicationId);
    }

    @Override
    public BlockSnapshotSessionList executeTask() throws Exception {
        VolumeGroupCopySetParam setParam = new VolumeGroupCopySetParam();
        setParam.setCopySetName(copySet);
        return getClient().application().getVolumeGroupSnapshotSessionsByCopySet(applicationId, setParam);
    }
}