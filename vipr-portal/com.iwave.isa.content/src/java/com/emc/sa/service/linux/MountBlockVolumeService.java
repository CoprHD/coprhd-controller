/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.linux;

import static com.emc.sa.service.ServiceParams.VOLUME;

import java.net.URI;

import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.engine.bind.Bindable;
import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.block.BlockStorageUtils;
import com.emc.sa.service.vipr.block.ExportBlockVolumeHelper;
import com.emc.storageos.model.block.BlockObjectRestRep;
import com.google.common.collect.Lists;

@Service("Linux-MountBlockVolume")
public class MountBlockVolumeService extends LinuxService {

    @Param(VOLUME)
    protected URI volumeId;

    private BlockObjectRestRep volume;

    @Bindable
    protected ExportBlockVolumeHelper exportBlockVolumeHelper = new ExportBlockVolumeHelper();

    protected MountBlockVolumeHelper mountBlockVolumeHelper;

    @Override
    public void init() throws Exception {
        super.init();
        mountBlockVolumeHelper = MountBlockVolumeHelper.createHelper(linuxSystem, hostPorts);
    }

    @Override
    public void precheck() throws Exception {
        super.precheck();
        exportBlockVolumeHelper.precheck();
        volume = BlockStorageUtils.getBlockResource(volumeId);
        if (BlockStorageUtils.isVolumeVMFSDatastore(volume)) {
            ExecutionUtils.fail("failTask.verifyVMFSDatastore", volume.getName(), volume.getName());
        }
        checkForBootVolume(volumeId);
        acquireHostsLock();
        mountBlockVolumeHelper.verifyMountConfiguration(volume);
        mountBlockVolumeHelper.precheck();
        mountBlockVolumeHelper.refreshStorage(Lists.newArrayList(volume));
    }

    @Override
    public void execute() throws Exception {
        exportBlockVolumeHelper.exportVolumes();
        volume = BlockStorageUtils.getBlockResource(volumeId);
        mountBlockVolumeHelper.refreshStorage(Lists.newArrayList(volume));
        mountBlockVolumeHelper.mountVolume(volume);
    }
}
