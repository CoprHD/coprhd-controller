/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.windows.tasks;

import java.util.List;
import java.util.Set;

import com.google.common.collect.Sets;
import com.iwave.ext.windows.model.wmi.Volume;

public class GetAssignedDriveLetters extends WindowsExecutionTask<Set<String>> {
    public GetAssignedDriveLetters() {
    }

    @Override
    public Set<String> executeTask() throws Exception {
        Set<String> driveLetters = Sets.newTreeSet();
        List<Volume> volumes = getTargetSystem().listVolumes();
        for ( Volume volume : volumes) {
            driveLetters.add(volume.getDriveLetter());
        }
        return driveLetters;
    }
}
