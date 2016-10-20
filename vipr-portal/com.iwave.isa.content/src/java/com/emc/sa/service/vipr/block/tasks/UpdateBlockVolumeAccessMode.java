/*
 * Copyright (c) 2012-2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block.tasks;

import java.net.URI;

import com.emc.sa.service.vipr.tasks.WaitForTasks;
import com.emc.storageos.model.block.CopiesParam;
import com.emc.storageos.model.block.Copy;
import com.emc.storageos.model.block.VolumeRestRep;
import com.emc.vipr.client.Tasks;

public class UpdateBlockVolumeAccessMode extends WaitForTasks<VolumeRestRep> {
    public static final String REMOTE_TARGET = "remote";
    private final URI volumeId;
    private final URI failoverTarget;
    private final String type;
    private final String accessMode;

    public UpdateBlockVolumeAccessMode(URI volumeId, URI failoverTarget, String accessMode) {
        this(volumeId, failoverTarget, "rp", accessMode);
    }

    public UpdateBlockVolumeAccessMode(URI volumeId, URI failoverTarget, String type, String accessMode) {
        this.volumeId = volumeId;
        this.failoverTarget = failoverTarget;
        this.type = type;
        this.accessMode = accessMode;
        provideDetailArgs(volumeId, failoverTarget, type, accessMode);
    }

    @Override
    protected Tasks<VolumeRestRep> doExecute() throws Exception {
        Copy copy = new Copy();
        copy.setType(type);
        copy.setCopyID(failoverTarget);
        copy.setAccessMode(accessMode);

        CopiesParam param = new CopiesParam();
        param.getCopies().add(copy);
        return getClient().blockVolumes().updateCopyAccessMode(volumeId, param);
    }
}
