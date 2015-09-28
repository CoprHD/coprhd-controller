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

@Service("DetachFullCopy")
public class DetachFullCopyService extends ViPRService {

    @Param(value = STORAGE_TYPE, required = false)
    protected String storageType;

    @Param(VOLUME)
    protected URI consistencyGroupId;

    @Param(COPIES)
    protected List<String> copyIds;

    @Override
    public void precheck() throws Exception {
        super.precheck();
        if (!ConsistencyUtils.isVolumeStorageType(storageType)) {
            if (!ConsistencyUtils.validateConsistencyGroupFullCopies(getClient(), consistencyGroupId)) {
                ExecutionUtils.fail("failTask.ConsistencyGroup.noFullCopies", consistencyGroupId, consistencyGroupId);
            }
        }
    }

    @Override
    public void execute() throws Exception {

        if (ConsistencyUtils.isVolumeStorageType(storageType)) {
            BlockStorageUtils.detachFullCopies(uris(copyIds));
        } else {
            ConsistencyUtils.detachFullCopy(consistencyGroupId);
        }
    }

}
