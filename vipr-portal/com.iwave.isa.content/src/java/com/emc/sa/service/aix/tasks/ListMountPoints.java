/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.aix.tasks;

import java.util.Map;

import com.emc.aix.command.ListMountPointsCommand;
import com.emc.aix.model.MountPoint;

/**
 * Returns a Map of Path -> MountPoint.
 */
public class ListMountPoints extends AixExecutionTask<Map<String, MountPoint>> {
    
    public ListMountPoints(){
    }
    
    @Override
    public Map<String, MountPoint> executeTask() throws Exception {
        ListMountPointsCommand command = new ListMountPointsCommand();
        return executeCommand(command, SHORT_TIMEOUT);
    }
}
