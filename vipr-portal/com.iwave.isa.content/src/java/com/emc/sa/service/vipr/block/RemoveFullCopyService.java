/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block;

import static com.emc.sa.service.ServiceParams.COPIES;
import static com.emc.sa.service.ServiceParams.STORAGE_TYPE;
import static com.emc.sa.service.ServiceParams.VOLUME;

import java.net.URI;
import java.util.List;

import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.ViPRService;
import com.emc.storageos.model.block.BlockObjectRestRep;

@Service("RemoveFullCopy")
public class RemoveFullCopyService extends ViPRService {

    @Param(value = STORAGE_TYPE, required = false)
    protected String storageType;

    @Param(VOLUME)
    protected URI volumeOrConsistencyGroupId;

    @Param(COPIES)
    protected List<String> copyIds;

    private BlockObjectRestRep volume;

    @Override
    public void precheck() {
        if (ConsistencyUtils.isVolumeStorageType(storageType)) {
            volume = BlockStorageUtils.getBlockResource(volumeOrConsistencyGroupId);
            logInfo("remove.full.copy.service.precheck", volume.getName());
        } else {
            if (!ConsistencyUtils.validateConsistencyGroupFullCopies(getClient(), volumeOrConsistencyGroupId)) {
                ExecutionUtils.fail("failTask.ConsistencyGroup.noFullCopies", volumeOrConsistencyGroupId, volumeOrConsistencyGroupId);
            }
        }
    }

    @Override
    public void execute() {
        if (ConsistencyUtils.isVolumeStorageType(storageType)) {
            BlockStorageUtils.removeFullCopies(uris(copyIds));
        } else {
            ConsistencyUtils.removeFullCopy(volumeOrConsistencyGroupId);
        }
    }
}
