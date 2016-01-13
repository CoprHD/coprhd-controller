/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource;

import static com.emc.storageos.api.mapper.DbObjectMapper.map;
import static com.emc.storageos.api.mapper.DbObjectMapper.toNamedRelatedResource;
import static com.emc.storageos.api.mapper.DbObjectMapper.toRelatedResource;
import static com.emc.storageos.api.mapper.HostMapper.map;
import static com.emc.storageos.api.mapper.TaskMapper.toTask;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import org.apache.commons.codec.digest.Md5Crypt;
import org.apache.commons.lang.StringUtils;
import org.apache.curator.framework.recipes.locks.InterProcessLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.storageos.api.mapper.TaskMapper;
import com.emc.storageos.api.mapper.functions.MapHost;
import com.emc.storageos.api.service.authorization.PermissionsHelper;
import com.emc.storageos.api.service.impl.resource.utils.AsyncTaskExecutorIntf;
import com.emc.storageos.api.service.impl.resource.utils.DiscoveredObjectTaskScheduler;
import com.emc.storageos.api.service.impl.resource.utils.HostConnectionValidator;
import com.emc.storageos.api.service.impl.resource.utils.VolumeIngestionUtil;
import com.emc.storageos.api.service.impl.response.BulkList;
import com.emc.storageos.api.service.impl.response.ResRepFilter;
import com.emc.storageos.computecontroller.ComputeController;
import com.emc.storageos.computesystemcontroller.ComputeSystemController;
import com.emc.storageos.computesystemcontroller.impl.ComputeSystemHelper;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.NamedElementQueryResultList;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.Cluster;
import com.emc.storageos.db.client.model.ComputeElement;
import com.emc.storageos.db.client.model.ComputeImage;
import com.emc.storageos.db.client.model.ComputeImage.ComputeImageStatus;
import com.emc.storageos.db.client.model.ComputeImageJob;
import com.emc.storageos.db.client.model.ComputeImageServer;
import com.emc.storageos.db.client.model.ComputeSystem;
import com.emc.storageos.db.client.model.ComputeVirtualPool;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.DiscoveredDataObject.DataCollectionJobStatus;
import com.emc.storageos.db.client.model.DiscoveredDataObject.RegistrationStatus;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.db.client.model.Host.ProvisioningJobStatus;
import com.emc.storageos.db.client.model.HostInterface;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.IpInterface;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.Task;
import com.emc.storageos.db.client.model.TenantOrg;
import com.emc.storageos.db.client.model.UCSServiceProfileTemplate;
import com.emc.storageos.db.client.model.VcenterDataCenter;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedExportMask;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume;
import com.emc.storageos.db.client.model.util.TaskUtils;
import com.emc.storageos.db.client.util.EndpointUtility;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.db.client.util.WWNUtility;
import com.emc.storageos.db.client.util.iSCSIUtility;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.imageservercontroller.ImageServerController;
import com.emc.storageos.imageservercontroller.impl.ImageServerUtils;
import com.emc.storageos.model.BulkIdParam;
import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.RelatedResourceRep;
import com.emc.storageos.model.ResourceOperationTypeEnum;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.TaskList;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.block.UnManagedExportMaskList;
import com.emc.storageos.model.block.UnManagedVolumeList;
import com.emc.storageos.model.compute.ComputeElementListRestRep;
import com.emc.storageos.model.compute.ComputeElementRestRep;
import com.emc.storageos.model.compute.ComputeSystemBulkRep;
import com.emc.storageos.model.compute.ComputeSystemRestRep;
import com.emc.storageos.model.compute.OsInstallParam;
import com.emc.storageos.model.host.BaseInitiatorParam;
import com.emc.storageos.model.host.HostBulkRep;
import com.emc.storageos.model.host.HostCreateParam;
import com.emc.storageos.model.host.HostList;
import com.emc.storageos.model.host.HostParam;
import com.emc.storageos.model.host.HostRestRep;
import com.emc.storageos.model.host.HostUpdateParam;
import com.emc.storageos.model.host.InitiatorCreateParam;
import com.emc.storageos.model.host.InitiatorList;
import com.emc.storageos.model.host.IpInterfaceCreateParam;
import com.emc.storageos.model.host.IpInterfaceList;
import com.emc.storageos.model.host.IpInterfaceParam;
import com.emc.storageos.model.host.IpInterfaceRestRep;
import com.emc.storageos.model.host.ProvisionBareMetalHostsParam;
import com.emc.storageos.security.audit.AuditLogManager;
import com.emc.storageos.security.authentication.StorageOSUser;
import com.emc.storageos.security.authorization.ACL;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.svcs.errorhandling.resources.BadRequestException;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.volumecontroller.AsyncTask;
import com.emc.storageos.volumecontroller.ControllerException;

/**
 * A service that provides APIs for viewing, updating and removing hosts and their
 * interfaces by authorized users.
 *
 */
@DefaultPermissions(readRoles = { Role.TENANT_ADMIN, Role.SYSTEM_MONITOR, Role.SYSTEM_ADMIN },
        writeRoles = { Role.TENANT_ADMIN },
        readAcls = { ACL.ANY })
@Path("/compute/hosts")
public class HostService extends TaskResourceService {

    // Logger
    protected final static Logger _log = LoggerFactory.getLogger(HostService.class);

