/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block;

import static com.emc.sa.service.ServiceParams.VOLUME;

import java.net.URI;

import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.ViPRService;

@Service("CreateVplexVolumeFromSnapshot")
public class CreateVplexVolumeFromSnapshotService extends ViPRService {

    @Param(value = VOLUME)
    protected URI snapshotId;

    @Override
    public void execute() throws Exception {
        getClient().blockSnapshots().createVplexVolume(this.snapshotId);
    }
}
