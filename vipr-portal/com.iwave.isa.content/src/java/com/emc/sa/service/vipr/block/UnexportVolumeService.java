/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block;

import static com.emc.sa.service.ServiceParams.EXPORT;
import static com.emc.sa.service.ServiceParams.PROJECT;
import static com.emc.sa.service.ServiceParams.VOLUME;

import java.net.URI;
import java.util.Collections;

import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.ViPRService;

@Service("UnexportVolume")
public class UnexportVolumeService extends ViPRService {

    @Param(PROJECT)
    protected URI projectId;

    @Param(VOLUME)
    protected URI volumeId;

    @Param(EXPORT)
    protected URI exportId;

    @Override
    public void precheck() throws Exception {
        super.precheck();
        checkForBootVolume(volumeId);
    }
    
    @Override
    public void execute() throws Exception {
        BlockStorageUtils.removeBlockResourcesFromExport(Collections.singletonList(volumeId), exportId);
    }
}
