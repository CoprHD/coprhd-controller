/*
 * Copyright (c) 2018 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.systemservices.impl.resource;

import com.emc.storageos.coordinator.client.model.Constants;
import com.emc.storageos.coordinator.client.model.DiagutilJobStatus;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.systemservices.exceptions.SysClientException;
import com.emc.storageos.systemservices.impl.client.SysClientFactory;
import com.emc.storageos.systemservices.impl.jobs.common.JobProducer;
import com.emc.storageos.systemservices.impl.jobs.consumer.DiagutilsJobConsumer;
import com.emc.storageos.systemservices.impl.upgrade.CoordinatorClientExt;
import com.emc.vipr.model.sys.diagutil.DiagutilInfo.*;
import com.emc.vipr.model.sys.diagutil.DiagutilInfo;
import com.emc.vipr.model.sys.diagutil.DiagutilParam;
import com.emc.vipr.model.sys.diagutil.DiagutilsJob;
import org.apache.commons.io.FileUtils;
import org.apache.curator.framework.recipes.locks.InterProcessLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.*;
import java.net.URI;
import java.security.InvalidParameterException;
import java.util.List;

/**
 * Defines the API for making request to diagnostics data collection service.
 */
@Path("/diagutil/")
public class DataCollectionService {
    private static final Logger log = LoggerFactory.getLogger(DataCollectionService.class);

    private JobProducer jobProducer;
    private CoordinatorClientExt _coordinator = null;

    public void setCoordinator(CoordinatorClientExt client) {
        this._coordinator = client;
    }

    public void setJobProducer(JobProducer jobProducer) {
        this.jobProducer = jobProducer;
    }

    @POST
    @CheckPermission(roles = {Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN})
    public Response collectDiagnosticData(@QueryParam("options") List<String> options, DiagutilParam diagutilParam) {
        if (isCollectDiagnosticDataJobRunning()) {
            throw APIException.badRequests.cannotExecuteOperationWhilePendingTask("Collecting Diagnostic Data");
        }
        log.info("query job info: {}",queryJobInfo());

        DiagutilJobStatus jobStatus = new DiagutilJobStatus(Long.toString(System.currentTimeMillis()), null, DiagutilStatus.INITIALIZE, null, null, null, null, diagutilParam.getUploadParam().getUploadType().toString());

        try {
            saveJobInfo(jobStatus);
        } catch (Exception e) {
            log.error("Failed to save job info, e={}", e);
            throw APIException.internalServerErrors.createObjectError("diagutil job", e);
        }
        log.info("diagutil job status {} saved in zk.", jobStatus);
        log.info("paramter is options {}",options);
        
        DiagutilsJob diagutilsJob = new DiagutilsJob();
        diagutilsJob.setOptions(options);
        diagutilsJob.setUploadParam(diagutilParam.getUploadParam());
        diagutilsJob.setLogEnable(diagutilParam.getLogEnable());
        if (diagutilParam.getLogEnable()) {
            diagutilsJob.setLogParam(diagutilParam.getLogParam());
        }
        jobProducer.enqueue(diagutilsJob);
        log.info("Diagutils job has been added to queue");

        return Response.status(Response.Status.ACCEPTED).build();
    }

    public DiagutilJobStatus queryJobInfo() {
        return _coordinator.getCoordinatorClient().queryRuntimeState(Constants.DIAGUTIL_JOB_STATUS, DiagutilJobStatus.class);
    }

    public void saveJobInfo(DiagutilJobStatus status) throws Exception {
        InterProcessLock lock = _coordinator.getCoordinatorClient().getLock(Constants.DIAGUTIL_JOB_LOCK);
        lock.acquire();
        _coordinator.getCoordinatorClient().persistRuntimeState(Constants.DIAGUTIL_JOB_STATUS, status);
        lock.release();
    }

    private boolean isCollectDiagnosticDataJobRunning() {
        DiagutilJobStatus jobStatus = queryJobInfo();
        if (jobStatus == null) {
            return false;
        }
        DiagutilStatus status = jobStatus.getStatus();
        if (DiagutilStatus.PRECHECK_ERROR.equals(status) || DiagutilStatus.COLLECTING_ERROR.equals(status)
                || DiagutilStatus.UPLOADING_ERROR.equals(status) || DiagutilStatus.DOWNLOAD_ERROR.equals(status)
                || DiagutilStatus.UNEXPECTED_ERROR.equals(status)|| DiagutilStatus.COMPLETE.equals(status)) {
            return false;
        }
        return true;
    }

