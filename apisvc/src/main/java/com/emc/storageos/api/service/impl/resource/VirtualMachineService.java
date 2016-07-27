/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource;

import static com.emc.storageos.api.mapper.DbObjectMapper.toNamedRelatedResource;
import static com.emc.storageos.api.mapper.TaskMapper.toTask;
import static com.emc.storageos.api.mapper.VirtualMachineMapper.map;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.storageos.api.mapper.functions.MapVirtualMachine;
import com.emc.storageos.api.service.impl.resource.utils.AsyncTaskExecutorIntf;
import com.emc.storageos.api.service.impl.resource.utils.DiscoveredObjectTaskScheduler;
import com.emc.storageos.api.service.impl.resource.utils.VirtualMachineConnectionValidator;
import com.emc.storageos.api.service.impl.response.BulkList;
import com.emc.storageos.computesystemcontroller.ComputeSystemController;
import com.emc.storageos.computesystemcontroller.impl.ComputeSystemHelper;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.NamedElementQueryResultList;
import com.emc.storageos.db.client.model.Cluster;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.DiscoveredDataObject.DataCollectionJobStatus;
import com.emc.storageos.db.client.model.DiscoveredDataObject.RegistrationStatus;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.db.client.model.HostInterface;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.Task;
import com.emc.storageos.db.client.model.TenantOrg;
import com.emc.storageos.db.client.model.VcenterDataCenter;
import com.emc.storageos.db.client.model.VirtualMachine;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.util.TaskUtils;
import com.emc.storageos.db.client.util.EndpointUtility;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.db.client.util.WWNUtility;
import com.emc.storageos.db.client.util.iSCSIUtility;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.model.ResourceOperationTypeEnum;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.TaskList;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.host.BaseInitiatorParam;
import com.emc.storageos.model.host.InitiatorCreateParam;
import com.emc.storageos.model.host.InitiatorList;
import com.emc.storageos.model.host.PairedInitiatorCreateParam;
import com.emc.storageos.model.host.VirtualMachineBulkRep;
import com.emc.storageos.model.host.VirtualMachineCreateParam;
import com.emc.storageos.model.host.VirtualMachineParam;
import com.emc.storageos.model.host.VirtualMachineRestRep;
import com.emc.storageos.security.authorization.ACL;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.volumecontroller.AsyncTask;
import com.emc.storageos.volumecontroller.ControllerException;

/**
 * A service that provides APIs for viewing, updating and removing virtualMachines and their
 * interfaces by authorized users.
 *
 */
@DefaultPermissions(readRoles = { Role.TENANT_ADMIN, Role.SYSTEM_MONITOR, Role.SYSTEM_ADMIN }, writeRoles = {
        Role.TENANT_ADMIN }, readAcls = { ACL.ANY })
@Path("/compute/virtualmachines")
public class VirtualMachineService extends TaskResourceService {

    // Logger
    protected final static Logger _log = LoggerFactory.getLogger(VirtualMachineService.class);

