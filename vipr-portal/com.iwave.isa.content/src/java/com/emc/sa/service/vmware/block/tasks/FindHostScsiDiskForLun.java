/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vmware.block.tasks;

import com.emc.sa.util.VolumeWWNUtils;
import com.emc.storageos.model.block.BlockObjectRestRep;
import org.apache.commons.lang.StringUtils;

import com.emc.sa.engine.ExecutionTask;
import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.service.vmware.VMwareUtils;
import com.iwave.ext.vmware.HostStorageAPI;
import com.vmware.vim25.HostScsiDisk;
import com.vmware.vim25.ScsiLunState;
import com.vmware.vim25.mo.HostSystem;

public class FindHostScsiDiskForLun extends ExecutionTask<HostScsiDisk> {
    private static long SECONDS = 1000;
    private static long MINUTES = 60 * SECONDS;
    private static long FIND_DISK_TIMEOUT = 10 * MINUTES; // 10 Minutes
    private static long VALID_STATE_TIMEOUT = 20 * MINUTES; // 20 Minutes
    private static long FIND_DISK_DELAY = 15 * SECONDS; // 15 Seconds
    private static long VALID_STATE_DELAY = 5 * SECONDS; // 5 Seconds

    private HostSystem host;
    private String lunDiskName;
    private HostStorageAPI storageAPI;
    private BlockObjectRestRep volume;

    public FindHostScsiDiskForLun(HostSystem host, BlockObjectRestRep volume) {
        this.host = host;
        this.volume = volume;
        this.storageAPI = new HostStorageAPI(host);
        this.lunDiskName = VMwareUtils.CANONICAL_NAME_PREFIX + StringUtils.lowerCase(volume.getWwn());
        provideDetailArgs(host.getName(), lunDiskName);
    }

    @Override
    public HostScsiDisk executeTask() throws Exception {
        HostScsiDisk lun = findLun();
        lun = waitForValidState(lun);
        return lun;
    }

    private boolean canRetry(long start, long length) {
        long timeout = start + length;
        return System.currentTimeMillis() < timeout;
    }

    private HostScsiDisk findLun() {
        HostScsiDisk lun = null;
        long startTime = System.currentTimeMillis();

        while ((lun == null) && canRetry(startTime, FIND_DISK_TIMEOUT)) {
            rescan();
            lun = getLunDisk();
        }

        if (lun == null) {
            diskNotFound();
        }
        logInfo("find.host.scsi.lun", lun.getDeviceName());
        return lun;
    }

    private HostScsiDisk getLunDisk() {
        for (HostScsiDisk entry : storageAPI.listScsiDisks()) {
            if (VolumeWWNUtils.wwnMatches(VMwareUtils.getDiskWwn(entry), volume)) {
                return entry;
            }
        }
        return null;
    }

    /**
     * Attaches the scsi disk to the host
     * 
     * @param disk the scsi disk to attach
     */
    private void attachDisk(HostScsiDisk disk) {
        logInfo("find.host.scsi.lun.esx.attach", disk.getDeviceName(), host.getName());
        new HostStorageAPI(host).attachScsiLun(disk);
    }

    private void rescan() {
        pause(FIND_DISK_DELAY);
        logInfo("find.host.scsi.lun.esx.rescan", host.getName());
        new HostStorageAPI(host).rescanHBAs();
    }

    /**
     * Waits for a valid state of the given scsi disk.
     * If the disk is in an 'off' state, the disk is attached to the host.
     * 
     * @param disk the scsi disk to monitor
     * @return the scsi disk once it is in a valid state
     */
    private HostScsiDisk waitForValidState(HostScsiDisk disk) {
        logInfo("find.host.scsi.lun.esx.wait.valid", disk.getDeviceName(), host.getName());
        long startTime = System.currentTimeMillis();
        while (!isValidState(disk) && canRetry(startTime, VALID_STATE_TIMEOUT)) {
            pause(VALID_STATE_DELAY);
            disk = getLunDisk();
            if (disk == null) {
                diskNotFound();
            } else if (isDiskOff(disk)) {
                attachDisk(disk);
            }
        }
        if (!isValidState(disk)) {
            diskInvalid(disk);
        }
        return disk;
    }

    /**
     * Returns true if the disk operational state is 'off'
     * 
     * @param disk the scsi disk
     * @return true if the disk operational state is 'off', otherwise returns false
     */
    private boolean isDiskOff(HostScsiDisk disk) {
        String[] state = disk.getOperationalState();
        if (state == null || state.length == 0) {
            return false;
        }
        String primaryState = state[0];
        return StringUtils.equals(primaryState, ScsiLunState.off.name());
    }

    private boolean isValidState(HostScsiDisk disk) {
        String[] state = disk.getOperationalState();
        if (state == null || state.length == 0) {
            return false;
        }
        String primaryState = state[0];
        if (StringUtils.equals(primaryState, ScsiLunState.ok.name())
                || StringUtils.equals(primaryState, ScsiLunState.degraded.name())) {
            logInfo("find.host.scsi.lun.esx.valid", disk.getDeviceName(), host.getName(),
                    StringUtils.join(state, ", "));
            return true;
        }
        else {
            logInfo("find.host.scsi.lun.esx.invalid", disk.getDeviceName(), host.getName(),
                    StringUtils.join(state, ", "));
            return false;
        }
    }

    private void pause(long delay) {
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            warn(e);
            Thread.currentThread().interrupt();
        }
    }

    private void diskNotFound() {
        throw stateException("FindHostScsiDiskForLun.illegalState.diskNotFound", lunDiskName, host.getName());
    }

    private void diskInvalid(HostScsiDisk disk) {
        String name = StringUtils.defaultIfBlank(disk.getDisplayName(), lunDiskName);
        String state = displayState(disk);
        throw stateException("FindHostScsiDiskForLun.illegalState.invalidState", name, host.getName(), state);
    }

    private String displayState(HostScsiDisk disk) {
        try {
            String[] state = disk.getOperationalState();
            if (state == null || state.length == 0) {
                return ExecutionUtils.getMessage("FindHostScsiDiskForLun.unknownState");
            }
            return StringUtils.join(state, ", ");
        } catch (RuntimeException e) {
            return ExecutionUtils.getMessage("FindHostScsiDiskForLun.unknownState");
        }
    }
}
