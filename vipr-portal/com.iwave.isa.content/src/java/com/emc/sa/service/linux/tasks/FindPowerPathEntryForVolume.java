/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.linux.tasks;

import java.util.List;

import com.emc.storageos.model.block.BlockObjectRestRep;
import com.iwave.ext.linux.command.powerpath.PowerPathInquiry;
import com.iwave.ext.linux.command.powerpath.PowerPathInvistaInquiry;
import com.iwave.ext.linux.command.powerpath.PowerPathHDSInquiry;
import com.iwave.ext.linux.model.PowerPathDevice;
import com.iwave.ext.linux.util.VolumeWWNUtils;

public class FindPowerPathEntryForVolume extends LinuxExecutionTask<PowerPathDevice> {

    private BlockObjectRestRep volume;

    public FindPowerPathEntryForVolume(BlockObjectRestRep volume) {
        this.volume = volume;
    }

    @Override
    public PowerPathDevice executeTask() throws Exception {
        PowerPathDevice entry = findPowerPathEntry(volume);
        if (entry == null) {
            throw stateException("FindPowerPathEntryForVolume.illegalState.noEntries", volume.getWwn().toLowerCase());
        }
        logInfo("find.powerpath.wwn", entry);
        return entry;
    }

    private PowerPathDevice findPowerPathEntry(BlockObjectRestRep blockVolume) {
        List<PowerPathDevice> entries = executeCommand(new PowerPathInquiry(), SHORT_TIMEOUT);
        for (PowerPathDevice device : entries) {
            String deviceWwn = device.getWwn();
            logDebug("FindPowerPathEntryForVolume.checking", device.getDevice(), deviceWwn, blockVolume.getWwn());
            if (VolumeWWNUtils.wwnMatches(deviceWwn, blockVolume)) {
                return device;
            }
        }

        entries = executeCommand(new PowerPathInvistaInquiry(), SHORT_TIMEOUT);
        for (PowerPathDevice device : entries) {
            String deviceWwn = device.getWwn();
            logDebug("FindPowerPathEntryForVolume.checking", device.getDevice(), deviceWwn, blockVolume.getWwn());
            if (VolumeWWNUtils.wwnMatches(deviceWwn, blockVolume)) {
                return device;
            }
        }

        entries = executeCommand(new PowerPathHDSInquiry(), SHORT_TIMEOUT);
        for (PowerPathDevice device : entries) {
            String deviceWwn = device.getWwn();
            logDebug("FindPowerPathEntryForVolume.checking", device.getDevice(), deviceWwn, blockVolume.getWwn());
            if (VolumeWWNUtils.wwnHDSMatches(deviceWwn, blockVolume)) {
                return device;
            }
        }

        logDebug("FindMultiPathEntryForVolume.noEntries", blockVolume.getWwn());
        return null;
    }

}
