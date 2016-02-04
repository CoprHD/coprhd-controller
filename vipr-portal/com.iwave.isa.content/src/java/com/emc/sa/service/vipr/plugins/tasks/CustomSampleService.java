/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.plugins.tasks;

import java.net.URI;
import java.util.List;

import com.emc.sa.engine.bind.Bindable;
import com.emc.sa.engine.bind.BindingUtils;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.ViPRService;
import com.emc.sa.service.vipr.plugins.tasks.CustomUtils.CustomParamTable;
import com.emc.sa.service.vipr.plugins.tasks.CustomUtils.CustomParams;
import com.google.common.collect.Lists;

@Service("CustomSample")
public class CustomSampleService extends ViPRService {


    @Bindable(itemType = CustomParamTable.class)
    protected CustomParamTable[] volumeTable;

    @Bindable
    protected CustomParams volumeParams = new CustomParams();
    
    protected List<CustomSampleHelper> createBlockVolumeHelpers = Lists.newArrayList();

    
    @Override
    public void init() throws Exception {
        super.init();

        // for each pair of volume name and size, create a createBlockVolumeHelper
        for (CustomParamTable volumes : volumeTable) {
        	CustomSampleHelper createBlockVolumeHelper = new CustomSampleHelper();
            BindingUtils.bind(createBlockVolumeHelper, CustomUtils.createParam(volumes, volumeParams));
            createBlockVolumeHelpers.add(createBlockVolumeHelper);
        }
    }
    
    @Override
    public void execute() throws Exception {
        if (!createBlockVolumeHelpers.isEmpty()) {
            List<URI> volumeIds = Lists.newArrayList();
            volumeIds.addAll(CustomUtils.createMultipleVolumes(createBlockVolumeHelpers));
        }
    }

}