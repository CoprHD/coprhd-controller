/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.windows.tasks;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

import com.emc.storageos.model.block.BlockObjectRestRep;
import com.iwave.ext.windows.model.wmi.DiskDrive;

public class DiscoverDisksForVolumes extends FindDisksForVolumes {
    private int retryAttempts;
    private long retryDelay;

    public DiscoverDisksForVolumes(Collection<? extends BlockObjectRestRep> volumes, int retryAttempts, long retryDelay) {
        super(volumes);
        this.retryAttempts = retryAttempts;
        this.retryDelay = retryDelay;
        provideDetailArgs(getVolumesDisplay(volumes));
    }

    @Override
    public Map<BlockObjectRestRep, DiskDrive> executeTask() throws Exception {
        Map<BlockObjectRestRep, DiskDrive> results = null;
        try {
            for (int i = 0; i < retryAttempts; i++) {
                results = findDisksForVolumes();
                if (!isMissingVolumes(results)) {
                    break;
                }
                Thread.sleep(retryDelay);
            }
        }
        catch (InterruptedException e) {
            // Will fail with missing disk drive exception
        }
        failIfMissingVolumes(results);
        return results;
    }
    

    public static String getVolumesDisplay(Collection<? extends BlockObjectRestRep> volumes) {
        StringBuilder sb = new StringBuilder();
        Iterator<? extends BlockObjectRestRep> i = volumes.iterator();
        while(i.hasNext()) {
            BlockObjectRestRep volume = i.next();
            sb.append(volume.getId());
            if (i.hasNext()) {
                sb.append(", ");
            }
        }
        return sb.toString();
    }
    
}
