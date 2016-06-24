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
import com.emc.storageos.computesystemcontroller.ComputeSystemController;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.ControlStation;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.model.ResourceOperationTypeEnum;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.TaskList;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.host.ControlStationCreateParam;
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

@Path("/compute/controlstation")
@DefaultPermissions(readRoles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR },
        writeRoles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
public class ControlStationService extends TaskResourceService {

    private static final Logger _log = LoggerFactory
            .getLogger(ControlStationService.class);

    private static final String EVENT_SERVICE_TYPE = "controlstation";

    @Override
    public String getServiceType() {
        return EVENT_SERVICE_TYPE;
    }

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
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.TENANT_ADMIN })
    public TaskResourceRep createControlStation(ControlStationCreateParam createParam,
            @QueryParam("validate_connection") @DefaultValue("false") final Boolean validateConnection,
            @QueryParam("discover_controlstation") @DefaultValue("true") final Boolean discoverControlStation) {
        ControlStation controlStation = createNewControlStation(createParam, validateConnection);

        controlStation.setRegistrationStatus(DiscoveredDataObject.RegistrationStatus.REGISTERED.toString());
        _dbClient.createObject(controlStation);
        auditOp(OperationTypeEnum.CREATE_CONTROL_STATION, true, null,
                controlStation.auditParameters());

        if (discoverControlStation) {
            return doDiscoverControlStation(queryObject(ControlStation.class, controlStation.getId(), true));
        } else {
            return createManualReadyTask(controlStation);

        }
    }

    /**
     * Gets the id, name, and self link for all registered control station .
     * 
     * @brief List control station
     * @return A reference to control station List.
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR })
    public ControlStationCreateParam testRestcall() {
        ControlStationCreateParam cs = new ControlStationCreateParam();
        cs.setName("test");
        return cs;
    }

    /**
     * Creates a manual (fake) controlStation discover task, so that
     * there wont be any controlStation discovery happening because of
     * this task.
     *
     * @param vcenter controlStation to create its manual/fake discovery task.
     *
     * @return returns fake/manual controlStation discovery task.
     */
    private TaskResourceRep createManualReadyTask(ControlStation controlStation) {
        // if not discoverable, manually create a ready task
        Operation op = new Operation();
        op.setResourceType(ResourceOperationTypeEnum.DISCOVER_CONTROL_STATION);
        op.ready("controlStation not discoverable.");

        String taskId = UUID.randomUUID().toString();
        _dbClient.createTaskOpStatus(ControlStation.class, controlStation.getId(), taskId, op);

        return toTask(controlStation, taskId, op);
    }

    private ControlStation createNewControlStation(ControlStationCreateParam createParam, Boolean validateConnection) {
        validateControlSationData(createParam, validateConnection);

        ControlStation cs = new ControlStation();
        cs.setId(URIUtil.createId(ControlStation.class));

        populateControlStation(cs, createParam);
        return cs;
    }

    private void populateControlStation(ControlStation cs, ControlStationCreateParam param) {
        cs.setLabel(param.getName());
        cs.setType(param.getType());
        cs.setOsVersion(param.getOsVersion());
        cs.setUsername(param.getUserName());
        cs.setPassword(param.getPassword());
        cs.setIpAddress(param.findIpAddress());
        cs.setPortNumber(param.getPortNumber());
        cs.setUseSSL(param.getUseSsl());

    }

    private void validateControlSationData(ControlStationCreateParam param, Boolean validateConnection) {
        if (param.findIpAddress() != null) {
            checkDuplicateAltId(ControlStation.class, "ipAddress", param.findIpAddress(), "controlstation");
        }

        if (param.getName() != null) {
            checkDuplicateLabel(ControlStation.class, param.getName());
        }

        ArgValidator.checkFieldNotNull(param.getUserName(), "username");
        ArgValidator.checkFieldNotNull(param.getPassword(), "password");

        // TODO check for connection validation
        if (validateConnection != null && validateConnection == true) {
            // String errorMessage = ControlStationConnectionValidator.isConnectionValid(param);
            // if (StringUtils.isNotBlank(errorMessage)) {
            // throw APIException.badRequests.invalidControlStationConnection(errorMessage);
            // }
        }

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
