/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vmware.block.tasks;

import javax.inject.Inject;

import com.emc.sa.service.vmware.tasks.VMwareTask;
import com.iwave.ext.vmware.VCenterAPI;
import com.vmware.vim25.StorageIORMConfigSpec;
import com.vmware.vim25.mo.Datastore;
import com.vmware.vim25.mo.StorageResourceManager;
import com.vmware.vim25.mo.Task;

public class SetStorageIOControl extends VMwareTask<Void> {
    @Inject
    private VCenterAPI vcenter;
    private Datastore datastore;
    private boolean enabled;

    public SetStorageIOControl(Datastore datastore, boolean enabled) {
        this.datastore = datastore;
        this.enabled = enabled;
        if (enabled) {
            setDetail("SetStorageIOControl.detail.enable", datastore.getName());
        } else {
            setDetail("SetStorageIOControl.detail.disable", datastore.getName());
        }
    }

    @Override
    public void execute() throws Exception {
        debug("Executing: %s", getDetail());
        StorageResourceManager manager = vcenter.getStorageResourceManager();
        StorageIORMConfigSpec spec = new StorageIORMConfigSpec();
        spec.setEnabled(enabled);

        Task task = null;
        try {
            task = manager.configureDatastoreIORM_Task(datastore, spec);
            waitForTask(task);
        } catch (Exception e) {
            logError("SetStorageIOControl.detail.error", datastore.getName());
            cancelTaskNoException(task);
        }
    }
}