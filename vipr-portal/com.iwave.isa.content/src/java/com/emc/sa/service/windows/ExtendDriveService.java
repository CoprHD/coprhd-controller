/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.windows;

import java.util.List;

import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.ServiceParams;
import com.emc.sa.service.vipr.block.BlockStorageUtils;
import com.emc.storageos.model.block.BlockObjectRestRep;

@Service("Windows-ExtendDrive")
public class ExtendDriveService extends WindowsService {
    @Param(ServiceParams.VOLUMES)
    protected List<String> volumeIds;
    private List<ExtendDriveHelper> extendDriveHelpers;

    public void init() throws Exception {
        super.init();
        extendDriveHelpers = ExtendDriveHelper.createHelpers(windowsSystems, 0);
    }

    @Override
    public void precheck() throws Exception {
        super.precheck();
        List<BlockObjectRestRep> volumes = BlockStorageUtils.getBlockResources(uris(volumeIds));
        for (ExtendDriveHelper extendDriveHelper : extendDriveHelpers) {
            extendDriveHelper.setVolumes(volumes);
            extendDriveHelper.precheck();
        }
    }

    @Override
    public void execute() {
        acquireHostAndClusterLock();
        for (ExtendDriveHelper extendDriveHelper : extendDriveHelpers) {
            extendDriveHelper.extendDrives();
        }
    }
}
