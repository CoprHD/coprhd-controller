/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.aix;

import static com.emc.sa.service.ServiceParams.VOLUMES;

import java.util.List;

import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.block.BlockStorageUtils;
import com.emc.storageos.model.block.BlockObjectRestRep;

@Service("Aix-UnmountAndDeleteBlockVolume")
public class UnmountAndDeleteBlockVolumeService extends AixService {
    @Param(VOLUMES)
    protected List<String> volumeIds;

    private UnmountBlockVolumeHelper unmountVolumeHelper;

    @Override
    public void init() throws Exception {
        super.init();
        unmountVolumeHelper = UnmountBlockVolumeHelper.create(aixSystem, hostPorts);
    }

    @Override
    public void precheck() throws Exception {
        super.precheck();
        List<? extends BlockObjectRestRep> volumes = BlockStorageUtils.getBlockResources(uris(volumeIds));
        acquireHostsLock();
        unmountVolumeHelper.setVolumes(volumes);
        unmountVolumeHelper.precheck();
    }

    @Override
    public void execute() {
        unmountVolumeHelper.unmountVolumes();
        BlockStorageUtils.removeVolumes(uris(volumeIds));
    }
}
