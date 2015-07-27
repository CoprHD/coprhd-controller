/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vmware.tasks;

import javax.inject.Inject;

import com.iwave.ext.vmware.VCenterAPI;
import com.vmware.vim25.StoragePlacementResult;
import com.vmware.vim25.mo.Datastore;
import com.vmware.vim25.mo.Task;

public class EnterMaintenanceMode extends VMwareTask<Void> {
    @Inject
    private VCenterAPI vcenter;
    private Datastore datastore;

    public EnterMaintenanceMode(Datastore datastore) {
        this.datastore = datastore;
		provideDetailArgs(datastore.getName());
    }

    @Override
    public void execute() throws Exception {
        StoragePlacementResult result = datastore.datastoreEnterMaintenanceMode();
        Task task = vcenter.lookupManagedObject(result.getTask());
        try {
            waitForTask(task);
        }
        catch (Exception e) {
            cancelTask(task);
            throw e;
        }
    }
}
