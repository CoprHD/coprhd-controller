/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block;

import static com.emc.sa.service.ServiceParams.COPIES;
import static com.emc.sa.service.ServiceParams.STORAGE_TYPE;
import static com.emc.sa.service.ServiceParams.VOLUME;

import java.net.URI;

import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.ViPRService;

@Service("RestoreFromFullCopy")
public class RestoreFromFullCopyService extends ViPRService {

    @Param(value = STORAGE_TYPE, required = false)
    protected String storageType;

    @Param(value = VOLUME, required = false)
    protected URI consistencyGroupId;

    @Param(COPIES)
    protected String copyId;

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
            logInfo("Executing Block Volume restore [%s]", consistencyGroupId);
            BlockStorageUtils.restoreFromFullCopy(uri(copyId));
        } else {
            logInfo("Executing Consistency Group restore [%s]", consistencyGroupId);
            ConsistencyUtils.restoreFullCopy(consistencyGroupId);
        }
    }
}