    private static final String EVENT_SERVICE_TYPE = "virtual_machine";
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
        return ResourceTypeEnum.VIRTUAL_MACHINE;
    }

    @Override
    public VirtualMachineBulkRep queryBulkResourceReps(List<URI> ids) {

        Iterator<VirtualMachine> _dbIterator = _dbClient.queryIterativeObjects(getResourceClass(), ids);
        return new VirtualMachineBulkRep(BulkList.wrapping(_dbIterator, MapVirtualMachine.getInstance()));
    }

    @Override
    public VirtualMachineBulkRep queryFilteredBulkResourceReps(List<URI> ids) {
        Iterator<VirtualMachine> _dbIterator = _dbClient.queryIterativeObjects(getResourceClass(), ids);
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
     * Creates a new host for the tenant organization. Discovery is initiated
     * after the host is created.
     *
     * @param createParam
     *            the parameter that has the type and attribute of the host to
     *            be created.
     * @prereq none
     * @brief Create host
     * @return the host discovery async task representation.
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.TENANT_ADMIN })
    public TaskResourceRep createVM(VirtualMachineCreateParam createParam,
            @QueryParam("validate_connection") @DefaultValue("false") final Boolean validateConnection) {
        // This is mostly to validate the tenant
        URI tid = createParam.getTenant();
        if ((tid == null) || tid.toString().isEmpty()) {
            _log.error("The tenant id is missing");
            throw APIException.badRequests.requiredParameterMissingOrEmpty("tenant");
        }

        TenantOrg tenant = _permissionsHelper.getObjectById(tid, TenantOrg.class);
        ArgValidator.checkEntity(tenant, tid, isIdEmbeddedInURL(tid), true);

        validateVMData(createParam, tid, null, validateConnection);

        // Create the host
        VirtualMachine vm = createNewVM(tenant, createParam);
        vm.setRegistrationStatus(RegistrationStatus.REGISTERED.toString());
        _dbClient.createObject(vm);
        auditOp(OperationTypeEnum.CREATE_VIRTUAL_MACHINE, true, null, vm.auditParameters());
        return doDiscoverVM(vm);
    }

    /**
     * Creates a new initiator for a host.
     *
     * @param id the URN of a ViPR Virtual Machine
     * @param createParam the details of the initiator
     * @brief Create VM Initiator
     * @return the details of the host initiator when creation
     *         is successfully.
     * @throws DatabaseException when a database error occurs.
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.TENANT_ADMIN })
    @Path("/{id}/initiators")
    public TaskResourceRep createInitiator(@PathParam("id") URI id,
            InitiatorCreateParam createParam) throws DatabaseException {
        VirtualMachine vm = queryObject(VirtualMachine.class, id, true);
        Cluster cluster = null;

        validateInitiatorData(createParam, null);
        // create and populate the initiator
        Initiator initiator = new Initiator();
        initiator.setVirtualMachine(id);

        // TODO amit s clean up. ..host added to avoid exception
        initiator.setHost(URIUtil.NULL_URI);
        initiator.setHostName(vm.getHostName());
        if (!NullColumnValueGetter.isNullURI(vm.getCluster())) {
            cluster = queryObject(Cluster.class, vm.getCluster(), false);
            initiator.setClusterName(cluster.getLabel());
        }
        initiator.setId(URIUtil.createId(Initiator.class));
        populateInitiator(initiator, createParam);

        _dbClient.createObject(initiator);
        String taskId = UUID.randomUUID().toString();
        Operation op = _dbClient.createTaskOpStatus(Initiator.class, initiator.getId(), taskId,
                ResourceOperationTypeEnum.ADD_VIRTUAL_MACHINE_INITIATOR);

        // if host in use. update export with new initiator
        if (ComputeSystemHelper.isHostInUse(_dbClient, vm.getId())
                && (cluster == null || cluster.getAutoExportEnabled())) {
            ComputeSystemController controller = getController(ComputeSystemController.class, null);
            controller.addInitiatorsToExport(initiator.getHost(), Arrays.asList(initiator.getId()), taskId);
        } else {
            // No updates were necessary, so we can close out the task.
            _dbClient.ready(Initiator.class, initiator.getId(), taskId);
        }

        auditOp(OperationTypeEnum.CREATE_HOST_INITIATOR, true, null,
                initiator.auditParameters());
        return toTask(initiator, taskId, op);
    }

    /**
     * Creates a new paired initiator for a host.
     *
     * @param id the URN of a ViPR Virtual Machine
     * @param createParam the details of the initiator
     * @brief Create VM Initiator
     * @return the details of the host initiator when creation
     *         is successfully.
     * @throws DatabaseException when a database error occurs.
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.TENANT_ADMIN })
    @Path("/{id}/pair-initiators")
    public TaskResourceRep createPairedInitiator(@PathParam("id") URI id,
            PairedInitiatorCreateParam createParam) throws DatabaseException {
        VirtualMachine vm = queryObject(VirtualMachine.class, id, true);
        Cluster cluster = null;
        validateInitiatorData(createParam.getFirstInitiator(), null);
        validateInitiatorData(createParam.getSeconedInitiator(), null);
        // create and populate the initiator
        Initiator firstInitiator = new Initiator();
        Initiator seconedInitiator = new Initiator();
        firstInitiator.setVirtualMachine(id);

        firstInitiator.setHost(URIUtil.NULL_URI);
        firstInitiator.setHostName(vm.getHostName());
        if (!NullColumnValueGetter.isNullURI(vm.getCluster())) {
            cluster = queryObject(Cluster.class, vm.getCluster(), false);
            firstInitiator.setClusterName(cluster.getLabel());
        }
        firstInitiator.setId(URIUtil.createId(Initiator.class));
        populateInitiator(firstInitiator, createParam.getFirstInitiator());

        seconedInitiator.setVirtualMachine(id);
        seconedInitiator.setHost(URIUtil.NULL_URI);
        seconedInitiator.setHostName(vm.getHostName());
        if (!NullColumnValueGetter.isNullURI(vm.getCluster())) {
            cluster = queryObject(Cluster.class, vm.getCluster(), false);
            seconedInitiator.setClusterName(cluster.getLabel());
        }
        seconedInitiator.setId(URIUtil.createId(Initiator.class));
        populateInitiator(seconedInitiator, createParam.getFirstInitiator());

        _dbClient.createObject(firstInitiator);
        _dbClient.createObject(seconedInitiator);

        String taskId = UUID.randomUUID().toString();
        Operation op = _dbClient.createTaskOpStatus(Initiator.class, firstInitiator.getId(), taskId,
                ResourceOperationTypeEnum.ADD_VIRTUAL_MACHINE_INITIATOR);

        // if host in use. update export with new initiator
        if (ComputeSystemHelper.isHostInUse(_dbClient, vm.getId())
                && (cluster == null || cluster.getAutoExportEnabled())) {
            ComputeSystemController controller = getController(ComputeSystemController.class, null);
            controller.addInitiatorsToExport(firstInitiator.getHost(), Arrays.asList(firstInitiator.getId()), taskId);
        } else {
            // No updates were necessary, so we can close out the task.
            _dbClient.ready(Initiator.class, firstInitiator.getId(), taskId);
        }

        auditOp(OperationTypeEnum.CREATE_HOST_INITIATOR, true, null,
                firstInitiator.auditParameters());
        return toTask(firstInitiator, taskId, op);
    }

    /**
     * Validates the create/update host input data
     *
     * @param vmParam the input parameter
     * @param vm the host being updated in case of update operation.
     *            This parameter must be null for create operations.n
     */
    protected void validateVMData(VirtualMachineParam vmParam, URI tenanUri, VirtualMachine vm, Boolean validateConnection) {
        Cluster cluster = null;
        VcenterDataCenter dataCenter = null;
        Project project = null;
        Volume volume = null;

        // validate the host type
        if (vmParam.getType() != null) {
            ArgValidator.checkFieldValueFromEnum(vmParam.getType(), "Type", VirtualMachine.HostType.class);
        }

        // validate the project is present, active, and in the same tenant org
        if (!NullColumnValueGetter.isNullURI(vmParam.getProject())) {
            project = queryObject(Project.class, vmParam.getProject(), true);
            if (!project.getTenantOrg().getURI().equals(tenanUri)) {
                throw APIException.badRequests.resourcedoesNotBelongToHostTenantOrg("project");
            }
        }
        if (!NullColumnValueGetter.isNullURI(vmParam.getBootVolume())) {
            volume = queryObject(Volume.class, vmParam.getBootVolume(), true);
            if (!volume.getTenant().getURI().equals(tenanUri)) {
                throw APIException.badRequests.resourcedoesNotBelongToHostTenantOrg("boot volume");
            }
        }
        // validate the cluster is present, active, and in the same tenant org
        if (!NullColumnValueGetter.isNullURI(vmParam.getCluster())) {
            cluster = queryObject(Cluster.class, vmParam.getCluster(),
                    true);
            if (!cluster.getTenant().equals(tenanUri)) {
                throw APIException.badRequests.resourcedoesNotBelongToHostTenantOrg("cluster");
            }
        }
        // validate the data center is present, active, and in the same tenant org
        if (!NullColumnValueGetter.isNullURI(vmParam.getVcenterDataCenter())) {
            dataCenter = queryObject(VcenterDataCenter.class, vmParam.getVcenterDataCenter(),
                    true);
            if (!dataCenter.getTenant().equals(tenanUri)) {
                throw APIException.badRequests.resourcedoesNotBelongToHostTenantOrg("data center");
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
                    throw APIException.badRequests.invalidParameterClusterNotInHostProject(cluster.getLabel());
                }
            }
        }
        // check the host name is not a duplicate
        if (vm == null || (vmParam.getHostName() != null &&
                !vmParam.getHostName().equals(vm.getHostName()))) {
            checkDuplicateAltId(VirtualMachine.class, "hostName", EndpointUtility.changeCase(vmParam.getHostName()), "host");
        }
        // check the host label is not a duplicate
        if (vm == null || (vmParam.getName() != null &&
                !vmParam.getName().equals(vm.getLabel()))) {
            checkDuplicateLabel(VirtualMachine.class, vmParam.getName());
        }
        // If the host project is being changed, check for active exports
        if (vm != null && !areEqual(vm.getProject(), vmParam.getProject())) {
            if (ComputeSystemHelper.isHostInUse(_dbClient, vm.getId())) {
                throw APIException.badRequests.hostProjectChangeNotAllowed(vm.getHostName());
            }
        }

        // Find out if the host should be discoverable by checking input and current values
        Boolean discoverable = vmParam.getDiscoverable() == null ? (vm == null ? Boolean.FALSE : vm.getDiscoverable())
                : vmParam.getDiscoverable();

        // If discoverable, ensure username and password are set in the current host or parameters
        if (discoverable != null && discoverable) {
            String username = vmParam.getUserName() == null ? (vm == null ? null : vm.getUsername()) : vmParam.getUserName();
            String password = vmParam.getPassword() == null ? (vm == null ? null : vm.getPassword()) : vmParam.getPassword();
            ArgValidator.checkFieldNotNull(username, "username");
            ArgValidator.checkFieldNotNull(password, "password");

            VirtualMachine.HostType hostType = VirtualMachine.HostType
                    .valueOf(vmParam.getType() == null ? (vm == null ? null : vm.getType()) : vmParam.getType());

            if (hostType != null && hostType == VirtualMachine.HostType.Windows) {
                Integer portNumber = vmParam.getPortNumber() == null ? (vm == null ? null : vm.getPortNumber()) : vmParam.getPortNumber();

                ArgValidator.checkFieldNotNull(portNumber, "port_number");
            }
        }

        if (validateConnection != null && validateConnection == true) {
            if (!VirtualMachineConnectionValidator.isVMConnectionValid(vmParam, vm)) {
                throw APIException.badRequests.invalidHostConnection();
            }
        }
    }

    /**
     * Deactivates the host and all its interfaces.
     *
     * @param id the URN of a ViPR Host to be deactivated
     * @param detachStorage
     *            if true, will first detach storage.
     * @param detachStorageDeprecated
     *            Deprecated. Use detachStorage instead.
     * @param deactivateBootVolume
     *            if true, and if the host was provisioned by ViPR the associated boot volume (if exists) will be deactivated
     * @brief Deactivate Host
     * @return OK if deactivation completed successfully
     * @throws DatabaseException when a DB error occurs
     */
    @POST
    @Path("/{id}/deactivate")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.TENANT_ADMIN })
    public TaskResourceRep deactivateVirtualMachine(@PathParam("id") URI id,
            @DefaultValue("false") @QueryParam("detach_storage") boolean detachStorage,
            @DefaultValue("false") @QueryParam("detach-storage") boolean detachStorageDeprecated,
            @DefaultValue("true") @QueryParam("deactivate_boot_volume") boolean deactivateBootVolume) throws DatabaseException {
        VirtualMachine vm = queryVirtualMachine(_dbClient, id);
        ArgValidator.checkEntity(vm, id, true);
        boolean hasPendingTasks = vmPendingTasks(id);
        if (hasPendingTasks) {
            throw APIException.badRequests.resourceCannotBeDeleted("Virtual Machine  with another operation in progress");
        }
        Cluster cluster = null;
        if (!NullColumnValueGetter.isNullURI(vm.getCluster())) {
            cluster = _dbClient.queryObject(Cluster.class, vm.getCluster());
        }
        boolean isVMInUse = ComputeSystemHelper.isVirtualMachineInUse(_dbClient, vm.getId());

        if (isVMInUse && cluster != null && !cluster.getAutoExportEnabled()) {
            throw APIException.badRequests.resourceInClusterWithAutoExportDisabled(VirtualMachine.class.getSimpleName(), id);
        } else if (isVMInUse && !(detachStorage || detachStorageDeprecated)) {
            throw APIException.badRequests.resourceHasActiveReferences(VirtualMachine.class.getSimpleName(), id);
        } else {
            String taskId = UUID.randomUUID().toString();
            Operation op = _dbClient.createTaskOpStatus(VirtualMachine.class, vm.getId(), taskId,
                    ResourceOperationTypeEnum.DELETE_VIRTUAL_MACHINE);
            ComputeSystemController controller = getController(ComputeSystemController.class, null);
            controller.detachHostStorage(vm.getId(), true, deactivateBootVolume, taskId);
            vm.setProvisioningStatus(VirtualMachine.ProvisioningJobStatus.IN_PROGRESS.toString());
            _dbClient.createObject(vm);
            auditOp(OperationTypeEnum.DELETE_VIRTUAL_MACHINE, true, op.getStatus(),
                    vm.auditParameters());
            return toTask(vm, taskId, op);
        }
    }

    /**
     * Gets the id and name for all the initiators of a vm.
     *
     * @param id the URN of a ViPR VirtualMachine
     * @brief List VirtualMachine Initiators
     * @return a list of initiators that belong to the VirtualMachine
     * @throws DatabaseException when a DB error occurs
     */
    @GET
    @Path("/{id}/initiators")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public InitiatorList getInitiators(@PathParam("id") URI id) throws DatabaseException {
        VirtualMachine vm = queryObject(VirtualMachine.class, id, false);
        // check the user permissions
        verifyAuthorizedInTenantOrg(vm.getTenant(), getUserFromContext());
        // get the initiators
        InitiatorList list = new InitiatorList();
        List<NamedElementQueryResultList.NamedElement> dataObjects = listChildren(id, Initiator.class, "iniport", "virtualMachine");
        for (NamedElementQueryResultList.NamedElement dataObject : dataObjects) {
            list.getInitiators().add(toNamedRelatedResource(ResourceTypeEnum.INITIATOR,
                    dataObject.getId(), dataObject.getName()));
        }
        return list;
    }

    /**
     * Returns the instance of VirtualMachine for the given id. Throws {@link DatabaseException} when id is not a valid URI. Throws
     * {@link NotFoundException} when the VirtualMachine has
     * been delete.
     *
     * @param dbClient an instance of {@link DbClient}
     * @param id the URN of a ViPR VirtualMachine to be fetched.
     * @return the instance of VirtualMachine for the given id.
     * @throws DatabaseException when a DB error occurs
     */
    protected VirtualMachine queryVirtualMachine(DbClient dbClient, URI id) throws DatabaseException {
        return queryObject(VirtualMachine.class, id, false);
    }

    private boolean vmPendingTasks(URI id) {
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

    /**
     * Validates the create/update initiator operation input data.
     *
     * @param param the input parameter
     * @param initiator the initiator being updated in case of update operation.
     *            This parameter must be null for create operations.n
     */
    public void validateInitiatorData(BaseInitiatorParam param, Initiator initiator) {
        String protocol = param.getProtocol() != null ? param.getProtocol() : (initiator != null ? initiator.getProtocol() : null);
        String node = param.getNode() != null ? param.getNode() : (initiator != null ? initiator.getInitiatorNode() : null);
        String port = param.getPort() != null ? param.getPort() : (initiator != null ? initiator.getInitiatorPort() : null);
        ArgValidator.checkFieldValueWithExpected(param == null
                || HostInterface.Protocol.FC.toString().equals(protocol)
                || HostInterface.Protocol.iSCSI.toString().equals(protocol),
                "protocol", protocol, HostInterface.Protocol.FC, HostInterface.Protocol.iSCSI);
        // Validate the passed node and port based on the protocol.
        // Note that for iSCSI the node is optional.
        if (HostInterface.Protocol.FC.toString().equals(protocol)) {
            // Make sure the node is passed in the request.
            ArgValidator.checkFieldNotNull(node, "node");
            // Make sure the node is passed in the request.
            ArgValidator.checkFieldNotNull(port, "port");
            // Make sure the port is a valid WWN.
            if (!WWNUtility.isValidWWN(port)) {
                throw APIException.badRequests.invalidWwnForFcInitiatorPort();
            }
            // Make sure the node is a valid WWN.
            if (!WWNUtility.isValidWWN(node)) {
                throw APIException.badRequests.invalidWwnForFcInitiatorNode();
            }
        } else {
            // Make sure the port is a valid iSCSI port.
            if (!iSCSIUtility.isValidIQNPortName(port) && !iSCSIUtility.isValidEUIPortName(port)) {
                throw APIException.badRequests.invalidIscsiInitiatorPort();
            }
            if (param.getNode() != null) {
                throw APIException.badRequests.invalidNodeForiScsiPort();
            }
        }
        // last validate that the initiator port is unique
        if (initiator == null || (param.getPort() != null &&
                !param.getPort().equalsIgnoreCase(initiator.getInitiatorPort()))) {
            checkDuplicateAltId(Initiator.class, "iniport", EndpointUtility.changeCase(param.getPort()),
                    "initiator", "Initiator Port");
        }
    }

    /**
     * Populates the initiator using values in the parameter
     *
     * @param param the initiator creation/update parameter that contains all the attributes
     * @param the initiator to be to be populated with data.
     */
    public void populateInitiator(Initiator initiator, BaseInitiatorParam param) {
        initiator.setInitiatorPort(param.getPort());
        initiator.setInitiatorNode(param.getNode());
        initiator.setProtocol(param.getProtocol());

        // Set label to the initiator port if not specified on create.
        if (initiator.getLabel() == null && param.getName() == null) {
            initiator.setLabel(initiator.getInitiatorPort());
        } else if (param.getName() != null) {
            initiator.setLabel(param.getName());
        }
    }

    /**
     * Creates a new instance of host.
     *
     * @param tenant the host parent tenant organization
     * @param param the input parameter containing the host attributes
     * @return an instance of {@link Host}
     */
    protected VirtualMachine createNewVM(TenantOrg tenant, VirtualMachineParam param) {
        VirtualMachine vm = new VirtualMachine();
        vm.setId(URIUtil.createId(VirtualMachine.class));
        vm.setTenant(tenant.getId());
        populateVMData(vm, param);
        if (!NullColumnValueGetter.isNullURI(vm.getCluster())) {
            Cluster cluster = _dbClient.queryObject(Cluster.class, vm.getCluster());
            if (ComputeSystemHelper.isClusterInExport(_dbClient, vm.getCluster()) && cluster.getAutoExportEnabled()) {
                String taskId = UUID.randomUUID().toString();
                ComputeSystemController controller = getController(ComputeSystemController.class, null);
                controller.addHostsToExport(Arrays.asList(vm.getId()), vm.getCluster(), taskId, null);
            } else {
                ComputeSystemHelper.updateInitiatorClusterName(_dbClient, vm.getCluster(), vm.getId());
            }
        }

        return vm;
    }

    /**
     * Populate an instance of host with the provided virtual machine parameter
     *
     * @param vm the virtual machine to be populated
     * @param param the parameter that contains the host attributes.
     */
    private void populateVMData(VirtualMachine vm, VirtualMachineParam param) {
        if (param.getName() != null) {
            vm.setLabel(param.getName());
        }
        if (param.getHostName() != null) {
            vm.setHostName(param.getHostName());
        }
        if (param.getCluster() != null) {
            vm.setCluster(param.getCluster());
        }
        if (param.getOsVersion() != null) {
            vm.setOsVersion(param.getOsVersion());
        }
        if (param.getUserName() != null) {
            vm.setUsername(param.getUserName());
        }
        if (param.getPassword() != null) {
            vm.setPassword(param.getPassword());
        }
        if (param.getPortNumber() != null) {
            vm.setPortNumber(param.getPortNumber());
        }
        if (param.getUseSsl() != null) {
            vm.setUseSSL(param.getUseSsl());
        }
        if (param.getType() != null) {
            vm.setType(param.getType());
        }
        if (param.getDiscoverable() != null) {
            vm.setDiscoverable(param.getDiscoverable());
        }

        if (param.getVcenterDataCenter() != null) {
            vm.setVcenterDataCenter(NullColumnValueGetter.isNullURI(param.getVcenterDataCenter()) ? NullColumnValueGetter.getNullURI()
                    : param.getVcenterDataCenter());
        }
        Cluster cluster = null;
        // make sure virtual machine data is consistent with the cluster
        if (!NullColumnValueGetter.isNullURI(param.getCluster())) {
            cluster = queryObject(Cluster.class, param.getCluster(), true);
            if (!NullColumnValueGetter.isNullURI(cluster.getVcenterDataCenter())) {
                vm.setVcenterDataCenter(cluster.getVcenterDataCenter());
            }
            if (!NullColumnValueGetter.isNullURI(cluster.getProject())) {
                vm.setProject(cluster.getProject());
            }
        }

        if (param.getBootVolume() != null) {
            vm.setBootVolumeId(NullColumnValueGetter.isNullURI(param.getBootVolume()) ? NullColumnValueGetter
                    .getNullURI() : param.getBootVolume());
        }

    }

    /**
     * Virtual Machine Discovery
     *
     * @param the Virtual Machine to be discovered.
     *            provided, a new taskId is generated.
     * @return the task used to track the discovery job
     */
    protected TaskResourceRep doDiscoverVM(VirtualMachine vm) {
        String taskId = UUID.randomUUID().toString();
        if (vm.getDiscoverable() != null && !vm.getDiscoverable()) {
            vm.setDiscoveryStatus(DataCollectionJobStatus.COMPLETE.name());
            _dbClient.updateObject(vm);
        }
        if ((vm.getDiscoverable() == null || vm.getDiscoverable())) {
            ComputeSystemController controller = getController(ComputeSystemController.class, "host");
            DiscoveredObjectTaskScheduler scheduler = new DiscoveredObjectTaskScheduler(
                    _dbClient, new DiscoverJobExec(controller));
            ArrayList<AsyncTask> tasks = new ArrayList<AsyncTask>(1);
            tasks.add(new AsyncTask(VirtualMachine.class, vm.getId(), taskId));

            TaskList taskList = scheduler.scheduleAsyncTasks(tasks);
            return taskList.getTaskList().iterator().next();
        } else {
            // if not discoverable, manually create a ready task
            Operation op = new Operation();
            op.setResourceType(ResourceOperationTypeEnum.DISCOVER_VIRTUAL_MACHINE);
            op.ready("Virtual machine is not discoverable");
            _dbClient.createTaskOpStatus(VirtualMachine.class, vm.getId(), taskId, op);
            return toTask(vm, taskId, op);
        }
    }

}
