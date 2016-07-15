/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource;

import static com.emc.storageos.api.mapper.VirtualMachineMapper.map;

import java.net.URI;
import java.util.Iterator;
import java.util.List;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.storageos.api.mapper.functions.MapVirtualMachine;
import com.emc.storageos.api.service.impl.resource.utils.AsyncTaskExecutorIntf;
import com.emc.storageos.api.service.impl.resource.utils.VirtualMachineConnectionValidator;
import com.emc.storageos.api.service.impl.response.BulkList;
import com.emc.storageos.computesystemcontroller.ComputeSystemController;
import com.emc.storageos.computesystemcontroller.impl.ComputeSystemHelper;
import com.emc.storageos.db.client.model.Cluster;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.Task;
import com.emc.storageos.db.client.model.VcenterDataCenter;
import com.emc.storageos.db.client.model.VirtualMachine;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.util.TaskUtils;
import com.emc.storageos.db.client.util.EndpointUtility;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.model.ResourceOperationTypeEnum;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.host.VirtualMachineBulkRep;
import com.emc.storageos.model.host.VirtualMachineParam;
import com.emc.storageos.model.host.VirtualMachineRestRep;
import com.emc.storageos.security.authorization.ACL;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.volumecontroller.AsyncTask;
import com.emc.storageos.volumecontroller.ControllerException;

/**
 * A service that provides APIs for viewing, updating and removing virtualMachines and their
 * interfaces by authorized users.
 *
 */
@DefaultPermissions(readRoles = { Role.TENANT_ADMIN, Role.SYSTEM_MONITOR, Role.SYSTEM_ADMIN },
        writeRoles = { Role.TENANT_ADMIN },
        readAcls = { ACL.ANY })
@Path("/compute/virtualmachines")
public class VirtualMachineService extends TaskResourceService {

    // Logger
    protected final static Logger _log = LoggerFactory.getLogger(VirtualMachineService.class);

    private static final String EVENT_SERVICE_TYPE = "virtualmachine";
    private static final String BLADE_RESERVATION_LOCK_NAME = "BLADE_RESERVATION_LOCK";

    @Autowired
    private ComputeSystemService computeSystemService;

    @Autowired
    private VirtualArrayService virtualArrayService;

    @Autowired
    private ClusterService clusterService;

