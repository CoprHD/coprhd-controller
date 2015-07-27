/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.windows.tasks;

import java.util.Collection;

import org.apache.commons.lang.StringUtils;

import com.emc.sa.service.windows.WindowsUtils;

public class VerifyMountPointHostDriveIsMounted extends WindowsExecutionTask<Void> {

    private final Collection<String> assignedMountPoints;
    private final String driveletter;
    
    public VerifyMountPointHostDriveIsMounted(String mountpoint, Collection<String> assignedMountPoints) {
        logDebug("verify.mount.host.drive.letter.mountpoint", mountpoint);
        this.driveletter = WindowsUtils.getDriveLetterFromMountPath(mountpoint);
        logInfo("verify.mount.host.drive.letter", driveletter);
        
        logDebug("verify.mount.host.assigned", StringUtils.join(assignedMountPoints, ", "));
        this.assignedMountPoints = assignedMountPoints;
        
        provideDetailArgs(driveletter);
    }
    
    @Override
    public void execute() throws Exception {
        if (!assignedMountPoints.contains(driveletter+":")) {
            throw stateException("illegalState.VerifyMountPointHostDriveIsMounted.notMounted");
        }
    }
    
}
