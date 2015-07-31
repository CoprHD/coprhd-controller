/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.aix.tasks;

import java.util.List;

import org.apache.commons.lang.StringUtils;

import com.emc.aix.command.PowerPathInquiry;
import com.emc.sa.service.aix.UnmountBlockVolumeHelper.VolumeSpec;
import com.google.common.collect.Lists;
import com.iwave.ext.linux.model.PowerPathDevice;

public class FindPowerPathEntriesForMountPoint extends AixExecutionTask<Void> {

    private final List<VolumeSpec> volumes;

    public FindPowerPathEntriesForMountPoint(List<VolumeSpec> volumes) {
        setName("FindPowerPathEntriesForMountPoint.name");
        this.volumes = volumes;
    }

    @Override
    public Void executeTask() throws Exception {
        List<PowerPathDevice> powerPathDevices = executeCommand(new PowerPathInquiry());

        for (VolumeSpec volume : volumes) {
            volume.powerpathDevices = Lists.newArrayList();
            String device = volume.mountPoint.getDevice();
            for (PowerPathDevice powerpathDevice : powerPathDevices) {
                if (StringUtils.equals(device, powerpathDevice.getDevice())) {
                    volume.powerpathDevices.add(powerpathDevice);
                    break;
                }
            }
            if (volume.powerpathDevices.size() == 0) {
                warn("No PowerPath devices for %s", volume.mountPoint.getPath());
            }
        }
        return null;
    }

}
