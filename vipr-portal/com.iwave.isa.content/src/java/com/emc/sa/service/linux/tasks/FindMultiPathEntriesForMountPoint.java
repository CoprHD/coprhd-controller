/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.linux.tasks;

import java.util.List;

import org.apache.commons.lang.StringUtils;

import com.emc.sa.service.linux.UnmountBlockVolumeHelper.VolumeSpec;
import com.google.common.collect.Lists;
import com.iwave.ext.linux.command.ListMultiPathEntriesCommand;
import com.iwave.ext.linux.model.MultiPathEntry;

public class FindMultiPathEntriesForMountPoint extends LinuxExecutionTask<Void> {
    private List<VolumeSpec> volumes;

    public FindMultiPathEntriesForMountPoint(List<VolumeSpec> volumes) {
        this.volumes = volumes;
    }

    @Override
    public Void executeTask() throws Exception {
        List<MultiPathEntry> allMultiPathEntries = executeCommand(new ListMultiPathEntriesCommand(), SHORT_TIMEOUT);
        
        for (VolumeSpec volume : volumes) {
            volume.multipathEntries = Lists.newArrayList();
            String device = volume.mountPoint.getDevice();
            if (StringUtils.startsWith(device, "/dev/mapper/")) {
                String deviceName = StringUtils.substringAfterLast(device, "/");
                for (MultiPathEntry entry : allMultiPathEntries) {
                    if (StringUtils.equals(deviceName, entry.getName())) {
                        volume.multipathEntries.add(entry);
                        break;
                    }
                }
            }
            if (volume.multipathEntries.size() == 0) {
                warn("FindMultiPathEntriesForMountPoint.noMultipathEntries", volume.mountPoint.getPath());
            }
        }
        return null;
        
    }

}
