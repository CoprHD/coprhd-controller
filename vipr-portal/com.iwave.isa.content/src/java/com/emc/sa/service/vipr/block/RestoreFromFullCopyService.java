/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block;

import static com.emc.sa.service.ServiceParams.COPIES;
import static com.emc.sa.service.ServiceParams.STORAGE_TYPE;
import static com.emc.sa.service.ServiceParams.VOLUME;

import java.net.URI;

import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.ViPRService;
import com.emc.storageos.model.DataObjectRestRep;
import com.emc.vipr.client.Tasks;

@Service("RestoreFromFullCopy")
public class RestoreFromFullCopyService extends ViPRService {

    @Param(value = STORAGE_TYPE, required = false)
    protected String storageType;

    @Param(value = VOLUME, required = false)
    protected URI consistencyGroupId;

    @Param(COPIES)
    protected String copyId;

    @Override
    public void execute() throws Exception {
        if (ConsistencyUtils.isVolumeStorageType(storageType)) {
            logInfo("full.copy.block.volume.restore.executing", copyId);
            BlockStorageUtils.restoreFromFullCopy(uri(copyId));
        } else {
            logInfo("full.copy.block.cg.restore.executing", consistencyGroupId);
            Tasks<? extends DataObjectRestRep> tasks = ConsistencyUtils.restoreFullCopy(consistencyGroupId, uri(copyId));
            addAffectedResources(tasks);
        }
    }
}
