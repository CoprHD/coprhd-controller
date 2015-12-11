/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.service.hpux.tasks;

import java.util.List;

import com.emc.hpux.command.ListMountPointsCommand;
import com.emc.hpux.model.MountPoint;

/**
 * Returns a Map of Path -> MountPoint.
 */
public class ListMountPoints extends HpuxExecutionTask<List<MountPoint>> {

    public ListMountPoints() {
    }

    @Override
    public List<MountPoint> executeTask() throws Exception {
        ListMountPointsCommand command = new ListMountPointsCommand();
        return executeCommand(command, SHORT_TIMEOUT);
    }
}
