/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vmware.block.tasks;

import java.util.List;

import org.apache.commons.lang.StringUtils;

import com.emc.sa.engine.ExecutionTask;
import com.emc.sa.engine.ExecutionUtils;
import com.emc.storageos.model.block.BlockObjectRestRep;
import com.iwave.ext.linux.util.VolumeWWNUtils;
import com.iwave.ext.vmware.HostStorageAPI;
import com.iwave.ext.vmware.VMwareUtils;
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
    private boolean availableDiskOnly = false;
    // By default, fail if the lun isn't found. Some other operations (like validate boot volume before delete)
    // would like to control the failure handling.
    private boolean throwIfNotFound = true;

    /**
     * Finds the SCSI disk on the host system that matches the volume.
     * 
     * @param host the host system
     * @param volume the volume to find
     */
    public FindHostScsiDiskForLun(HostSystem host, BlockObjectRestRep volume) {
        this.host = host;
        this.volume = volume;
        this.storageAPI = new HostStorageAPI(host);
        this.lunDiskName = VMwareUtils.CANONICAL_NAME_PREFIX + StringUtils.lowerCase(volume.getWwn());
        provideDetailArgs(host.getName(), lunDiskName);
    }

    /**
     * Finds the SCSI disk on the host system that matches the volume.
     * 
     * @param host the host system
     * @param volume the volume to find
     * @param availableDiskOnly if true, only find available disk for VMFS. if false, find disk even if it's not available for VMFS.
     */
    public FindHostScsiDiskForLun(HostSystem host, BlockObjectRestRep volume, boolean availableDiskOnly) {
        this(host, volume);
        this.availableDiskOnly = availableDiskOnly;
    }

    /**
     * Finds the SCSI disk on the host system that matches the volume.
     * 
     * @param host
     *            the host system
     * @param volume
     *            the volume to find
     * @param availableDiskOnly
     *            if true, only find available disk for VMFS. if false, find disk even if it's not available for VMFS.
     * @param throwIfNotFound
     *            throws an exception if the lun is not found. (defaults to true)
     */
    public FindHostScsiDiskForLun(HostSystem host, BlockObjectRestRep volume, boolean availableDiskOnly, boolean throwIfNotFound) {
        this(host, volume, availableDiskOnly);
        this.throwIfNotFound = throwIfNotFound;
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
        HostScsiDisk lun = getLunDisk();
        long startTime = System.currentTimeMillis();

        while ((lun == null) && canRetry(startTime, FIND_DISK_TIMEOUT)) {
            rescan();
            lun = getLunDisk();
        }

        if (lun == null) {
            diskNotFound(true);
        }
        logInfo("find.host.scsi.lun", lunDiskName);
        return lun;
    }

    private HostScsiDisk getLunDisk() {

        List<HostScsiDisk> scsiDisks = storageAPI.listScsiDisks();

        // List all disks and attach the disk if it is found
        for (HostScsiDisk entry : scsiDisks) {
            if (VolumeWWNUtils.wwnMatches(VMwareUtils.getDiskWwn(entry), volume.getWwn()) && VMwareUtils.isDiskOff(entry)) {
                attachDisk(entry);
            }
        }

        if (availableDiskOnly) {
            scsiDisks = storageAPI.queryAvailableDisksForVmfs(null);
        } else {
            scsiDisks = storageAPI.listScsiDisks();
        }

        for (HostScsiDisk entry : scsiDisks) {
            if (VolumeWWNUtils.wwnMatches(VMwareUtils.getDiskWwn(entry), volume.getWwn())) {
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
        logInfo("find.host.scsi.lun.esx.attach", lunDiskName, host.getName());
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
        logInfo("find.host.scsi.lun.esx.wait.valid", lunDiskName, host.getName());
        long startTime = System.currentTimeMillis();
        while ((disk == null || !isValidState(disk)) && canRetry(startTime, VALID_STATE_TIMEOUT)) {
            pause(VALID_STATE_DELAY);
            disk = getLunDisk();
            if (disk == null) {
                diskNotFound(false);
            } else if (VMwareUtils.isDiskOff(disk)) {
                attachDisk(disk);
            }
        }
        if (disk == null) {
            diskNotFound(true);
        } else if (!isValidState(disk)) {
            diskInvalid(disk);
        }
        return disk;
    }

    private boolean isValidState(HostScsiDisk disk) {
        String[] state = disk.getOperationalState();
        if (state == null || state.length == 0) {
            return false;
        }
        String primaryState = state[0];
        if (StringUtils.equals(primaryState, ScsiLunState.ok.name())
                || StringUtils.equals(primaryState, ScsiLunState.degraded.name())) {
            logInfo("find.host.scsi.lun.esx.valid", lunDiskName, host.getName(),
                    StringUtils.join(state, ", "));
            return true;
        } else {
            logInfo("find.host.scsi.lun.esx.invalid", lunDiskName, host.getName(),
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

    private void diskNotFound(boolean fail) {
        if (fail && this.throwIfNotFound) {
            if (availableDiskOnly) {
                throw stateException("FindHostScsiDiskForLun.illegalState.diskNotFoundCheckDatastoreRDM", lunDiskName, host.getName());
            } else {
                throw stateException("FindHostScsiDiskForLun.illegalState.diskNotFound", lunDiskName, host.getName());
            }
        } else {
            logInfo("FindHostScsiDiskForLun.illegalState.diskNotFound", lunDiskName, host.getName());
        }
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
