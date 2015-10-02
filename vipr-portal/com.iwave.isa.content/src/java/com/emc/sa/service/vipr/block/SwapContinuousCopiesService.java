/*
 * Copyright (c) 2012-2015 iWave Software LLC
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
import com.emc.storageos.model.block.BlockObjectRestRep;
import com.emc.storageos.model.block.VolumeRestRep;
import com.emc.vipr.client.Task;
import com.emc.vipr.client.Tasks;

@Service("SwapContinuousCopies")
public class SwapContinuousCopiesService extends ViPRService {
    @Param(VOLUMES)
    protected URI volumeId;

    @Param(FAILOVER_TARGET)
    protected URI failoverTarget;

    private BlockObjectRestRep sourceVolume;
    private BlockObjectRestRep targetVolume;
    private String type;

    @Override
    public void precheck() throws Exception {
        super.precheck();
        sourceVolume = BlockStorageUtils.getVolume(volumeId);
        targetVolume = BlockStorageUtils.getVolume(failoverTarget);
        type = BlockStorageUtils.getFailoverType(targetVolume);

        if (type == null) {
            ExecutionUtils.fail("failTask.SwapContinuousCopiesService", args(stringId(targetVolume)), args());
        }

        logInfo("swap.continuous.copies.service.precheck", type.toUpperCase(), targetVolume.getName());
    }

    @Override
    public void execute() throws Exception {
        Tasks<VolumeRestRep> copies = BlockStorageUtils.swapContinuousCopy(failoverTarget, type);
        for (Task<VolumeRestRep> copy : copies.getTasks()) {
            logInfo("swap.continuous.copies.service", copy.getResource().getName(), copy.getResource().getId());
        }
    }
}
