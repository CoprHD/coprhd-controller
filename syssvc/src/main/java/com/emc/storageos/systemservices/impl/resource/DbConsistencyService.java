/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.systemservices.impl.resource;

import java.util.Date;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.emc.storageos.coordinator.client.model.Constants;
import com.emc.storageos.coordinator.client.model.DbConsistencyStatus;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.model.db.DbConsistencyStatusRestRep;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.systemservices.impl.jobs.DbConsistencyJob;
import com.emc.storageos.systemservices.impl.jobs.common.JobProducer;


/**
 * Db consistency service is used to trigger db consistency checker and
 * query db consistency status
 */
@Path("/control/db/")
public class DbConsistencyService {
    private static final Logger log = LoggerFactory.getLogger(DbConsistencyService.class);
    private CoordinatorClient coordinator;
    private JobProducer jobProducer;

    @POST
    @Path("consistency")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    public Response checkDbConsistency() {
        log.info("receive db consistency check request");
        if (isDbConsistencyCheckInProgress()) {
            log.warn("db consistency check is already in progress");
            throw APIException.badRequests.dbConsistencyCheckAlreadyProgress();
        }
        
        enqueueDbConsistencyJob();
        log.info("enqueue job into queue successfully");
        return Response.ok().build();
    }

    @GET
    @Path("consistency")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public DbConsistencyStatusRestRep getDbConsistencyStatus() {
        DbConsistencyStatus status = getStatusFromZk();
        log.info("db consistency check status {}", status);
        return toStatusRestRep(status);
    }
    
    @POST
    @Path("consistency/cancel")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    public Response cancelDbConsistencyCheck() {
        log.info("receive cancel db consistency check request");
        if (!isDbConsistencyCheckInProgress()) {
            log.warn("db consistency check is not in progress");
            throw APIException.badRequests.canNotCanceldbConsistencyCheck();
        }
        DbConsistencyStatus status = getStatusFromZk();
        status.setStatus(DbConsistencyStatusRestRep.Status.CANCEL);
        this.coordinator.persistRuntimeState(Constants.DB_CONSISTENCY_STATUS, status);
        log.info("try to cancel db consistency check");
        return Response.ok().build();
    }

    private DbConsistencyStatusRestRep toStatusRestRep(DbConsistencyStatus status) {
        DbConsistencyStatusRestRep statusRestRep = new DbConsistencyStatusRestRep();
        if (status != null) {
            statusRestRep.setStatus(status.getStatus());
            statusRestRep.setStartTime(status.getStartTime());
            statusRestRep.setEndTime(status.getEndTime());
            statusRestRep.setProgress(status.getProgress());
            statusRestRep.setWorkingPoint(status.getWorkingPoint());
        } else {
            statusRestRep.setStatus(DbConsistencyStatusRestRep.Status.NOT_STARTED);
        }
        return statusRestRep;
    }

    private DbConsistencyStatus getStatusFromZk() {
        return this.coordinator.queryRuntimeState(Constants.DB_CONSISTENCY_STATUS,  DbConsistencyStatus.class);
    }
    
    
    private void enqueueDbConsistencyJob() {
        DbConsistencyJob job = new DbConsistencyJob();
        job.setStatus(DbConsistencyStatusRestRep.Status.NOT_STARTED);
        job.setStartTime(new Date());
        jobProducer.enqueue(job);
    }

    private boolean isDbConsistencyCheckInProgress() {
        DbConsistencyStatus state = getStatusFromZk();
        return state!=null && state.getStatus()==DbConsistencyStatusRestRep.Status.IN_PROGRESS;
    }

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
}