/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.service.impl.resource.utils.AsyncTaskExecutorIntf;
import com.emc.storageos.api.service.impl.resource.utils.DiscoveredObjectTaskScheduler;
import com.emc.storageos.computesystemcontroller.ComputeSystemController;
import com.emc.storageos.db.client.model.ControlStation;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.DiscoveredDataObject.RegistrationStatus;
import com.emc.storageos.db.client.model.Task;
import com.emc.storageos.db.client.model.TenantOrg;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.model.RelatedResourceRep;
import com.emc.storageos.model.ResourceOperationTypeEnum;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.RestLinkRep;
import com.emc.storageos.model.TaskList;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.host.ControlStationCreateParam;
import com.emc.storageos.security.authentication.StorageOSUser;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.volumecontroller.AsyncTask;
import com.emc.storageos.volumecontroller.ControllerException;

/**
 * Service class responsible for serving rest requests of ComputeImageServer
 * 
 *
 */
@Path("/compute/controlStation")
@DefaultPermissions(readRoles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR }, writeRoles = {
        Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
public class ControlStationService extends TaskResourceService {

    private static final Logger _log = LoggerFactory
            .getLogger(ControlStationService.class);

    private static final String EVENT_SERVICE_TYPE = "ControlStationService";

    private static class DiscoverJobExec implements AsyncTaskExecutorIntf {

        private final ComputeSystemController _controller;

        DiscoverJobExec(ComputeSystemController controller) {
            _controller = controller;
        }

        @Override
        public void executeTasks(AsyncTask[] tasks) throws ControllerException {
            _controller.discover(tasks);
        }

        @Override
        public ResourceOperationTypeEnum getOperation() {
            return ResourceOperationTypeEnum.DISCOVER_VCENTER;
        }
    }

    /**
     * Creates a new Control Station for the tenant organization. Discovery is initiated
     * after the Control Station is created.
     *
     * @param createParam
     *            the parameter that has the type and attribute of the host to
     *            be created.
     * @prereq none
     * @brief Create Control Station
     * @return the Control Station discovery async task representation.
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.TENANT_ADMIN })
    public TaskResourceRep createControlStation(ControlStationCreateParam createParam,
            @QueryParam("validate_connection") @DefaultValue("false") final Boolean validateConnection) {
        // This is mostly to validate the tenant
        URI tid = createParam.getTenant();
        if ((tid == null) || tid.toString().isEmpty()) {
            _log.error("The tenant id is missing");
            throw APIException.badRequests.requiredParameterMissingOrEmpty("tenant");
        }

        TenantOrg tenant = _permissionsHelper.getObjectById(tid, TenantOrg.class);
        ArgValidator.checkEntity(tenant, tid, isIdEmbeddedInURL(tid), true);

        validateControlStationData(createParam, tid, null, validateConnection);

        // Create the host
        ControlStation cs = createNewControlStation(tenant, createParam);
        cs.setRegistrationStatus(RegistrationStatus.REGISTERED.toString());
        _dbClient.createObject(cs);
        auditOp(OperationTypeEnum.CREATE_CONTROL_STATION, true, null, cs.auditParameters());
        return doDiscoverControlStation(cs);
    }

    /**
     * Control Station Discovery
     *
     * @param the ControlStation to be discovered.
     *            provided, a new taskId is generated.
     * @return the task used to track the discovery job
     */
    private TaskResourceRep doDiscoverControlStation(ControlStation cs) {
        ComputeSystemController controller = getController(ComputeSystemController.class, "controlstation");
        DiscoveredObjectTaskScheduler scheduler = new DiscoveredObjectTaskScheduler(
                _dbClient, new DiscoverJobExec(controller));
        String taskId = UUID.randomUUID().toString();
        ArrayList<AsyncTask> tasks = new ArrayList<AsyncTask>(1);
        tasks.add(new AsyncTask(ControlStation.class, cs.getId(), taskId));

        TaskList taskList = scheduler.scheduleAsyncTasks(tasks);

        TaskResourceRep taskResourceRep = taskList.getTaskList().iterator().next();
        updateTaskTenant(taskResourceRep);

        return taskResourceRep;
    }

    /**
     * Updates the tenant information in the Task data object and
     * TaskResourceRep (the response object to the API request).
     * Both Task and TaskResourceRep is updated with the user's
     * tenant information if it they don't contain any tenant information
     * already.
     *
     * @param taskResourceRep api response to be updated.
     */
    private void updateTaskTenant(TaskResourceRep taskResourceRep) {
        Task task = _dbClient.queryObject(Task.class, taskResourceRep.getId());
        if (areEqual(task.getTenant(), NullColumnValueGetter.getNullURI())) {
            StorageOSUser user = getUserFromContext();
            URI userTenantUri = URI.create(user.getTenantId());
            task.setTenant(userTenantUri);

            RelatedResourceRep tenant = new RelatedResourceRep();
            tenant.setId(userTenantUri);
            tenant.setLink(new RestLinkRep("self", URI.create("/tenants/" + userTenantUri.toString())));

            taskResourceRep.setTenant(tenant);
            _dbClient.persistObject(task);

            List<String> traceParams = new ArrayList<String>();
            traceParams.add(task.getId().toString());
            traceParams.add(user.getName());
            traceParams.add(user.getTenantId());

            _log.info("Update the task {} with the user's {} tenant {}", traceParams);
        }
    }

    private ControlStation createNewControlStation(TenantOrg tenant, ControlStationCreateParam createParam) {
        // TODO Auto-generated method stub
        return null;
    }

    private void validateControlStationData(ControlStationCreateParam createParam, URI tid, Object object, Boolean validateConnection) {
        // TODO Auto-generated method stub

    }

    @Override
    protected DataObject queryResource(URI id) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    protected URI getTenantOwner(URI id) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    protected ResourceTypeEnum getResourceType() {
        // TODO Auto-generated method stub
        return null;
    }

}
