/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.linux.tasks;

import java.util.List;

import org.apache.commons.lang.StringUtils;

import com.emc.sa.service.linux.UnmountBlockVolumeHelper.VolumeSpec;
import com.google.common.collect.Lists;
import com.iwave.ext.linux.command.powerpath.PowerPathInquiry;
import com.iwave.ext.linux.model.PowerPathDevice;

public class FindPowerPathEntriesForMountPoint extends LinuxExecutionTask<Void> {

	private final List<VolumeSpec> volumes;
	
	public FindPowerPathEntriesForMountPoint(List<VolumeSpec> volumes) {
		this.volumes = volumes;
	}
	
	@Override
	public Void executeTask() throws Exception {
		List<PowerPathDevice> powerPathDevices = executeCommand(new PowerPathInquiry());
		
		for (VolumeSpec volume : volumes) {
		    volume.powerpathDevices = Lists.newArrayList();
		    String device = volume.mountPoint.getDevice();
		    for (PowerPathDevice powerpathDevice : powerPathDevices) {
		        logDebug("FindPowerPathEntriesForMountPoint.checking", powerpathDevice.getDeviceName(), powerpathDevice.getDevice(), device);
		        if (StringUtils.equals(device, powerpathDevice.getDevice())) {
		            volume.powerpathDevices.add(powerpathDevice);
		            break;
                }
		    }
		    if (volume.powerpathDevices.size() == 0) {
		        logWarn("FindPowerPathEntriesForMountPoint.noDevices", volume.mountPoint.getPath());
		    }
		}
		return null;
	}

}
