/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.windows;

import java.util.Collections;
import java.util.List;

import com.emc.sa.engine.bind.Bindable;
import com.emc.sa.engine.service.Service;
import com.emc.sa.service.vipr.block.CreateBlockVolumeForHostHelper;
import com.emc.storageos.model.block.BlockObjectRestRep;
import com.iwave.ext.windows.model.wmi.DiskDrive;

@Service("Windows-CreateAndMountBlockVolume")
public class CreateAndMountBlockVolumeService extends WindowsService {
    @Bindable
    protected CreateBlockVolumeForHostHelper createBlockVolumeHelper = new CreateBlockVolumeForHostHelper();

    protected List<MountBlockVolumeHelper> mountBlockVolumeHelpers;

    @Override
    public void init() throws Exception {
        super.init();
    }

    @Override
    public void precheck() throws Exception {
        super.precheck();
        createBlockVolumeHelper.precheck();

        long capacityInBytes = createBlockVolumeHelper.getSizeInGb().longValue() * 1024 * 1024 * 1024;
        mountBlockVolumeHelpers = MountBlockVolumeHelper.createHelpers(windowsSystems, capacityInBytes);
        for (MountBlockVolumeHelper mountBlockVolumeHelper : mountBlockVolumeHelpers) {
            mountBlockVolumeHelper.precheck();
        }

        if (isClustered()) {
            mountBlockVolumeHelpers.get(0).verifyClusterHosts(hosts);
        }
    }

    @Override
    public void execute() throws Exception {
        BlockObjectRestRep volume = createBlockVolumeHelper.createAndExportVolumes().get(0);
        acquireHostAndClusterLock();

        // Only perform mounting/formatting on ONE host
        DiskDrive diskDrive = mountBlockVolumeHelpers.get(0).mountVolume(volume);

        if (isClustered()) {
            for (int i = 1; i < mountBlockVolumeHelpers.size(); i++) {
                mountBlockVolumeHelpers.get(i).rescanDisks();
            }

            mountBlockVolumeHelpers.get(0).addDisksToCluster(Collections.singleton(diskDrive));
        }

        // Only perform formatting on ONE machine
        // for (MountBlockVolumeHelper mountBlockVolumeHelper : mountBlockVolumeHelper) {
        // mountBlockVolumeHelper.doFormat = false;
        // }
        // mountBlockVolumeHelper.get(0).doFormat = true;
        //
        //
        // for (MountBlockVolumeHelper mountBlockVolumeHelper : mountBlockVolumeHelper) {
        // mountBlockVolumeHelper.mountVolumes(volumes, false);
        // }
    }
}
