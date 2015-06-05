/**
* Copyright 2012-2015 iWave Software LLC
* All Rights Reserved
 */
package com.emc.sa.service.aix.tasks;

import java.net.URI;
import java.util.List;
import java.util.Map;

import com.emc.aix.command.ListMountPointsCommand;
import com.emc.aix.model.MountPoint;
import com.emc.sa.service.aix.AixUtils;
import com.emc.sa.service.aix.UnmountBlockVolumeHelper.VolumeSpec;

/**
 * Returns a Map of VolumeId -> MountPoint for all the specified Volumes
 */
public class FindMountPointsForVolumes extends AixExecutionTask<Void> {
    
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
            volume.mountPoint = AixUtils.getMountPoint(hostId, results, volume.viprVolume);
        }
        return null;
    }

}
