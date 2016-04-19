/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.service.hpux;

import static com.emc.sa.service.ServiceParams.VOLUMES;

import java.util.List;

import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.block.BlockStorageUtils;
import com.emc.storageos.model.block.BlockObjectRestRep;

@Service("Hpux-UnmountAndDeleteBlockVolume")
public class UnmountAndDeleteBlockVolumeService extends HpuxService {
    @Param(VOLUMES)
    protected List<String> volumeIds;

    private UnmountBlockVolumeHelper unmountVolumeHelper;

    @Override
    public void init() throws Exception {
        super.init();
        unmountVolumeHelper = UnmountBlockVolumeHelper.create(hpuxSystem, hostPorts);
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
