/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block;

import static com.emc.sa.service.ServiceParams.SIZE_IN_GB;
import static com.emc.sa.service.ServiceParams.VOLUMES;

import java.util.List;

import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.ViPRService;

@Service("ExpandBlockStorage")
public class ExpandBlockStorageService extends ViPRService {
    @Param(VOLUMES)
    protected List<String> volumeIds;
    @Param(SIZE_IN_GB)
    protected Double sizeInGb;

    @Override
    public void precheck() {
        BlockStorageUtils.getBlockResources(uris(volumeIds));
    }

    @Override
    public void execute() {
        BlockStorageUtils.expandVolumes(uris(volumeIds), sizeInGb);
    }
}