    private static final String EVENT_SERVICE_TYPE = "host";
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
            return ResourceOperationTypeEnum.DISCOVER_HOST;
        }
    }

    /**
     * Gets the information for one host.
     *
     * @param id the URN of a ViPR Host
     * @brief Show Host
     * @return All the non-null attributes of the host.
     * @throws DatabaseException when a DB error occurs.
     */
    @GET
    @Path("/{id}")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public HostRestRep getHost(@PathParam("id") URI id) throws DatabaseException {
        Host host = queryObject(Host.class, id, false);
        // check the user permissions
        verifyAuthorizedInTenantOrg(host.getTenant(), getUserFromContext());
        return map(host);
    }

    /**
     * Lists the id and name for all the hosts that belong to the given tenant organization.
     *
     * @param tid the URN of a ViPR tenant organization
     * @prereq none
     * @brief List hosts
     * @return a list of hosts that belong to the tenant organization.
     * @throws DatabaseException when a DB error occurs
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public HostList listHosts(@QueryParam("tenant") final URI tid) throws DatabaseException {
        URI tenantId;
        StorageOSUser user = getUserFromContext();
        if (tid == null || StringUtils.isBlank(tid.toString())) {
            tenantId = URI.create(user.getTenantId());
        } else {
            tenantId = tid;
        }
        // this call validates the tenant id
        TenantOrg tenant = _permissionsHelper.getObjectById(tenantId, TenantOrg.class);
        ArgValidator.checkEntity(tenant, tenantId, isIdEmbeddedInURL(tenantId), true);

        // check the user permissions for this tenant org
        verifyAuthorizedInTenantOrg(tenantId, user);
        // get all host children
        HostList list = new HostList();
        list.setHosts(map(ResourceTypeEnum.HOST, listChildren(tenantId, Host.class, "label", "tenant")));
        return list;
    }

    /**
     * Updates one or more of the host attributes. Discovery is initiated
     * after the host is updated.
     *
     * @param id the URN of a ViPR Host
     * @param updateParam the parameter that has the attributes to be
     *            updated.
     * @brief Update Host Attributes
     * @return the host discovery async task representation.
     */
    @PUT
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.TENANT_ADMIN })
    @Path("/{id}")
    public TaskResourceRep updateHost(@PathParam("id") URI id,
            HostUpdateParam updateParam,
            @QueryParam("validate_connection") @DefaultValue("false") final Boolean validateConnection) {
        // update the host
        Host host = queryObject(Host.class, id, true);
        validateHostData(updateParam, host.getTenant(), host, validateConnection);
        boolean hasPendingTasks = hostHasPendingTasks(id);
        if (hasPendingTasks) {
            throw APIException.badRequests.cannotUpdateHost("another operation is in progress for this host");
        }
        URI oldClusterURI = host.getCluster();
        populateHostData(host, updateParam);
        if (updateParam.getHostName() != null) {
            ComputeSystemHelper.updateInitiatorHostName(_dbClient, host);
        }
        String taskId = UUID.randomUUID().toString();
        ComputeSystemController controller = getController(ComputeSystemController.class, null);

        Cluster oldCluster = NullColumnValueGetter.isNullURI(oldClusterURI) ? null : _dbClient.queryObject(Cluster.class, oldClusterURI);
        Cluster newCluster = NullColumnValueGetter.isNullURI(host.getCluster()) ? null : _dbClient.queryObject(Cluster.class,
                host.getCluster());

        // We only want to update the export group if we're changing the cluster during a host update
        if (updateParam.getCluster() != null) {
            if (!NullColumnValueGetter.isNullURI(oldClusterURI)
                    && NullColumnValueGetter.isNullURI(host.getCluster())
                    && ComputeSystemHelper.isClusterInExport(_dbClient, oldClusterURI)
                    && (oldCluster != null && oldCluster.getAutoExportEnabled())) {
                // Remove host from shared export
                controller.removeHostsFromExport(Arrays.asList(host.getId()), oldClusterURI, taskId);
            } else if (NullColumnValueGetter.isNullURI(oldClusterURI)
                    && !NullColumnValueGetter.isNullURI(host.getCluster())
                    && ComputeSystemHelper.isClusterInExport(_dbClient, host.getCluster())
                    && (newCluster != null && newCluster.getAutoExportEnabled())) {
                // Non-clustered host being added to a cluster
                controller.addHostsToExport(Arrays.asList(host.getId()), host.getCluster(), taskId, oldClusterURI);
            } else if (!NullColumnValueGetter.isNullURI(oldClusterURI)
                    && !NullColumnValueGetter.isNullURI(host.getCluster())
                    && !oldClusterURI.equals(host.getCluster())
                    && (ComputeSystemHelper.isClusterInExport(_dbClient, oldClusterURI)
                    || ComputeSystemHelper.isClusterInExport(_dbClient, host.getCluster()))
                    && ((oldCluster != null && oldCluster.getAutoExportEnabled())
                    || (newCluster != null && newCluster.getAutoExportEnabled()))) {
                // Clustered host being moved to another cluster
                controller.addHostsToExport(Arrays.asList(host.getId()), host.getCluster(), taskId, oldClusterURI);
            } else {
                ComputeSystemHelper.updateInitiatorClusterName(_dbClient, host.getCluster(), host.getId());
            }
        }
        /*
         * It is not merely enough to make the Host -> (Boot)Volume association
         * by setting the boot volume id on the Host. A volume is truly a boot
         * volume, iff it's exported to the Host with HLU 0. Hence an update to
         * the Host to set the boot volume should only really be made *after*
         * the volume has been exported to the Host.
         * TODO Consider making the
         * above requirement a hard one, by validating that such an export in
         * fact exists. For the time being following piece of code suffices
         * for satisfying some high level requirements, although it may not be
         * sufficient from an API purity standpoint
         */
        if (host.getComputeElement() != null && updateParam.getBootVolume() != null) {
            controller.setHostSanBootTargets(host.getId(), updateParam.getBootVolume());
        }

        _dbClient.updateAndReindexObject(host);
        auditOp(OperationTypeEnum.UPDATE_HOST, true, null,
                host.auditParameters());

        return doDiscoverHost(host);
    }

    /**
     * Discovers (refreshes) a host. This is an asynchronous call.
     *
     * @param id The URI of the host.
     * @prereq none
     * @brief Discover host
     * @return TaskResourceRep (asynchronous call)
     */
    @POST
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/discover")
    @CheckPermission(roles = { Role.TENANT_ADMIN })
    public TaskResourceRep discoverHost(@PathParam("id") URI id) {
        ArgValidator.checkFieldUriType(id, Host.class, "id");
        Host host = queryObject(Host.class, id, true);

        return doDiscoverHost(host);
    }

    /**
     * Host Discovery
     *
     * @param the Host to be discovered.
     *            provided, a new taskId is generated.
     * @return the task used to track the discovery job
     */
    protected TaskResourceRep doDiscoverHost(Host host) {
        String taskId = UUID.randomUUID().toString();
        if (host.getDiscoverable() != null && !host.getDiscoverable()) {
            host.setDiscoveryStatus(DataCollectionJobStatus.COMPLETE.name());
            _dbClient.persistObject(host);
        }
        if ((host.getDiscoverable() == null || host.getDiscoverable())) {
            ComputeSystemController controller = getController(ComputeSystemController.class, "host");
            DiscoveredObjectTaskScheduler scheduler = new DiscoveredObjectTaskScheduler(
                    _dbClient, new DiscoverJobExec(controller));
            ArrayList<AsyncTask> tasks = new ArrayList<AsyncTask>(1);
            tasks.add(new AsyncTask(Host.class, host.getId(), taskId));

            TaskList taskList = scheduler.scheduleAsyncTasks(tasks);
            return taskList.getTaskList().iterator().next();
        } else {
            // if not discoverable, manually create a ready task
            Operation op = new Operation();
            op.setResourceType(ResourceOperationTypeEnum.DISCOVER_HOST);
            op.ready("Host is not discoverable");
            _dbClient.createTaskOpStatus(Host.class, host.getId(), taskId, op);
            return toTask(host, taskId, op);
        }
    }

    /**
     * Validates the create/update host input data
     *
     * @param hostParam the input parameter
     * @param host the host being updated in case of update operation.
     *            This parameter must be null for create operations.n
     */
    protected void validateHostData(HostParam hostParam, URI tenanUri, Host host, Boolean validateConnection) {
        Cluster cluster = null;
        VcenterDataCenter dataCenter = null;
        Project project = null;
        Volume volume = null;

        // validate the host type
        if (hostParam.getType() != null) {
            ArgValidator.checkFieldValueFromEnum(hostParam.getType(), "Type", Host.HostType.class);
        }

        // validate the project is present, active, and in the same tenant org
        if (!NullColumnValueGetter.isNullURI(hostParam.getProject())) {
            project = queryObject(Project.class, hostParam.getProject(), true);
            if (!project.getTenantOrg().getURI().equals(tenanUri)) {
                throw APIException.badRequests.resourcedoesNotBelongToHostTenantOrg("project");
            }
        }
        if (!NullColumnValueGetter.isNullURI(hostParam.getBootVolume())) {
            volume = queryObject(Volume.class, hostParam.getBootVolume(), true);
            if (!volume.getTenant().getURI().equals(tenanUri)) {
                throw APIException.badRequests.resourcedoesNotBelongToHostTenantOrg("boot volume");
            }
        }
        // validate the cluster is present, active, and in the same tenant org
        if (!NullColumnValueGetter.isNullURI(hostParam.getCluster())) {
            cluster = queryObject(Cluster.class, hostParam.getCluster(),
                    true);
            if (!cluster.getTenant().equals(tenanUri)) {
                throw APIException.badRequests.resourcedoesNotBelongToHostTenantOrg("cluster");
            }
        }
        // validate the data center is present, active, and in the same tenant org
        if (!NullColumnValueGetter.isNullURI(hostParam.getVcenterDataCenter())) {
            dataCenter = queryObject(VcenterDataCenter.class, hostParam.getVcenterDataCenter(),
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
        if (host == null || (hostParam.getHostName() != null &&
                !hostParam.getHostName().equals(host.getHostName()))) {
            checkDuplicateAltId(Host.class, "hostName", EndpointUtility.changeCase(hostParam.getHostName()), "host");
        }
        // check the host label is not a duplicate
        if (host == null || (hostParam.getName() != null &&
                !hostParam.getName().equals(host.getLabel()))) {
            checkDuplicateLabel(Host.class, hostParam.getName());
        }
        // If the host project is being changed, check for active exports
        if (host != null && !areEqual(host.getProject(), hostParam.getProject())) {
            if (ComputeSystemHelper.isHostInUse(_dbClient, host.getId())) {
                throw APIException.badRequests.hostProjectChangeNotAllowed(host.getHostName());
            }
        }

        // Find out if the host should be discoverable by checking input and current values
        Boolean discoverable = hostParam.getDiscoverable() == null ?
                (host == null ? Boolean.FALSE : host.getDiscoverable()) :
                hostParam.getDiscoverable();

        // If discoverable, ensure username and password are set in the current host or parameters
        if (discoverable != null && discoverable) {
            String username = hostParam.getUserName() == null ?
                    (host == null ? null : host.getUsername()) :
                    hostParam.getUserName();
            String password = hostParam.getPassword() == null ?
                    (host == null ? null : host.getPassword()) :
                    hostParam.getPassword();
            ArgValidator.checkFieldNotNull(username, "username");
            ArgValidator.checkFieldNotNull(password, "password");

            Host.HostType hostType = Host.HostType.valueOf(hostParam.getType() == null ?
                    (host == null ? null : host.getType()) :
                    hostParam.getType());

            if (hostType != null && hostType == Host.HostType.Windows) {
                Integer portNumber = hostParam.getPortNumber() == null ?
                        (host == null ? null : host.getPortNumber()) :
                        hostParam.getPortNumber();

                ArgValidator.checkFieldNotNull(portNumber, "port_number");
            }
        }

        if (validateConnection != null && validateConnection == true) {
            if (!HostConnectionValidator.isHostConnectionValid(hostParam, host)) {
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
    public TaskResourceRep deactivateHost(@PathParam("id") URI id,
            @DefaultValue("false") @QueryParam("detach_storage") boolean detachStorage,
            @DefaultValue("false") @QueryParam("detach-storage") boolean detachStorageDeprecated,
            @DefaultValue("true") @QueryParam("deactivate_boot_volume") boolean deactivateBootVolume) throws DatabaseException {
        Host host = queryHost(_dbClient, id);
        ArgValidator.checkEntity(host, id, true);
        boolean hasPendingTasks = hostHasPendingTasks(id);
        if (hasPendingTasks) {
            throw APIException.badRequests.resourceCannotBeDeleted("Host with another operation in progress");
        }
        Cluster cluster = null;
        if (!NullColumnValueGetter.isNullURI(host.getCluster())) {
            cluster = _dbClient.queryObject(Cluster.class, host.getCluster());
        }
        boolean isHostInUse = ComputeSystemHelper.isHostInUse(_dbClient, host.getId());

        if (isHostInUse && cluster != null && !cluster.getAutoExportEnabled()) {
            throw APIException.badRequests.resourceInClusterWithAutoExportDisabled(Host.class.getSimpleName(), id);
        } else if (isHostInUse && !(detachStorage || detachStorageDeprecated)) {
            throw APIException.badRequests.resourceHasActiveReferences(Host.class.getSimpleName(), id);
        } else {
            String taskId = UUID.randomUUID().toString();
            Operation op = _dbClient.createTaskOpStatus(Host.class, host.getId(), taskId,
                    ResourceOperationTypeEnum.DELETE_HOST);
            ComputeSystemController controller = getController(ComputeSystemController.class, null);
            controller.detachHostStorage(host.getId(), true, deactivateBootVolume, taskId);
            host.setProvisioningStatus(Host.ProvisioningJobStatus.IN_PROGRESS.toString());
            _dbClient.persistObject(host);
            auditOp(OperationTypeEnum.DELETE_HOST, true, op.getStatus(),
                    host.auditParameters());
            return toTask(host, taskId, op);
        }
    }

    private boolean hostHasPendingTasks(URI id) {
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
     * Updates export groups and fileshare exports that are referenced by the given host by removing
     * the host reference, initiators and IP interfaces belonging to this host. Volumes are left intact.
     *
     * @param id the URN of a ViPR Host
     * @brief Detach storage from Host
     * @return OK if detaching completed successfully
     * @throws DatabaseException when a DB error occurs
     */
    @POST
    @Path("/{id}/detach-storage")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.TENANT_ADMIN })
    public TaskResourceRep detachStorage(@PathParam("id") URI id) throws DatabaseException {
        Host host = queryHost(_dbClient, id);
        ArgValidator.checkEntity(host, id, true);

        boolean hasPendingTasks = hostHasPendingTasks(id);
        if (hasPendingTasks) {
            throw APIException.badRequests.cannotDetachStorageForHost("another operation is in progress for this host");
        }

        String taskId = UUID.randomUUID().toString();
        Operation op = _dbClient.createTaskOpStatus(Host.class, host.getId(), taskId,
                ResourceOperationTypeEnum.DETACH_HOST_STORAGE);
        ComputeSystemController controller = getController(ComputeSystemController.class, null);
        controller.detachHostStorage(host.getId(), false, false, taskId);
        return toTask(host, taskId, op);
    }

    /**
     * Creates a new ip interface for a host.
     *
     * @param id the URN of a ViPR Host
     * @param createParam the details of the interfaces
     * @brief Create Host Interface Ip
     * @return the details of the host interface, including its id and link,
     *         when creation completes successfully.
     * @throws DatabaseException when a database error occurs
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.TENANT_ADMIN })
    @Path("/{id}/ip-interfaces")
    public IpInterfaceRestRep createIpInterface(@PathParam("id") URI id,
            IpInterfaceCreateParam createParam) throws DatabaseException {
        Host host = queryObject(Host.class, id, true);
        validateIpInterfaceData(createParam, null);
        IpInterface ipInterface = new IpInterface();
        ipInterface.setHost(host.getId());
        ipInterface.setId(URIUtil.createId(IpInterface.class));
        populateIpInterface(createParam, ipInterface);
        _dbClient.createObject(ipInterface);
        auditOp(OperationTypeEnum.CREATE_HOST_IPINTERFACE, true, null,
                ipInterface.auditParameters());
        return map(ipInterface);
    }

    /**
     * Validates the create/update IP interface operation input data.
     *
     * @param param the input parameter
     * @param ipInterface the IP interface being updated in case of update operation.
     *            This parameter must be null for create operations.
     */
    public void validateIpInterfaceData(IpInterfaceParam param, IpInterface ipInterface) {
        String protocol = param.findProtocol() != null ?
                param.findProtocol() : ipInterface.getProtocol();
        if (!HostInterface.Protocol.IPV4.toString().equals(protocol) && !HostInterface.Protocol.IPV6.toString().equals(protocol)) {
            throw APIException.badRequests.invalidIpProtocol();
        }

        // Validate the passed address matches the protocol
        if (param.findIPaddress() != null) { // it can be null on update
            String ipAddress = param.findIPaddress();
            if (HostInterface.Protocol.IPV4.toString().equals(protocol)) {
                ArgValidator.checkFieldValidIPV4(ipAddress, "ipAddress");
            } else {
                ArgValidator.checkFieldValidIPV6(ipAddress, "ipAddress");
            }
        }
        // last validate that the ip address is unique
        if (ipInterface == null || (param.findIPaddress() != null &&
                !param.findIPaddress().equalsIgnoreCase(ipInterface.getIpAddress()))) {
            checkDuplicateAltId(IpInterface.class, "ipAddress",
                    EndpointUtility.changeCase(param.findIPaddress()), "IP interface");
        }
    }

    /**
     * Gets the id and name for all the interfaces of a host.
     *
     * @param id the URN of a ViPR Host
     * @brief List Host Interfaces
     * @return a list of interfaces that belong to the host
     * @throws DatabaseException when a DB error occurs
     */
    @GET
    @Path("/{id}/ip-interfaces")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public IpInterfaceList getIpInterfaces(@PathParam("id") URI id) throws DatabaseException {
        Host host = queryObject(Host.class, id, false);
        // check the user permissions
        verifyAuthorizedInTenantOrg(host.getTenant(), getUserFromContext());
        // get the ip interfaces
        IpInterfaceList list = new IpInterfaceList();
        List<NamedElementQueryResultList.NamedElement> dataObjects = listChildren(id, IpInterface.class, "ipAddress", "host");
        for (NamedElementQueryResultList.NamedElement dataObject : dataObjects) {
            list.getIpInterfaces().add(toNamedRelatedResource(ResourceTypeEnum.IPINTERFACE,
                    dataObject.getId(), dataObject.getName()));
        }
        return list;
    }

    /**
     * Creates a new initiator for a host.
     *
     * @param id the URN of a ViPR Host
     * @param createParam the details of the initiator
     * @brief Create Host Initiator
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
        Host host = queryObject(Host.class, id, true);
        Cluster cluster = null;
        validateInitiatorData(createParam, null);
        // create and populate the initiator
        Initiator initiator = new Initiator();
        initiator.setHost(id);
        initiator.setHostName(host.getHostName());
        if (!NullColumnValueGetter.isNullURI(host.getCluster())) {
            cluster = queryObject(Cluster.class, host.getCluster(), false);
            initiator.setClusterName(cluster.getLabel());
        }
        initiator.setId(URIUtil.createId(Initiator.class));
        populateInitiator(initiator, createParam);

        _dbClient.createObject(initiator);
        String taskId = UUID.randomUUID().toString();
        Operation op = _dbClient.createTaskOpStatus(Initiator.class, initiator.getId(), taskId,
                ResourceOperationTypeEnum.ADD_HOST_INITIATOR);

        // if host in use. update export with new initiator
        if (ComputeSystemHelper.isHostInUse(_dbClient, host.getId())
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
     * Validates the create/update initiator operation input data.
     *
     * @param param the input parameter
     * @param initiator the initiator being updated in case of update operation.
     *            This parameter must be null for create operations.n
     */
    public void validateInitiatorData(BaseInitiatorParam param, Initiator initiator) {
        String protocol = param.getProtocol() != null ?
                param.getProtocol() : (initiator != null ? initiator.getProtocol() : null);
        String node = param.getNode() != null ? param.getNode() :
                (initiator != null ? initiator.getInitiatorNode() : null);
        String port = param.getPort() != null ? param.getPort() :
                (initiator != null ? initiator.getInitiatorPort() : null);
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
     * Gets the id and name for all the host initiators of a host.
     *
     * @param id the URN of a ViPR Host
     * @brief List Host Initiators
     * @return a list of initiators that belong to the host
     * @throws DatabaseException when a DB error occurs
     */
    @GET
    @Path("/{id}/initiators")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public InitiatorList getInitiators(@PathParam("id") URI id) throws DatabaseException {
        Host host = queryObject(Host.class, id, false);
        // check the user permissions
        verifyAuthorizedInTenantOrg(host.getTenant(), getUserFromContext());
        // get the initiators
        InitiatorList list = new InitiatorList();
        List<NamedElementQueryResultList.NamedElement> dataObjects = listChildren(id, Initiator.class, "iniport", "host");
        for (NamedElementQueryResultList.NamedElement dataObject : dataObjects) {
            list.getInitiators().add(toNamedRelatedResource(ResourceTypeEnum.INITIATOR,
                    dataObject.getId(), dataObject.getName()));
        }
        return list;
    }

    /**
     * Populates the interface using values in the parameter
     *
     * @param param the interface creation/update parameter that contains all the attributes
     * @param the IP interface to be to be populated with data.
     */
    public void populateIpInterface(
            IpInterfaceParam param, IpInterface ipInterface) {
        ipInterface.setIpAddress(param.findIPaddress());
        ipInterface.setProtocol(param.findProtocol());
        ipInterface.setScopeId(param.getScopeId());
        ipInterface.setPrefixLength(param.getPrefixLength());
        if (param.getNetmask() != null) {
            ipInterface.setNetmask(param.getNetmask().toString());
        }

        // Set label to ipAddress if not specified on create.
        if (ipInterface.getLabel() == null && param.getName() == null) {
            ipInterface.setLabel(ipInterface.getIpAddress());
        } else if (param.getName() != null) {
            ipInterface.setLabel(param.getName());
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
    protected Host createNewHost(TenantOrg tenant, HostParam param) {
        Host host = new Host();
        host.setId(URIUtil.createId(Host.class));
        host.setTenant(tenant.getId());
        populateHostData(host, param);
        if (!NullColumnValueGetter.isNullURI(host.getCluster())) {
            Cluster cluster = _dbClient.queryObject(Cluster.class, host.getCluster());
            if (ComputeSystemHelper.isClusterInExport(_dbClient, host.getCluster()) && cluster.getAutoExportEnabled()) {
                String taskId = UUID.randomUUID().toString();
                ComputeSystemController controller = getController(ComputeSystemController.class, null);
                controller.addHostsToExport(Arrays.asList(host.getId()), host.getCluster(), taskId, null);
            } else {
                ComputeSystemHelper.updateInitiatorClusterName(_dbClient, host.getCluster(), host.getId());
            }
        }

        return host;
    }

    /**
     * Populate an instance of host with the provided host parameter
     *
     * @param host the host to be populated
     * @param param the parameter that contains the host attributes.
     */
    private void populateHostData(Host host, HostParam param) {
        if (param.getName() != null) {
            host.setLabel(param.getName());
        }
        if (param.getHostName() != null) {
            host.setHostName(param.getHostName());
        }
        if (param.getCluster() != null) {
            host.setCluster(param.getCluster());
        }
        if (param.getOsVersion() != null) {
            host.setOsVersion(param.getOsVersion());
        }
        if (param.getUserName() != null) {
            host.setUsername(param.getUserName());
        }
        if (param.getPassword() != null) {
            host.setPassword(param.getPassword());
        }
        if (param.getPortNumber() != null) {
            host.setPortNumber(param.getPortNumber());
        }
        if (param.getUseSsl() != null) {
            host.setUseSSL(param.getUseSsl());
        }
        if (param.getType() != null) {
            host.setType(param.getType());
        }
        if (param.getDiscoverable() != null) {
            host.setDiscoverable(param.getDiscoverable());
        }
        // Commented out because host project is not currently used
        // if (param.project != null) {
        // host.setProject(NullColumnValueGetter.isNullURI(param.project) ?
        // NullColumnValueGetter.getNullURI() : param.project);
        // }
        if (param.getVcenterDataCenter() != null) {
            host.setVcenterDataCenter(NullColumnValueGetter.isNullURI(param.getVcenterDataCenter()) ?
                    NullColumnValueGetter.getNullURI() : param.getVcenterDataCenter());
        }
        Cluster cluster = null;
        // make sure host data is consistent with the cluster
        if (!NullColumnValueGetter.isNullURI(param.getCluster())) {
            cluster = queryObject(Cluster.class, param.getCluster(), true);
            if (!NullColumnValueGetter.isNullURI(cluster.getVcenterDataCenter())) {
                host.setVcenterDataCenter(cluster.getVcenterDataCenter());
            }
            if (!NullColumnValueGetter.isNullURI(cluster.getProject())) {
                host.setProject(cluster.getProject());
            }
        }

        if (param.getBootVolume() != null) {
            host.setBootVolumeId(NullColumnValueGetter.isNullURI(param.getBootVolume()) ? NullColumnValueGetter
                    .getNullURI() : param.getBootVolume());
        }

    }

    /**
     * Returns the instance of host for the given id. Throws {@link DatabaseException} when id is not a valid URI. Throws
     * {@link NotFoundException} when the host has
     * been delete.
     *
     * @param dbClient an instance of {@link DbClient}
     * @param id the URN of a ViPR Host to be fetched.
     * @return the instance of host for the given id.
     * @throws DatabaseException when a DB error occurs
     */
    protected Host queryHost(DbClient dbClient, URI id) throws DatabaseException {
        return queryObject(Host.class, id, false);
    }

    /**
     * Retrieve resource representations based on input ids.
     *
     * @param param POST data containing the id list.
     * @brief List data of host resources
     * @return list of representations.
     *
     * @throws DatabaseException When an error occurs querying the database.
     */
    @POST
    @Path("/bulk")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Override
    public HostBulkRep getBulkResources(BulkIdParam param) {
        return (HostBulkRep) super.getBulkResources(param);
    }

    @Override
    protected DataObject queryResource(URI id) {
        return queryObject(Host.class, id, false);
    }

    @Override
    protected URI getTenantOwner(URI id) {
        Host host = queryObject(Host.class, id, false);
        return host.getTenant();
    }

    @SuppressWarnings("unchecked")
    @Override
    public Class<Host> getResourceClass() {
        return Host.class;
    }

    @Override
    protected ResourceTypeEnum getResourceType() {
        return ResourceTypeEnum.HOST;
    }

    @Override
    public HostBulkRep queryBulkResourceReps(List<URI> ids) {

        Iterator<Host> _dbIterator =
                _dbClient.queryIterativeObjects(getResourceClass(), ids);
        return new HostBulkRep(BulkList.wrapping(_dbIterator, MapHost.getInstance()));
    }

    @Override
    public HostBulkRep queryFilteredBulkResourceReps(List<URI> ids) {
        Iterator<Host> _dbIterator =
                _dbClient.queryIterativeObjects(getResourceClass(), ids);
        BulkList.ResourceFilter filter = new BulkList.HostFilter(getUserFromContext(), _permissionsHelper);
        return new HostBulkRep(BulkList.wrapping(_dbIterator, MapHost.getInstance(), filter));
    }

    @Override
    protected boolean isZoneLevelResource() {
        return false;
    }

    @Override
    protected boolean isSysAdminReadableResource() {
        return true;
    }

    public static class HostResRepFilter<E extends RelatedResourceRep>
            extends ResRepFilter<E> {
        public HostResRepFilter(StorageOSUser user,
                PermissionsHelper permissionsHelper) {
            super(user, permissionsHelper);
        }

        @Override
        public boolean isAccessible(E resrep) {
            boolean ret = false;
            URI id = resrep.getId();

            Host obj = _permissionsHelper.getObjectById(id, Host.class);
            if (obj == null) {
                return false;
            }
            if (obj.getTenant().toString().equals(_user.getTenantId())) {
                return true;
            }

            ret = isTenantAccessible(obj.getTenant());
            return ret;
        }
    }

    /**
     * Get object specific permissions filter
     */
    @Override
    public ResRepFilter<? extends RelatedResourceRep> getPermissionFilter(StorageOSUser user,
            PermissionsHelper permissionsHelper)
    {
        return new HostResRepFilter(user, permissionsHelper);
    }

    /**
     * Gets the UnManagedVolumes exposed to a Host.
     *
     * @param id the URI of a ViPR Host
     * @return a list of UnManagedVolumes exposed to this host
     * @throws DatabaseException when a database error occurs
     */
    @GET
    @Path("/{id}/unmanaged-volumes")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public UnManagedVolumeList getUnmanagedVolumes(@PathParam("id") URI id) throws DatabaseException {
        Host host = queryObject(Host.class, id, false);

        // check the user permissions
        verifyAuthorizedInTenantOrg(host.getTenant(), getUserFromContext());

        // get the unmanaged volumes
        List<UnManagedVolume> unmanagedVolumes = VolumeIngestionUtil.findUnManagedVolumesForHost(id, _dbClient);

        UnManagedVolumeList list = new UnManagedVolumeList();
        for (UnManagedVolume volume : unmanagedVolumes) {
            list.getUnManagedVolumes()
                    .add(toRelatedResource(ResourceTypeEnum.UNMANAGED_VOLUMES, volume.getId()));
        }

        return list;
    }

    /**
     * Gets the UnManagedExportMasks found for a Host.
     *
     * @param id the URI of a ViPR Host
     * @return a list of UnManagedExportMasks found for the Host
     * @throws DatabaseException when a database error occurs
     */
    @GET
    @Path("/{id}/unmanaged-export-masks")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public UnManagedExportMaskList getUnmanagedExportMasks(@PathParam("id") URI id) throws DatabaseException {
        Host host = queryObject(Host.class, id, false);

        // check the user permissions
        verifyAuthorizedInTenantOrg(host.getTenant(), getUserFromContext());

        // get the unmanaged export masks
        List<UnManagedExportMask> uems = VolumeIngestionUtil.findUnManagedExportMasksForHost(id, _dbClient);

        UnManagedExportMaskList list = new UnManagedExportMaskList();
        for (UnManagedExportMask uem : uems) {
            list.getUnManagedExportMasks()
                    .add(toRelatedResource(ResourceTypeEnum.UNMANAGED_EXPORT_MASKS, uem.getId()));
        }

        return list;
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
    public TaskResourceRep createHost(HostCreateParam createParam,
            @QueryParam("validate_connection") @DefaultValue("false") final Boolean validateConnection) {
        // This is mostly to validate the tenant
        URI tid = createParam.getTenant();
        if ((tid == null) || tid.toString().isEmpty()) {
            _log.error("The tenant id is missing");
            throw APIException.badRequests.requiredParameterMissingOrEmpty("tenant");
        }

        TenantOrg tenant = _permissionsHelper.getObjectById(tid, TenantOrg.class);
        ArgValidator.checkEntity(tenant, tid, isIdEmbeddedInURL(tid), true);

        validateHostData(createParam, tid, null, validateConnection);

        // Create the host
        Host host = createNewHost(tenant, createParam);
        host.setRegistrationStatus(RegistrationStatus.REGISTERED.toString());
        _dbClient.createObject(host);
        auditOp(OperationTypeEnum.CREATE_HOST, true, null, host.auditParameters());
        return doDiscoverHost(host);
    }

    /**
     * Provision bare metal hosts by taking compute elements from the compute
     * virtual pool.
     *
     * @param param
     *            parameter for multiple host creation
     * @brief Provision bare metal hosts
     * @return TaskResourceRep (asynchronous call)
     * @throws DatabaseException
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/provision-bare-metal")
    public TaskList provisionBareMetalHosts(ProvisionBareMetalHostsParam param) throws DatabaseException {
        ComputeVirtualPool cvp = _dbClient.queryObject(ComputeVirtualPool.class, param.getComputeVpool());
        ArgValidator.checkEntity(cvp, param.getComputeVpool(), false);

        VirtualArray varray = _dbClient.queryObject(VirtualArray.class, param.getVarray());
        ArgValidator.checkEntity(varray, param.getVarray(), false);

        TenantOrg tenant = _dbClient.queryObject(TenantOrg.class, param.getTenant());
        ArgValidator.checkEntity(tenant, param.getTenant(), false);

        if (!NullColumnValueGetter.isNullURI(param.getCluster())) {
            Cluster cluster = _dbClient.queryObject(Cluster.class, param.getCluster());
            ArgValidator.checkEntity(cluster, param.getCluster(), false);
        }

        _log.debug("checking if CVP is accessible");
        _permissionsHelper.checkTenantHasAccessToComputeVirtualPool(tenant.getId(), cvp);
        validateHostNames(param);

        InterProcessLock lock = lockBladeReservation();

        List<String> ceList = null;
        try {
            ceList = takeComputeElementsFromPool(cvp, param.getHostNames().size(), varray, param.getCluster());
        } catch (Exception e) {
            _log.error("unable to takeComputeElementsFromPool", e);
            throw e;
        } finally {
            unlockBladeReservation(lock);
        }

        Set<Host> hosts = new HashSet<Host>();
        for (int i = 0; i < param.getHostNames().size(); i++) {
            Host host = populateHost(tenant, param.getHostNames().get(i), ceList.get(i), param.getCluster(), cvp.getId());
            hosts.add(host);
            _dbClient.createObject(host);
        }
        return createHostTasks(hosts, param.getComputeVpool(), param.getVarray());
    }

    private InterProcessLock lockBladeReservation() {
        InterProcessLock lock = _coordinator.getLock(BLADE_RESERVATION_LOCK_NAME);
        try {
            lock.acquire();
            _log.info("acquired BladeReservation lock");
        } catch (Exception e) {
            _log.error("failed to acquire BladeReservation lock", e);
            throw BadRequestException.badRequests.unableToLockBladeReservation();
        }
        return lock;
    }

    private void unlockBladeReservation(InterProcessLock lock) {
        try {
            lock.release();
            _log.info("unlocked BladeReservation");
        } catch (Exception e) {
            _log.error("could not unlock BladeReservation", e);
        }
    }

    private void validateHostNames(ProvisionBareMetalHostsParam param) {
        // check that the host names are valid
        for (int i = 0; i < param.getHostNames().size(); i++) {
            String hostName = param.getHostNames().get(i);
            hostName = hostName.trim();
            hostName = EndpointUtility.changeCase(hostName);
            if (EndpointUtility.isValidHostName(hostName)) {
                param.getHostNames().set(i, hostName);
            }
            else {
                throw APIException.badRequests.invalidHostName(hostName);
            }
        }

        // check that all host names are unique
        Set<String> set = new HashSet<String>();
        set.addAll(param.getHostNames());
        if (set.size() != param.getHostNames().size()) {
            throw APIException.badRequests.invalidHostNamesAreNotUnique();
        }
    }

    private TaskList createHostTasks(Set<Host> hosts, URI cvpUri, URI varray) {
        TaskList tl = new TaskList();
        Set<AsyncTask> tasks = new HashSet<AsyncTask>();

        for (Host host : hosts) {
            String taskId = UUID.randomUUID().toString();
            AsyncTask task = new AsyncTask(Host.class, host.getId(), taskId);
            Operation op = new Operation();
            op.setResourceType(ResourceOperationTypeEnum.CREATE_HOST);
            _dbClient.createTaskOpStatus(Host.class, host.getId(), task._opId, op);
            tasks.add(task);
            tl.getTaskList().add(TaskMapper.toTask(host, task._opId, op));

            host.setProvisioningStatus(Host.ProvisioningJobStatus.IN_PROGRESS.toString());

            auditOp(OperationTypeEnum.CREATE_HOST, true, AuditLogManager.AUDITOP_BEGIN, host.auditParameters());
        }

        /*
         * Persist the IN_PROGRESS ProvisioningJobStatus
         */
        _dbClient.persistObject(hosts);
        /*
         * Dispatch the request to the controller
         */
        ComputeController computeController = getController(ComputeController.class, null);
        computeController.createHosts(varray, cvpUri, tasks.toArray(new AsyncTask[0]));

        return tl;
    }

    private Map<URI, List<ComputeElement>> findComputeElementsUsedInCluster(URI clusterId) throws DatabaseException {
        Map<URI, List<ComputeElement>> computeSystemToComputeElementMap = new HashMap<URI, List<ComputeElement>>();

        HostList hostList = clusterService.getClusterHosts(clusterId);
        List<NamedRelatedResourceRep> list = hostList.getHosts();
        for (NamedRelatedResourceRep hostRep : list) {
            HostRestRep host = getHost(hostRep.getId());
            RelatedResourceRep computeElement = host.getComputeElement();
            if (computeElement == null) {
                // this can happen if cluster has hosts that were not provisioned by vipr
                continue;
            }
            ComputeElement ce = _dbClient.queryObject(ComputeElement.class, computeElement.getId());

            URI computeSystem = ce.getComputeSystem();
            List<ComputeElement> usedComputeElements;
            if (computeSystemToComputeElementMap.containsKey(computeSystem)) {
                usedComputeElements = computeSystemToComputeElementMap.get(computeSystem);
            }
            else {
                usedComputeElements = new ArrayList<ComputeElement>();
            }
            usedComputeElements.add(ce);
            computeSystemToComputeElementMap.put(computeSystem, usedComputeElements);
        }
        return computeSystemToComputeElementMap;
    }

    /*
     * Returns a map of compute system URI to compute elements available on that compute system
     */
    private Map<URI, List<URI>> findComputeElementsMatchingVarrayAndCVP(ComputeVirtualPool cvp, VirtualArray varray) {
        Map<URI, List<URI>> computeSystemToComputeElementsMap = new HashMap<URI, List<URI>>();

        _log.debug("Look up compute elements for cvp " + cvp.getId());
        List<String> cvpCEList = new ArrayList<String>();
        if (cvp.getMatchedComputeElements() != null) {
            Iterator<String> iter = cvp.getMatchedComputeElements().iterator();
            while (iter.hasNext()) {
                String uriStr = iter.next();
                cvpCEList.add(uriStr);
            }
        }
        // Find all SPTs assigned for this CVP and their corresponding ComputeSystems
        Map<URI, URI> cvpTemplatesMap = new HashMap<URI, URI>();
        if (cvp.getServiceProfileTemplates() != null) {
            for (String templateIdString : cvp.getServiceProfileTemplates()) {
                URI templateId = URI.create(templateIdString);
                UCSServiceProfileTemplate template = _dbClient.queryObject(UCSServiceProfileTemplate.class, templateId);
                if (template.getUpdating() == true) {
                    if (!computeSystemService.isUpdatingSPTValid(template, _dbClient)) {
                        throw APIException.badRequests.invalidUpdatingSPT(template.getLabel());
                    }
                    StringSet varrayIds = new StringSet();
                    varrayIds.add(varray.getId().toString());
                    if (!computeSystemService.isServiceProfileTemplateValidForVarrays(varrayIds, templateId)) {
                        throw APIException.badRequests.incompatibleSPT(template.getLabel(), varray.getLabel());
                    }
                }
                cvpTemplatesMap.put(template.getComputeSystem(), templateId);
            }
        }

        _log.debug("Look up compute systems for virtual array " + varray.getId());
        ComputeSystemBulkRep computeSystemBulkRep = virtualArrayService.getComputeSystems(varray.getId());

        if (computeSystemBulkRep.getComputeSystems() != null) {
            for (ComputeSystemRestRep computeSystemRestRep : computeSystemBulkRep.getComputeSystems()) {
                _log.debug("Found compute system " + computeSystemRestRep.getId() + " for virtual array " + varray.getId());
                if (!cvpTemplatesMap.containsKey(computeSystemRestRep.getId())) {
                    _log.info("The CVP has no service profile templates assigned from compute system " + computeSystemRestRep.getName()
                            + ". So no blades will be used from this compute system.");
                    continue;
                }
                ComputeElementListRestRep computeElementListRestRep = computeSystemService.getComputeElements(computeSystemRestRep.getId());
                if (computeElementListRestRep.getList() != null) {
                    List<URI> computeElementList = new ArrayList<URI>();
                    for (ComputeElementRestRep computeElementRestRep : computeElementListRestRep.getList()) {
                        _log.debug("Compute system contains compute element " + computeElementRestRep.getId());
                        for (String computeElement : cvpCEList) {
                            if (computeElement.equals(computeElementRestRep.getId().toString())) {
                                if (computeElementRestRep.getAvailable()
                                        && computeElementRestRep.getRegistrationStatus().equals(RegistrationStatus.REGISTERED.name())) {
                                    computeElementList.add(computeElementRestRep.getId());
                                    _log.debug("Added compute element " + computeElementRestRep.getId());
                                } else {
                                    _log.debug("found unavailable compute element" + computeElementRestRep.getId());
                                }
                            }
                        }
                    }
                    computeSystemToComputeElementsMap.put(computeSystemRestRep.getId(), computeElementList);
                }
            }
        } else {
            throw APIException.badRequests.noComputeSystemsFoundForVarray();
        }
        return computeSystemToComputeElementsMap;
    }

    private Map<URI, List<URI>> sortMapByNumberOfElements(Map<URI, List<URI>> computeSystemToComputeElementsMap) {
        HashMap<URI, List<URI>> sortedHashMap = new LinkedHashMap<URI, List<URI>>();
        Map<URI, Integer> computeSystemToNumComputeElementsMap = new HashMap<URI, Integer>();
        for (URI key : computeSystemToComputeElementsMap.keySet()) {
            List<URI> computeElements = computeSystemToComputeElementsMap.get(key);
            int count = computeElements.size();
            computeSystemToNumComputeElementsMap.put(key, count);
        }

        List<Map.Entry<URI, Integer>> list = new LinkedList<Map.Entry<URI, Integer>>(
                computeSystemToNumComputeElementsMap.entrySet());
        Collections.sort(list, new Comparator<Map.Entry<URI, Integer>>() {
            @Override
            public int compare(Map.Entry<URI, Integer> o1, Map.Entry<URI, Integer> o2) {
                return o1.getValue().compareTo(o2.getValue());
            }
        });

        for (Iterator<Map.Entry<URI, Integer>> it = list.iterator(); it.hasNext();) {
            Map.Entry<URI, Integer> entry = it.next();
            URI key = entry.getKey();
            sortedHashMap.put(key, computeSystemToComputeElementsMap.get(key));
        }
        return sortedHashMap;
    }

    private List<String> takeComputeElementsFromPool(ComputeVirtualPool cvp, int numHosts, VirtualArray varray, URI clusterId) {
        List<URI> selectedCEsList = new ArrayList<URI>();
        List<String> bladeSelections = new ArrayList<String>();
        // Map of compute systems to compute elements from this Compute system used in this cluster
        Map<URI, List<ComputeElement>> usedComputeElementsMap = findComputeElementsUsedInCluster(clusterId);
        // Map of compute systems to compute elements from this Compute system that are available in this cvp
        Map<URI, List<URI>> computeSystemToComputeElementsMap = findComputeElementsMatchingVarrayAndCVP(cvp, varray);

        int numRequiredCEs = numHosts;
        int totalAvailableCEs = 0;

        List<URI> availableCEList = new ArrayList<URI>();

        // If total # of available CEs less than required, throw exception
        for (URI key : computeSystemToComputeElementsMap.keySet()) {
            List<URI> computeElements = computeSystemToComputeElementsMap.get(key);
            totalAvailableCEs = totalAvailableCEs + computeElements.size();
        }
        if (totalAvailableCEs < numRequiredCEs) {
            throw APIException.badRequests.notEnoughComputeElementsInPool();
        }
        // First try to pick blades from compute systems already used for this cluster.
        if (!usedComputeElementsMap.isEmpty()) {
            _log.debug("first try to pick blades from compute systems already used for this cluster");
            Set<URI> usedComputeSystems = usedComputeElementsMap.keySet();
            for (URI uri : usedComputeSystems) {
                _log.debug("Looking in used compute system:" + uri);
                availableCEList = computeSystemToComputeElementsMap.get(uri);
                if (availableCEList.size() <= numRequiredCEs) {
                    selectedCEsList.addAll(availableCEList);
                    numRequiredCEs = numRequiredCEs - availableCEList.size();
                    _log.debug("Picked all available " + availableCEList.size() + " blades from compute system");
                }
                else {
                    List<URI> selections = pickBladesByStrafingAlgorithm(availableCEList, numRequiredCEs,
                            usedComputeElementsMap.get(uri));
                    selectedCEsList.addAll(selections);
                    numRequiredCEs = numRequiredCEs - selections.size();
                    _log.debug("Picked " + selections.size() + " blades from compute system");
                }
                // Remove this compute system and its elements from map since they have been considered.
                computeSystemToComputeElementsMap.remove(uri);
            }
        }
        // If we have required number of hosts, return
        if (numRequiredCEs == 0) {
            for (URI uri : selectedCEsList) {
                bladeSelections.add(uri.toString());
            }
            setCeUnavailable(bladeSelections);
            return bladeSelections;
        }
        // Else sort compute systems by ascending number of compute elements available.
        Map<URI, List<URI>> sortedMap = sortMapByNumberOfElements(computeSystemToComputeElementsMap);
        _log.debug("Compute Systems sorted by number of available Compute elements:");
        for (URI key : sortedMap.keySet()) {
            _log.debug("computeSystem: " + key);
            List<URI> computeElements = sortedMap.get(key);
            int count = computeElements.size();
            if (count == numHosts) {
                _log.debug("found required number of blades in compute system:" + key);
                selectedCEsList.addAll(computeElements);
                numRequiredCEs = 0;
                break;
            }
            else if (numHosts < count) {
                _log.debug("Taking " + numHosts + " blades from compute system: " + key + " . Need no more.");
                // pick n blades from m available blades.
                availableCEList.addAll(computeElements);
                selectedCEsList.addAll(pickBladesByStrafingAlgorithm(availableCEList, numRequiredCEs,
                        usedComputeElementsMap.get(key)));
                numRequiredCEs = numRequiredCEs - numHosts;
                break;
            }
        }

        if (numRequiredCEs > 0) {

            _log.debug("No single Compute System has enough compute elements. So pick from multiple.");
            for (URI key : sortedMap.keySet()) {
                _log.debug("computeSystem: " + key);
                List<URI> computeElements = sortedMap.get(key);
                int count = computeElements.size();
                if (numRequiredCEs >= count) {
                    selectedCEsList.addAll(computeElements);
                    _log.debug("Taking " + count + " blades from compute system: " + key);
                    numRequiredCEs = numRequiredCEs - count;
                    if (numRequiredCEs == 0) {
                        _log.debug("Need no more");
                        break;
                    }
                }
                else {
                    availableCEList.addAll(computeElements);
                    _log.debug("Pick " + numRequiredCEs + " blades from " + count + " blades on compute system: " + key);
                    // pick n blades from m available blades.
                    selectedCEsList.addAll(pickBladesByStrafingAlgorithm(availableCEList, numRequiredCEs, usedComputeElementsMap.get(key)));
                    numRequiredCEs = 0;
                    break;
                }
            }
        }

        for (URI uri : selectedCEsList) {
            bladeSelections.add(uri.toString());
        }
        setCeUnavailable(bladeSelections);
        return bladeSelections;
    }

    private void setCeUnavailable(List<String> ceUriStrs) {
        List<URI> ceUris = URIUtil.toURIList(ceUriStrs);
        if (ceUris == null) {
            return;
        }
        List<ComputeElement> ceList = _dbClient.queryObject(ComputeElement.class, ceUris);
        for (ComputeElement ce : ceList) {
            ce.setAvailable(false);
        }
        _dbClient.persistObject(ceList);
    }

    private List<URI> pickBladesByStrafingAlgorithm(List<URI> availableList, int numRequiredBlades, List<ComputeElement> usedCEList)
            throws DatabaseException {
        if (usedCEList == null) {
            usedCEList = new ArrayList<ComputeElement>();
        }
        _log.debug("In pickBladesByStrafingAlgorithm");
        List<URI> selectedCEList = new ArrayList<URI>();
        List<ComputeElement> availableCEList = new ArrayList<ComputeElement>();

        // Find numChassis and numSlot (M and N)
        // Populate M x N matrix of 1s for available blades
        // Build chassisUsageMap : chassisUsageCount to Set of chassisIds . sort by key asc
        // Build slotUsageMap: slotUsageCount to set of SlotIds. sort by key desc
        // Build slotCountMap: slotId to slotCount
        // ///Build slot availabilityMap availableSlotsCount to Set of slotIds.
        // boolean useUsedSlots = false if usedCEsList is empty
        // For each chassisCount in chassisUsageMap, loop through the chassises wit that count
        // if useUsedSlots is true, On each of those chassis, find the slot that is available and has maximum usage count
        // if useUsedSlots is false, on each of those chassis, find the slot that has maximum available slots
        for (URI uri : availableList) {
            ComputeElement computeElement = _dbClient.queryObject(ComputeElement.class, uri);
            _log.debug("computeElement:" + computeElement.getLabel() + " chassisId:" + computeElement.getChassisId() + " slotId:"
                    + computeElement.getSlotId());
            // ComputeSystem cs = _dbClient.queryObject(ComputeSystem.class,computeElement.getComputeSystem());
            availableCEList.add(computeElement);
        }
        int numChassis = 0;
        int numSlot = 0;
        for (ComputeElement ce : availableCEList) {
            int chassisId = Integer.parseInt(ce.getChassisId());
            long slotId = ce.getSlotId();
            if (chassisId > numChassis) {
                numChassis = chassisId;
            }
            if (slotId > numSlot) {
                numSlot = (int) slotId;
            }
        }
        for (ComputeElement ce : usedCEList) {
            int chassisId = Integer.parseInt(ce.getChassisId());
            long slotId = ce.getSlotId();
            if (chassisId > numChassis) {
                numChassis = chassisId;
            }
            if (slotId > numSlot) {
                numSlot = (int) slotId;
            }
        }
        int M = numChassis + 1;
        int N = numSlot + 1;
        _log.debug("M:" + M + " N:" + N);
        int[][] availableBlades = new int[M][N];
        Map<String, URI> availableBladesByChassisIdAndSlotId = new HashMap<String, URI>();
        for (ComputeElement ce : availableCEList) {
            int chassisId = Integer.parseInt(ce.getChassisId());
            long l_slotId = ce.getSlotId();
            int slotId = (int) l_slotId;
            if (ce.getAvailable()) {
                availableBlades[chassisId][slotId] = 1;
                availableBladesByChassisIdAndSlotId.put(chassisId + "," + slotId, ce.getId());
            } else {
                availableBlades[chassisId][slotId] = 0;
            }
        }
        int[][] usedBlades = new int[M][N];
        for (ComputeElement ce : usedCEList) {
            int chassisId = Integer.parseInt(ce.getChassisId());
            long l_slotId = ce.getSlotId();
            int slotId = (int) l_slotId;
            usedBlades[chassisId][slotId] = 1;
        }
        Map<Integer, Integer> chassisIdToCountMap = new HashMap<Integer, Integer>();
        Map<Integer, Set<Integer>> chassisUsageMap = new TreeMap<Integer, Set<Integer>>();
        List<Integer> unusedChassisIds = new ArrayList<Integer>();
        for (int chassisId = 1; chassisId <= numChassis; chassisId++) {
            int chassisUsageCount = 0;
            for (int slotId = 1; slotId <= numSlot; slotId++) {
                chassisUsageCount = chassisUsageCount + usedBlades[chassisId][slotId];
            }
            Integer key = Integer.valueOf(chassisUsageCount);
            Set<Integer> chassisIdSet = chassisUsageMap.get(key);
            if (chassisIdSet == null) {
                chassisIdSet = new HashSet<Integer>();
            }
            chassisIdSet.add(Integer.valueOf(chassisId));
            chassisUsageMap.put(chassisUsageCount, chassisIdSet);
            chassisIdToCountMap.put(Integer.valueOf(chassisId), Integer.valueOf(chassisUsageCount));
            if (chassisUsageCount == 0) {
                unusedChassisIds.add(chassisId);
            }
            _log.debug("chassisId:" + chassisId + " usageCount:" + chassisUsageCount);
        }
        for (int chassisId : unusedChassisIds) {
            _log.debug("unusedChassis:" + chassisId);
        }

        Map<Integer, Integer> slotIdToCountMap = new HashMap<Integer, Integer>();
        NavigableMap<Integer, Set<Integer>> slotUsageMap = new TreeMap<Integer, Set<Integer>>();
        for (int slotId = 1; slotId <= numSlot; slotId++) {
            int slotUsageCount = 0;
            for (int chassisId = 1; chassisId <= numChassis; chassisId++) {
                slotUsageCount = slotUsageCount + usedBlades[chassisId][slotId];
            }
            Integer key = Integer.valueOf(slotUsageCount);
            Set<Integer> slotIdSet = slotUsageMap.get(key);
            if (slotIdSet == null) {
                slotIdSet = new HashSet<Integer>();
            }
            slotIdSet.add(Integer.valueOf(slotId));
            slotUsageMap.put(slotUsageCount, slotIdSet);
            slotIdToCountMap.put(Integer.valueOf(slotId), Integer.valueOf(slotUsageCount));
            _log.debug("slotId:" + slotId + " usageCount:" + slotUsageCount);
        }
        // sort the slotUsageMap by its keys in descending order
        Map<Integer, Set<Integer>> descSlotUsageMap = slotUsageMap.descendingMap();

        boolean useUsedSlots = true;

        if (usedCEList.isEmpty()) {
            useUsedSlots = false;
        }
        boolean avoidUsedChassis = true;
        if (unusedChassisIds.isEmpty()) {
            avoidUsedChassis = false;
        }
        List<String> selectedBlades = new ArrayList<String>();

        while (numRequiredBlades > 0) {
            if (!useUsedSlots) {// No slots used so far.
                // Now find the slot or slots which are max available
                _log.debug("No slots used so far. Find max available slot.");
                int selectedSlotId = 0;
                int maxSum = 0;
                for (int slotId = 1; slotId <= numSlot; slotId++) {
                    int sum = 0;
                    for (int chassisId = 1; chassisId <= numChassis; chassisId++) {
                        sum = sum + availableBlades[chassisId][slotId];
                    }
                    if (sum > maxSum) {
                        selectedSlotId = slotId;
                        maxSum = sum;
                        if (sum >= numRequiredBlades) { // Stop when we find first slot that enough free blades to satisfy numRequiredBlades
                            break;
                        }
                    }
                }
                _log.debug("max available slot is:" + selectedSlotId);
                // From selected slot pick as many blades as possible
                for (int chassisId = 1; chassisId <= numChassis; chassisId++) {
                    if (availableBlades[chassisId][selectedSlotId] == 1) {
                        _log.debug("selected blade " + chassisId + "/" + selectedSlotId);
                        selectedBlades.add(chassisId + "," + selectedSlotId);
                        availableBlades[chassisId][selectedSlotId] = 0;
                        numRequiredBlades--;
                        if (numRequiredBlades == 0) {
                            break;
                        }
                    }
                }
                useUsedSlots = true;
            } else {
                Set<Integer> preferredChassisIds = new HashSet<Integer>();
                if (avoidUsedChassis) {
                    _log.debug("Pick from unused chassis first");
                    preferredChassisIds.addAll(unusedChassisIds);
                    // Pick blades from the max used slots on the preferred chassis
                    // Starting with max used slot, loop thru slots to find ones that are available on preferred chassis
                    Iterator<Integer> iter = descSlotUsageMap.keySet().iterator();
                    while (iter.hasNext()) {
                        Integer slotUsageCount = iter.next();
                        Set<Integer> preferredSlotIds = descSlotUsageMap.get(slotUsageCount);// slotIds with max usage
                        /*
                         * This is not part of the blade strafing algorithm in UIM. Hence commenting out.
                         * Uncomment if this modification to algorithm is required.
                         * //Order these slots that have same slotUSageCount, in descending order of number of available blades on unused
                         * chassis.
                         * NavigableMap<Integer,Set<Integer> >slotIdAvailabilityMap = new TreeMap<Integer,Set<Integer>>();
                         * for (int slotId : preferredSlotIds){
                         * int availabilityCount = 0;
                         * for (int chassisId : preferredChassisIds){
                         * if (availableBlades[chassisId][slotId] ==1){
                         * availabilityCount++;
                         * }
                         * }
                         * Set<Integer> slotIdSet = slotIdAvailabilityMap.get(availabilityCount);
                         * if (slotIdSet ==null){
                         * slotIdSet = new HashSet<Integer>();
                         * }
                         * slotIdSet.add(slotId);
                         * slotIdAvailabilityMap.put(availabilityCount,slotIdSet);
                         * }
                         *
                         * Iterator<Integer> iterator = slotIdAvailabilityMap.keySet().iterator();
                         * while(iterator.hasNext()){
                         * Integer availabilityCount = iterator.next();
                         * Set<Integer> mostPreferredSlotIds = slotIdAvailabilityMap.get(availabilityCount);
                         * for (int slotId : mostPreferredSlotIds){
                         * Comment the next statement - for(int slotId : preferredSlotIds) when uncommenting this section
                         */
                        for (int slotId : preferredSlotIds) {
                            _log.debug("preferred slotId: " + slotId + " with usage count: " + slotUsageCount);
                            for (int chassisId : preferredChassisIds) {
                                if (availableBlades[chassisId][slotId] == 1) {
                                    _log.debug("selected Blade: " + chassisId + "/" + slotId);
                                    selectedBlades.add(chassisId + "," + slotId);
                                    availableBlades[chassisId][slotId] = 0;
                                    numRequiredBlades--;
                                    unusedChassisIds.remove((Integer) chassisId);
                                    Integer currentCount = chassisIdToCountMap.get(chassisId);
                                    chassisIdToCountMap.put(chassisId, currentCount + 1);
                                    // updateChassisUsageMap
                                    chassisUsageMap = updateChassisUsageMap(chassisIdToCountMap);
                                    if (numRequiredBlades == 0) {
                                        break;
                                    }
                                }
                            }
                            if (numRequiredBlades == 0) {
                                break;
                            }
                        }
                    }
                    avoidUsedChassis = false;
                } else {
                    _log.debug(" No more blades from unused chassis. Pick from least used chassis.");
                    Iterator<Integer> iter = chassisUsageMap.keySet().iterator();
                    while (iter.hasNext()) {
                        Integer chassisUsageCount = iter.next();
                        preferredChassisIds = chassisUsageMap.get(chassisUsageCount);// chassisIds with least usage
                        // Pick blades from preferredChassis
                        for (int chassisId : preferredChassisIds) {
                            int count = 0;
                            _log.debug("preferred chassis :" + chassisId + "with usgae count:" + chassisUsageCount);
                            for (int slotId = 1; slotId <= numSlot; slotId++) {
                                if (availableBlades[chassisId][slotId] == 1) {
                                    numRequiredBlades--;
                                    _log.debug("selected blade: " + chassisId + "/" + slotId);
                                    selectedBlades.add(chassisId + "," + slotId);
                                    count++;
                                    if (preferredChassisIds.size() > 1) { // pick one from each if there are multiple preferred chassis
                                        break;
                                    }
                                    if (numRequiredBlades == 0) {
                                        break;
                                    }
                                }
                            }
                            if (count > 0) {
                                unusedChassisIds.remove((Integer) chassisId);
                                Integer currentCount = chassisIdToCountMap.get(chassisId);
                                chassisIdToCountMap.put(chassisId, currentCount + count);
                                // updateChassisUsageMap
                                chassisUsageMap = updateChassisUsageMap(chassisIdToCountMap);
                            }
                            if (numRequiredBlades == 0) {
                                break;
                            }

                        }
                        if (numRequiredBlades == 0) {
                            break;
                        }
                    }
                }

            }

        }
        StringBuffer sbuf = new StringBuffer();
        sbuf.append("Selected Blades are:\n");
        for (String blade : selectedBlades) {
            sbuf.append("Blade " + blade);
            URI ceURI = availableBladesByChassisIdAndSlotId.get(blade);
            selectedCEList.add(ceURI);
        }
        _log.debug(sbuf.toString());
        return selectedCEList;
    }

    private NavigableMap<Integer, Set<Integer>> updateChassisUsageMap(Map<Integer, Integer> chassisIdToCountMap) {
        NavigableMap<Integer, Set<Integer>> chassisUsageMap = new TreeMap<Integer, Set<Integer>>();
        for (Map.Entry<Integer, Integer> entry : chassisIdToCountMap.entrySet()) {
            int chassisId = entry.getKey();
            int chassisCount = entry.getValue();
            Set<Integer> chassisNumberSet = chassisUsageMap.get(chassisCount);
            if (chassisNumberSet == null) {
                chassisNumberSet = new HashSet<Integer>();
            }
            chassisNumberSet.add(chassisId);
            chassisUsageMap.put(chassisCount, chassisNumberSet);
        }
        return chassisUsageMap;
    }

    private Host populateHost(TenantOrg t, String name, String ceId, URI clusterId, URI cvpId) {
        URI ceUri = URI.create(ceId);
        Host host = new Host();
        host.setId(URIUtil.createId(Host.class));
        host.setTenant(t.getId());
        host.setHostName(name);
        host.setLabel(name);
        host.setType(Host.HostType.No_OS.name());
        host.setDiscoveryStatus(DiscoveredDataObject.DataCollectionJobStatus.CREATED.name());
        host.setDiscoverable(false);
        host.setComputeElement(ceUri);
        host.setComputeVirtualPoolId(cvpId);
        if (clusterId != null) {
            host.setCluster(clusterId);
        }
        return host;
    }

    /**
     * Install operating system on the host.
     *
     * @param hostId
     *            host URI
     * @param param
     *            OS install data
     * @brief Install operating system on the host
     * @return TaskResourceRep (asynchronous call)
     */
    @PUT
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/os-install")
    public TaskResourceRep osInstall(@PathParam("id") URI hostId, OsInstallParam param) {
        // validate params
        ArgValidator.checkFieldUriType(hostId, Host.class, "id");
        ArgValidator.checkFieldNotNull(param.getComputeImage(), "compute_image");

        // get data
        ComputeImage img = queryObject(ComputeImage.class, param.getComputeImage(), true);
        ArgValidator.checkEntity(img, param.getComputeImage(),
                isIdEmbeddedInURL(param.getComputeImage()));
        if (!ComputeImageStatus.AVAILABLE.name().equals(img.getComputeImageStatus())) {
            throw APIException.badRequests.invalidParameterComputeImageIsNotAvailable(img.getId());
        }

        Host host = queryObject(Host.class, hostId, true);
        ArgValidator.checkEntity(host, hostId, isIdEmbeddedInURL(hostId));

        // only support os install on hosts with compute elements
        if (host.getComputeElement() == null) {
            throw APIException.badRequests.invalidParameterHostHasNoComputeElement();
        }

        if (!host.getType().equals(Host.HostType.No_OS.name()) && !param.getForceInstallation()) {
            throw APIException.badRequests.invalidParameterHostAlreadyHasOs(host.getType());
        }

        if (!StringUtils.isNotBlank(param.getRootPassword())) {
            throw APIException.badRequests.hostPasswordNotSet();
        } else {
            host.setPassword(param.getRootPassword());
            host.setUsername("root");
        }

        // check that CS has os install network
        ComputeElement ce = queryObject(ComputeElement.class, host.getComputeElement(), true);
        ArgValidator.checkEntity(ce, host.getComputeElement(), isIdEmbeddedInURL(host.getComputeElement()));

        if (ce.getUuid() == null) {
            throw APIException.badRequests.computeElementHasNoUuid();
        }

        ComputeSystem cs = queryObject(ComputeSystem.class, ce.getComputeSystem(), true);
        ArgValidator.checkEntity(cs, ce.getComputeSystem(), isIdEmbeddedInURL(ce.getComputeSystem()));

        verifyImagePresentOnImageServer(cs, img);

        if (!StringUtils.isNotBlank(cs.getOsInstallNetwork())) {
            throw APIException.badRequests.osInstallNetworkNotSet();
        }

        if (!cs.getVlans().contains(cs.getOsInstallNetwork())) {
            throw APIException.badRequests.osInstallNetworkNotValid(cs.getOsInstallNetwork());
        }

        // check that there is no os install in progress for this host
        URIQueryResultList jobUriList = new URIQueryResultList();
        _dbClient.queryByConstraint(ContainmentConstraint.Factory.getComputeImageJobsByHostConstraint(host.getId()),
                jobUriList);
        Iterator<URI> iterator = jobUriList.iterator();
        while (iterator.hasNext()) {
            ComputeImageJob existingJob = _dbClient.queryObject(ComputeImageJob.class, iterator.next());
            if (!existingJob.getInactive() && existingJob.getJobStatus().equals(ComputeImageJob.JobStatus.CREATED.name())) {
                throw APIException.badRequests.osInstallAlreadyInProgress();
            }
        }

        // openssl passwd -1 (MD5 encryption of password)
        String passwordHash = Md5Crypt.md5Crypt(host.getPassword().getBytes());

        // create session
        ComputeImageJob job = new ComputeImageJob();
        job.setId(URIUtil.createId(ComputeImageJob.class));
        job.setComputeImageId(img.getId());
        job.setHostId(host.getId());
        job.setPasswordHash(passwordHash);
        job.setHostName(param.getHostName());
        job.setHostIp(param.getHostIp());
        job.setNetmask(param.getNetmask());
        job.setGateway(param.getGateway());
        job.setNtpServer(param.getNtpServer());
        job.setDnsServers(param.getDnsServers());
        job.setManagementNetwork(param.getManagementNetwork());
        job.setPxeBootIdentifier(ImageServerUtils.uuidFromString(host.getUuid()).toString());
        job.setComputeImageServerId(cs.getComputeImageServer());

        // volume id is optional
        if (!NullColumnValueGetter.isNullURI(param.getVolume())
                || !NullColumnValueGetter.isNullURI(host.getBootVolumeId())) {
            Volume vol = null;
            if (!NullColumnValueGetter.isNullURI(param.getVolume())) {
                vol = queryObject(Volume.class, param.getVolume(), true);
                host.setBootVolumeId(vol.getId());
            }
            else {
                vol = queryObject(Volume.class, host.getBootVolumeId(), true);
            }
            job.setVolumeId(vol.getId());
            StorageSystem st = queryObject(StorageSystem.class, vol.getStorageController(), true);
            // VMAX and VNX volumes use UUID to identify volumes
            // XtremIO uses some other ID type (e.g. 514f0c5dc9600016)
            if (st != null && DiscoveredDataObject.Type.xtremio.name().equals(st.getSystemType())) {
                _log.info("xtremio volume id {}", vol.getNativeId());
                job.setBootDevice(vol.getNativeId());
            }
            else {
                _log.info("volume id {}", vol.getWWN());
                job.setBootDevice(ImageServerUtils.uuidFromString(vol.getWWN()).toString());
            }
        }
        host.setProvisioningStatus(ProvisioningJobStatus.IN_PROGRESS.toString());
        _dbClient.persistObject(host);
        _dbClient.createObject(job);

        // create task
        String taskId = UUID.randomUUID().toString();
        Operation op = new Operation();
        op.setResourceType(ResourceOperationTypeEnum.INSTALL_OPERATING_SYSTEM);
        _dbClient.createTaskOpStatus(Host.class, host.getId(), taskId, op);

        ImageServerController controller = getController(ImageServerController.class, null);

        AsyncTask task = new AsyncTask(Host.class, host.getId(), taskId);

        try {
            controller.installOperatingSystem(task, job.getId());
        } catch (InternalException e) {
            _log.error("Did not install OS due to controller error", e);
            job.setJobStatus(ComputeImageJob.JobStatus.FAILED.name());
            _dbClient.persistObject(job);
            _dbClient.error(Host.class, host.getId(), taskId, e);
        }

        return toTask(host, taskId, op);
    }

    /**
     * Method to check if the selected image is present on the
     * ComputeImageServer which is associated with the CoputeSystem
     *
     * @param cs {@link  ComputeSystem}
     * @param img {@link ComputeImage} instance selected
     * @throws APIException
     */
    private void verifyImagePresentOnImageServer(ComputeSystem cs,
            ComputeImage img) throws APIException {

        URI imageServerURI = cs.getComputeImageServer();
        _log.info("Verify if selected image {} exists on imageServer {}",
                img.getLabel(), imageServerURI);
        if (NullColumnValueGetter.isNullURI(imageServerURI)) {
            _log.info(
                    "Compute system {} does not have an image server associated with it. Cannot proceed with OS install.",
                    img.getLabel());
            throw APIException.badRequests
                    .noImageServerAssociatedToComputeSystem(cs.getLabel());
        } else {
            ComputeImageServer imageServer = queryObject(
                    ComputeImageServer.class, imageServerURI, true);
            StringSet computeImagesSet = imageServer.getComputeImages();
            if (computeImagesSet == null
                    || !computeImagesSet.contains(img.getId().toString())) {
                _log.info("Selected image {} does not exist on imageServer {}",
                        img.getLabel(), imageServer.getLabel());
                throw APIException.badRequests
                        .imageNotPresentOnComputeImageServer(img.getLabel(),
                                imageServer.getLabel());
            }
            _log.info("Selected image {} exists on imageServer {}",
                    img.getLabel(), imageServer.getLabel());
        }
    }
}
