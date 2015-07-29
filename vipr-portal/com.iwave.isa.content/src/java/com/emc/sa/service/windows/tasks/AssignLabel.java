/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.windows.tasks;

import com.iwave.ext.windows.WindowsUtils;
import com.iwave.ext.windows.model.Volume;

public class AssignLabel extends WindowsExecutionTask<Void> {
    private String disk;
    private String label;

    public AssignLabel(Volume volume, String label) {
        this(volume.getMountPoint(), volume.getFileSystem(), label);
    }

    public AssignLabel(String disk, String fsType, String label) {
        this.disk = disk;
        this.label = WindowsUtils.normalizeDriveLabel(fsType, label);
        provideDetailArgs(this.disk, this.label);
    }

    @Override
    public void execute() throws Exception {
        getTargetSystem().assignLabel(disk, label);
    }
}
