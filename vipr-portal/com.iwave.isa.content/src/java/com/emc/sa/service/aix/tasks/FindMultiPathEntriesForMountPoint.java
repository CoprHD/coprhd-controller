/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.aix.tasks;

import java.util.List;

import org.apache.commons.lang.StringUtils;

import com.emc.aix.command.MultiPathInquiry;
import com.emc.sa.service.aix.UnmountBlockVolumeHelper.VolumeSpec;
import com.google.common.collect.Lists;
import com.emc.aix.model.MultiPathDevice;

public class FindMultiPathEntriesForMountPoint extends AixExecutionTask<Void> {

    private final List<VolumeSpec> volumes;
    
    public FindMultiPathEntriesForMountPoint(List<VolumeSpec> volumes) {
        setName("FindMultiPathEntriesForMountPoint.name");
        this.volumes = volumes;
    }
    
    @Override
    public Void executeTask() throws Exception {
        List<MultiPathDevice> multiPathDevices = executeCommand(new MultiPathInquiry());
        
        for (VolumeSpec volume : volumes) {
            volume.multipathEntries = Lists.newArrayList();
            String device = volume.mountPoint.getDevice();
            for (MultiPathDevice multiPathDevice : multiPathDevices) {
                if (StringUtils.equals(device, multiPathDevice.getDevice())) {
                    volume.multipathEntries.add(multiPathDevice);
                    break;
                }
            }
            if (volume.multipathEntries.size() == 0) {
                warn("No MultiPath devices for %s", volume.mountPoint.getPath());
            }
        }
        return null;
    }

}
