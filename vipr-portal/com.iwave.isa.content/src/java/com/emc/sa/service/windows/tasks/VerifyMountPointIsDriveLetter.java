/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.windows.tasks;

import com.emc.sa.service.windows.WindowsUtils;

public class VerifyMountPointIsDriveLetter extends WindowsExecutionTask<Void> {

    private final String mountpoint;

    public VerifyMountPointIsDriveLetter(String mountpoint) {
        this.mountpoint = mountpoint;
        provideDetailArgs(mountpoint);
    }

    @Override
    public void execute() throws Exception {
        if (!WindowsUtils.isMountPointDriveLetterOnly(mountpoint)) {
            throw stateException("illegalState.VerifyMountPointIsDriveLetter.notMounted");
        }
    }

}
