/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.service.vmware.tasks;

import com.iwave.ext.vmware.HostStorageAPI;
import com.iwave.ext.vmware.VMWareException;
import com.vmware.vim25.ResourceInUse;
import com.vmware.vim25.mo.HostSystem;

public class UnmountVmfsVolume extends RetryableTask<Void> {

    private final String vmfsUuid;

    private final HostSystem host;

    public UnmountVmfsVolume(HostSystem host, String vmfsUuid) {
        this.vmfsUuid = vmfsUuid;
        this.host = host;
    }

    @Override
    protected Void tryExecute() {
        HostStorageAPI storageAPI = new HostStorageAPI(host);
        storageAPI.unmountVmfsVolume(this.vmfsUuid);
        return null;
    }

    @Override
    protected boolean canRetry(VMWareException e) {
        return e.getCause() instanceof ResourceInUse;
    }
}
