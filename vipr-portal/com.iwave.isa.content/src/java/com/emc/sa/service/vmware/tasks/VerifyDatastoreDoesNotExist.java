/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vmware.tasks;

import javax.inject.Inject;

import com.emc.sa.engine.ExecutionTask;
import com.iwave.ext.vmware.VCenterAPI;
import com.vmware.vim25.mo.Datastore;

public class VerifyDatastoreDoesNotExist extends ExecutionTask<Void> {
    @Inject
    private VCenterAPI vcenter;
    private String datacenterName;
    private String datastoreName;

    public VerifyDatastoreDoesNotExist(String datacenterName, String datastoreName) {
        this.datacenterName = datacenterName;
        this.datastoreName = datastoreName;
        provideDetailArgs(datastoreName, datacenterName);
    }

    @Override
    public void execute() throws Exception {
        Datastore ds = vcenter.findDatastore(datacenterName, datastoreName);
        if (ds != null) {
            throw stateException("VerifyDatastoreDoesNotExist.illegalState.alreadyExists", datastoreName, datacenterName);
        }
    }
}
