/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource;

import static com.emc.storageos.api.mapper.DbObjectMapper.map;
import static com.emc.storageos.api.mapper.DbObjectMapper.toRelatedResource;
import static com.emc.storageos.api.mapper.HostMapper.map;
import static com.emc.storageos.api.mapper.TaskMapper.toTask;

import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.storageos.api.mapper.functions.MapCluster;
import com.emc.storageos.api.service.authorization.PermissionsHelper;
import com.emc.storageos.api.service.impl.resource.utils.VolumeIngestionUtil;
import com.emc.storageos.api.service.impl.response.BulkList;
import com.emc.storageos.api.service.impl.response.ResRepFilter;
import com.emc.storageos.computesystemcontroller.ComputeSystemController;
import com.emc.storageos.computesystemcontroller.impl.ComputeSystemHelper;
import com.emc.storageos.computesystemcontroller.impl.adapter.VcenterDiscoveryAdapter;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.NamedElementQueryResultList;
import com.emc.storageos.db.client.model.Cluster;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.Task;
import com.emc.storageos.db.client.model.TenantOrg;
import com.emc.storageos.db.client.model.Vcenter;
import com.emc.storageos.db.client.model.VcenterDataCenter;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedExportMask;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume;
import com.emc.storageos.db.client.model.util.TaskUtils;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.model.BulkIdParam;
import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.RelatedResourceRep;
import com.emc.storageos.model.ResourceOperationTypeEnum;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.block.UnManagedExportMaskList;
import com.emc.storageos.model.block.UnManagedVolumeList;
import com.emc.storageos.model.host.HostList;
import com.emc.storageos.model.host.cluster.ClusterBulkRep;
import com.emc.storageos.model.host.cluster.ClusterParam;
import com.emc.storageos.model.host.cluster.ClusterRestRep;
import com.emc.storageos.model.host.cluster.ClusterUpdateParam;
import com.emc.storageos.security.authentication.StorageOSUser;
import com.emc.storageos.security.authorization.ACL;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.google.common.base.Strings;
import com.google.common.collect.Sets;
import com.iwave.ext.vmware.VCenterAPI;
import com.vmware.vim25.mo.ClusterComputeResource;
import com.vmware.vim25.mo.HostSystem;

/**
 * A service that provides APIs for viewing, updating and deleting clusters.
 *
 */
@DefaultPermissions(readRoles = { Role.TENANT_ADMIN, Role.SYSTEM_MONITOR, Role.SYSTEM_ADMIN },
writeRoles = { Role.TENANT_ADMIN },
readAcls = { ACL.ANY })
@Path("/compute/clusters")
public class ClusterService extends TaskResourceService {

    // Logger
    protected final static Logger _log = LoggerFactory.getLogger(ClusterService.class);

    private static final String EVENT_SERVICE_TYPE = "cluster";

    @Override
    public String getServiceType() {
        return EVENT_SERVICE_TYPE;
    }

    @Autowired
    private HostService _hostService;

