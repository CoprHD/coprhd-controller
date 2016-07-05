/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource;

import static com.emc.storageos.api.mapper.TaskMapper.toTask;

import java.net.URI;
import java.util.ArrayList;
import java.util.UUID;

import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.service.impl.resource.utils.AsyncTaskExecutorIntf;
import com.emc.storageos.api.service.impl.resource.utils.DiscoveredObjectTaskScheduler;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.ManagementStation;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.managementstation.ManagementStationController;
import com.emc.storageos.model.ResourceOperationTypeEnum;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.TaskList;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.host.ManagementStationCreateParam;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.volumecontroller.AsyncTask;
import com.emc.storageos.volumecontroller.ControllerException;

/**
 * Service class responsible for serving rest requests of ComputeImageServer
 * 
 *
 */

@Path("/compute/managementstation")
@DefaultPermissions(readRoles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR }, writeRoles = { Role.SYSTEM_ADMIN,
        Role.RESTRICTED_SYSTEM_ADMIN })
public class ManagementStationService extends TaskResourceService {

    private static final Logger _log = LoggerFactory
            .getLogger(ManagementStationService.class);

    private static final String EVENT_SERVICE_TYPE = "managementstation";

    @Override
    public String getServiceType() {
        return EVENT_SERVICE_TYPE;
    }

    private static class DiscoverJobExec implements AsyncTaskExecutorIntf {

        private final ManagementStationController _controller;

        DiscoverJobExec(ManagementStationController controller) {
            _controller = controller;
        }

        @Override
        public void executeTasks(AsyncTask[] tasks) throws ControllerException {
            _controller.discover(tasks);
        }

        @Override
        public ResourceOperationTypeEnum getOperation() {
            return ResourceOperationTypeEnum.DISCOVER_CONTROL_STATION;
        }
    }

    /**
     * Creates a new Management Station for the tenant organization. Discovery is initiated
     * after the Management Station is created.
     *
     * @param createParam
     *            the parameter that has the type and attribute of the host to
     *            be created.
     * @prereq none
     * @brief Create Management Station
     * @return the Management Station discovery async task representation.
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.TENANT_ADMIN })
    public TaskResourceRep createManagementStation(ManagementStationCreateParam createParam,
            @QueryParam("validate_connection") @DefaultValue("false") final Boolean validateConnection,
            @QueryParam("discover_managementstation") @DefaultValue("true") final Boolean discoverManagementStation) {
        ManagementStation managementStation = createNewManagementStation(createParam, validateConnection);

        managementStation.setRegistrationStatus(DiscoveredDataObject.RegistrationStatus.REGISTERED.toString());
        _dbClient.createObject(managementStation);
        auditOp(OperationTypeEnum.CREATE_MANAGEMENT_STATION, true, null,
                managementStation.auditParameters());

        if (discoverManagementStation) {
            return doDiscoverManagementStation(queryObject(ManagementStation.class, managementStation.getId(), true));
        } else {
            return createManualReadyTask(managementStation);

        }
    }

    /**
     * Gets the id, name, and self link for all registered management station .
     * 
     * @brief List management station
     * @return A reference to management station List.
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR })
    public ManagementStationCreateParam testRestcall() {
        ManagementStationCreateParam cs = new ManagementStationCreateParam();
        cs.setName("test");
        return cs;
    }

    /**
     * Creates a manual (fake) managementStation discover task, so that
     * there wont be any managementStation discovery happening because of
     * this task.
     *
     * @param vcenter managementStation to create its manual/fake discovery task.
     *
     * @return returns fake/manual managementStation discovery task.
     */
    private TaskResourceRep createManualReadyTask(ManagementStation managementStation) {
        // if not discoverable, manually create a ready task
        Operation op = new Operation();
        op.setResourceType(ResourceOperationTypeEnum.DISCOVER_CONTROL_STATION);
        op.ready("managementStation not discoverable.");

        String taskId = UUID.randomUUID().toString();
        _dbClient.createTaskOpStatus(ManagementStation.class, managementStation.getId(), taskId, op);

        return toTask(managementStation, taskId, op);
    }

    private ManagementStation createNewManagementStation(ManagementStationCreateParam createParam, Boolean validateConnection) {
        validateManagementSationData(createParam, validateConnection);

        ManagementStation cs = new ManagementStation();
        cs.setId(URIUtil.createId(ManagementStation.class));

        populateManagementStation(cs, createParam);
        return cs;
    }

    private void populateManagementStation(ManagementStation cs, ManagementStationCreateParam param) {
        cs.setLabel(param.getName());
        cs.setType(param.getType());
        cs.setOsVersion(param.getOsVersion());
        cs.setUsername(param.getUserName());
        cs.setPassword(param.getPassword());
        cs.setIpAddress(param.findIpAddress());
        cs.setPortNumber(param.getPortNumber());
        cs.setUseSSL(param.getUseSsl());

    }

    private void validateManagementSationData(ManagementStationCreateParam param, Boolean validateConnection) {
        if (param.findIpAddress() != null) {
            checkDuplicateAltId(ManagementStation.class, "ipAddress", param.findIpAddress(), "managementstation");
        }

        if (param.getName() != null) {
            checkDuplicateLabel(ManagementStation.class, param.getName());
        }

        ArgValidator.checkFieldNotNull(param.getUserName(), "username");
        ArgValidator.checkFieldNotNull(param.getPassword(), "password");

        // TODO check for connection validation
        if (validateConnection != null && validateConnection == true) {
            // String errorMessage = ManagementStationConnectionValidator.isConnectionValid(param);
            // if (StringUtils.isNotBlank(errorMessage)) {
            // throw APIException.badRequests.invalidManagementStationConnection(errorMessage);
            // }
        }

    }

    /**
     * Management Station Discovery
     *
     * @param the ManagementStation to be discovered.
     *            provided, a new taskId is generated.
     * @return the task used to track the discovery job
     */
    private TaskResourceRep doDiscoverManagementStation(ManagementStation cs) {
        ManagementStationController controller = getController(ManagementStationController.class, "managementstation");
        DiscoveredObjectTaskScheduler scheduler = new DiscoveredObjectTaskScheduler(
                _dbClient, new DiscoverJobExec(controller));
        String taskId = UUID.randomUUID().toString();
        ArrayList<AsyncTask> tasks = new ArrayList<AsyncTask>(1);
        tasks.add(new AsyncTask(ManagementStation.class, cs.getId(), taskId));

        TaskList taskList = scheduler.scheduleAsyncTasks(tasks);

        TaskResourceRep taskResourceRep = taskList.getTaskList().iterator().next();

        return taskResourceRep;
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
