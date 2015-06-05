/**
* Copyright 2012-2015 iWave Software LLC
* All Rights Reserved
 */
package com.emc.sa.service.linux.tasks;

import java.util.Map;

import com.iwave.ext.linux.command.ListMountPointsCommand;
import com.iwave.ext.linux.model.MountPoint;

/**
 * Returns a Map of Path -> MountPoint.
 */
public class ListMountPoints extends LinuxExecutionTask<Map<String, MountPoint>> {
    @Override
    public Map<String, MountPoint> executeTask() throws Exception {
        ListMountPointsCommand command = new ListMountPointsCommand();
        return executeCommand(command, SHORT_TIMEOUT);
    }
}
