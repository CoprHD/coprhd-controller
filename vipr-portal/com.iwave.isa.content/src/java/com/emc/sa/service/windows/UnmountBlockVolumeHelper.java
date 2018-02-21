/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.windows;

import static com.emc.sa.service.vipr.ViPRExecutionUtils.logInfo;

<<<<<<< HEAD
import java.net.URI;
=======
>>>>>>> 88286dbcd8dcc248675f8d0d29a73f16d70aee2a
import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.engine.bind.BindingUtils;
import com.emc.storageos.model.block.BlockObjectRestRep;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.iwave.ext.windows.WindowsSystemWinRM;
import com.iwave.ext.windows.model.Disk;
import com.iwave.ext.windows.model.Volume;
import com.iwave.ext.windows.model.wmi.DiskDrive;

public class UnmountBlockVolumeHelper {

    private final WindowsSupport windows;

    private URI hostId;

    /** The volumes to unmount. */
    private Collection<? extends BlockObjectRestRep> volumes;

    /** Mapping of volume to disk. */
    private Map<? extends BlockObjectRestRep, DiskDrive> volume2disk;

    public static List<UnmountBlockVolumeHelper> createHelpers(URI hostId, List<WindowsSystemWinRM> windowsSystems) {
        List<UnmountBlockVolumeHelper> helpers = Lists.newArrayList();
        for (WindowsSystemWinRM windowsSystem : windowsSystems) {
            WindowsSupport windowsSupport = new WindowsSupport(windowsSystem);
            UnmountBlockVolumeHelper unmountBlockVolumeHelper = new UnmountBlockVolumeHelper(hostId, windowsSupport);
            BindingUtils.bind(unmountBlockVolumeHelper, ExecutionUtils.currentContext().getParameters());
            helpers.add(unmountBlockVolumeHelper);
        }

        return helpers;
    }

    private UnmountBlockVolumeHelper(URI hostId, WindowsSupport windowsSupport) {
        this.hostId = hostId;
        this.windows = windowsSupport;
    }

    public void setVolumes(Collection<? extends BlockObjectRestRep> volumes) {
        this.volumes = volumes;
    }

    public void precheck() {
        windows.verifyWinRM();
        windows.verifyVolumesMounted(volumes);
        volume2disk = windows.findDisks(volumes);

        // Get the actual mount points for the volumes from the system
        Map<BlockObjectRestRep, String> volume2mountPoint = Maps.newHashMap();
        for (Map.Entry<? extends BlockObjectRestRep, DiskDrive> entry : volume2disk.entrySet()) {
            BlockObjectRestRep volume = entry.getKey();
            DiskDrive disk = entry.getValue();
            Disk detail = windows.getDiskDetail(disk);
            String mountPoint = ExtendDriveHelper.getMountPoint(disk, detail);
            volume2mountPoint.put(volume, mountPoint);
        }
        WindowsUtils.verifyMountPoints(hostId, volume2mountPoint);
    }

    public void removeVolumesFromCluster() {
        Map<String, String> diskToResourceMap = windows.getDiskToResourceMap();

        for (BlockObjectRestRep volume : volumes) {
            DiskDrive diskDrive = volume2disk.get(volume);
            Disk diskDetail = windows.getDiskDetail(diskDrive);
            String resourceName = "";
            if (windows.isGuid(diskDetail.getDiskId())) {
                resourceName = diskToResourceMap.get(diskDetail.getDiskId());
            } else {
                resourceName = diskToResourceMap.get(diskDrive.getSignature());
            }

            windows.offlineClusterResource(resourceName);
            windows.deleteClusterResource(resourceName);
        }
    }

    public void unmountVolumes() {
        for (BlockObjectRestRep volume : volumes) {
            DiskDrive disk = volume2disk.get(volume);
            Disk diskDetail = windows.getDiskDetail(disk);

            if (diskDetail.getVolumes() != null) {
                for (Volume diskVolume : diskDetail.getVolumes()) {
                    windows.unmountVolume(diskVolume.getNumber(), diskVolume.getMountPoint());
                    boolean isDriveLetterOnly = WindowsUtils.isMountPointDriveLetterOnly(diskVolume.getMountPoint());
                    if (!isDriveLetterOnly && windows.isDirectoryEmpty(diskVolume.getMountPoint())) {
                        windows.deleteDirectory(diskVolume.getMountPoint());
                    }
                }
            }

            if (diskDetail.isOnline()) {
                windows.offlineDisk(disk);
            } else {
                logInfo("win.unmount.block.volume.disk.offline", disk.getNumber(), volume.getWwn());
            }
            windows.removeVolumeMountPoint(volume);
        }
    }
}