    /**
     * Updates one or more of the cluster attributes.
     *
     * @param id the URN of a ViPR cluster
     * @param updateParam the parameter that has the attributes to be
     *            updated.
     * @prereq none
     * @brief Update cluster attributes
     * @return the representation of the updated cluster.
     */
    @PUT
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.TENANT_ADMIN })
    @Path("/{id}")
    public ClusterRestRep updateCluster(@PathParam("id") URI id,
            ClusterUpdateParam updateParam,
            @DefaultValue("true") @QueryParam("update-exports") boolean updateExports) {
        // update the cluster
        Cluster cluster = queryObject(Cluster.class, id, true);

        validateClusterData(updateParam, cluster.getTenant(), cluster, _dbClient);
        populateCluster(updateParam, cluster);
        _dbClient.persistObject(cluster);
        if (!Strings.isNullOrEmpty(updateParam.findName())) {
            ComputeSystemHelper.updateInitiatorClusterName(_dbClient, cluster.getId());
        }
        auditOp(OperationTypeEnum.UPDATE_CLUSTER, true, null,
                cluster.auditParameters());

        return map(queryObject(Cluster.class, id, false));
    }

    /**
     * Validates the input parameter for create and update cluster.
     *
     * @param param the input parameter
     * @param cluster the cluster to be updated. This should be null for create validation.
     * @param tid the parent tenant Id
     * @param dbClient and instance of {@link DbClient}
     */
    protected void validateClusterData(ClusterParam param, URI tenanUri,
            Cluster cluster, DbClient dbClient) {
        // validate the project is present and active
        if (!NullColumnValueGetter.isNullURI(param.getProject())) {
            Project project = queryObject(Project.class, param.getProject(), true);
            if (!project.getTenantOrg().getURI().equals(tenanUri)) {
                throw APIException.badRequests.resourcedoesNotBelongToClusterTenantOrg("project");
            }
        }
        // make sure the cluster is unique for the tenant
        if (cluster == null || (param.findName() != null && !cluster.getLabel().equals(param.findName()))) {
            checkDuplicateChildName(tenanUri, Cluster.class, "label",
                    "tenant", param.findName(), dbClient);
        }
        // when the cluster is in a vcenter data center, validate the data center
        if (!NullColumnValueGetter.isNullURI(param.getVcenterDataCenter())) {
            VcenterDataCenter dataCenter = queryObject(VcenterDataCenter.class, param.getVcenterDataCenter(),
                    true);
            if (!dataCenter.getTenant().equals(tenanUri)) {
                throw APIException.badRequests.resourcedoesNotBelongToClusterTenantOrg("data center");
            }
        }
        // if the cluster project is being modified, check for active exports
        if (cluster != null && !areEqual(cluster.getProject(), param.getProject())) {
            if (isClusterInUse(cluster)) {
                throw APIException.badRequests.clusterProjectChangeNotAllowed(cluster.getLabel());
            }
        }
    }

    /**
     * Checks if the cluster has one or more of its hosts in use
     *
     * @param cluster the cluster to be checked
     * @return true if one or more of the cluster hosts is in use
     * @see HostService#isHostInUse(URI)
     */
    private boolean isClusterInUse(Cluster cluster) {
        List<Host> hosts =
                CustomQueryUtility.queryActiveResourcesByConstraint(_dbClient, Host.class,
                        ContainmentConstraint.Factory.getContainedObjectsConstraint(cluster.getId(), Host.class, "cluster"));
        for (Host host : hosts) {
            if (ComputeSystemHelper.isHostInUse(_dbClient, host.getId())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Updates export groups that are referenced by the given cluster by removing the cluster
     * reference and initiators belonging to hosts in this cluster. Volumes are left intact.
     *
     * @param id the URN of a ViPR Cluster
     * @brief Detach storage from cluster
     * @return OK if detaching completed successfully
     * @throws DatabaseException when a DB error occurs
     */
    @POST
    @Path("/{id}/detach-storage")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.TENANT_ADMIN })
    public TaskResourceRep detachStorage(@PathParam("id") URI id) throws DatabaseException {
        Cluster cluster = queryObject(Cluster.class, id, true);

        String taskId = UUID.randomUUID().toString();
        Operation op = _dbClient.createTaskOpStatus(Cluster.class, id, taskId,
                ResourceOperationTypeEnum.DETACH_VCENTER_DATACENTER_STORAGE);
        ComputeSystemController controller = getController(ComputeSystemController.class, null);
        controller.detachClusterStorage(cluster.getId(), false, true, taskId);
        return toTask(cluster, taskId, op);
    }

    /**
     * Checks if the cluster has any export
     *
     * @param cluster the cluster to be checked
     * @return true if one or more of the cluster hosts is in use
     */
    private boolean isClusterInExport(Cluster cluster) {
        List<ExportGroup> exportGroups = CustomQueryUtility.queryActiveResourcesByConstraint(
                _dbClient, ExportGroup.class,
                AlternateIdConstraint.Factory.getConstraint(
                        ExportGroup.class, "clusters", cluster.getId().toString()));
        return !exportGroups.isEmpty();
    }

    /**
     * Shows the information for one cluster.
     *
     * @param id the URN of a ViPR cluster
     * @prereq none
     * @brief Show cluster
     * @return All the non-null attributes of the cluster.
     * @throws DatabaseException when a DB error occurs.
     */
    @GET
    @Path("/{id}")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public ClusterRestRep getCluster(@PathParam("id") URI id) throws DatabaseException {
        Cluster cluster = queryObject(Cluster.class, id, false);
        // check the user permissions
        verifyAuthorizedInTenantOrg(cluster.getTenant(), getUserFromContext());
        return map(cluster);
    }

    /**
     * List the hosts of a cluster.
     *
     * @param id the URN of a ViPR cluster
     * @prereq none
     * @brief List cluster hosts
     * @return The list of hosts of the cluster.
     * @throws DatabaseException when a DB error occurs.
     */
    @GET
    @Path("/{id}/hosts")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public HostList getClusterHosts(@PathParam("id") URI id) throws DatabaseException {
        Cluster cluster = queryObject(Cluster.class, id, true);
        // check the user permissions
        verifyAuthorizedInTenantOrg(cluster.getTenant(), getUserFromContext());
        HostList list = new HostList();
        list.setHosts(map(ResourceTypeEnum.HOST, listChildren(id, Host.class, "label", "cluster")));
        return list;
    }

    /**
     * Deactivates the cluster and removes reference from its hosts. By passing in true for detachStorage,
     * any associated storage with the cluster can be detached.
     *
     * @param id the URN of a ViPR cluster to be deactivated
     * @prereq The cluster hosts must not have block or file exports if it is to be deleted without detaching storage
     * @brief Delete cluster.
     * @return OK if deactivation completed successfully
     * @throws DatabaseException when a DB error occurs
     */
    @POST
    @Path("/{id}/deactivate")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.TENANT_ADMIN })
    public TaskResourceRep deactivateCluster(@PathParam("id") URI id,
            @DefaultValue("false") @QueryParam("detach-storage") boolean detachStorage,
            @DefaultValue("true") @QueryParam("check-vms") boolean checkVms) throws DatabaseException {
        Cluster cluster = queryObject(Cluster.class, id, true);

        if (ComputeSystemHelper.isClusterInExport(_dbClient, cluster.getId()) && !detachStorage) {
            throw APIException.badRequests.resourceHasActiveReferences(Cluster.class.getSimpleName(), id);
        } else if (resourceHasPendingTasks(id)) {
            throw APIException.badRequests
            .resourceCannotBeDeleted("Cluster has another operation in progress.  " + cluster.getLabel());
        } else {
            List<URI> clusterHosts = ComputeSystemHelper.getChildrenUris(_dbClient, id, Host.class, "cluster");
            List<Host> hosts = _dbClient.queryObject(Host.class, clusterHosts);
            if (null != hosts && !hosts.isEmpty()) {
                for (Host host : hosts) {
                    if (null != host && resourceHasPendingTasks(host.getId())) {
                        // throw exception and do not proceed with
                        // cluster delete...
                        throw APIException.badRequests.resourceCannotBeDeleted(
                                "Cluster has host - "+ host.getLabel() +" with another operation in progress.  " + cluster.getLabel());
                    }
                }
            }
            validateVcenterClusterHosts(cluster);
            String taskId = UUID.randomUUID().toString();
            Operation op = _dbClient.createTaskOpStatus(Cluster.class, id, taskId,
                    ResourceOperationTypeEnum.DELETE_CLUSTER);
            ComputeSystemController controller = getController(ComputeSystemController.class, null);
            // VBDU [DONE]: COP-28449, Cross verify checkVMs is always passed as TRUE. We can explicitly make this true
            // always in the code.
            controller.detachClusterStorage(id, true, true, taskId);
            auditOp(OperationTypeEnum.DELETE_CLUSTER, true, op.getStatus(),
                    cluster.auditParameters());
            return toTask(cluster, taskId, op);
        }
    }

    /**
     * Updates the shared export groups of the give cluster [Deprecated]
     *
     * @param id the URN of a ViPR cluster
     * @brief Update shared export export groups
     * @return the representation of the updated cluster.
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.TENANT_ADMIN })
    @Path("/{id}/update-shared-exports")
    @Deprecated
    public TaskResourceRep updateClusterSharedExports(@PathParam("id") URI id) {
        // This call is immediately deprecated. It only served the UI, so nobody externally should be using it.
        // In case someone is, they should move over to update hosts like the UI has.
        throw APIException.badRequests.deprecatedRestCall("POST /compute/clusters/{cluster-id}/update-shared-exports",
                "PUT /compute/hosts/{host-id}");
    }

    /**
     * List data for the specified clusters.
     *
     * @param param
     *            POST data containing the id list.
     * @prereq none
     * @brief List data of specified clusters
     * @return list of representations.
     *
     * @throws DatabaseException
     *             When an error occurs querying the database.
     */
    @POST
    @Path("/bulk")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Override
    public ClusterBulkRep getBulkResources(BulkIdParam param) {
        return (ClusterBulkRep) super.getBulkResources(param);
    }

    /**
     * Creates an instance of host cluster for the provided parameter
     *
     * @param tenant the tenant organization to which the cluster belongs
     * @param param the parameter that contains the cluster properties and attributes.
     * @return an instance of {@link Host}
     */
    protected Cluster createNewCluster(TenantOrg tenant, ClusterParam param) {
        Cluster cluster = new Cluster();
        cluster.setId(URIUtil.createId(Cluster.class));
        cluster.setTenant(tenant.getId());
        populateCluster(param, cluster);
        return cluster;
    }

    private void populateCluster(ClusterParam param, Cluster cluster) {
        String clusterName = param.findName();
        if (!Strings.isNullOrEmpty(clusterName)) {
            cluster.setLabel(clusterName);
        }
        if (param.getVcenterDataCenter() != null) {
            cluster.setVcenterDataCenter(NullColumnValueGetter.isNullURI(param.getVcenterDataCenter()) ?
                    NullColumnValueGetter.getNullURI() : param.getVcenterDataCenter());
        }
        if (param.getAutoExportEnabled() != null) {
            cluster.setAutoExportEnabled(param.getAutoExportEnabled());
        }
        // Commented out because cluster project is not currently used
        // if (param.project != null) {
        // cluster.setProject(NullColumnValueGetter.isNullURI(param.project) ?
        // NullColumnValueGetter.getNullURI() : param.project);
        // }
    }

    @Override
    protected DataObject queryResource(URI id) {
        return queryObject(Cluster.class, id, false);
    }

    @Override
    protected URI getTenantOwner(URI id) {
        Cluster cluster = queryObject(Cluster.class, id, false);
        return cluster.getTenant();
    }

    @Override
    protected ResourceTypeEnum getResourceType() {
        return ResourceTypeEnum.CLUSTER;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Class<Cluster> getResourceClass() {
        return Cluster.class;
    }

    @Override
    public ClusterBulkRep queryBulkResourceReps(List<URI> ids) {

        Iterator<Cluster> _dbIterator =
                _dbClient.queryIterativeObjects(getResourceClass(), ids);
        return new ClusterBulkRep(BulkList.wrapping(_dbIterator, MapCluster.getInstance()));
    }

    @Override
    public ClusterBulkRep queryFilteredBulkResourceReps(List<URI> ids) {

        Iterator<Cluster> _dbIterator =
                _dbClient.queryIterativeObjects(getResourceClass(), ids);
        BulkList.ResourceFilter filter = new BulkList.ClusterFilter(getUserFromContext(), _permissionsHelper);
        return new ClusterBulkRep(BulkList.wrapping(_dbIterator, MapCluster.getInstance(), filter));
    }

    @Override
    protected boolean isZoneLevelResource() {
        return false;
    }

    @Override
    protected boolean isSysAdminReadableResource() {
        return true;
    }

    public static class ClusterResRepFilter<E extends RelatedResourceRep>
    extends ResRepFilter<E> {
        public ClusterResRepFilter(StorageOSUser user,
                PermissionsHelper permissionsHelper) {
            super(user, permissionsHelper);
        }

        @Override
        public boolean isAccessible(E resrep) {
            boolean ret = false;
            URI id = resrep.getId();
            Cluster obj = _permissionsHelper.getObjectById(id, Cluster.class);
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
        return new ClusterResRepFilter(user, permissionsHelper);
    }

    /**
     * Gets the UnManagedVolumes exposed to a Cluster.
     *
     * @param id the URI of a ViPR Cluster
     * @brief List unmanaged volumes exposed to a cluster
     * @return a list of UnManagedVolumes exposed to this host
     * @throws DatabaseException when a database error occurs
     */
    @GET
    @Path("/{id}/unmanaged-volumes")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public UnManagedVolumeList getUnmanagedVolumes(@PathParam("id") URI id) throws DatabaseException {
        Cluster cluster = queryObject(Cluster.class, id, false);

        // check the user permissions
        verifyAuthorizedInTenantOrg(cluster.getTenant(), getUserFromContext());

        // get the unmanaged volumes
        List<UnManagedVolume> unmanagedVolumes = VolumeIngestionUtil.findUnManagedVolumesForCluster(id, _dbClient);

        UnManagedVolumeList list = new UnManagedVolumeList();
        for (UnManagedVolume volume : unmanagedVolumes) {
            list.getUnManagedVolumes()
            .add(toRelatedResource(ResourceTypeEnum.UNMANAGED_VOLUMES, volume.getId()));
        }

        return list;
    }

    /**
     * Gets the UnManagedExportMasks found for a Cluster.
     *
     * @param id the URI of a ViPR Cluster
     * @brief List unmanaged export masks for a cluster
     * @return a list of UnManagedExportMasks found for the Cluster
     * @throws DatabaseException when a database error occurs
     */
    @GET
    @Path("/{id}/unmanaged-export-masks")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public UnManagedExportMaskList getUnmanagedExportMasks(@PathParam("id") URI id) throws DatabaseException {
        Cluster cluster = queryObject(Cluster.class, id, false);

        // check the user permissions
        verifyAuthorizedInTenantOrg(cluster.getTenant(), getUserFromContext());

        // get the unmanaged export masks
        List<UnManagedExportMask> uems = VolumeIngestionUtil.findUnManagedExportMasksForCluster(id, _dbClient);

        UnManagedExportMaskList list = new UnManagedExportMaskList();
        for (UnManagedExportMask uem : uems) {
            list.getUnManagedExportMasks()
            .add(toRelatedResource(ResourceTypeEnum.UNMANAGED_EXPORT_MASKS, uem.getId()));
        }

        return list;
    }

    /**
     * Verify if the given resource has pending/running tasks associated.
     * @param id URI of resource to check for task
     *
     * @return true if resource has tasks running/pending else false.
     */
    private boolean resourceHasPendingTasks(URI id) {
        boolean hasPendingTasks = false;
        List<Task> taskList = TaskUtils.findResourceTasks(_dbClient, id);
        for (Task task : taskList) {
            if (!task.getInactive() && task.isPending()) {
                hasPendingTasks = true;
                break;
            }
        }
        return hasPendingTasks;
    }

    /**
     * List the vblock hosts of a cluster.
     *
     * @param id the URN of a ViPR cluster
     * @brief List vblock hosts for a cluster
     * @return The list of vblock hosts in the cluster.
     * @throws DatabaseException when a DB error occurs.
     */
    @GET
    @Path("/{id}/vblock-hosts")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public HostList getClusterVblockHosts(@PathParam("id") URI id) throws DatabaseException {
        Cluster cluster = queryObject(Cluster.class, id, true);
        // check the user permissions
        verifyAuthorizedInTenantOrg(cluster.getTenant(), getUserFromContext());
        HostList list = new HostList();
        list.setHosts(map(ResourceTypeEnum.HOST, getVblockHostsFromCluster(id)));
        return list;
    }

    /**
     * This function is to retrieve the children of a given class.
     *
     * @param id the URN of parent
     * @param clzz the child class
     * @param nameField the name of the field of the child class that will be displayed as
     *            name in {@link NamedRelatedResourceRep}. Note this field should be a required
     *            field because, objects for which this field is null will not be returned by
     *            this function.
     *
     * @brief List Vblock hosts for a cluster
     * @return a list of children of tenant for the given class
     */
    private <T extends DataObject> List<NamedElementQueryResultList.NamedElement> getVblockHostsFromCluster(URI id) {
        List<Host> hosts = CustomQueryUtility.queryActiveResourcesByConstraint(_dbClient, Host.class,
                ContainmentConstraint.Factory.getContainedObjectsConstraint(id, Host.class, "cluster"));
        if (hosts != null && !hosts.isEmpty()) {
            List<NamedElementQueryResultList.NamedElement> elements = new ArrayList<NamedElementQueryResultList.NamedElement>(
                    hosts.size());
            for (Host host : hosts) {
                if (!NullColumnValueGetter.isNullURI(host.getComputeElement()) || !NullColumnValueGetter.isNullURI(host.getServiceProfile())) {
                    elements.add(NamedElementQueryResultList.NamedElement.createElement(
                            host.getId(), host.getLabel() == null ? "" : host.getLabel()));
                }
            }
            return elements;
        } else {
            return new ArrayList<NamedElementQueryResultList.NamedElement>();
        }
    }

    /**
     * Validate that the hosts in the cluster in our database matches the vCenter environment
     * 
     * @param cluster the cluster to check
     */
    public void validateVcenterClusterHosts(Cluster cluster) {

        if (null == cluster) {
            _log.error("Validation cluster is not set, not performing vCenter cluster validation");
            return;
        }

        // We can only proceed if this cluster belongs to a datacenter
        if (NullColumnValueGetter.isNullURI(cluster.getVcenterDataCenter())) {
            _log.info("Cluster is not synced to vcenter");
            return;
        }

        // Get a list of the cluster's hosts that are in our database.
        List<URI> clusterHosts = ComputeSystemHelper.getChildrenUris(_dbClient, cluster.getId(), Host.class, "cluster");

        VcenterDataCenter vcenterDataCenter = _dbClient.queryObject(VcenterDataCenter.class,
                cluster.getVcenterDataCenter());

        // If the datacenter is not in our database, we must fail the validation.
        if (vcenterDataCenter == null) {
            throw APIException.badRequests.vCenterDataCenterNotFound(cluster.getVcenterDataCenter());
        }

        // If the datacenter has a null vCenter reference, we must fail the validation.
        if (NullColumnValueGetter.isNullURI(vcenterDataCenter.getVcenter())) {
            throw APIException.badRequests.vCenterDataCenterHasNullVcenter(vcenterDataCenter.forDisplay());
        }
 
        Vcenter vcenter = _dbClient.queryObject(Vcenter.class,
                vcenterDataCenter.getVcenter());

        // If the vCenter is not in our database, we must fail the validation.
        if (vcenter == null) {
            throw APIException.badRequests.vCenterNotFound(vcenterDataCenter.getVcenter());
        }

        List<Host> dbHosts = _dbClient.queryObject(Host.class, clusterHosts);
        VCenterAPI api = VcenterDiscoveryAdapter.createVCenterAPI(vcenter);

        try {

            // Query the vCenter to get a reference to the cluster so that we can compare the hosts between the actual
            // environment and our database representation of the cluster.
            ClusterComputeResource vcenterCluster = api.findCluster(vcenterDataCenter.getLabel(), cluster.getLabel());

            // If we can't find the cluster on the vCenter environment, we can not proceed with validation.
            // The cluster may not have been pushed to vCenter yet.
            if (vcenterCluster == null) {
                _log.info("Unable to find cluster %s in datacenter %s in vCenter environment %s", cluster.getLabel(),
                        vcenterDataCenter.getLabel(), vcenter.getLabel());
                return;
            }

            // Gather a set of all the host UUIDs in this vCenter cluster.
            Set<String> vCenterHostUuids = Sets.newHashSet();
            for (HostSystem hostSystem : vcenterCluster.getHosts()) {
                if (hostSystem != null && hostSystem.getHardware() != null && hostSystem.getHardware().systemInfo != null) {
                    vCenterHostUuids.add(hostSystem.getHardware().systemInfo.uuid);
                }
            }

            // Gather a set of all the host UUIDs in our database.
            Set<String> dbHostUuids = Sets.newHashSet();
            for (Host host : dbHosts) {
                dbHostUuids.add(host.getUuid());
            }

            // If there are hosts in vCenter that are not in our database, fail the validation
            if (!dbHostUuids.containsAll(vCenterHostUuids)) {
                throw APIException.badRequests.clusterHostMismatch(cluster.forDisplay());
            }
        } finally {
            if (api != null) {
                api.logout();
            }
        }
    }
}
