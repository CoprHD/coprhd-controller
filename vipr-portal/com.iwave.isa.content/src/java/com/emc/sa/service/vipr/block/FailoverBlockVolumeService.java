/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block;

import static com.emc.sa.service.ServiceParams.FAILOVER_TARGET;
import static com.emc.sa.service.ServiceParams.VOLUMES;
import static com.emc.vipr.client.core.util.ResourceUtils.stringId;

import java.net.URI;

import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.ViPRService;
import com.emc.sa.service.vipr.block.tasks.FailoverBlockVolume;
import com.emc.storageos.model.block.BlockObjectRestRep;

@Service("FailoverBlockVolume")
public class FailoverBlockVolumeService extends ViPRService {
    @Param(VOLUMES)
    protected URI volumeId;

    @Param(FAILOVER_TARGET)
    protected URI failoverTarget;

    private BlockObjectRestRep sourceVolume;
    private BlockObjectRestRep targetVolume;
    private String type;

    @Override
    public void precheck() {
        sourceVolume = BlockStorageUtils.getVolume(volumeId);
        targetVolume = BlockStorageUtils.getVolume(failoverTarget);
        type = BlockStorageUtils.getFailoverType(targetVolume);

        if (type == null) {
            ExecutionUtils.fail("failTask.FailoverBlockVolumeService", args(stringId(targetVolume)), args());
        }

        logInfo("fail.over.block.volume.service", type.toUpperCase(), targetVolume.getName());
    }

    @Override
    public void execute() {
        execute(new FailoverBlockVolume(volumeId, failoverTarget, type));
    }
}
