/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.service.vmware.block.tasks;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang.StringUtils;

import com.emc.sa.engine.ExecutionTask;
import com.iwave.ext.vmware.VMwareUtils;
import com.vmware.vim25.HostScsiDisk;
import com.vmware.vim25.mo.HostSystem;

public class DetachLunsFromHost extends ExecutionTask<Void> {
    private final HostSystem host;
    private final List<HostScsiDisk> disks;

    public DetachLunsFromHost(HostSystem host, List<HostScsiDisk> disks) {
        this.host = host;
        this.disks = disks;
        
        List<String> deviceNames = new ArrayList<>();

        for (HostScsiDisk disk : disks) {
        	deviceNames.add(disk.getDeviceName());
        }
        
        String lunsString = StringUtils.join(deviceNames, ',');
        provideDetailArgs(lunsString, host.getName());
    }

    @Override
    public void execute() throws Exception {
        for (HostScsiDisk disk : disks) {
            if (!VMwareUtils.isDiskOff(disk)) {
                info("Detaching Scsi Lun : %s", disk.getCanonicalName());
                host.getHostStorageSystem().detachScsiLun(disk.getUuid());
                logInfo("detach.host.scsi.lun", disk.getDeviceName(), host.getName());
            } else {
                info("Disk %s is not in a valid state to detach", disk.getCanonicalName());
            }
        }
    }
}
