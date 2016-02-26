/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.linux;

import java.net.URI;
import java.util.List;

import com.emc.sa.engine.bind.Bindable;
import com.emc.sa.engine.bind.BindingUtils;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.ViPRService;
import com.emc.sa.service.vipr.block.BlockStorageUtils;
import com.emc.sa.service.vipr.block.CreateBlockVolumeForHostHelper;
import com.emc.sa.service.vipr.block.BlockStorageUtils.HostVolumeParams;
import com.emc.sa.service.vipr.block.BlockStorageUtils.VolumeTable;
import com.google.common.collect.Lists;

@Service("PowerPathMigrationEnabler")
public class PowerPathMigrationEnablerService extends LinuxService {
    
    @Bindable(itemType = VolumeTable.class)
    protected VolumeTable[] volumeTable;

    @Bindable
    protected HostVolumeParams hostVolumeParams = new HostVolumeParams();
    
    protected List<CreateBlockVolumeForHostHelper> createBlockVolumeHelpers = Lists.newArrayList();

    @Override
    public void init() throws Exception {
        super.init();

        // for each pair of volume name and size, create a createBlockVolumeHelper
    }
    
    @Override
    public void precheck() {
    }

    @Override
    public void execute() {

    }
}
