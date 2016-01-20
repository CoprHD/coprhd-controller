/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.service.hpux;

import static com.emc.sa.service.vipr.ViPRExecutionUtils.logInfo;

import java.util.List;

import com.emc.hpux.HpuxSystem;
import com.emc.hpux.model.MountPoint;
import com.emc.hpux.model.RDisk;
import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.engine.bind.BindingUtils;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.model.block.BlockObjectRestRep;

public class ExpandBlockVolumeHelper {
    private final HpuxSupport hpuxSupport;

    private MountPoint mountPoint;

    protected boolean usePowerPath;

    public static ExpandBlockVolumeHelper createHelper(HpuxSystem hpuxSystem, List<Initiator> hostPorts) {
        HpuxSupport hpuxSupport = new HpuxSupport(hpuxSystem);
        ExpandBlockVolumeHelper expandBlockVolumeHelper = new ExpandBlockVolumeHelper(hpuxSupport);
        BindingUtils.bind(expandBlockVolumeHelper, ExecutionUtils.currentContext().getParameters());
        return expandBlockVolumeHelper;
    }

    public ExpandBlockVolumeHelper(HpuxSupport hpuxSupport) {
        this.hpuxSupport = hpuxSupport;
    }

    public void precheck(BlockObjectRestRep volume) {
        usePowerPath = hpuxSupport.checkForPowerPath();
        mountPoint = hpuxSupport.findMountPoint(volume);
    }

    public void expandVolume(BlockObjectRestRep volume, Double newSizeInGB) {
        logInfo("expand.block.volume.unmounting", hpuxSupport.getHostName(), mountPoint.getPath());
        hpuxSupport.unmount(mountPoint.getPath());
        hpuxSupport.removeVolumeMountPointTag(volume);

        logInfo("expand.block.volume.resize.volume", volume.getName(), newSizeInGB.toString());
        hpuxSupport.resizeVolume(volume, newSizeInGB);
        hpuxSupport.rescan();

        logInfo("expand.block.volume.remounting", hpuxSupport.getHostName(), mountPoint.getPath());
        RDisk rdisk = hpuxSupport.findRDisk(volume, usePowerPath);
        hpuxSupport.mount(rdisk.getDevicePath(), mountPoint.getPath());
        hpuxSupport.setVolumeMountPointTag(volume, mountPoint.getPath());
    }

}
