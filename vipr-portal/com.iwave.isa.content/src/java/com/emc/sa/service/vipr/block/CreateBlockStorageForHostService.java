/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block;

import com.emc.sa.engine.bind.Bindable;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.ViPRService;

@Service("CreateBlockStorageForHost")
public class CreateBlockStorageForHostService extends ViPRService {
    @Bindable
    protected CreateBlockVolumeHelper helper = new CreateBlockVolumeHelper();

    @Override
    public void precheck() {
        helper.precheck();
    }

    @Override
    public void execute() {
        helper.createAndExportVolumes();
    }
}
