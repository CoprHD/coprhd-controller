/*
 * Copyright (c) 2016 EMC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.application.tasks;

import java.net.URI;

import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;
import com.emc.storageos.model.application.VolumeGroupCopySetParam;
import com.emc.storageos.model.block.NamedVolumesList;

public class GetFullCopyList extends ViPRExecutionTask<NamedVolumesList> {
    private final URI applicationId;
    private final String copySet;

    public GetFullCopyList(URI applicationId, String copySet) {
        this.applicationId = applicationId;
        this.copySet = copySet;
        provideDetailArgs(applicationId, copySet);
    }

    @Override
    public NamedVolumesList executeTask() throws Exception {
        VolumeGroupCopySetParam setParam = new VolumeGroupCopySetParam();
        setParam.setCopySetName(copySet);
        return getClient().application().getVolumeGroupFullCopiesForSet(applicationId, setParam);
    }
}