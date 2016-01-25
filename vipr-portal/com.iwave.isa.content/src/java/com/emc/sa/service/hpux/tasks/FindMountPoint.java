/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.service.hpux.tasks;

import java.net.URI;
import java.util.List;

import com.emc.hpux.command.ListMountPointsCommand;
import com.emc.hpux.model.MountPoint;
import com.emc.storageos.model.block.BlockObjectRestRep;

/**
 * Returns a Map of VolumeId -> MountPoint for all the specified Volumes
 */
public class FindMountPoint extends HpuxExecutionTask<MountPoint> {

    private BlockObjectRestRep volume;

    private URI hostId;

    public FindMountPoint(URI hostId, BlockObjectRestRep volume) {
        this.volume = volume;
        this.hostId = hostId;
    }

    @Override
    public MountPoint executeTask() throws Exception {
        ListMountPointsCommand command = new ListMountPointsCommand();
        List<MountPoint> results = executeCommand(command, SHORT_TIMEOUT);
        MountPoint mp = HpuxUtils.getMountPoint(hostId, results, volume);
        return mp;
    }

}
