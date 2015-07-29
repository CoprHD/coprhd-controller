/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.windows;

import java.util.List;

import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.ServiceParams;
import com.emc.sa.service.vipr.block.BlockStorageUtils;
import com.emc.storageos.model.block.BlockObjectRestRep;

@Service("Windows-UnmountBlockVolume")
public class UnmountBlockVolumeService extends WindowsService {
    @Param(ServiceParams.VOLUMES)
    protected List<String> volumeIds;

    private List<UnmountBlockVolumeHelper> unmountBlockVolumeHelpers;

    public void init() throws Exception {
        super.init();
        unmountBlockVolumeHelpers = UnmountBlockVolumeHelper.createHelpers(windowsSystems);
    }

    @Override
    public void precheck() throws Exception {
        super.precheck();
        List<? extends BlockObjectRestRep> volumes = BlockStorageUtils.getBlockResources(uris(volumeIds));
        acquireHostAndClusterLock();
        unmountBlockVolumeHelpers.get(0).setVolumes(volumes);
        unmountBlockVolumeHelpers.get(0).precheck();
    }

    @Override
    public void execute() {
        if (isClustered()) {
            unmountBlockVolumeHelpers.get(0).removeVolumesFromCluster();
        }

        unmountBlockVolumeHelpers.get(0).unmountVolumes();

        if (hostId != null) {
            ExecutionUtils.addAffectedResource(hostId.toString());
        }
    }
}
