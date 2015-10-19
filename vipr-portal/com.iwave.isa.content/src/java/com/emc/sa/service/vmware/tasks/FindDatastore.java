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
    private final String datacenterName;
    private final String datastoreName;

    private static long SECONDS = 1000;
    private static long MINUTES = 60 * SECONDS;
    private static long FIND_DATASTORE_TIMEOUT = 5 * MINUTES;
    private static long DELAY = 5 * SECONDS;

    public FindDatastore(String datacenterName, String datastoreName) {
        this.datacenterName = datacenterName;
        this.datastoreName = datastoreName;
        provideDetailArgs(datastoreName, datacenterName);
    }

    private boolean canRetry(long start, long length) {
        long timeout = start + length;
        return System.currentTimeMillis() < timeout;
    }

    @Override
    public Datastore executeTask() throws Exception {
        debug("Executing: %s", getDetail());
        Datastore datastore = null;
        long startTime = System.currentTimeMillis();

        while ((datastore == null) && canRetry(startTime, FIND_DATASTORE_TIMEOUT)) {
            pause(DELAY);
            datastore = vcenter.findDatastore(datacenterName, datastoreName);
        }

        if (datastore == null) {
            throw stateException("FindDatastore.illegalState.noDatastore",
                    datacenterName, vcenter.getAboutInfo().getFullName(), datastoreName);
        }
        return datastore;
    }

    private void pause(long delay) {
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            warn(e);
            Thread.currentThread().interrupt();
        }
    }
}
