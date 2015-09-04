/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.windows;

import static com.emc.sa.service.ServiceParams.BLOCK_SIZE;
import static com.emc.sa.service.ServiceParams.DO_FORMAT;
import static com.emc.sa.service.ServiceParams.PARTITION_TYPE;
import static com.emc.sa.service.ServiceParams.FILE_SYSTEM_TYPE;
import static com.emc.sa.service.ServiceParams.LABEL;
import static com.emc.sa.service.ServiceParams.MOUNT_POINT;
import static com.emc.sa.service.vipr.ViPRExecutionUtils.logInfo;
import static com.iwave.ext.windows.WindowsUtils.isFat32;
import static com.iwave.ext.windows.WindowsUtils.isFat32CapacityInBytesTooLarge;
import static com.iwave.ext.windows.WindowsUtils.isMBR;
import static com.iwave.ext.windows.WindowsUtils.isMBRCapacityInBytesTooLarge;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.StringUtils;

import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.engine.bind.BindingUtils;
import com.emc.sa.engine.bind.Param;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.model.block.BlockObjectRestRep;
import com.google.common.collect.Lists;
import com.iwave.ext.windows.WindowsSystemWinRM;
import com.iwave.ext.windows.model.Disk;
import com.iwave.ext.windows.model.Volume;
import com.iwave.ext.windows.model.wmi.DiskDrive;

public class MountBlockVolumeHelper {

    private static final short MAX_FAT32_LABEL_LENGTH = 11;

    private static final short MAX_NTFS_LABEL_LENGTH = 32;

    private final WindowsSupport windows;

    private final String hostname;

    @Param(MOUNT_POINT)
    protected String mountPoint;

    @Param(value = FILE_SYSTEM_TYPE, required = false)
    protected String fileSystemType;

    @Param(value = BLOCK_SIZE, required = false)
    protected String blockSize;

    @Param(value = DO_FORMAT, required = false)
    protected boolean doFormat = true;

    @Param(value = PARTITION_TYPE, required = false)
    protected String partitionType;

    @Param(value = LABEL, required = false)
    protected String label;

    /** The amount of time to wait between attempts to refresh the storage. */
    private long refreshDelay = 15 * 1000;

    /** The number of attempts to try refreshing the storage waiting for a disk to appear. */
    private int refreshAttempts = 60;

    private long volumeCapacityInBytes = 0;

    private Set<String> assignedMountpoints;

    public static List<MountBlockVolumeHelper> createHelpers(List<WindowsSystemWinRM> windowsSystems, long volumeCapacityInBytes) {
        List<MountBlockVolumeHelper> helpers = Lists.newArrayList();
        for (WindowsSystemWinRM windowsSystem : windowsSystems) {
            WindowsSupport windowsSupport = new WindowsSupport(windowsSystem);
            MountBlockVolumeHelper mountBlockVolumeHelper = new MountBlockVolumeHelper(windowsSupport, volumeCapacityInBytes);
            BindingUtils.bind(mountBlockVolumeHelper, ExecutionUtils.currentContext().getParameters());
            helpers.add(mountBlockVolumeHelper);
        }

        return helpers;
    }

    private MountBlockVolumeHelper(WindowsSupport windowsSupport, long volumeCapacityInBytes) {
        this.windows = windowsSupport;
        this.hostname = windowsSupport.getHostName();
        this.volumeCapacityInBytes = volumeCapacityInBytes;
    }

    public long getRefreshDelay() {
        return refreshDelay;
    }

    public void setRefreshDelay(long refreshDelay) {
        this.refreshDelay = refreshDelay;
    }

    public int getRefreshAttempts() {
        return refreshAttempts;
    }

    public void setRefreshAttempts(int refreshAttempts) {
        this.refreshAttempts = refreshAttempts;
    }

    public void precheck() {
        verifyCapacity();
        verifyDriveLabel();

        windows.verifyWinRM();
        windows.rescanDisks();

        if (windows.isClustered()) {
            logInfo("win.mount.block.volume.mount.share");
            windows.verifyClusterSupport();
            windows.verifyMountPointIsDriveLetter(mountPoint);
        }

        assignedMountpoints = windows.getAssignedDriveLetters();
        if (WindowsUtils.isMountPointDriveLetterOnly(mountPoint)) {
            windows.verifyMountPointLetterIsAvailable(mountPoint, assignedMountpoints);
        }
        else {
            logInfo("win.mount.block.volume.mount.path");
            windows.verifyMountPointHostDriveIsMounted(mountPoint, assignedMountpoints);
            windows.verifyMountPointIsNotShared(mountPoint);
        }
    }

    public void verifyDriveLabel() {
        if (isFat32(fileSystemType) && label.length() > MAX_FAT32_LABEL_LENGTH) {
            ExecutionUtils.fail("failTask.MountBlockVolumeHelper.fat32.driveLabelTooLarge", label);
        } else if (label.length() > MAX_NTFS_LABEL_LENGTH) {
            ExecutionUtils.fail("failTask.MountBlockVolumeHelper.ntfs.driveLabelTooLarge", label);
        }
    }

    public void verifyCapacity() {
        if (this.doFormat && isFat32(fileSystemType)) {
            if (isFat32CapacityInBytesTooLarge(volumeCapacityInBytes)) {
                ExecutionUtils.fail("failTask.MountBlockVolumeHelper.fat32.tooLarge", fileSystemType);
            }
        }

        if (this.doFormat && isMBR(this.partitionType)) {
            if (isMBRCapacityInBytesTooLarge(volumeCapacityInBytes)) {
                ExecutionUtils.fail("failTask.MountBlockVolumeHelper.MBR.tooLarge", partitionType);
            }
        }
    }

