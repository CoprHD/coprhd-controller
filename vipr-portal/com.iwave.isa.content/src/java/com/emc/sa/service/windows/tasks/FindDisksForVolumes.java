/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.windows.tasks;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.text.StrBuilder;

import com.emc.storageos.model.block.BlockObjectRestRep;
import com.google.common.collect.Maps;
import com.iwave.ext.linux.util.VolumeWWNUtils;
import com.iwave.ext.windows.model.wmi.DiskDrive;
import com.iwave.ext.windows.winrm.WinRMException;

public class FindDisksForVolumes extends WindowsExecutionTask<Map<BlockObjectRestRep, DiskDrive>> {
    private final Collection<? extends BlockObjectRestRep> volumes;

    public FindDisksForVolumes(Collection<? extends BlockObjectRestRep> volumes) {
        this.volumes = volumes;
        provideDetailArgs(getVolumesDisplay(volumes));
    }

    private String getVolumesDisplay(Collection<? extends BlockObjectRestRep> volumes) {
        StringBuilder sb = new StringBuilder();
        Iterator<? extends BlockObjectRestRep> v = volumes.iterator();
        while (v.hasNext()) {
            sb.append(v.next().getId());
            if (v.hasNext()) {
                sb.append(", ");
            }
        }
        return sb.toString();
    }

    @Override
    public Map<BlockObjectRestRep, DiskDrive> executeTask() throws Exception {
        Map<BlockObjectRestRep, DiskDrive> results = findDisksForVolumes();
        failIfMissingVolumes(results);
        return results;
    }

    protected Map<BlockObjectRestRep, DiskDrive> findDisksForVolumes() throws WinRMException {
        Map<BlockObjectRestRep, DiskDrive> results = Maps.newHashMap();

        List<DiskDrive> disks = getTargetSystem().listDiskDrives();
        for (DiskDrive disk : disks) {
            String wwid = getTargetSystem().getWwid(disk);
            logDebug("find.disks.volumes.wwid", wwid, disk.getDeviceId());
            for (BlockObjectRestRep volume : volumes) {
                if (VolumeWWNUtils.wwnMatches(wwid, volume.getWwn())) {
                    logInfo("find.disks.volumes.wwid", wwid, disk.getSerialNumber());
                    results.put(volume, disk);
                    break;
                }
            }
        }

        return results;
    }

    protected boolean isMissingVolumes(Map<BlockObjectRestRep, DiskDrive> results) {
        return (results == null) || (results.size() < volumes.size());
    }

    protected void failIfMissingVolumes(Map<BlockObjectRestRep, DiskDrive> results) {
        if (isMissingVolumes(results)) {
            // Build error message
            StrBuilder wwids = new StrBuilder();
            int missingCount = 0;
            for (BlockObjectRestRep volume : volumes) {
                DiskDrive disk = results.get(volume);
                if (disk == null) {
                    wwids.appendSeparator(", ");
                    wwids.append(volume.getWwn());
                    missingCount++;
                }
            }
            if (missingCount > 1) {
                throw stateException("illegalState.FindDisksForVolumes.noVolumes", wwids);
            }
            else {
                throw stateException("illegalState.FindDisksForVolumes.noVolume", wwids);
            }
        }
    }
}
