/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vmware.tasks;

import javax.inject.Inject;

import com.iwave.ext.vmware.VCenterAPI;
import com.iwave.ext.vmware.VMWareException;
import com.vmware.vim25.mo.Datastore;

public class FindDatastore extends RetryableTask<Datastore> {
    @Inject
    private VCenterAPI vcenter;
    private String datacenterName;
    private String datastoreName;

    public FindDatastore(String datacenterName, String datastoreName) {
        this.datacenterName = datacenterName;
        this.datastoreName = datastoreName;
        provideDetailArgs(datastoreName, datacenterName);
    }

    @Override
    protected Datastore tryExecute() {
        debug("Executing: %s", getDetail());
        Datastore datastore = vcenter.findDatastore(datacenterName, datastoreName);
        if (datastore == null) {
            throw new VMWareException(
                    String.format("Unable to find datastore %s in datacenter %s", datastoreName, datacenterName));
        }
        return datastore;
    }

    @Override
    protected boolean canRetry(VMWareException e) {
        return true;
    }
}
