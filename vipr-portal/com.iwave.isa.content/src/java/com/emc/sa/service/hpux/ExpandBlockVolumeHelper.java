/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.service.hpux;

import static com.emc.sa.service.vipr.ViPRExecutionUtils.logInfo;

import java.util.List;

import com.emc.hpux.HpuxSystem;
import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.engine.bind.BindingUtils;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.model.block.BlockObjectRestRep;
import com.iwave.ext.linux.model.MountPoint;

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
        hpuxSupport.findMountPoint(volume);
    }

    public void expandVolume(BlockObjectRestRep volume, Double newSizeInGB) {
        logInfo("expand.block.volume.unmounting", hpuxSupport.getHostName(), mountPoint.getPath());
        hpuxSupport.unmount(mountPoint.getPath());
        hpuxSupport.removeVolumeMountPointTag(volume);

        logInfo("expand.block.volume.resize.volume", volume.getName(), newSizeInGB.toString());
        hpuxSupport.resizeVolume(volume, newSizeInGB);
        hpuxSupport.rescan();

        // logInfo("expand.block.volume.resize.partition", volume.getName());
        // hpuxSupport.resizePartition(parentDevice);

        // logInfo("expand.block.volume.resize.file", hpuxSupport.getHostName());
        // hpuxSupport.resizeFileSystem(mountPoint.getDevice());

        logInfo("expand.block.volume.remounting", hpuxSupport.getHostName(), mountPoint.getPath());
        String rdisk = hpuxSupport.findRDisk(volume, usePowerPath);
        hpuxSupport.mount(rdisk, mountPoint.getPath());
        hpuxSupport.setVolumeMountPointTag(volume, mountPoint.getPath());
    }

}
