/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vmware.block.tasks;

import com.emc.sa.service.vmware.tasks.RetryableTask;
import com.iwave.ext.vmware.HostStorageAPI;
import com.iwave.ext.vmware.VMWareException;
import com.iwave.ext.vmware.VMwareUtils;
import com.vmware.vim25.HostConfigFault;
import com.vmware.vim25.HostScsiDisk;
import com.vmware.vim25.HostVmfsVolume;
import com.vmware.vim25.mo.Datastore;
import com.vmware.vim25.mo.HostSystem;

public class ExpandVmfsDatastore extends RetryableTask<Void> {
    private HostScsiDisk disk;
    private Datastore datastore;
    private HostStorageAPI hostStorageAPI;

    public ExpandVmfsDatastore(HostSystem host, HostScsiDisk disk, Datastore datastore) {
        this.disk = disk;
        this.datastore = datastore;
        this.hostStorageAPI = new HostStorageAPI(host);
        provideDetailArgs(datastore.getName(), host.getName(), disk.getDisplayName());
        provideNameArgs(datastore.getName());
        setDelay(30000);
    }

    @Override
    protected Void tryExecute() {
        expandVmfsDatastore();
        return null;
    }

    @Override
    protected boolean canRetry(VMWareException e) {
        return (e.getCause() instanceof HostConfigFault);
    }

    protected void expandVmfsDatastore() {
        HostVmfsVolume vmfs = VMwareUtils.getHostVmfsVolume(datastore);
        if (vmfs != null) {
            logInfo("expand.vmfs.datastore", vmfs.getVersion());
        }
        hostStorageAPI.expandVmfsDatastore(disk, datastore);
    }
}
