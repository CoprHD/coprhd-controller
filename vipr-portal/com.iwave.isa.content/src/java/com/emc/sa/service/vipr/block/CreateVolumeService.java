/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block;

import java.net.URI;
import java.util.List;

import com.emc.sa.engine.bind.Bindable;
import com.emc.sa.engine.bind.BindingUtils;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.ViPRService;
import com.emc.sa.service.vipr.block.BlockStorageUtils.NoHostVolumeParams;
import com.emc.sa.service.vipr.block.BlockStorageUtils.VolumeTable;
import com.google.common.collect.Lists;

@Service("CreateVolume")
public class CreateVolumeService extends ViPRService {

    @Bindable(itemType = VolumeTable.class)
    protected VolumeTable[] volumeTable;

    @Bindable
    protected NoHostVolumeParams volumeParams = new NoHostVolumeParams();
    
    protected List<CreateBlockVolumeHelper> createBlockVolumeHelpers = Lists.newArrayList();

    
    @Override
    public void init() throws Exception {
        super.init();

        // for each pair of volume name and size, create a createBlockVolumeHelper
        for (VolumeTable volumes : volumeTable) {
            CreateBlockVolumeHelper createBlockVolumeHelper = new CreateBlockVolumeHelper();
            BindingUtils.bind(createBlockVolumeHelper, BlockStorageUtils.createNoHostVolumeParam(volumes, volumeParams));
            createBlockVolumeHelpers.add(createBlockVolumeHelper);
        }
    }
    
    @Override
    public void execute() throws Exception {
        if (!createBlockVolumeHelpers.isEmpty()) {
            List<URI> volumeIds = Lists.newArrayList();
            volumeIds.addAll(BlockStorageUtils.createMultipleVolumes(createBlockVolumeHelpers));
//            createBlockVolumeHelpers.get(0).exportVolumes(volumeIds);
        }
//        BlockStorageUtils.createVolumes(project, virtualArray, virtualPool, volumeName, sizeInGb, count,
//                consistencyGroup);
    }

//    public URI getVirtualPool() {
//        return virtualPool;
//    }
//
//    public void setVirtualPool(URI virtualPool) {
//        this.virtualPool = virtualPool;
//    }
//
//    public URI getProject() {
//        return project;
//    }
//
//    public void setProject(URI project) {
//        this.project = project;
//    }
//
//    public Double getSizeInGb() {
//        return sizeInGb;
//    }
//
//    public void setSizeInGb(Double sizeInGb) {
//        this.sizeInGb = sizeInGb;
//    }
//
//    public URI getVirtualArray() {
//        return virtualArray;
//    }
//
//    public void setVirtualArray(URI virtualArray) {
//        this.virtualArray = virtualArray;
//    }
}