    @Autowired
    private ComputeElementService computeElementService;

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
            return ResourceOperationTypeEnum.DISCOVER_VIRTUAL_MACHINE;
        }
    }

    /**
     * Gets the information for one virtual machine.
     *
     * @param id the URN of a ViPR VirtualMachine
     * @brief Show VirtualMachine
     * @return All the non-null attributes of the virtual machine.
     * @throws DatabaseException when a DB error occurs.
     */
    @GET
    @Path("/{id}")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public VirtualMachineRestRep getVirtualMachine(@PathParam("id") URI id) throws DatabaseException {
        VirtualMachine virtualMachine = queryObject(VirtualMachine.class, id, false);
        // check the user permissions
        verifyAuthorizedInTenantOrg(virtualMachine.getTenant(), getUserFromContext());
        return map(virtualMachine);
    }

    /**
     * Updates one or more of the virtualMachine attributes. Discovery is initiated
     * after the virtualMachine is updated.
     *
     * @param id the URN of a ViPR VirtualMachine
     * @param updateParam the parameter that has the attributes to be
     *            updated.
     * @brief Update VirtualMachine Attributes
     * @return the virtualMachine discovery async task representation.
     */

    /**
     * Validates the create/update virtualMachine input data
     *
     * @param virtualMachineParam the input parameter
     * @param virtualMachine the virtualMachine being updated in case of update operation.
     *            This parameter must be null for create operations.n
     * @throws Exception
     */
    protected void validateVirtualMachineData(VirtualMachineParam virtualMachineParam, URI tenanUri, VirtualMachine virtualMachine,
            Boolean validateConnection) throws Exception {
        Cluster cluster = null;
        VcenterDataCenter dataCenter = null;
        Project project = null;
        Volume volume = null;

        // validate the virtualMachine type
        if (virtualMachineParam.getType() != null) {
            ArgValidator.checkFieldValueFromEnum(virtualMachineParam.getType(), "Type", VirtualMachine.HostType.class);
        }

        // validate the project is present, active, and in the same tenant org
        if (!NullColumnValueGetter.isNullURI(virtualMachineParam.getProject())) {
            project = queryObject(Project.class, virtualMachineParam.getProject(), true);
            if (!project.getTenantOrg().getURI().equals(tenanUri)) {
                throw APIException.badRequests.resourcedoesNotBelongToVirtualMachineTenantOrg("project");
            }
        }
        if (!NullColumnValueGetter.isNullURI(virtualMachineParam.getBootVolume())) {
            volume = queryObject(Volume.class, virtualMachineParam.getBootVolume(), true);
            if (!volume.getTenant().getURI().equals(tenanUri)) {
                throw APIException.badRequests.resourcedoesNotBelongToVirtualMachineTenantOrg("boot volume");
            }
        }
        // validate the cluster is present, active, and in the same tenant org
        if (!NullColumnValueGetter.isNullURI(virtualMachineParam.getCluster())) {
            cluster = queryObject(Cluster.class, virtualMachineParam.getCluster(),
                    true);
            if (!cluster.getTenant().equals(tenanUri)) {
                throw APIException.badRequests.resourcedoesNotBelongToVirtualMachineTenantOrg("cluster");
            }
        }
        // validate the data center is present, active, and in the same tenant org
        if (!NullColumnValueGetter.isNullURI(virtualMachineParam.getVcenterDataCenter())) {
            dataCenter = queryObject(VcenterDataCenter.class, virtualMachineParam.getVcenterDataCenter(),
                    true);
            if (!dataCenter.getTenant().equals(tenanUri)) {
                throw APIException.badRequests.resourcedoesNotBelongToVirtualMachineTenantOrg("data center");
            }
        }
        if (cluster != null) {
            if (dataCenter != null) {
                // check the cluster and data center are consistent
                if (!dataCenter.getId().equals(cluster.getVcenterDataCenter())) {
                    throw APIException.badRequests.invalidParameterClusterNotInDataCenter(cluster.getLabel(), dataCenter.getLabel());
                }
            } else if (project != null) {
                // check the cluster and data center are consistent
                if (!project.getId().equals(cluster.getProject())) {
                    throw APIException.badRequests.invalidParameterClusterNotInVirtualMachineProject(cluster.getLabel());
                }
            }
        }
        // check the virtualMachine name is not a duplicate
        if (virtualMachine == null || (virtualMachineParam.getVirtualMachineName() != null &&
                !virtualMachineParam.getVirtualMachineName().equals(virtualMachine.getHostName()))) {
            checkDuplicateAltId(VirtualMachine.class, "virtualMachineName",
                    EndpointUtility.changeCase(virtualMachineParam.getVirtualMachineName()), "virtualMachine");
        }
        // check the virtualMachine label is not a duplicate
        if (virtualMachine == null || (virtualMachineParam.getName() != null &&
                !virtualMachineParam.getName().equals(virtualMachine.getLabel()))) {
            checkDuplicateLabel(VirtualMachine.class, virtualMachineParam.getName());
        }
        // If the virtualMachine project is being changed, check for active exports
        if (virtualMachine != null && !areEqual(virtualMachine.getProject(), virtualMachineParam.getProject())) {
            if (ComputeSystemHelper.isVirtualMachineInUse(_dbClient, virtualMachine.getId())) {
                throw APIException.badRequests.virtualMachineProjectChangeNotAllowed(virtualMachine.getHostName());
            }
        }

        // Find out if the virtualMachine should be discoverable by checking input and current values
        Boolean discoverable = virtualMachineParam.getDiscoverable() == null ?
                (virtualMachine == null ? Boolean.FALSE : virtualMachine.getDiscoverable()) :
                virtualMachineParam.getDiscoverable();

        // If discoverable, ensure username and password are set in the current virtualMachine or parameters
        if (discoverable != null && discoverable) {
            String username = virtualMachineParam.getUserName() == null ?
                    (virtualMachine == null ? null : virtualMachine.getUsername()) :
                    virtualMachineParam.getUserName();
            String password = virtualMachineParam.getPassword() == null ?
                    (virtualMachine == null ? null : virtualMachine.getPassword()) :
                    virtualMachineParam.getPassword();
            ArgValidator.checkFieldNotNull(username, "username");
            ArgValidator.checkFieldNotNull(password, "password");

            VirtualMachine.HostType virtualMachineType = VirtualMachine.HostType
                    .valueOf(virtualMachineParam.getType() == null ?
                            (virtualMachine == null ? null : virtualMachine.getType()) :
                            virtualMachineParam.getType());

            if (virtualMachineType != null && virtualMachineType == VirtualMachine.HostType.Windows) {
                Integer portNumber = virtualMachineParam.getPortNumber() == null ?
                        (virtualMachine == null ? null : virtualMachine.getPortNumber()) :
                        virtualMachineParam.getPortNumber();

                ArgValidator.checkFieldNotNull(portNumber, "port_number");
            }
        }

        if (validateConnection != null && validateConnection == true) {
            if (!VirtualMachineConnectionValidator.isHostConnectionValid(virtualMachineParam, virtualMachine)) {
                throw APIException.badRequests.invalidVirtualMachineConnection();
            }
        }
    }

    private boolean virtualMachineHasPendingTasks(URI id) {
        boolean hasPendingTasks = false;
        List<Task> taskList = TaskUtils.findResourceTasks(_dbClient, id);
        for (Task task : taskList) {
            if (task.isPending()) {
                hasPendingTasks = true;
                break;
            }
        }
        return hasPendingTasks;
    }

    @Override
    protected DataObject queryResource(URI id) {
        return queryObject(VirtualMachine.class, id, false);
    }

    @Override
    protected URI getTenantOwner(URI id) {
        VirtualMachine virtualMachine = queryObject(VirtualMachine.class, id, false);
        return virtualMachine.getTenant();
    }

    @SuppressWarnings("unchecked")
    @Override
    public Class<VirtualMachine> getResourceClass() {
        return VirtualMachine.class;
    }

    @Override
    protected ResourceTypeEnum getResourceType() {
        return ResourceTypeEnum.HOST;
    }

    @Override
    public VirtualMachineBulkRep queryBulkResourceReps(List<URI> ids) {

        Iterator<VirtualMachine> _dbIterator =
                _dbClient.queryIterativeObjects(getResourceClass(), ids);
        return new VirtualMachineBulkRep(BulkList.wrapping(_dbIterator, MapVirtualMachine.getInstance()));
    }

    @Override
    public VirtualMachineBulkRep queryFilteredBulkResourceReps(List<URI> ids) {
        Iterator<VirtualMachine> _dbIterator =
                _dbClient.queryIterativeObjects(getResourceClass(), ids);
        BulkList.ResourceFilter filter = new BulkList.VirtualMachineFilter(getUserFromContext(), _permissionsHelper);
        return new VirtualMachineBulkRep(BulkList.wrapping(_dbIterator, MapVirtualMachine.getInstance(), filter));
    }

    @Override
    protected boolean isZoneLevelResource() {
        return false;
    }

    @Override
    protected boolean isSysAdminReadableResource() {
        return true;
    }

    /**
     * Populate an instance of virtualMachine with the provided virtualMachine parameter
     *
     * @param virtualMachine the virtualMachine to be populated
     * @param param the parameter that contains the virtualMachine attributes.
     */
    private void populateVirtualMachineData(VirtualMachine virtualMachine, VirtualMachineParam param) {
        if (param.getName() != null) {
            virtualMachine.setLabel(param.getName());
        }
        if (param.getVirtualMachineName() != null) {
            virtualMachine.setHostName(param.getVirtualMachineName());
        }
        if (param.getCluster() != null) {
            virtualMachine.setCluster(param.getCluster());
        }
        if (param.getOsVersion() != null) {
            virtualMachine.setOsVersion(param.getOsVersion());
        }
        if (param.getUserName() != null) {
            virtualMachine.setUsername(param.getUserName());
        }
        if (param.getPassword() != null) {
            virtualMachine.setPassword(param.getPassword());
        }
        if (param.getPortNumber() != null) {
            virtualMachine.setPortNumber(param.getPortNumber());
        }
        if (param.getUseSsl() != null) {
            virtualMachine.setUseSSL(param.getUseSsl());
        }
        if (param.getType() != null) {
            virtualMachine.setType(param.getType());
        }
        if (param.getDiscoverable() != null) {
            virtualMachine.setDiscoverable(param.getDiscoverable());
        }
        // Commented out because virtualMachine project is not currently used
        // if (param.project != null) {
        // virtualMachine.setProject(NullColumnValueGetter.isNullURI(param.project) ?
        // NullColumnValueGetter.getNullURI() : param.project);
        // }
        if (param.getVcenterDataCenter() != null) {
            virtualMachine.setVcenterDataCenter(NullColumnValueGetter.isNullURI(param.getVcenterDataCenter()) ?
                    NullColumnValueGetter.getNullURI() : param.getVcenterDataCenter());
        }
        Cluster cluster = null;
        // make sure virtualMachine data is consistent with the cluster
        if (!NullColumnValueGetter.isNullURI(param.getCluster())) {
            cluster = queryObject(Cluster.class, param.getCluster(), true);
            if (!NullColumnValueGetter.isNullURI(cluster.getVcenterDataCenter())) {
                virtualMachine.setVcenterDataCenter(cluster.getVcenterDataCenter());
            }
            if (!NullColumnValueGetter.isNullURI(cluster.getProject())) {
                virtualMachine.setProject(cluster.getProject());
            }
        }

        if (param.getBootVolume() != null) {
            virtualMachine.setBootVolumeId(NullColumnValueGetter.isNullURI(param.getBootVolume()) ? NullColumnValueGetter
                    .getNullURI() : param.getBootVolume());
        }

    }

}
