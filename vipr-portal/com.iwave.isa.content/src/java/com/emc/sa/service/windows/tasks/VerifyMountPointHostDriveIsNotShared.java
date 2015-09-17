/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.windows.tasks;

import org.apache.commons.lang.StringUtils;

import com.emc.sa.service.windows.WindowsUtils;
import com.iwave.ext.windows.model.Disk;
import com.iwave.ext.windows.model.Volume;
import com.iwave.ext.windows.model.wmi.DiskDrive;
import com.iwave.ext.windows.winrm.WinRMException;

public class VerifyMountPointHostDriveIsNotShared extends WindowsExecutionTask<Void> {

    private final String driveletter;

    public VerifyMountPointHostDriveIsNotShared(String mountpoint) {
        this.driveletter = WindowsUtils.getDriveLetterFromMountPath(mountpoint);
        provideDetailArgs(driveletter);
    }

    @Override
    public void execute() throws Exception {
        Boolean hostDriveIsShared = isSharedStorage(driveletter);
        if (hostDriveIsShared == null) {
            throw stateException("illegalState.VerifyMountPointHostDriveIsNotShared.noVolume", driveletter);
        }
        else if (hostDriveIsShared) {
            throw stateException("illegalState.VerifyMountPointHostDriveIsNotShared.shared", driveletter);
        }
    }

    public Boolean isSharedStorage(String driveletter) throws WinRMException {
        for (DiskDrive drive : getTargetSystem().listDiskDrives()) {
            Disk disk = getTargetSystem().detailDisk(drive.getNumber());
            if (disk != null) {
                for (Volume volume : disk.getVolumes()) {
                    if (isVolumeMountedAtDriveLetter(driveletter, volume)) {
                        return disk.getClusteredDisk();
                    }
                }
            }
        }
        return null;
    }

    public static boolean isVolumeMountedAtDriveLetter(String driveLetter, Volume volume) {
        String volumeDriveLetter = WindowsUtils.getDriveLetterFromMountPath(volume.getMountPoint());
        return StringUtils.equals(driveLetter, volumeDriveLetter);
    }

}
