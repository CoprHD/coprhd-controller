/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.windows;

import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.engine.bind.BindingUtils;
import com.emc.sa.service.ArtificialFailures;
import com.emc.sa.service.vipr.ViPRService;
import com.emc.storageos.model.block.BlockObjectRestRep;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.iwave.ext.windows.WindowsSystemWinRM;
import com.iwave.ext.windows.model.Disk;
import com.iwave.ext.windows.model.Volume;
import com.iwave.ext.windows.model.wmi.DiskDrive;

public class ExtendDriveHelper {
    private final WindowsSupport windows;
    private Collection<? extends BlockObjectRestRep> volumes;
    private Map<? extends BlockObjectRestRep, DiskDrive> volume2disk;
    private Map<BlockObjectRestRep, String> volume2mountPoint;

    private long volumeSizeInBytes;

    private URI hostId;

    private boolean foundClusteredVolume = false;

    public static List<ExtendDriveHelper> createHelpers(URI hostId, List<WindowsSystemWinRM> windowsSystems, long volumeSizeInBytes) {
        List<ExtendDriveHelper> helpers = Lists.newArrayList();
        for (WindowsSystemWinRM windowsSystem : windowsSystems) {
            WindowsSupport windowsSupport = new WindowsSupport(windowsSystem);
            ExtendDriveHelper extendDriveHelper = new ExtendDriveHelper(hostId, windowsSupport, volumeSizeInBytes);
            BindingUtils.bind(extendDriveHelper, ExecutionUtils.currentContext().getParameters());
            helpers.add(extendDriveHelper);
        }

        return helpers;
    }

    private ExtendDriveHelper(URI hostId, WindowsSupport windowsSupport, long volumeSizeInBytes) {
        this.hostId = hostId;
        windows = windowsSupport;
        this.volumeSizeInBytes = volumeSizeInBytes;
    }

    public void setVolumes(Collection<BlockObjectRestRep> volumes) {
        this.volumes = volumes;
    }

    public void precheck() {
        windows.verifyWinRM();

        volume2disk = windows.findDisks(volumes);

        // Get the actual mount points for the volumes from the system
        volume2mountPoint = Maps.newHashMap();
        for (Map.Entry<? extends BlockObjectRestRep, DiskDrive> entry : volume2disk.entrySet()) {
            BlockObjectRestRep volume = entry.getKey();
            DiskDrive disk = entry.getValue();

            logInfo("extendDrive.diskVolumeOnHost", disk.getNumber(), windows.getHostName());
            Disk detail = windows.getDiskDetail(disk);

            if (windows.isClustered()) {
                if (isDiskVolumeOnHost(detail)) { // host in cluster and found
                    foundClusteredVolume = true;
                } else { // host in cluster and not found, don't process
                    continue;
                }
            }

            windows.checkPartitionRestriction(disk, volumeSizeInBytes);

            String mountPoint = getMountPoint(disk, detail);

            logInfo("extendDrive.volumeMountPoint", volume.getName(), mountPoint);
            volume2mountPoint.put(volume, mountPoint);
        }
        WindowsUtils.verifyMountPoints(hostId, volume2mountPoint);
    }

    /**
     * Helper function to determine if the volumes is on host when shared export to cluster
     * 
     * @return true or false if the volume found on the host
     */
    public boolean isDiskVolumeOnHost(Disk detail) {
        if (detail == null) {
            return false;
        }
        if ((detail.getVolumes() == null) || (detail.getVolumes().isEmpty())) {
            return false;
        }
        if (detail.getVolumes().size() > 1) {
            return false;
        }

        return true;
    }

    /**
     * Clustered volume only appear on one host. After precheck is done, this can be called to
     * see if the volume was found on the host.
     * 
     * @return true or false if the volume was found on the host
     */
    public boolean foundClusteredVolume() {
        return foundClusteredVolume;
    }

    /**
     * Gets the mount point for the given disk drive. The disk must have only a single volume and that volume must have
     * a non-empty mount point.
     * 
     * @param disk
     *            the disk drive.
     * @param detail the detail of the disk
     * @return the mount point for the disk.
     */
    public static String getMountPoint(DiskDrive disk, Disk detail) {
        if (detail == null) {
            ExecutionUtils.fail("failTask.ExtendDriveHelper.couldNotDetailDisk", disk.getNumber(), disk.getNumber());
        }
        if ((detail.getVolumes() == null) || (detail.getVolumes().isEmpty())) {
            ExecutionUtils.fail("failTask.ExtendDriveHelper.noVolumes", disk.getNumber(), disk.getNumber());
        }
        if (detail.getVolumes().size() > 1) {
            ExecutionUtils.fail("failTask.ExtendDriveHelper.moreThanOneVolume", disk.getNumber(), detail.getVolumes().size());
        }
        Volume volume = detail.getVolumes().get(0);
        String mountPoint = volume.getMountPoint();
        if (StringUtils.isBlank(mountPoint)) {
            ExecutionUtils
                    .fail("failTask.ExtendDriveHelper.volumeHasNoMountPoint", disk.getNumber(), volume.getNumber(), volume.getLabel());
        }
        return mountPoint;
    }

    public void extendDrives() {
        windows.rescanDisks();
        for (Map.Entry<? extends BlockObjectRestRep, String> entry : volume2mountPoint.entrySet()) {
            BlockObjectRestRep volume = entry.getKey();
            String mountPoint = entry.getValue();
            ViPRService.artificialFailure(ArtificialFailures.ARTIFICIAL_FAILURE_WINDOWS_BEFORE_EXTEND_DRIVE);
            windows.extendDrive(volume, mountPoint);
            // Updates the volume mount point, it may have changed
            ViPRService.artificialFailure(ArtificialFailures.ARTIFICIAL_FAILURE_WINDOWS_AFTER_EXTEND_DRIVE);
            windows.addVolumeMountPoint(volume, mountPoint);
        }
        ExecutionUtils.clearRollback();
    }

    private void logInfo(String messageKey, Object... args) {
        ExecutionUtils.currentContext().logInfo(ExecutionUtils.getMessage(messageKey, args));
    }
}
