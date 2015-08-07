/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block.tasks;

import java.net.URI;

import com.emc.sa.service.vipr.tasks.ViPRExecutionTask;
import com.emc.sa.util.ResourceType;
import com.emc.storageos.model.block.BlockObjectRestRep;
import com.emc.storageos.model.block.BlockSnapshotRestRep;
import com.emc.storageos.model.block.VolumeRestRep;
import com.emc.vipr.client.ViPRCoreClient;

public class GetBlockResource extends ViPRExecutionTask<BlockObjectRestRep> {

    private URI resourceId;

    public GetBlockResource(String resourceId) {
        this(uri(resourceId));
    }

    public GetBlockResource(URI resourceId) {
        this.resourceId = resourceId;
        provideDetailArgs(resourceId);
    }

    @SuppressWarnings("incomplete-switch")
    @Override
    public BlockObjectRestRep executeTask() throws Exception {
        ViPRCoreClient client = getClient();
        ResourceType volumeType = ResourceType.fromResourceId(resourceId.toString());
        switch (volumeType) {
            case VOLUME:
                VolumeRestRep volume = client.blockVolumes().get(resourceId);
                if (volume != null) {
                    return volume;
                }
                break;
            case BLOCK_SNAPSHOT:
                BlockSnapshotRestRep snapshot = client.blockSnapshots().get(resourceId);
                if (snapshot != null) {
                    return snapshot;
                }
                break;
        }
        throw stateException("GetBlockResource.illegalState.notFound", resourceId);
    }

}