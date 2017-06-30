/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.linux;

import static com.emc.sa.service.vipr.ViPRExecutionUtils.logInfo;
import static com.emc.sa.service.vipr.ViPRExecutionUtils.logWarn;

import java.util.Collections;
import java.util.List;

import org.apache.commons.lang.StringUtils;

import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.engine.bind.BindingUtils;
import com.emc.sa.service.ArtificialFailures;
import com.emc.sa.service.vipr.ViPRService;
import com.emc.sa.service.vipr.block.BlockStorageUtils;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.model.block.BlockObjectRestRep;
import com.iwave.ext.linux.LinuxSystemCLI;
import com.iwave.ext.linux.model.MountPoint;

public class ExpandBlockVolumeHelper {
    private final LinuxSupport linuxSupport;

    private static final int MAX_RESIZE_RETRIES = 3;

    private static final long EXPAND_RETRY_DELAY = 5000;

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
        linuxSupport.verifyVolumeFilesystemMount(volume, mountPoint.getPath(), usePowerPath);
    }

    public void expandVolume(BlockObjectRestRep volume, Double newSizeInGB) {
        logInfo("expand.block.volume.unmounting", linuxSupport.getHostName(), mountPoint.getPath());
        linuxSupport.unmountPath(mountPoint.getPath());
        ViPRService.artificialFailure(ArtificialFailures.ARTIFICIAL_FAILURE_LINUX_EXPAND_VOLUME_AFTER_UNMOUNT);
        linuxSupport.removeFromFSTab(mountPoint);
        linuxSupport.removeVolumeMountPointTag(volume);
        ViPRService.artificialFailure(ArtificialFailures.ARTIFICIAL_FAILURE_LINUX_EXPAND_VOLUME_AFTER_REMOVE_TAG);

        // Skip the expand if the current volume capacity is larger than the requested expand size
        if (BlockStorageUtils.isVolumeExpanded(volume, newSizeInGB)) {
            logWarn("linux.expand.skip", volume.getId(), BlockStorageUtils.getCapacity(volume));
        } else {
            logInfo("expand.block.volume.resize.volume", volume.getName(), newSizeInGB.toString());
            linuxSupport.resizeVolume(volume, newSizeInGB);
            ViPRService.artificialFailure(ArtificialFailures.ARTIFICIAL_FAILURE_LINUX_EXPAND_VOLUME_AFTER_VOLUME_RESIZE);
        }

        linuxSupport.refreshStorage(Collections.singletonList(volume), usePowerPath);

        logInfo("expand.block.volume.find.parent", mountPoint.getDevice());
        String parentDevice = linuxSupport.getParentDevice(mountPoint.getDevice(), usePowerPath);

        String initialBlockSize = linuxSupport.getFilesystemBlockSize(parentDevice);
        logInfo("expand.block.volume.partition.size", initialBlockSize);

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

        boolean fileSystemExpanded = false;
        int resizeAttempts = 0;
        while (!fileSystemExpanded) {
            resizeAttempts++;
            logInfo("expand.block.volume.resize.partition", volume.getName());
            linuxSupport.resizePartition(parentDevice);
            ViPRService.artificialFailure(ArtificialFailures.ARTIFICIAL_FAILURE_LINUX_EXPAND_VOLUME_AFTER_RESIZE_PARTITION);
            String currentBlockSize = linuxSupport.getFilesystemBlockSize(parentDevice);
            logInfo("expand.block.volume.partition.size", currentBlockSize);

            if (initialBlockSize == null || currentBlockSize == null || !StringUtils.equalsIgnoreCase(initialBlockSize, currentBlockSize)) {
                fileSystemExpanded = true;
            } else if (resizeAttempts >= MAX_RESIZE_RETRIES) {
                fileSystemExpanded = true;
                logWarn("expand.block.volume.unable.to.determine.resize");
            } else {
                try {
                    Thread.sleep(EXPAND_RETRY_DELAY);
                } catch (InterruptedException e) {
                    logWarn("expand.block.volume.resize.sleep.failure");
                }
            }
        }

        logInfo("expand.block.volume.resize.file", linuxSupport.getHostName());
        linuxSupport.resizeFileSystem(mountPoint.getDevice());
        ViPRService.artificialFailure(ArtificialFailures.ARTIFICIAL_FAILURE_LINUX_EXPAND_VOLUME_AFTER_RESIZE_FILESYSTEM);

        logInfo("expand.block.volume.remounting", linuxSupport.getHostName(), mountPoint.getPath());
        linuxSupport.addToFSTab(mountPoint.getDevice(), mountPoint.getPath(), mountPoint.getFsType(), null);
        ExecutionUtils.clearRollback();

        linuxSupport.mountPath(mountPoint.getPath());
        ExecutionUtils.clearRollback();

        ViPRService.artificialFailure(ArtificialFailures.ARTIFICIAL_FAILURE_LINUX_EXPAND_VOLUME_AFTER_MOUNT);
        linuxSupport.setVolumeMountPointTag(volume, mountPoint.getPath());
        ExecutionUtils.clearRollback();
    }

}