    @GET
    @CheckPermission(roles = {Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN})
    @Produces({MediaType.APPLICATION_OCTET_STREAM})
    public Response getDiagutilData(@QueryParam("node_id") String nodeId, @QueryParam("file_name") String fileName) {
        if (nodeId == null || nodeId.length() == 0) {
            throw APIException.badRequests.parameterIsNotValid("node_id");
        }
        if (fileName == null || fileName.length() == 0) {
            throw APIException.badRequests.parameterIsNotValid("file_name");
        }
        try {
            InputStream is = SysClientFactory.getSysClient(_coordinator.getNodeEndpoint(nodeId)).get(URI.create(String.format(SysClientFactory.URI_INTERNAL_NODE_GET_DIAGUTIL_DATA_TEMPLATE, fileName)), InputStream.class, MediaType.APPLICATION_OCTET_STREAM);
            return Response.ok(is).build();
            //return SysClientFactory.getSysClient(_coordinator.getNodeEndpoint(nodeId)).get(URI.create(INTERNAL_NODE_GET_DIAGUTIL_DATA + "?file_name=" + fileName), Response.class, MediaType.APPLICATION_OCTET_STREAM);
        } catch (SysClientException e) {
            throw APIException.internalServerErrors.sysClientError("get diagutil data");
        }

        //return Response.ok(so).build();
    }

    @GET
    @Path("internal/data")
    @Produces({MediaType.APPLICATION_OCTET_STREAM})
    public Response getNodeDiagutilData(@QueryParam("file_name") String fileName) {

        FileInputStream fi = null;
        String filePath = DiagutilsJobConsumer._DIAGUTIL_COLLECT_DIR + fileName;
        try {
            fi = new FileInputStream(filePath);
        } catch (FileNotFoundException e) {
            log.error("Can't open file {}", filePath);
            throw APIException.badRequests.parameterIsNotValid("file_name");
        }
        return Response.ok(fi).build();
    }

    @GET
    @Path("/status")
    @CheckPermission(roles = {Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN, Role.SYSTEM_MONITOR})
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public DiagutilInfo getDiagutilStatus() {

        DiagutilJobStatus jobStatus = queryJobInfo();
        return jobStatus != null ? jobStatus.toDiagutilInfo() : new DiagutilInfo();
    }

    @DELETE
    @CheckPermission(roles = {Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN})
    public Response deleteDiagutilJob() {
        DiagutilJobStatus jobStatus = queryJobInfo();
        if (jobStatus == null) {
            throw APIException.badRequests.invalidParameterWithCause("cancel job", null, new InvalidParameterException("no diagutil job running"));
        }
        try {
            InterProcessLock lock = _coordinator.getCoordinatorClient().getLock(Constants.DIAGUTIL_JOB_LOCK);
            lock.acquire();
            _coordinator.getCoordinatorClient().removeRuntimeState(Constants.DIAGUTIL_JOB_STATUS);
            lock.release();
        } catch (Exception e) {
            throw APIException.internalServerErrors.releaseLockFailure(Constants.DIAGUTIL_JOB_LOCK);
        }
        //clean up left data file
        String nodeId = jobStatus.getNodeId();
        String path = jobStatus.getLocation();
        if (nodeId != null && path != null) {
            if (nodeId.equals(_coordinator.getMyNodeId())) {
                FileUtils.deleteQuietly(new File(path));
            } else {
                SysClientFactory.getSysClient(_coordinator.getNodeEndpoint(nodeId))
                        .post(URI.create(String.format(SysClientFactory.URI_INTERNAL_NODE_CLEANUP_DATA_TEMPLATE, path)), Response.class, null);
            }
        }

        return Response.status(Response.Status.OK).build();
    }

    @POST
    @Path("/internal/cleanup")
    public Response cleanupLeftData(@QueryParam("data_path") String path) {
        FileUtils.deleteQuietly(new File(path));
        return Response.ok().build();
    }
}
