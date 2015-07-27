/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vmware.tasks;

import javax.inject.Inject;

import com.emc.sa.engine.ExecutionTask;
import com.iwave.ext.vmware.VCenterAPI;
import com.vmware.vim25.mo.Datastore;

public class FindDatastore extends ExecutionTask<Datastore> {
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
    public Datastore executeTask() throws Exception {
        debug("Executing: %s", getDetail());
        Datastore datastore = vcenter.findDatastore(datacenterName, datastoreName);
        if (datastore == null) {
            // TODO: remove the datastore tags?
            throw stateException("FindDatastore.illegalState.noDatastore", 
            		datacenterName, vcenter.getAboutInfo().getFullName(), datastoreName);
        }
        return datastore;
    }
}
