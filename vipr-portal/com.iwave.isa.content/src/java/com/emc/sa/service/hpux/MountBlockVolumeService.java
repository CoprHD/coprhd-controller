/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.service.hpux;

import static com.emc.sa.service.ServiceParams.VOLUME;

import java.net.URI;

import com.emc.sa.engine.bind.Bindable;
import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.block.BlockStorageUtils;
import com.emc.sa.service.vipr.block.ExportBlockVolumeHelper;
import com.emc.storageos.model.block.BlockObjectRestRep;

@Service("Hpux-MountBlockVolume")
public class MountBlockVolumeService extends HpuxService {

    @Param(VOLUME)
    protected URI volumeId;

    private BlockObjectRestRep volume;

    @Bindable
    protected ExportBlockVolumeHelper exportBlockVolumeHelper = new ExportBlockVolumeHelper();

    protected MountBlockVolumeHelper mountBlockVolumeHelper;

    @Override
    public void init() throws Exception {
        super.init();
        mountBlockVolumeHelper = MountBlockVolumeHelper.create(hpuxSystem, hostPorts);
    }

    @Override
    public void precheck() throws Exception {
        super.precheck();
        exportBlockVolumeHelper.precheck();
        volume = BlockStorageUtils.getBlockResource(volumeId);
        acquireHostsLock();
        mountBlockVolumeHelper.precheck();
    }

    @Override
    public void execute() throws Exception {
        exportBlockVolumeHelper.exportVolumes();
        volume = BlockStorageUtils.getBlockResource(volumeId);
        mountBlockVolumeHelper.mount(volume);
    }
}