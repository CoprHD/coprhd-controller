/*
 * Copyright 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.service.vmware.block.tasks;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang.StringUtils;

import com.emc.sa.service.vmware.tasks.RetryableTask;
import com.iwave.ext.vmware.HostStorageAPI;
import com.iwave.ext.vmware.VMWareException;
import com.vmware.vim25.HostScsiDisk;
import com.vmware.vim25.QuiesceDatastoreIOForHAFailed;
import com.vmware.vim25.ResourceInUse;
import com.vmware.vim25.mo.HostSystem;

public class AttachScsiDisk extends RetryableTask<Void> {
    private final HostSystem host;
    private final List<HostScsiDisk> disks;

    public AttachScsiDisk(HostSystem host, List<HostScsiDisk> disks) {
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
    protected Void tryExecute() {
        for (HostScsiDisk disk : disks) {
            new HostStorageAPI(host).attachScsiLun(disk);
        }
        return null;
    }

    @Override
    protected boolean canRetry(VMWareException e) {
        return e.getCause() instanceof QuiesceDatastoreIOForHAFailed || e.getCause() instanceof ResourceInUse;
    }
}
