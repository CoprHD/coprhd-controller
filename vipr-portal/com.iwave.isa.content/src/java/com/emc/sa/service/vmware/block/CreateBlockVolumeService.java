/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vmware.block;

import java.net.URI;
import java.util.List;

import com.emc.sa.engine.bind.Bindable;
import com.emc.sa.engine.bind.BindingUtils;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.ViPRService;
import com.emc.sa.service.vipr.block.CreateBlockVolumeHelper;
import com.emc.sa.service.vmware.VMwareUtils;
import com.emc.sa.service.vmware.VMwareUtils.VolumeParams;
import com.emc.sa.service.vmware.VMwareUtils.VolumeTable;
import com.google.common.collect.Lists;

@Service("VMware-CreateBlockVolume")
public class CreateBlockVolumeService extends ViPRService {

    @Bindable(itemType = VolumeTable.class)
    protected VolumeTable[] volumeTable;

    @Bindable
    protected VolumeParams volumeParams = new VolumeParams();

    List<String> datastoreNames = null;
    List<String> volumeNames = null;

    protected List<CreateBlockVolumeHelper> createBlockVolumeHelpers = Lists.newArrayList();

    @Override
    public void init() throws Exception {
        super.init();

        // for each pair of volume name and size, create a createBlockVolumeHelper
        for (VolumeTable volumes : volumeTable) {
            CreateBlockVolumeHelper createBlockVolumeHelper = new CreateBlockVolumeHelper();
            BindingUtils.bind(createBlockVolumeHelper, VMwareUtils.createVolumeParam(volumes, volumeParams));
            createBlockVolumeHelpers.add(createBlockVolumeHelper);
        }
    }

    @Override
    public void precheck() throws Exception {
        for (CreateBlockVolumeHelper helper : createBlockVolumeHelpers) {
            helper.precheck();
        }
    }

    @Override
    public void execute() throws Exception {
        if (!createBlockVolumeHelpers.isEmpty()) {
            List<URI> volumeIds = Lists.newArrayList();
            for (CreateBlockVolumeHelper helper : createBlockVolumeHelpers) {
                volumeIds.addAll(helper.createVolumes());
            }
            createBlockVolumeHelpers.get(0).exportVolumes(volumeIds);
        }
    }
}