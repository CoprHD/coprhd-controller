/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.windows;

import static com.emc.sa.service.ServiceParams.SIZE_IN_GB;
import static com.emc.sa.service.ServiceParams.VOLUMES;

import java.util.List;

import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.block.BlockStorageUtils;
import com.emc.storageos.model.block.BlockObjectRestRep;

@Service("Windows-ExtendBlockVolume")
public class ExtendBlockVolumeService extends WindowsService {
    @Param(VOLUMES)
    protected List<String> volumeIds;
    @Param(SIZE_IN_GB)
    protected Double sizeInGb;

    private List<ExtendDriveHelper> extendDriveHelpers;

    public void init() throws Exception {
        super.init();
        extendDriveHelpers = ExtendDriveHelper.createHelpers(windowsSystems, getVolumeCapacityInBytes(sizeInGb));
    }

    @Override
    public void precheck() throws Exception {
        super.precheck();
        boolean clusteredVolumeSuccess = false;

        List<BlockObjectRestRep> volumes = BlockStorageUtils.getBlockResources(uris(volumeIds));
        for (ExtendDriveHelper extendDriveHelper : extendDriveHelpers) {
            extendDriveHelper.setVolumes(volumes);
            extendDriveHelper.precheck();

            if (isClustered() && !clusteredVolumeSuccess) {
                clusteredVolumeSuccess = extendDriveHelper.foundClusteredVolume();
            }
        }

        if (isClustered() && !clusteredVolumeSuccess) {
            ExecutionUtils.fail("failTask.ExtendDriveHelper.couldNotFindVolumeForCluster", cluster.getLabel(), cluster.getLabel());
        }
    }

    @Override
    public void execute() throws Exception {
        BlockStorageUtils.expandVolumes(uris(volumeIds), sizeInGb);
        acquireHostAndClusterLock();
        for (ExtendDriveHelper extendDriveHelper : extendDriveHelpers) {
            extendDriveHelper.extendDrives();
        }

        if (hostId != null) {
            ExecutionUtils.addAffectedResource(hostId.toString());
        }
    }

    private long getVolumeCapacityInBytes(Double size) {
        return size.longValue() * 1024 * 1024 * 1024;
    }
}
