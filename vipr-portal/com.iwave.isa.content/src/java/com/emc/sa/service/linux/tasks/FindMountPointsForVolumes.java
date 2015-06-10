/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.linux.tasks;

import java.net.URI;
import java.util.List;
import java.util.Map;

import com.emc.sa.service.linux.LinuxUtils;
import com.emc.sa.service.linux.UnmountBlockVolumeHelper.VolumeSpec;
import com.iwave.ext.linux.command.ListMountPointsCommand;
import com.iwave.ext.linux.model.MountPoint;

/**
 * Returns a Map of VolumeId -> MountPoint for all the specified Volumes
 */
public class FindMountPointsForVolumes extends LinuxExecutionTask<Void> {

    private List<VolumeSpec> volumes;
    private URI hostId;

    public FindMountPointsForVolumes(URI hostId, List<VolumeSpec> volumes) {
        this.volumes = volumes;
        this.hostId = hostId;
    }

    @Override
    public Void executeTask() throws Exception {
        ListMountPointsCommand command = new ListMountPointsCommand();
        Map<String, MountPoint> results = executeCommand(command, SHORT_TIMEOUT);
        for (VolumeSpec volume : volumes) {
            volume.mountPoint = LinuxUtils.getMountPoint(hostId, results, volume.viprVolume);
        }
        return null;
    }

}