    public void verifyClusterHosts(List<Host> hosts) {
        if (windows.isClustered()) {
            windows.verifyClusterHosts(hosts);
        }
    }

    /**
     * Mounts the given volumes. If {@link #doFormat} is true, the volume is formatted first.
     * 
     * @param volume
     *            the volumes to mount.
     */
    public DiskDrive mountVolume(BlockObjectRestRep volume) {
        windows.rescanDisks();
        Map<? extends BlockObjectRestRep, DiskDrive> volume2disk =
                windows.discoverDisks(Collections.singleton(volume), refreshAttempts, refreshDelay);
        DiskDrive disk = volume2disk.get(volume);
        mount(volume, disk, getMountPoint());
        return disk;
    }

    private String getMountPoint() {
        String mountpoint = WindowsUtils.normalizeMountPath(mountPoint);

        if (!WindowsUtils.isMountPointDriveLetterOnly(mountpoint)) {
            logInfo("win.mount.block.volume.mkdir.mount", mountpoint);
            windows.makeDirectory(mountpoint);
        }

        return mountpoint;
    }

    /**
     * Mounts the volume. If {@link #doFormat} is true, the volume is formatted first.
     * 
     * @param volume
     *            the volume to mount.
     * @param mountPoint
     *            the mount point to assign the volume. An empty mount point will cause the system to auto-assign a
     *            drive letter.
     */
    public void mount(BlockObjectRestRep volume, DiskDrive disk, String mountPoint) {
        Disk diskDetail = detailDisk(disk);

        boolean isOnline = diskDetail.isOnline();
        if (!isOnline) {
            logInfo("win.mount.block.volume.disk.online", hostname, disk.getNumber(), volume.getWwn());
            windows.onlineDisk(diskDetail);
        }

        if (doFormat) {
            logInfo("win.mount.block.volume.format", hostname, disk.getNumber(), fileSystemType, volume.getWwn());
            windows.formatAndMount(disk, fileSystemType, getBlockSize(), getActualLabel(volume), mountPoint, partitionType);
        }
        else {
            // If the disk was not online, detail it again since no volume information would have been available
            if (!isOnline) {
                diskDetail = detailDisk(disk);
            }

            if (diskDetail.getVolumes() == null || diskDetail.getVolumes().isEmpty()) {
                ExecutionUtils
                        .fail("failTask.MountBlockVolumeHelper.noVolumes", disk.getName(), hostname, disk.getName(), disk.getNumber());
            }

            // Mount the first volume only
            Volume diskVolume = diskDetail.getVolumes().get(0);
            int volumeNumber = diskVolume.getNumber();
            String label = StringUtils
                    .defaultIfBlank(diskVolume.getLabel(), ExecutionUtils.getMessage("MountBlockVolumeHelper.label.none"));
            String fs = diskVolume.getFileSystem();

            logInfo("win.mount.block.volume.mount", hostname, volumeNumber, label, fs, volume.getWwn());
            windows.mountVolume(volumeNumber, mountPoint);
        }

        // Refresh the disk details
        diskDetail = detailDisk(disk);
        String assignedMountpoint = windows.getAssignedMountPoint(disk, diskDetail);
        assignedMountpoints.add(assignedMountpoint);
        logInfo(ExecutionUtils.getMessage("MountBlockVolumeHelper.log.mountpointToVolume", hostname, assignedMountpoint, volume.getWwn()));

        // Check to see if the the desired volume label is different than the actual label if it wasn't formatted
        if (!doFormat) {
            Volume diskVolume = diskDetail.getVolumes().get(0);
            String desiredLabel = getActualLabel(volume);
            if (!StringUtils.defaultString(diskVolume.getLabel()).equals(desiredLabel)) {
                windows.assignLabel(diskVolume, label);
            }
        }

        windows.addVolumeMountPoint(volume, assignedMountpoint);
    }

    public Disk detailDisk(DiskDrive disk) {
        Disk diskDetail;
        diskDetail = windows.getDiskDetail(disk);
        if (diskDetail == null) {
            ExecutionUtils.fail("failTask.MountBlockVolumeHelper.couldNotDetailDisk", disk.getName(), hostname, disk.getNumber());
        }
        return diskDetail;
    }

    private String getActualLabel(BlockObjectRestRep volume) {
        String actualLabel = label;
        if (StringUtils.isBlank(actualLabel)) {
            actualLabel = volume.getName();
        }
        return actualLabel;
    }

    private String getBlockSize() {
        if (StringUtils.equalsIgnoreCase("DEFAULT", blockSize)) {
            return StringUtils.EMPTY;
        }
        return blockSize;
    }

    public void rescanDisks() {
        windows.rescanDisks();
    }

    public void addDisksToCluster(Collection<DiskDrive> diskDrives) {
        for (DiskDrive diskDrive : diskDrives) {
            addDiskToCluster(diskDrive);
        }
    }

    public void addDiskToCluster(DiskDrive diskDrive) {
        Disk diskDetail = windows.getDiskDetail(diskDrive);
        String signature = "";

        if (windows.isGuid(diskDetail.getDiskId())) {
            signature = diskDetail.getDiskId();
        } else {
            signature = "" + Long.decode("0x" + diskDetail.getDiskId());
        }

        String resourceName = windows.addDiskToCluster(signature);
        logInfo("win.mount.block.volume.added.disk.cluster", hostname, signature, resourceName);
    }
}
