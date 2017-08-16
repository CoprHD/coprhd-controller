/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.hds.prov.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.hds.HDSException;
import com.emc.storageos.hds.api.HDSApiFactory;
import com.emc.storageos.volumecontroller.JobContext;
import com.emc.storageos.volumecontroller.impl.JobPollResult;
import com.emc.storageos.volumecontroller.impl.hds.prov.job.HDSJob;

public class HDSCommandHelper {
    private static final Logger log = LoggerFactory.getLogger(HDSCommandHelper.class);
    private DbClient dbClient = null;
    private HDSApiFactory hdsApiFactory;
    private long trackingPeriodInMillis;

    /**
     * wait for hds async job.
     * 
     * @param job {@link HDSJob}
     */
    public void waitForAsyncHDSJob(HDSJob job) throws HDSException {

        JobContext jobContext = new JobContext(dbClient, null, null, hdsApiFactory, null, null, null, null);
        long startTime = System.currentTimeMillis();
        while (true) {
            JobPollResult result = job.poll(jobContext, trackingPeriodInMillis);
            if (!job.isJobInTerminalState()) {
                if (System.currentTimeMillis() - startTime > HDSJob.ERROR_TRACKING_LIMIT) {
                    log.error("Timed out waiting on hds job to complete after {} milliseconds", System.currentTimeMillis() - startTime);
                    throw HDSException.exceptions.asyncTaskFailedTimeout(HDSJob.ERROR_TRACKING_LIMIT);
                } else {
                    try {
                        Thread.sleep(trackingPeriodInMillis);
                    } catch (InterruptedException e) {
                        log.error("Thread waiting for hds job to complete was interrupted and "
                                + "will be resumed");
                    }
                }
            } else {
                if (job.isJobInTerminalFailedState()) {
                    throw HDSException.exceptions.asyncTaskFailed(result.getErrorDescription());
                }
                break;
            }
        }
    }

    public void setDbClient(DbClient dbClient) {
        this.dbClient = dbClient;
    }

    public void setHdsApiFactory(HDSApiFactory hdsApiFactory) {
        this.hdsApiFactory = hdsApiFactory;
    }

    public void setTrackingPeriodInMillis(long trackingPeriodInMillis) {
        this.trackingPeriodInMillis = trackingPeriodInMillis;
    }

}
