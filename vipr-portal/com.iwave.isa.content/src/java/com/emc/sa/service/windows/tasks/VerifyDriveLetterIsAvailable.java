/**
* Copyright 2012-2015 iWave Software LLC
* All Rights Reserved
 */
package com.emc.sa.service.windows.tasks;

import org.apache.commons.lang.StringUtils;

import java.util.Set;

public class VerifyDriveLetterIsAvailable extends WindowsExecutionTask<Void> {
    private static final int MAX_DRIVE_LETTERS = 24; // A & B are floppy disks
    private final String driveLetter;
    private final Set<String> usedDriveLetters;

    public VerifyDriveLetterIsAvailable(String driveLetter, Set<String> usedDriveLetters) {
        this.driveLetter = driveLetter;
        this.usedDriveLetters = usedDriveLetters;
        provideDetailArgs(driveLetter);
    }
    
    @Override
    public void execute() throws Exception {
        if (StringUtils.isBlank(driveLetter)) {
            if (usedDriveLetters.size() == MAX_DRIVE_LETTERS) {
                String hostname = getTargetSystem().getTarget().getHost();
                throw stateException("illegalState.VerifyDriveLetterIsAvailable.noSpareDriveLetters", hostname);
            }
        }
        else {
            if (usedDriveLetters.contains(driveLetter+":")) {
                String hostname = getTargetSystem().getTarget().getHost();
                throw stateException("illegalState.VerifyDriveLetterIsAvailable.driveLetterUnavailable", driveLetter, hostname);
            }
        }
    }

}
