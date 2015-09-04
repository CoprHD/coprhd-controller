/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block;

import static com.emc.sa.service.ServiceParams.COPIES;
import static com.emc.sa.service.ServiceParams.VOLUME;

import java.net.URI;

import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.ViPRService;

@Service("RestoreFromFullCopy")
public class RestoreFromFullCopyService extends ViPRService {

    @Param(value = VOLUME, required = false)
    protected URI consistencyGroupId;

    @Param(COPIES)
    protected String copyId;

    @Override
    public void execute() throws Exception {
        if (consistencyGroupId != null && !"NONE".equals(consistencyGroupId.toString())) {
            logInfo("Executing Consistency Group restore [%s]", consistencyGroupId);
            ConsistencyUtils.restoreFullCopy(consistencyGroupId, uri(copyId));
        } else {
            logInfo("Executing Block Volume restore [%s]", consistencyGroupId);
            BlockStorageUtils.restoreFromFullCopy(uri(copyId));
        }
    }
}
