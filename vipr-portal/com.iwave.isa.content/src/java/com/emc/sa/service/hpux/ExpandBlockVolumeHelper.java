/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.service.hpux;

import static com.emc.sa.service.vipr.ViPRExecutionUtils.logInfo;
import static com.emc.sa.service.vipr.ViPRExecutionUtils.logWarn;

import java.util.List;

import org.apache.commons.lang.StringUtils;

import com.emc.hpux.HpuxSystem;
import com.emc.hpux.model.MountPoint;
import com.emc.hpux.model.RDisk;
import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.engine.bind.BindingUtils;
import com.emc.sa.service.ArtificialFailures;
import com.emc.sa.service.vipr.ViPRService;
import com.emc.sa.service.vipr.block.BlockStorageUtils;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.model.block.BlockObjectRestRep;

public class ExpandBlockVolumeHelper {
    private static final int MAX_RESIZE_RETRIES = 3;

    private static final long EXPAND_RETRY_DELAY = 5000;

    private final HpuxSupport hpuxSupport;

    private MountPoint mountPoint;

    protected boolean usePowerPath;

    protected RDisk rdisk;

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
        rdisk = hpuxSupport.findRDisk(volume, usePowerPath);
        hpuxSupport.verifyMountedDevice(mountPoint, rdisk);
    }

    public void expandVolume(BlockObjectRestRep volume, Double newSizeInGB) {
        logInfo("expand.block.volume.unmounting", hpuxSupport.getHostName(), mountPoint.getPath());
        hpuxSupport.unmount(mountPoint.getPath(), rdisk.getDevicePath());
        ViPRService.artificialFailure(ArtificialFailures.ARTIFICIAL_FAILURE_HPUX_EXPAND_VOLUME_AFTER_UNMOUNT);
        hpuxSupport.removeFromFSTab(mountPoint);
        hpuxSupport.removeVolumeMountPointTag(volume);
        ViPRService.artificialFailure(ArtificialFailures.ARTIFICIAL_FAILURE_HPUX_EXPAND_VOLUME_AFTER_REMOVE_TAG);

        // Skip the expand if the current volume capacity is larger than the requested expand size
        if (BlockStorageUtils.isVolumeExpanded(volume, newSizeInGB)) {
            logWarn("hpux.expand.skip", volume.getId(), BlockStorageUtils.getCapacity(volume));
        } else {
            logInfo("expand.block.volume.resize.volume", volume.getName(), newSizeInGB.toString());
            hpuxSupport.resizeVolume(volume, newSizeInGB);
            ViPRService.artificialFailure(ArtificialFailures.ARTIFICIAL_FAILURE_HPUX_EXPAND_VOLUME_AFTER_VOLUME_RESIZE);
        }

        hpuxSupport.rescan();

        String initialBlockSize = hpuxSupport.getFilesystemBlockSize(rdisk.getDevicePath());
        logInfo("expand.block.volume.filesystem.size", initialBlockSize);

        boolean fileSystemExpanded = false;
        int resizeAttempts = 0;
        while (!fileSystemExpanded) {
            resizeAttempts++;
            logInfo("expand.block.volume.extend.filesystem", rdisk.getDevicePath());
            hpuxSupport.extendFilesystem(rdisk.getDevicePath());

            String currentBlockSize = hpuxSupport.getFilesystemBlockSize(rdisk.getDevicePath());
            logInfo("expand.block.volume.filesystem.size", currentBlockSize);

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

        logInfo("expand.block.volume.remounting", hpuxSupport.getHostName(), mountPoint.getPath());
        hpuxSupport.mount(rdisk.getDevicePath(), mountPoint.getPath());
        ExecutionUtils.clearRollback();

        hpuxSupport.addToFSTab(mountPoint.getDevice(), mountPoint.getPath(), null);
        ExecutionUtils.clearRollback();

        ViPRService.artificialFailure(ArtificialFailures.ARTIFICIAL_FAILURE_HPUX_EXPAND_VOLUME_AFTER_MOUNT);
        hpuxSupport.setVolumeMountPointTag(volume, mountPoint.getPath());
        ExecutionUtils.clearRollback();
    }

}