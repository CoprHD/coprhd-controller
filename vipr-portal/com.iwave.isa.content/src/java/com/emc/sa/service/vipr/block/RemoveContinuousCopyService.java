/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block;

import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.ViPRService;
import java.util.List;
import static com.emc.sa.service.ServiceParams.COPIES;
import static com.emc.sa.service.ServiceParams.VOLUMES;

@Service("RemoveContinuousCopy")
public class RemoveContinuousCopyService extends ViPRService {
    @Param(VOLUMES)
    protected String volumeId;

    @Param(COPIES)
    protected List<String> copyIds;

    @Override
    public void precheck() {
        BlockStorageUtils.getBlockResource(uri(volumeId));
    }

    @Override
    public void execute() {
        BlockStorageUtils.removeContinuousCopiesForVolume(uri(volumeId), uris(copyIds));
    }
}
