/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.linux;

import static com.emc.sa.service.vipr.ViPRExecutionUtils.logInfo;

import java.util.Collections;
import java.util.List;

import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.engine.bind.BindingUtils;
import com.emc.sa.service.ArtificialFailures;
import com.emc.sa.service.vipr.ViPRService;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.model.block.BlockObjectRestRep;
import com.iwave.ext.linux.LinuxSystemCLI;
import com.iwave.ext.linux.model.MountPoint;

public class ExpandBlockVolumeHelper {
    private final LinuxSupport linuxSupport;

    private MountPoint mountPoint;

    protected boolean usePowerPath;

    public static ExpandBlockVolumeHelper createHelper(LinuxSystemCLI linuxSystem, List<Initiator> hostPorts) {
        LinuxSupport linuxSupport = new LinuxSupport(linuxSystem, hostPorts);
        ExpandBlockVolumeHelper expandBlockVolumeHelper = new ExpandBlockVolumeHelper(linuxSupport);
        BindingUtils.bind(expandBlockVolumeHelper, ExecutionUtils.currentContext().getParameters());
        return expandBlockVolumeHelper;
    }

    public ExpandBlockVolumeHelper(LinuxSupport linuxSupport) {
        this.linuxSupport = linuxSupport;
    }

    public void precheck(BlockObjectRestRep volume) {
        usePowerPath = linuxSupport.checkForMultipathingSoftware();
        mountPoint = linuxSupport.findMountPoint(volume);
        linuxSupport.verifyVolumeMount(volume, mountPoint.getPath(), usePowerPath);
    }

    public void expandVolume(BlockObjectRestRep volume, Double newSizeInGB) {
        logInfo("expand.block.volume.unmounting", linuxSupport.getHostName(), mountPoint.getPath());
        linuxSupport.unmountPath(mountPoint.getPath());
        ViPRService.artificialFailure(ArtificialFailures.ARTIFICIAL_FAILURE_LINUX_EXPAND_VOLUME_AFTER_UNMOUNT);
        linuxSupport.removeFromFSTab(mountPoint.getPath());
        linuxSupport.removeVolumeMountPointTag(volume);
        ViPRService.artificialFailure(ArtificialFailures.ARTIFICIAL_FAILURE_LINUX_EXPAND_VOLUME_AFTER_REMOVE_TAG);

        linuxSupport.addMountExpandRollback(volume, mountPoint);

        logInfo("expand.block.volume.resize.volume", volume.getName(), newSizeInGB.toString());
        linuxSupport.resizeVolume(volume, newSizeInGB);
        ViPRService.artificialFailure(ArtificialFailures.ARTIFICIAL_FAILURE_LINUX_EXPAND_VOLUME_AFTER_VOLUME_RESIZE);
        linuxSupport.refreshStorage(Collections.singletonList(volume), usePowerPath);

        logInfo("expand.block.volume.find.parent", mountPoint.getDevice());
        String parentDevice = linuxSupport.getParentDevice(mountPoint.getDevice(), usePowerPath);

        List<String> blockDevices = linuxSupport.getBlockDevices(parentDevice, volume, usePowerPath);
        if (blockDevices != null && !blockDevices.isEmpty()) {
            linuxSupport.rescanBlockDevices(blockDevices);
        }

        if (!usePowerPath) {
            logInfo("expand.block.volume.resize.multipath", linuxSupport.getHostName(), mountPoint.getDevice());
            linuxSupport.resizeMultipathPath(parentDevice);
        }

        // this is the dm-* name
        // TODO: get the multipath device name using this dm-* name

        logInfo("expand.block.volume.resize.partition", volume.getName());
        linuxSupport.resizePartition(parentDevice);
        ViPRService.artificialFailure(ArtificialFailures.ARTIFICIAL_FAILURE_LINUX_EXPAND_VOLUME_AFTER_RESIZE_PARTITION);

        logInfo("expand.block.volume.resize.file", linuxSupport.getHostName());
        linuxSupport.resizeFileSystem(mountPoint.getDevice());
        ViPRService.artificialFailure(ArtificialFailures.ARTIFICIAL_FAILURE_LINUX_EXPAND_VOLUME_AFTER_RESIZE_FILESYSTEM);

        logInfo("expand.block.volume.remounting", linuxSupport.getHostName(), mountPoint.getPath());
        linuxSupport.addToFSTab(mountPoint.getDevice(), mountPoint.getPath(), mountPoint.getFsType(), null);
        linuxSupport.mountPath(mountPoint.getPath());
        ViPRService.artificialFailure(ArtificialFailures.ARTIFICIAL_FAILURE_LINUX_EXPAND_VOLUME_AFTER_MOUNT);
        linuxSupport.setVolumeMountPointTag(volume, mountPoint.getPath());

        ExecutionUtils.clearRollback();
    }

}
