/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.aix;

import java.util.List;

import com.emc.sa.engine.bind.Bindable;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.block.CreateBlockVolumeForHostHelper;
import com.emc.storageos.model.block.BlockObjectRestRep;

@Service("Aix-CreateAndMountBlockVolume")
public class CreateAndMountVolumeService extends AixService {
    @Bindable
    private CreateBlockVolumeForHostHelper createVolumeHelper = new CreateBlockVolumeForHostHelper();

    private MountBlockVolumeHelper mountBlockVolumeHelper;

    @Override
    public void init() throws Exception {
        super.init();
        mountBlockVolumeHelper = MountBlockVolumeHelper.create(this.aixSystem, hostPorts);
    }

    @Override
    public void precheck() throws Exception {
        super.precheck();
        createVolumeHelper.precheck();
        mountBlockVolumeHelper.precheck();
    }

    @Override
    public void execute() throws Exception {
        BlockObjectRestRep volume = createVolume();
        acquireHostsLock();
        mountVolume(volume);
    }

    private BlockObjectRestRep createVolume() {
        List<BlockObjectRestRep> volumes = createVolumeHelper.createAndExportVolumes();
        if (volumes.size() != 1) {
            throw new IllegalStateException("This service can create only one volume.");
        }
        return volumes.get(0);
    }

    private void mountVolume(BlockObjectRestRep volume) {
        mountBlockVolumeHelper.mount(volume);
    }
}
