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
import java.util.Iterator;
import java.util.List;
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

import com.google.common.base.Strings;
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
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.model.Cluster;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.TenantOrg;
import com.emc.storageos.db.client.model.VcenterDataCenter;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedExportMask;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.model.BulkIdParam;
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
            ClusterUpdateParam updateParam) {
        // update the cluster
        Cluster cluster = queryObject(Cluster.class, id, true);
        boolean oldExportEnabled = cluster.getAutoExportEnabled();

        validateClusterData(updateParam, cluster.getTenant(), cluster, _dbClient);
        populateCluster(updateParam, cluster);
        _dbClient.persistObject(cluster);
        auditOp(OperationTypeEnum.UPDATE_CLUSTER, true, null,
                cluster.auditParameters());

        boolean enablingAutoExports = !oldExportEnabled && cluster.getAutoExportEnabled();

        if (enablingAutoExports) {
            String taskId = UUID.randomUUID().toString();
            ComputeSystemController controller = getController(ComputeSystemController.class, null);
            controller.synchronizeSharedExports(cluster.getId(), taskId);
            Operation op = _dbClient.createTaskOpStatus(Cluster.class, cluster.getId(), taskId,
                    ResourceOperationTypeEnum.UPDATE_CLUSTER);
            auditOp(OperationTypeEnum.UPDATE_CLUSTER, true, op.getStatus(),
                    cluster.auditParameters());
        }

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
     * @brief Detach storage from Cluster
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
        controller.detachClusterStorage(cluster.getId(), false, false, taskId);
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
        } else {
            String taskId = UUID.randomUUID().toString();
            Operation op = _dbClient.createTaskOpStatus(Cluster.class, id, taskId,
                    ResourceOperationTypeEnum.DELETE_CLUSTER);
            ComputeSystemController controller = getController(ComputeSystemController.class, null);
            controller.detachClusterStorage(id, true, checkVms, taskId);
            auditOp(OperationTypeEnum.DELETE_CLUSTER, true, op.getStatus(),
                    cluster.auditParameters());
            return toTask(cluster, taskId, op);
        }
    }

    /**
     * List data for the specified clusters.
     * 
     * @param param POST data containing the id list.
     * @prereq none
     * @brief List data of specified clusters
     * @return list of representations.
     * 
     * @throws DatabaseException When an error occurs querying the database.
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
}
