/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource;

import static com.emc.storageos.api.mapper.DbObjectMapper.map;
import static com.emc.storageos.api.mapper.HostMapper.map;
import static com.emc.storageos.api.mapper.TaskMapper.toTask;

import java.net.URI;
import java.util.UUID;
import java.util.Collection;
import java.util.Set;
import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;

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

import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.NamedElementQueryResultList;
import com.emc.storageos.db.client.model.*;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.model.compute.VcenterClusterParam;
import com.emc.storageos.vcentercontroller.VcenterController;
import com.emc.storageos.volumecontroller.AsyncTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.storageos.api.mapper.HostMapper;
import com.emc.storageos.api.mapper.functions.MapVcenterDataCenter;
import com.emc.storageos.api.service.authorization.PermissionsHelper;
import com.emc.storageos.api.service.impl.response.BulkList;
import com.emc.storageos.api.service.impl.response.ResRepFilter;
import com.emc.storageos.computesystemcontroller.ComputeSystemController;
import com.emc.storageos.computesystemcontroller.impl.ComputeSystemHelper;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.model.BulkIdParam;
import com.emc.storageos.model.RelatedResourceRep;
import com.emc.storageos.model.ResourceOperationTypeEnum;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.host.HostList;
import com.emc.storageos.model.host.cluster.ClusterList;
import com.emc.storageos.model.host.vcenter.VcenterDataCenterBulkRep;
import com.emc.storageos.model.host.vcenter.VcenterDataCenterRestRep;
import com.emc.storageos.model.host.vcenter.VcenterDataCenterUpdate;
import com.emc.storageos.security.authentication.StorageOSUser;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import org.springframework.util.CollectionUtils;

/**
 * Service providing APIs for vcenterdatacenter.
 */
@Path("/compute/vcenter-data-centers")
@DefaultPermissions(readRoles = { Role.TENANT_ADMIN, Role.SYSTEM_MONITOR, Role.SYSTEM_ADMIN, Role.SECURITY_ADMIN },
        writeRoles = { Role.TENANT_ADMIN, Role.SYSTEM_ADMIN, Role.SECURITY_ADMIN })
public class VcenterDataCenterService extends TaskResourceService {
    private static Logger _log = LoggerFactory.getLogger(VcenterDataCenter.class);

    private static final String EVENT_SERVICE_TYPE = "vcenterdatacenter";

    public String getServiceType() {
        return EVENT_SERVICE_TYPE;
    }

    @Autowired
    private HostService _hostService;

    @Override
    protected URI getTenantOwner(URI id) {
        VcenterDataCenter vcenterDataCenter = queryObject(VcenterDataCenter.class, id, false);
        return vcenterDataCenter.getTenant();
    }

    @Override
    protected VcenterDataCenter queryResource(URI id) {
        return queryObject(VcenterDataCenter.class, id, false);
    }

