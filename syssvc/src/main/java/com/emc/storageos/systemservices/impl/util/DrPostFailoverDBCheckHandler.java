/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.systemservices.impl.util;

import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.client.model.Constants;
import com.emc.storageos.coordinator.client.model.DbConsistencyStatus;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.coordinator.client.service.DrPostFailoverHandler;
import com.emc.storageos.model.db.DbConsistencyStatusRestRep;
import com.emc.storageos.systemservices.impl.jobs.DbConsistencyJob;
import com.emc.storageos.systemservices.impl.jobs.common.JobProducer;

/**
 * Db scan after failover. We need check db index/object CF inconsistencies
 *
 */
public class DrPostFailoverDBCheckHandler extends DrPostFailoverHandler {
    private static final Logger log = LoggerFactory.getLogger(JobProducer.class);
    private JobProducer jobProducer;
    private CoordinatorClient coordinator;
    
    public CoordinatorClient getCoordinator() {
        return coordinator;
    }

    public void setCoordinator(CoordinatorClient coordinator) {
        this.coordinator = coordinator;
    }

    public JobProducer getJobProducer() {
        return jobProducer;
    }
    
    public void setJobProducer(JobProducer jobProducer) {
        this.jobProducer = jobProducer;
    }

    @Override
    protected void execute() {
        if (isDbConsistencyCheckInProgress()) {
            log.warn("DB consistency check is in progress");
            return;
        }
        
        DbConsistencyJob job = new DbConsistencyJob();
        job.setStatus(DbConsistencyStatusRestRep.Status.NOT_STARTED);
        job.setStartTime(new Date());
        jobProducer.enqueue(job);
        log.info("DB consistency job has been added to queue");
    }
    
    private boolean isDbConsistencyCheckInProgress() {
        DbConsistencyStatus state = coordinator.queryRuntimeState(Constants.DB_CONSISTENCY_STATUS,  DbConsistencyStatus.class);
        return state!=null && state.getStatus()==DbConsistencyStatusRestRep.Status.IN_PROGRESS;
    }
    
}
