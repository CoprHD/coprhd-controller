/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.service.hpux.tasks;

import java.net.URI;
import java.util.List;

import com.emc.hpux.command.ListMountPointsCommand;
import com.emc.hpux.model.MountPoint;
import com.emc.sa.service.hpux.VolumeSpec;

/**
 * Returns a Map of VolumeId -> MountPoint for all the specified Volumes
 */
public class FindMountPointsForVolumes extends HpuxExecutionTask<Void> {

    private List<VolumeSpec> volumes;

    private URI hostId;

    public FindMountPointsForVolumes(URI hostId, List<VolumeSpec> volumes) {
        this.volumes = volumes;
        this.hostId = hostId;
    }

    @Override
    public Void executeTask() throws Exception {
        ListMountPointsCommand command = new ListMountPointsCommand();
        List<MountPoint> results = executeCommand(command, SHORT_TIMEOUT);
        for (VolumeSpec volume : volumes) {
            volume.mountPoint = HpuxUtils.getMountPoint(hostId, results, volume.viprVolume);
        }
        return null;
    }

}
