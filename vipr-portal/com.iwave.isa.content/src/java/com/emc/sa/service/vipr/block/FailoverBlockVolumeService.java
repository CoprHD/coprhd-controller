/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block;

import static com.emc.sa.service.ServiceParams.FAILOVER_TARGET;
import static com.emc.sa.service.ServiceParams.STORAGE_TYPE;
import static com.emc.sa.service.ServiceParams.VOLUMES;
import static com.emc.vipr.client.core.util.ResourceUtils.stringId;

import java.net.URI;

import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.ViPRService;
import com.emc.sa.service.vipr.block.tasks.FailoverBlockConsistencyGroup;
import com.emc.sa.service.vipr.block.tasks.FailoverBlockVolume;
import com.emc.storageos.model.DataObjectRestRep;
import com.emc.storageos.model.block.BlockConsistencyGroupRestRep;
import com.emc.storageos.model.block.BlockObjectRestRep;
import com.emc.storageos.model.varray.VirtualArrayRestRep;
import com.emc.vipr.client.Tasks;

@Service("FailoverBlockVolume")
public class FailoverBlockVolumeService extends ViPRService {
    @Param(value = STORAGE_TYPE, required = false)
    protected String storageType;

    @Param(VOLUMES)
    protected URI protectionSource;

    @Param(FAILOVER_TARGET)
    protected URI protectionTarget;

    private String type;

    @Override
    public void precheck() {
        String sourceId = "";
        String targetId = "";
        String targetName = "";
        String sourceName = "";

        if (ConsistencyUtils.isVolumeStorageType(storageType)) {
            // The type selected is volume
            BlockObjectRestRep targetVolume = BlockStorageUtils.getVolume(protectionTarget);
            BlockObjectRestRep sourceVolume = BlockStorageUtils.getVolume(protectionSource);
            type = BlockStorageUtils.getFailoverType(targetVolume);
            targetId = stringId(targetVolume);
            targetName = targetVolume.getName();
            sourceId = stringId(sourceVolume);
            sourceName = sourceVolume.getName();
        } else {
            // The type selected is consistency group
            BlockConsistencyGroupRestRep cg = BlockStorageUtils.getBlockConsistencyGroup(protectionSource);
            VirtualArrayRestRep virtualArray = BlockStorageUtils.getVirtualArray(protectionTarget);
            type = ConsistencyUtils.getFailoverType(cg);
            targetId = stringId(virtualArray);
            targetName = virtualArray.getName();
            sourceId = stringId(cg);
            sourceName = cg.getName();
        }

        if (type == null) {
            ExecutionUtils.fail("failTask.FailoverBlockVolumeService", args(sourceId, targetId), args());
        }

        logInfo("fail.over.block.volume.service", type.toUpperCase(), sourceName, targetName);
    }

    @Override
    public void execute() {
        Tasks<? extends DataObjectRestRep> tasks;

        if (ConsistencyUtils.isVolumeStorageType(storageType)) {
            // The type selected is volume
            tasks = execute(new FailoverBlockVolume(protectionSource, protectionTarget, type));
        } else {
            // The type selected is consistency group
            tasks = execute(new FailoverBlockConsistencyGroup(protectionSource, protectionTarget, type));
        }
        if (tasks != null) {
            addAffectedResources(tasks);
        }
    }
}