    /**
     * Shows the data for a vCenter data center
     * 
     * @param id the URN of a ViPR vCenter data center
     * @prereq none
     * @brief Show vCenter data center
     * @return A VcenterDataCenterRestRep reference specifying the data for the
     *         vCenter data center with the passed id.
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}")
    public VcenterDataCenterRestRep getVcenterDataCenter(@PathParam("id") URI id) {
        VcenterDataCenter vcenterdatacenter = queryResource(id);
        // check the user permissions
        verifyAuthorizedSystemOrTenantOrgUser(vcenterdatacenter.getTenant());
        return HostMapper.map(vcenterdatacenter);
    }

    /**
     * Update a vCenter data center.
     * 
     * @param id the URN of a ViPR vCenter data center
     * @param updateParam the details of the vCenter data center
     * @prereq none
     * @brief Update vCenter data center
     * @return the details of the vCenter data center, including its id and link,
     *         when update completes successfully.
     * @throws DatabaseException when a database error occurs.
     */
    @PUT
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SECURITY_ADMIN, Role.TENANT_ADMIN })
    @Path("/{id}")
    public VcenterDataCenterRestRep updateVcenterDataCenter(@PathParam("id") URI id,
            VcenterDataCenterUpdate updateParam) throws DatabaseException {
        VcenterDataCenter dataCenter = queryResource(id);
        ArgValidator.checkEntity(dataCenter, id, isIdEmbeddedInURL(id));

        if (updateParam.getName() != null && !dataCenter.getLabel().equals(updateParam.getName())) {
            checkDuplicateChildName(dataCenter.getVcenter(), VcenterDataCenter.class, "label",
                    "vcenter", updateParam.getName(), _dbClient);
            dataCenter.setLabel(updateParam.getName());
        }

        checkUserPrivileges(updateParam, dataCenter);

        validateTenant(updateParam, dataCenter);

        ComputeSystemHelper.updateVcenterDataCenterTenant(_dbClient, dataCenter, updateParam.getTenant());

        _dbClient.persistObject(dataCenter);
        auditOp(OperationTypeEnum.UPDATE_VCENTER_DATACENTER, true, null,
                dataCenter.auditParameters());
        return map(dataCenter);
    }

    /**
     * Deactivates the vCenter data center, all its clusters and hosts
     * 
     * @param id the URN of a ViPR vCenter data center to be deactivated
     * @prereq none
     * @brief Delete vCenter data center
     * @return the task used for tracking the deactivation
     * @throws DatabaseException when a DB error occurs
     */
    @POST
    @Path("/{id}/deactivate")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.TENANT_ADMIN })
    public TaskResourceRep deactivateVcenterDataCenter(@PathParam("id") URI id,
            @DefaultValue("false") @QueryParam("detach-storage") boolean detachStorage) throws DatabaseException {
        if (ComputeSystemHelper.isDataCenterInUse(_dbClient, id) && !detachStorage) {
            throw APIException.badRequests.resourceHasActiveReferences(VcenterDataCenter.class.getSimpleName(), id);
        } else {
            VcenterDataCenter dataCenter = queryResource(id);
            ArgValidator.checkEntity(dataCenter, id, isIdEmbeddedInURL(id));

            String taskId = UUID.randomUUID().toString();
            Operation op = _dbClient.createTaskOpStatus(VcenterDataCenter.class, dataCenter.getId(), taskId,
                    ResourceOperationTypeEnum.DELETE_VCENTER_DATACENTER_STORAGE);
            ComputeSystemController controller = getController(ComputeSystemController.class, null);
            controller.detachDataCenterStorage(dataCenter.getId(), true, taskId);

            auditOp(OperationTypeEnum.DELETE_VCENTER_DATACENTER, true, null,
                    dataCenter.auditParameters());
            return toTask(dataCenter, taskId, op);
        }
    }

    /**
     * Detaches storage from the data center.
     * 
     * @param id the URN of a ViPR data center
     * @brief Detach storage from data center
     * @return the task used for tracking the detach-storage
     * @throws DatabaseException when a DB error occurs
     */
    @POST
    @Path("/{id}/detach-storage")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.TENANT_ADMIN })
    public TaskResourceRep detachStorage(@PathParam("id") URI id) throws DatabaseException {
        VcenterDataCenter dataCenter = queryObject(VcenterDataCenter.class, id, true);
        ArgValidator.checkEntity(dataCenter, id, true);

        String taskId = UUID.randomUUID().toString();
        Operation op = _dbClient.createTaskOpStatus(Vcenter.class, dataCenter.getId(), taskId,
                ResourceOperationTypeEnum.DETACH_VCENTER_DATACENTER_STORAGE);
        ComputeSystemController controller = getController(ComputeSystemController.class, null);
        controller.detachDataCenterStorage(dataCenter.getId(), false, taskId);
        return toTask(dataCenter, taskId, op);
    }

    /**
     * List the clusters in a vCenter data center.
     * 
     * @param id the URN of a ViPR vCenter data center
     * @prereq none
     * @brief List vCenter data center clusters
     * @return The list of clusters of the vCenter data center.
     * @throws DatabaseException when a DB error occurs.
     */
    @GET
    @Path("/{id}/clusters")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public ClusterList getVcenterDataCenterClusters(@PathParam("id") URI id) throws DatabaseException {
        VcenterDataCenter dataCenter = queryResource(id);
        // check the user permissions
        verifyAuthorizedInTenantOrg(dataCenter.getTenant(), getUserFromContext());
        ClusterList list = new ClusterList();
        list.setClusters(map(ResourceTypeEnum.CLUSTER, listChildren(id, Cluster.class, "label", "vcenterDataCenter")));
        return list;
    }

    /**
     * List the hosts in a vCenter data center.
     * 
     * @param id the URN of a ViPR vCenter data center
     * @prereq none
     * @brief List vCenter data center hosts
     * @return The list of hosts of the vCenter data center.
     * @throws DatabaseException when a DB error occurs.
     */
    @GET
    @Path("/{id}/hosts")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public HostList getVcenterDataCenterHosts(@PathParam("id") URI id) throws DatabaseException {
        VcenterDataCenter dataCenter = queryResource(id);
        // check the user permissions
        verifyAuthorizedInTenantOrg(dataCenter.getTenant(), getUserFromContext());
        // add all hosts in the data center
        HostList list = new HostList();
        list.setHosts(map(ResourceTypeEnum.HOST, listChildren(dataCenter.getId(), Host.class, "label", "vcenterDataCenter")));
        return list;
    }

    /**
     * List data of specified vCenter data centers.
     * 
     * @param param POST data containing the id list.
     * @prereq none
     * @brief List data of specified vCenter data centers
     * @return list of representations.
     */
    @POST
    @Path("/bulk")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Override
    public VcenterDataCenterBulkRep getBulkResources(BulkIdParam param) {
        return (VcenterDataCenterBulkRep) super.getBulkResources(param);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Class<VcenterDataCenter> getResourceClass() {
        return VcenterDataCenter.class;
    }

    @Override
    public VcenterDataCenterBulkRep queryBulkResourceReps(List<URI> ids) {

        Iterator<VcenterDataCenter> _dbIterator =
                _dbClient.queryIterativeObjects(getResourceClass(), ids);
        return new VcenterDataCenterBulkRep(BulkList.wrapping(_dbIterator, MapVcenterDataCenter.getInstance()));
    }

    @Override
    public VcenterDataCenterBulkRep queryFilteredBulkResourceReps(List<URI> ids) {
        Iterator<VcenterDataCenter> _dbIterator =
                _dbClient.queryIterativeObjects(getResourceClass(), ids);
        BulkList.ResourceFilter filter = new BulkList.VcenterDataCenterFilter(getUserFromContext(), _permissionsHelper);
        return new VcenterDataCenterBulkRep(BulkList.wrapping(_dbIterator, MapVcenterDataCenter.getInstance(), filter));
    }

    @Override
    protected ResourceTypeEnum getResourceType() {
        return ResourceTypeEnum.VCENTERDATACENTER;
    }

    @Override
    protected boolean isZoneLevelResource() {
        return false;
    }

    @Override
    protected boolean isSysAdminReadableResource() {
        return true;
    }

    public static class VcenterDataCenterResRepFilter<E extends RelatedResourceRep>
            extends ResRepFilter<E> {
        public VcenterDataCenterResRepFilter(StorageOSUser user,
                PermissionsHelper permissionsHelper) {
            super(user, permissionsHelper);
        }

        @Override
        public boolean isAccessible(E resrep) {
            boolean ret = false;
            URI id = resrep.getId();

            VcenterDataCenter obj = _permissionsHelper.getObjectById(id, VcenterDataCenter.class);
            if (obj == null) {
                return false;
            }
            if (obj.getTenant().toString().equals(_user.getTenantId()) ||
                    isSecurityAdmin() || isSystemAdmin()) {
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
        return new VcenterDataCenterResRepFilter(user, permissionsHelper);
    }

    private boolean isHostCompatibleForVcenterCluster(Host host) {
        if (host.getType().equalsIgnoreCase(Host.HostType.Esx.name())) {
            _log.info("Host " + host.getLabel() + " is compatible for vCenter cluster operation due to type " + host.getType()
                    + " and OS version " + host.getOsVersion());
            return true;
        } else {
            _log.info("Host " + host.getLabel() + " is not compatible for vCenter cluster operation due to type " + host.getType());
            return false;
        }
    }

    /*
     * @param create - Whether to create a new cluster vs update an existing
     */
    private TaskResourceRep createOrUpdateVcenterCluster(boolean createCluster, URI id, URI clusterId, List<URI> addHosts,
            List<URI> removeHosts) {
        _log.info("createOrUpdateVcenterCluster " + createCluster + " " + id + " " + clusterId);

        ArgValidator.checkFieldUriType(id, VcenterDataCenter.class, "id");
        VcenterDataCenter vcenterDataCenter = queryObject(VcenterDataCenter.class, id, false);
        ArgValidator.checkEntity(vcenterDataCenter, id, isIdEmbeddedInURL(id));

        ArgValidator.checkFieldUriType(clusterId, Cluster.class, "clusterId");
        Cluster cluster = queryObject(Cluster.class, clusterId, false);
        ArgValidator.checkEntity(cluster, clusterId, isIdEmbeddedInURL(clusterId));

        verifyAuthorizedInTenantOrg(cluster.getTenant(), getUserFromContext());

        /*
         * Check if explicit add host or remove hosts are specified
         * If one or both are, only execute whats specified
         * If nothing is specified, do automatic host selection and import all hosts in cluster
         */
        Collection<URI> addHostUris = new ArrayList<URI>();
        Collection<URI> removeHostUris = new ArrayList<URI>();

        boolean manualHostSpecification = false;
        if (addHosts != null && !addHosts.isEmpty()) {
            _log.info("Request to explicitly add hosts " + addHosts);
            manualHostSpecification = true;
        }
        if (removeHosts != null && !removeHosts.isEmpty()) {
            _log.info("Request to explicitly remove hosts " + removeHosts);
            manualHostSpecification = true;
        }

        if (manualHostSpecification) {
            for (URI uri : addHosts) {
                Host host = queryObject(Host.class, uri, false);
                if (isHostCompatibleForVcenterCluster(host)) {
                    addHostUris.add(uri);
                }
            }
            for (URI uri : removeHosts) {
                Host host = queryObject(Host.class, uri, false);
                if (isHostCompatibleForVcenterCluster(host)) {
                    removeHostUris.add(uri);
                }
            }

        } else {
            // If no hosts specified by default add all hosts within cluster
            Collection<URI> hostUris = new ArrayList<URI>();
            List<NamedElementQueryResultList.NamedElement> hostNamedElements = listChildren(clusterId, Host.class, "label", "cluster");
            for (NamedElementQueryResultList.NamedElement hostNamedElement : hostNamedElements) {
                Host host = queryObject(Host.class, hostNamedElement.getId(), false);
                if (isHostCompatibleForVcenterCluster(host)) {
                    addHostUris.add(host.getId());
                }
            }
            if (addHostUris.isEmpty()) { // Require at least a single compatible host for automatic mode, do not create empty cluster
                _log.error("Cluster " + cluster.getLabel()
                        + " does not contain any ESX/ESXi hosts and is thus incompatible for vCenter operations");
                throw APIException.badRequests.clusterContainsNoCompatibleHostsForVcenter();
            }
        }

        // Find all shared volumes in the cluster
        List<URI> volumeUris = new ArrayList<URI>();
        try {
            List<ExportGroup> exportGroups = CustomQueryUtility.queryActiveResourcesByConstraint(_dbClient, ExportGroup.class,
                    AlternateIdConstraint.Factory.getConstraint(ExportGroup.class, "clusters", cluster.getId().toString()));
            _log.info("Found " + exportGroups.size() + " export groups for cluster " + cluster.getLabel());
            for (ExportGroup exportGroup : exportGroups) {
                _log.info("Cluster " + cluster.getLabel() + " has export group " + exportGroup.getLabel() + " of type "
                        + exportGroup.getType());
                if (exportGroup.forCluster()) {
                    _log.info("Export group " + exportGroup.getLabel() + " is cluster/shared type");
                    StringMap volumes = exportGroup.getVolumes();
                    _log.info("Export group " + exportGroup.getLabel() + " has " + volumes.size() + " volumes");
                    for (String volumeUriString : volumes.keySet()) {
                        _log.info("Volume URI " + volumeUriString + " found in export group " + exportGroup.getLabel());
                        URI uri = URI.create(volumeUriString);
                        volumeUris.add(uri);
                    }
                }

            }
        } catch (Exception e) {
            _log.error("Exception navigating cluster export groups for shared volumes " + e);
            // for time being just ignore
        }
        Collection<Volume> volumes = _dbClient.queryObject(Volume.class, volumeUris);
        for (Volume volume : volumes) {
            _log.info("Volume " + volume.getLabel() + " " + volume.getWWN() + " is shared and compatible for VMFS datastore");
        }

        String taskId = UUID.randomUUID().toString();
        Operation op = new Operation();
        op.setResourceType(createCluster ? ResourceOperationTypeEnum.CREATE_VCENTER_CLUSTER
                : ResourceOperationTypeEnum.UPDATE_VCENTER_CLUSTER);
        _dbClient.createTaskOpStatus(VcenterDataCenter.class, vcenterDataCenter.getId(), taskId, op);
        AsyncTask task = new AsyncTask(VcenterDataCenter.class, vcenterDataCenter.getId(), taskId);

        VcenterController vcenterController = getController(VcenterController.class, null);
        if (createCluster) {
            vcenterController.createVcenterCluster(task, clusterId, addHostUris.toArray(new URI[0]), volumeUris.toArray(new URI[0]));
        } else {
            vcenterController.updateVcenterCluster(task, clusterId, addHostUris.toArray(new URI[0]), removeHostUris.toArray(new URI[0]),
                    volumeUris.toArray(new URI[0]));
        }
        auditOp(OperationTypeEnum.CREATE_UPDATE_VCENTER_CLUSTER, true, null, vcenterDataCenter.auditParameters());
        return toTask(vcenterDataCenter, taskId, op);
    }

    /**
     * Create a new vCenter cluster with all hosts and datastores
     * 
     * @param id the URN of a discovered vCenter datacenter
     * @param vcenterClusterParam the URN of the ViPR cluster
     * @brief Create a new vCenter cluster with all hosts and datastores
     * @return task representation
     */
    @POST
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/create-vcenter-cluster")
    @CheckPermission(roles = { Role.TENANT_ADMIN })
    public TaskResourceRep createVcenterCluster(@PathParam("id") URI id, VcenterClusterParam vcenterClusterParam) {
        return createOrUpdateVcenterCluster(true, id, vcenterClusterParam.getId(), null, null);
    }

    /**
     * Updates an existing vCenter cluster with new hosts and datastores
     * 
     * @param id the URN of a discovered vCenter datacenter
     * @param vcenterClusterParam the URN of the ViPR cluster
     * @brief Update an existing vCenter cluster with new hosts and datastores
     * @return task representation
     */
    @POST
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/update-vcenter-cluster")
    @CheckPermission(roles = { Role.TENANT_ADMIN })
    public TaskResourceRep updateVcenterCluster(@PathParam("id") URI id, VcenterClusterParam vcenterClusterParam) {
        List<URI> addHosts = vcenterClusterParam.getAddHosts();
        List<URI> removeHosts = vcenterClusterParam.getRemoveHosts();
        return createOrUpdateVcenterCluster(false, id, vcenterClusterParam.getId(), addHosts, removeHosts);
    }

    /**
     * Checks if the user is authorized to view resources in a tenant organization.
     * The user can see resources if:
     *
     * The user is in the tenant organization.
     * The user has SysAdmin, SysMonitor, SecAdmin role.
     * The user has TenantAdmin role to this tenant organization even
     * if the user is in another tenant org
     *
     * @param tenantId the tenant organization URI
     */
    protected void verifyAuthorizedSystemOrTenantOrgUser(URI tenantId) {
        if (isSystemAdmin() || isSecurityAdmin()) {
            return;
        }

        StorageOSUser user = getUserFromContext();
        verifyAuthorizedInTenantOrg(tenantId, user);
    }

    /**
     * Validates if the tenant in the update param is sharing the
     * vCenter or not.
     *
     * @param updateParam input to update the vCenterDataCenter.
     * @param dataCenter the vCenterDataCenter resource to be updated.
     */
    private void validateTenant(VcenterDataCenterUpdate updateParam, VcenterDataCenter dataCenter) {
        Vcenter vcenter = _dbClient.queryObject(Vcenter.class, dataCenter.getVcenter());
        ArgValidator.checkEntity(vcenter, dataCenter.getVcenter(), isIdEmbeddedInURL(dataCenter.getVcenter()));

        //Set the current tenant of the datacenter to the updateParam if
        //updateParam does not contain the tenant information.
        //To set the null tenant for the datacenter, use the string "null".
        if (updateParam.getTenant() == null) {
            updateParam.setTenant(dataCenter.getTenant());
        }

        if (NullColumnValueGetter.isNullURI(updateParam.getTenant()) &&
                vcenter.getCascadeTenancy()) {
            throw APIException.badRequests.cannotRemoveDatacenterTenant(dataCenter.getLabel(), vcenter.getLabel());
        }

        Set<URI> vcenterTenants = _permissionsHelper.getUsageURIsFromAcls(vcenter.getAcls());
        if (!NullColumnValueGetter.isNullURI(updateParam.getTenant()) &&
                (CollectionUtils.isEmpty(vcenterTenants) || !vcenterTenants.contains(updateParam.getTenant()))) {
            //Since, the given tenant in the update param is not a null URI
            //and it is not sharing the vCenter, return the error.
            TenantOrg tenant = _dbClient.queryObject(TenantOrg.class, updateParam.getTenant());
            throw APIException.badRequests.tenantDoesNotShareTheVcenter(tenant.getLabel(), vcenter.getLabel());
        }
    }

    /**
     * Check if the user has a privilege to update the
     * vCenterDataCenter's tenant or not.
     *
     * @param updateParam
     * @param dataCenter
     */
    private void checkUserPrivileges(VcenterDataCenterUpdate updateParam, VcenterDataCenter dataCenter) {
        if(!(isSecurityAdmin() || isSystemAdmin())) {
            if(updateParam.getTenant() != null &&
                    dataCenter.getTenant() != null &&
                    !URIUtil.identical(updateParam.getTenant(), dataCenter.getTenant())) {
                throw APIException.forbidden.insufficientPermissionsForUser(getUserFromContext().getName());
            } else if(updateParam.getTenant() == null &&
                    dataCenter.getTenant() != null) {
                throw APIException.forbidden.insufficientPermissionsForUser(getUserFromContext().getName());
            } else if(updateParam.getTenant() != null &&
                    dataCenter.getTenant() == null) {
                throw APIException.forbidden.insufficientPermissionsForUser(getUserFromContext().getName());
            }
        }
    }
}
