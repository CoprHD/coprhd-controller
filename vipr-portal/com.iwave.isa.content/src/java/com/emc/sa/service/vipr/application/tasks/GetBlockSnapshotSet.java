/*
 * Copyright (c) 2015 EMC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.application.tasks;

import java.net.URI;

import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;
import com.emc.storageos.model.SnapshotList;
import com.emc.storageos.model.application.VolumeGroupCopySetParam;

public class GetBlockSnapshotSet extends ViPRExecutionTask<SnapshotList> {
    private final URI applicationId;
    private final String copySet;

    public GetBlockSnapshotSet(URI applicationId, String copySet) {
        this.applicationId = applicationId;
        this.copySet = copySet;
        provideDetailArgs(applicationId);
    }

    @Override
    public SnapshotList executeTask() throws Exception {
        VolumeGroupCopySetParam setParam = new VolumeGroupCopySetParam();
        setParam.setCopySetName(copySet);
        return getClient().application().getVolumeGroupSnapshotsForSet(applicationId, setParam);
    }
}