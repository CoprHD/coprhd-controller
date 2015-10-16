/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.service.vmware.block.tasks;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang.StringUtils;

import com.emc.sa.engine.ExecutionTask;
import com.emc.storageos.services.util.Strings;
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
        	info("Detaching Scsi Lun : %s", disk.getCanonicalName());
        	host.getHostStorageSystem().detachScsiLun(disk.getUuid());
        	logInfo("detach.host.scsi.lun", disk.getDeviceName(), host.getName());
        }
    }
}
