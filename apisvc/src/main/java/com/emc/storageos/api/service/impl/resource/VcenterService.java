/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource;

import static com.emc.storageos.api.mapper.DbObjectMapper.map;
import static com.emc.storageos.api.mapper.HostMapper.map;
import static com.emc.storageos.api.mapper.TaskMapper.toTask;

import java.net.URI;
import java.util.*;

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

import com.emc.storageos.db.client.model.*;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.model.auth.ACLAssignmentChanges;
import com.emc.storageos.model.auth.ACLAssignments;
import com.emc.storageos.model.auth.ACLEntry;
import com.emc.storageos.model.host.vcenter.*;
import com.emc.storageos.security.authorization.*;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.storageos.api.mapper.functions.MapVcenter;
import com.emc.storageos.api.service.authorization.PermissionsHelper;
import com.emc.storageos.api.service.impl.resource.utils.AsyncTaskExecutorIntf;
import com.emc.storageos.api.service.impl.resource.utils.DiscoveredObjectTaskScheduler;
import com.emc.storageos.api.service.impl.resource.utils.VCenterConnectionValidator;
import com.emc.storageos.api.service.impl.response.BulkList;
import com.emc.storageos.api.service.impl.response.ResRepFilter;
import com.emc.storageos.computesystemcontroller.ComputeSystemController;
import com.emc.storageos.computesystemcontroller.impl.ComputeSystemHelper;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.NamedElementQueryResultList;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.model.BulkIdParam;
import com.emc.storageos.model.RelatedResourceRep;
import com.emc.storageos.model.ResourceOperationTypeEnum;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.TaskList;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.host.HostList;
import com.emc.storageos.model.host.cluster.ClusterList;
import com.emc.storageos.security.authentication.StorageOSUser;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.volumecontroller.AsyncTask;
import com.emc.storageos.volumecontroller.ControllerException;
import org.springframework.util.CollectionUtils;

/**
 * A service that provides APIs for viewing, updating and deleting vcenters and their
 * data centers.
 * 
 */
@DefaultPermissions(read_roles = { Role.TENANT_ADMIN, Role.SYSTEM_MONITOR, Role.SYSTEM_ADMIN,
        Role.RESTRICTED_SYSTEM_ADMIN, Role.SECURITY_ADMIN },
        write_roles = { Role.SYSTEM_ADMIN, Role.TENANT_ADMIN })
@Path("/compute/vcenters")
public class VcenterService extends TaskResourceService {

    // Logger
    protected final static Logger _log = LoggerFactory.getLogger(VcenterService.class);

    private static final String EVENT_SERVICE_TYPE = "vcenter";

    public String getServiceType() {
        return EVENT_SERVICE_TYPE;
    }

    @Autowired
    private HostService _hostService;

    @Autowired
    private VcenterDataCenterService _vcenterDataCenterService;

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
     * Updates one or more of the vCenter attributes. Discovery is initiated
     * after the vCenter is updated.
     * 
     * @param id the URN of a ViPR vCenter
     * @param updateParam the parameter that has the attributes to be updated.
     * @prereq none
     * @brief Update vCenter
     * @return the vCenter discovery async task representation.
     */
    @PUT
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.TENANT_ADMIN })
    @Path("/{id}")
    public TaskResourceRep updateVcenter(@PathParam("id") URI id,
            VcenterUpdateParam updateParam,
            @QueryParam("validate_connection") @DefaultValue("false") final Boolean validateConnection) {

        // update the host
        Vcenter vcenter = queryObject(Vcenter.class, id, true);
        validateVcenter(updateParam, vcenter, validateConnection);

        populateVcenterData(vcenter, updateParam);
        _dbClient.persistObject(vcenter);
        auditOp(OperationTypeEnum.UPDATE_VCENTER, true, null,
                vcenter.auditParameters());

        return doDiscoverVcenter(vcenter);
    }

    /**
     * Discovers (refreshes) a vCenter. This is an asynchronous call.
     * 
     * @param id The URI of the vCenter.
     * @prereq none
     * @brief Discover vCenter
     * @return TaskResourceRep (asynchronous call)
     */
    @POST
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/discover")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.TENANT_ADMIN })
    public TaskResourceRep discoverVcenter(@PathParam("id") URI id) {
        ArgValidator.checkFieldUriType(id, Vcenter.class, "id");
        Vcenter vcenter = queryObject(Vcenter.class, id, true);

        return doDiscoverVcenter(vcenter);
    }

    /**
     * Vcenter Discovery
     * 
     * @param vcenter the Vcenter to be discovered.
     *            provided, a new taskId is generated.
     * @return the task used to track the discovery job
     */
    protected TaskResourceRep doDiscoverVcenter(Vcenter vcenter) {
        ComputeSystemController controller = getController(ComputeSystemController.class, "vcenter");
        DiscoveredObjectTaskScheduler scheduler = new DiscoveredObjectTaskScheduler(
                _dbClient, new DiscoverJobExec(controller));
        String taskId = UUID.randomUUID().toString();
        ArrayList<AsyncTask> tasks = new ArrayList<AsyncTask>(1);
        tasks.add(new AsyncTask(Vcenter.class, vcenter.getId(), taskId));

        TaskList taskList = scheduler.scheduleAsyncTasks(tasks);
        return taskList.getTaskList().iterator().next();
    }

    /**
     * Validates the create/update vCenter input data
     * 
     * @param param the input parameter
     * @param vcenter the vcenter being updated in case of update operation.
     *            This parameter must be null for create operations.
     */
    protected void validateVcenter(VcenterParam param, Vcenter vcenter, Boolean validateConnection) {
        if (vcenter == null || (param.findIpAddress() != null && !param.findIpAddress().equals(vcenter.getIpAddress()))) {
            checkDuplicateAltId(Vcenter.class, "ipAddress", param.findIpAddress(), "vcenter");
        }
        if (vcenter == null || (param.getName() != null && !param.getName().equals(vcenter.getLabel()))) {
            checkDuplicateLabel(Vcenter.class, param.getName(), "vcenter");
        }
        if (validateConnection != null && validateConnection == true) {
            String errorMessage = VCenterConnectionValidator.isVCenterConnectionValid(param);
            if (StringUtils.isNotBlank(errorMessage)) {
                throw APIException.badRequests.invalidVCenterConnection(errorMessage);
            }
        }
    }

    /**
     * Shows the information for one vCenter server.
     * 
     * @param id the URN of a ViPR vCenter
     * @prereq none
     * @brief Show vCenter
     * @return All non-null attributes of the vCenter.
     * @throws DatabaseException when a DB error occurs.
     */
    @GET
    @Path("/{id}")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public VcenterRestRep getVcenter(@PathParam("id") URI id) throws DatabaseException {
        Vcenter vcenter = queryObject(Vcenter.class, id, false);
        // check the user permissions for this tenant org
        verifyAuthorizedSystemOrTenantOrgUser(_permissionsHelper.convertToACLEntries(vcenter.getAcls()), getUserFromContext());
        return map(vcenter);
    }

    /**
     * List the hosts of a vCenter.
     * 
     * @param id the URN of a ViPR vCenter
     * @prereq none
     * @brief List vCenter hosts
     * @return The list of hosts of the vCenter.
     * @throws DatabaseException when a DB error occurs.
     */
    @GET
    @Path("/{id}/hosts")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public HostList getVcenterHosts(@PathParam("id") URI id) throws DatabaseException {
        Vcenter vcenter = queryObject(Vcenter.class, id, false);
        ArgValidator.checkEntity(vcenter, id, isIdEmbeddedInURL(id));

        // check the user permissions for this tenant org
        verifyAuthorizedInTenantOrg(_permissionsHelper.convertToACLEntries(vcenter.getAcls()), getUserFromContext());

        // check the user permissions for this tenant org
        URI tenantId = URI.create(getUserFromContext().getTenantId());

        List<NamedElementQueryResultList.NamedElement> vCentersDataCenters = filterTenantResourcesByTenant(tenantId,
                VcenterDataCenter.class, listChildren(id, VcenterDataCenter.class, "label", "vcenter"));

        HostList list = new HostList();
        Iterator<NamedElementQueryResultList.NamedElement> dataCentersIterator = vCentersDataCenters.iterator();
        while (dataCentersIterator.hasNext()) {
            NamedElementQueryResultList.NamedElement dataCenterElement = dataCentersIterator.next();
            list.getHosts().addAll(map(ResourceTypeEnum.HOST, listChildren(dataCenterElement.getId(), Host.class,
                    "label", "vcenterDataCenter")));
        }

        return list;
    }

    /**
     * List the clusters in a vCenter
     * 
     * @param id the URN of a ViPR vCenter
     * @prereq none
     * @brief List vCenter clusters
     * @return The list of clusters of the vCenter.
     * @throws DatabaseException when a DB error occurs.
     */
    @GET
    @Path("/{id}/clusters")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public ClusterList getVcenterClusters(@PathParam("id") URI id) throws DatabaseException {
        Vcenter vcenter = queryObject(Vcenter.class, id, false);
        ArgValidator.checkEntity(vcenter, id, isIdEmbeddedInURL(id));

        // check the user permissions for this tenant org
        verifyAuthorizedInTenantOrg(_permissionsHelper.convertToACLEntries(vcenter.getAcls()), getUserFromContext());

        URI tenantId = URI.create(getUserFromContext().getTenantId());

        List<NamedElementQueryResultList.NamedElement> vCentersDataCenters = filterTenantResourcesByTenant(tenantId,
                VcenterDataCenter.class, listChildren(id, VcenterDataCenter.class, "label", "vcenter"));

        ClusterList list = new ClusterList();
        Iterator<NamedElementQueryResultList.NamedElement> dataCentersIterator = vCentersDataCenters.iterator();
        while (dataCentersIterator.hasNext()) {
            NamedElementQueryResultList.NamedElement dataCenterElement = dataCentersIterator.next();
            list.getClusters().addAll(map(ResourceTypeEnum.CLUSTER, listChildren(dataCenterElement.getId(), Cluster.class,
                    "label", "vcenterDataCenter")));
        }

        return list;
    }

    /**
     * Deactivates the vCenter, its vCenter data centers, clusters and hosts.
     * 
     * @param id the URN of a ViPR vCenter to be deactivated
     * @prereq none
     * @brief Delete vCenter
     * @return OK if deactivation completed successfully
     * @throws DatabaseException when a DB error occurs
     */
    @POST
    @Path("/{id}/deactivate")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.TENANT_ADMIN })
    public TaskResourceRep deactivateVcenter(@PathParam("id") URI id,
            @DefaultValue("false") @QueryParam("detach-storage") boolean detachStorage) throws DatabaseException {
        if (ComputeSystemHelper.isVcenterInUse(_dbClient, id) && !detachStorage) {
            throw APIException.badRequests.resourceHasActiveReferences(Vcenter.class.getSimpleName(), id);
        } else {
            Vcenter vcenter = queryObject(Vcenter.class, id, true);

            checkIfOtherTenantsUsingTheVcenter(vcenter);

            String taskId = UUID.randomUUID().toString();
            Operation op = _dbClient.createTaskOpStatus(Vcenter.class, vcenter.getId(), taskId,
                    ResourceOperationTypeEnum.DELETE_VCENTER);

            ComputeSystemController controller = getController(ComputeSystemController.class, null);
            controller.detachVcenterStorage(vcenter.getId(), true, taskId);

            auditOp(OperationTypeEnum.DELETE_VCENTER, true, null, vcenter.auditParameters());

            return toTask(vcenter, taskId, op);
        }
    }

    /**
     * Detaches storage from the vcenter.
     * 
     * @param id the URN of a ViPR vcenter
     * @brief Detach storage from vcenter
     * @return task
     * @throws DatabaseException when a DB error occurs
     */
    @POST
    @Path("/{id}/detach-storage")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.TENANT_ADMIN })
    public TaskResourceRep detachStorage(@PathParam("id") URI id) throws DatabaseException {
        Vcenter vcenter = queryObject(Vcenter.class, id, true);
        ArgValidator.checkEntity(vcenter, id, true);

        checkIfOtherTenantsUsingTheVcenter(vcenter);

        String taskId = UUID.randomUUID().toString();
        Operation op = _dbClient.createTaskOpStatus(Vcenter.class, vcenter.getId(), taskId,
                ResourceOperationTypeEnum.DETACH_VCENTER_DATACENTER_STORAGE);
        ComputeSystemController controller = getController(ComputeSystemController.class, null);
        controller.detachVcenterStorage(vcenter.getId(), false, taskId);
        return toTask(vcenter, taskId, op);
    }

    /**
     * Creates a new vCenter data center.
     * 
     * @param id the URN of the parent vCenter
     * @param createParam the details of the data center
     * @prereq none
     * @brief Create vCenter data center
     * @return the details of the vCenter data center, including its id and link,
     *         when creation completes successfully.
     * @throws DatabaseException when a database error occurs.
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.TENANT_ADMIN })
    @Path("/{id}/vcenter-data-centers")
    public VcenterDataCenterRestRep createVcenterDataCenter(@PathParam("id") URI id,
            VcenterDataCenterCreate createParam) throws DatabaseException {
        Vcenter vcenter = queryObject(Vcenter.class, id, false);
        checkDuplicateChildName(id, VcenterDataCenter.class, "label",
                "vcenter", createParam.getName(), _dbClient);
        VcenterDataCenter datacenter = new VcenterDataCenter();
        datacenter.setId(URIUtil.createId(VcenterDataCenter.class));
        datacenter.setLabel(createParam.getName());
        datacenter.setVcenter(id);
        if (vcenter.getTenantCreated()) {
            datacenter.setTenant(_permissionsHelper.getTenant(vcenter.getAcls()));
        } else {
            datacenter.setTenant(NullColumnValueGetter.getNullURI());
        }
        _dbClient.createObject(datacenter);
        auditOp(OperationTypeEnum.CREATE_VCENTER_DATACENTER, true, null,
                datacenter.auditParameters());
        return map(datacenter);
    }

    /**
     * List the vCenter data centers of the vCenter.
     * 
     * @param id the URN of a ViPR vCenter
     * @prereq none
     * @brief List vCenter data centers
     * @return All the list of vCenter data centers.
     * @throws DatabaseException when a DB error occurs.
     */
    @GET
    @Path("/{id}/vcenter-data-centers")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public VcenterDataCenterList getVcenterDataCenters(@PathParam("id") URI id, @QueryParam("tenant") URI tid) throws DatabaseException {
        Vcenter vcenter = queryObject(Vcenter.class, id, false);
        ArgValidator.checkEntity(vcenter, id, isIdEmbeddedInURL(id));

        // check the user permissions for this tenant org
        verifyAuthorizedSystemOrTenantOrgUser(_permissionsHelper.convertToACLEntries(vcenter.getAcls()), getUserFromContext());

        URI tenantId;
        if (isSecurityAdmin() || isSystemOrRestrictedSystemAdmin()) {
            tenantId = tid;
        } else {
            // check the user permissions for this tenant org
            tenantId = URI.create(getUserFromContext().getTenantId());
        }

        // get the vcenters
        VcenterDataCenterList list = new VcenterDataCenterList();
        List<NamedElementQueryResultList.NamedElement> elements = listChildren(id,
                VcenterDataCenter.class, "label", "vcenter");

        //Filter the vCenterDataCenters based on the tenant.
        list.setDataCenters(map(ResourceTypeEnum.VCENTERDATACENTER, id,
                filterTenantResourcesByTenant(tenantId, VcenterDataCenter.class, elements)));
        return list;
    }

    /**
     * Creates a new instance of vcenter.
     * 
     * @param tenant the vcenter parent tenant organization
     * @param param the input parameter containing the vcenter attributes
     * @return an instance of {@link Vcenter}
     */
    protected Vcenter createNewVcenter(TenantOrg tenant, VcenterParam param) {
        Vcenter vcenter = new Vcenter();
        vcenter.setId(URIUtil.createId(Vcenter.class));
        addVcenterAclIfTenantAdmin(tenant, vcenter);
        populateVcenterData(vcenter, param);
        if (isSystemAdmin()) {
            //Since, the creating user is either SysAdmin or Restricted SysAdmin or
            //TenantAdmin use the "shared" parameter from the api payload.
            vcenter.setTenantCreated(Boolean.FALSE);
        } else {
            //Since the creating user is a TenantAdmin, dont make the vCenter
            //as a shared resource by default.
            vcenter.setTenantCreated(Boolean.TRUE);
        }
        return vcenter;
    }

    /**
     * Populate an instance of vcenter with the provided vcenter parameter
     * 
     * @param vcenter the vcenter to be populated
     * @param param the parameter that contains the attributes.
     */
    protected void populateVcenterData(Vcenter vcenter, VcenterParam param) {
        vcenter.setLabel(param.getName());
        vcenter.setOsVersion(param.getOsVersion());
        vcenter.setUsername(param.getUserName());
        vcenter.setPassword(param.getPassword());
        vcenter.setIpAddress(param.findIpAddress());
        vcenter.setPortNumber(param.getPortNumber());
        vcenter.setUseSSL(param.getUseSsl());
    }

    /**
     * List data of specified vCenters.
     * 
     * @param param POST data containing the id list.
     * @prereq none
     * @brief List data of vCenters
     * @return list of representations.
     * 
     * @throws DatabaseException When an error occurs querying the database.
     */
    @POST
    @Path("/bulk")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Override
    public VcenterBulkRep getBulkResources(BulkIdParam param) {
        return (VcenterBulkRep) super.getBulkResources(param);
    }

    @Override
    protected DataObject queryResource(URI id) {
        return queryObject(Vcenter.class, id, false);
    }

    @Override
    protected URI getTenantOwner(URI id) {
        Vcenter vcenter = queryObject(Vcenter.class, id, false);
        return vcenter.getTenant();
    }

    @SuppressWarnings("unchecked")
    @Override
    public Class<Vcenter> getResourceClass() {
        return Vcenter.class;
    }

    @Override
    protected ResourceTypeEnum getResourceType() {
        return ResourceTypeEnum.VCENTER;
    }

    @Override
    public VcenterBulkRep queryBulkResourceReps(List<URI> ids) {

        Iterator<Vcenter> _dbIterator =
                _dbClient.queryIterativeObjects(getResourceClass(), ids);
        return new VcenterBulkRep(BulkList.wrapping(_dbIterator, MapVcenter.getInstance()));
    }

    @Override
    public VcenterBulkRep queryFilteredBulkResourceReps(List<URI> ids) {

        Iterator<Vcenter> _dbIterator =
                _dbClient.queryIterativeObjects(getResourceClass(), ids);
        BulkList.ResourceFilter filter = new BulkList.VcenterFilter(getUserFromContext(), _permissionsHelper);
        return new VcenterBulkRep(BulkList.wrapping(_dbIterator, MapVcenter.getInstance(), filter));
    }

    @Override
    protected boolean isZoneLevelResource() {
        return false;
    }

    @Override
    protected boolean isSysAdminReadableResource() {
        return true;
    }

    public static class VcenterResRepFilter<E extends RelatedResourceRep>
            extends ResRepFilter<E> {
        public VcenterResRepFilter(StorageOSUser user,
                PermissionsHelper permissionsHelper) {
            super(user, permissionsHelper);
        }

        @Override
        public boolean isAccessible(E resrep) {
            boolean ret = false;
            URI id = resrep.getId();

            Vcenter obj = _permissionsHelper.getObjectById(id, Vcenter.class);
            if (obj == null) {
                return false;
            }
            if (obj.getTenant().toString().equals(_user.getTenantId()) ||
                    isSystemOrRestrictedSystemAdmin() || isSecurityAdmin()) {
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
        return new VcenterResRepFilter(user, permissionsHelper);
    }

    /**
     * Creates a new vCenter. Discovery is initiated after the vCenter is created.
     *
     * @param createParam the parameter that has the attributes of the vCenter
     *                    to be created.
     * @param validateConnection specifies if the connection to the vCenter to be
     *                           validated before creating the vCenter or not.
     *                           Default value is "false", so connection to the
     *                           vCenter will not be validated if it is not specified.
     * @return the vCenter discovery async task.
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.TENANT_ADMIN })
    public TaskResourceRep createVcenter(VcenterCreateParam createParam,
                                         @QueryParam("validate_connection") @DefaultValue("false") final Boolean validateConnection) {
        validateVcenter(createParam, null, validateConnection);

        // create and persist the vcenter
        Vcenter vcenter = createNewVcenter(null, createParam);
        vcenter.setRegistrationStatus(DiscoveredDataObject.RegistrationStatus.REGISTERED.toString());
        _dbClient.createObject(vcenter);
        auditOp(OperationTypeEnum.CREATE_VCENTER, true, null,
                vcenter.auditParameters());

        return doDiscoverVcenter(vcenter);
    }

    /**
     * Lists the id and name of all the vCenters that belong to the given
     * tenant organization if the requesting user is a SysAdmin, Restricted SysAdmin
     * or SecAdmin. If the requested user is a TenantAdmin, the "tenant" query param
     * is ignored and returned only the vCenters that the user's tenant shares.
     *
     * @param tid tenant to filter the vCenters.
     *
     * @return a list of vCenters that belong to the tenant organization.
     * @throws DatabaseException when a DB error occurs
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public VcenterList listVcenters(@QueryParam("tenant") final URI tid) throws DatabaseException {
        VcenterList list = new VcenterList();
        List<Vcenter> vcenters = null;
        final String nameField = "label";

        if (isSecurityAdmin() || isSystemOrRestrictedSystemAdmin()) {
            if ( NullColumnValueGetter.isNullURI(tid)||
                    Vcenter.ALL_TENANT_RESOURCES.equalsIgnoreCase(tid.toString())) {
                vcenters = getDataObjects(Vcenter.class);
                list.setVcenters(map(ResourceTypeEnum.VCENTER, getNamedElementsList(Vcenter.class, nameField, vcenters)));
            } else if (Vcenter.TENANT_RESOURCES_WITH_NO_TENANTS.equalsIgnoreCase(tid.toString())) {
                vcenters = getDataObjects(Vcenter.class);
                list.setVcenters(map(ResourceTypeEnum.VCENTER, getNamedElementsWithNoAcls(Vcenter.class, nameField, vcenters)));
            } else {
                ArgValidator.checkEntity(_dbClient.queryObject(tid), tid, isIdEmbeddedInURL(tid));
                list.setVcenters(map(ResourceTypeEnum.VCENTER, listChildrenWithAcls(tid, Vcenter.class, nameField)));
            }
            return list;
        }

        vcenters = getDataObjects(Vcenter.class);
        if (!CollectionUtils.isEmpty(vcenters)) {
            //Get the vCenters based on the User's tenant org. If the user is not a tenant admin, insufficient
            //permission exception will be thrown.
            List<Vcenter> tenantVcenterList = filterVcentersByTenant(vcenters, NullColumnValueGetter.getNullURI());
            list.setVcenters(map(ResourceTypeEnum.VCENTER, getNamedElementsList(Vcenter.class, nameField, tenantVcenterList)));
        }
        return list;
    }

    /**
     * Get vCenter ACLs
     *
     * @param id the URN of a ViPR vCenter
     * @prereq none
     * @brief Show vCenter ACL
     * @return ACL Assignment details
     */
    @GET
    @Path("/{id}/acl")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public ACLAssignments getAclAssignments(@PathParam("id") URI id) {
        return getAclAssignmentsResponse(id);
    }

    /**
     * Add or remove individual ACL entry(s). When the vCenter is created
     * with no shared access (Vcenter.shared = Boolean.FALSE), there cannot
     * be multiple ACL Entries associated with this vCenter.
     *
     * @param changes ACL assignment changes. Request body must include at least one add or remove operation
     * @param id the URN of a ViPR Project
     * @return No data returned in response body
     */
    @PUT
    @Path("/{id}/acl")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SECURITY_ADMIN, Role.TENANT_ADMIN, Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    public TaskResourceRep updateAclAssignments(@PathParam("id") URI id,
                                                ACLAssignmentChanges changes) {
        //Make sure the vCenter is a valid one.
        Vcenter vcenter = queryObject(Vcenter.class, id, true);
        ArgValidator.checkEntity(vcenter, id, isIdEmbeddedInURL(id));

        //Validate the acl assignment changes. It is not valid when an
        //acl entry contains more than one privilege or privileges
        //other than USE.
        validateAclAssignments(changes);

        //Make sure that the vCenter with respect to the tenants
        //that we are removing is not in use (means the datacenters
        //and its clusters and hosts with the removing tenant do not
        //have any exports).
        checkVcenterUsage(vcenter, changes);

        _permissionsHelper.updateACLs(vcenter, changes, new PermissionsHelper.UsageACLFilter(_permissionsHelper));

        _dbClient.updateAndReindexObject(vcenter);

        auditOp(OperationTypeEnum.UPDATE_VCENTER, true, null, vcenter.getId()
                .toString(), vcenter.getLabel(), changes);

        //Rediscover the vCenter, this will update the updated
        //list of tenants based its latest acls to its datacenters
        //and hosts and clusters.
        return doDiscoverVcenter(queryObject(Vcenter.class, vcenter.getId(), true));
    }

    /**
     * Filters the vCenters by the tenant. If the provided tenant is
     * null or the tenant does not share the vCenter than the vCenters
     * are filtered with the user's tenant.
     *
     * @param vcenters to be filtered by the tenant.
     * @param tenantId to be used for filtering the vCenter.
     * @return the list of vCenters that belong to the tenantId or the user's tenant org.
     */
    private List<Vcenter> filterVcentersByTenant(List<Vcenter> vcenters, URI tenantId) {
        List<Vcenter> tenantVcenterList = new ArrayList<Vcenter>();
        Iterator<Vcenter> vcenterIt = vcenters.iterator();
        while (vcenterIt.hasNext()) {
            Vcenter vcenter = vcenterIt.next();
            if (vcenter == null) {
                continue;
            }

            Set<URI> tenantUris = _permissionsHelper.getUsageURIsFromAcls(vcenter.getAcls());
            if (CollectionUtils.isEmpty(tenantUris)) {
                continue;
            }

            if (!NullColumnValueGetter.isNullURI(tenantId) && !tenantUris.contains(tenantId)) {
                //The tenantId is not a null URI and it is not available in the vCenter acls,
                //so, dont add to the filtered list.
                continue;
            }

            Iterator<URI> tenantUriIt = tenantUris.iterator();
            while (tenantUriIt.hasNext()) {
                if(verifyAuthorizedInTenantOrg(tenantUriIt.next())) {
                    tenantVcenterList.add(vcenter);
                }
            }
        }
        return tenantVcenterList;
    }

    /**
     * Adds the tenant to the vCenter acls if the tenant admin is
     * creating it. This always sets the vCenter tenant (the old
     * deprecated filed to null).
     *
     * @param tenant a valid tenant org if the tenant admin is
     *               creating it.
     * @param vcenter the vCenter being created.
     */
    private void addVcenterAclIfTenantAdmin(TenantOrg tenant, Vcenter vcenter) {
        //Always set the deprecated tenant field of a vCenter to null.
        vcenter.setTenant(NullColumnValueGetter.getNullURI());

        if (isSystemAdmin()) {
            return;
        }

        URI tenantId;
        if (tenant != null) {
            tenantId = tenant.getId();
        } else {
            //If the tenant org is not valid, try to use the
            //user's tenant org.
            tenantId = URI.create(getUserFromContext().getTenantId());
        }

        //If the User is an admin in the tenant org, allow the
        //operation otherwise, report the insufficient permission
        //exception.
        if (verifyAuthorizedInTenantOrg(tenantId)) {
            //Generate the acl entry and add to the vCenters acls.
            String aclKey = _permissionsHelper.getTenantUsePermissionKey(tenantId.toString());
            vcenter.addAcl(aclKey, ACL.USE.name());
        }
    }

    /**
     * Check if the vCenter being updated is used by any of its vCenterDataCenters
     * or clusters or hosts or not. This validates only with respect to the tenant
     * that is being removed from the vCenter acls. If the tenant that is getting
     * removed teh vCenter has any exports with the vCenter's vCenterDataCenter or
     * its clusters or hosts.
     *
     * @param vcenter the vCenter being updated.
     * @param changes new acl assignment changes for the vCenter.
     */
    private void checkVcenterUsage(Vcenter vcenter, ACLAssignmentChanges changes) {
        //Make a copy of the vCenter's existing tenant list.
        List<ACLEntry> existingAclEntries = _permissionsHelper.convertToACLEntries(vcenter.getAcls());
        if (CollectionUtils.isEmpty(existingAclEntries)) {
            //If there no existing acl entries for the vCenter
            //there is nothing to validate if it is in user or oot.
            return;
        }

        //If there are no tenants to be removed from the vCenter acls,
        //there is nothing to check for usage.
        if (CollectionUtils.isEmpty(changes.getRemove())) {
            return;
        }

        Set<String> tenantsInUse = new HashSet<String>();

        Set<URI> removingTenants = _permissionsHelper.getUsageURIsFromAclEntries(changes.getRemove());
        Set<URI> existingTenants = _permissionsHelper.getUsageURIsFromAclEntries(existingAclEntries);

        Iterator<URI> removingTenantsIterator = removingTenants.iterator();
        while (removingTenantsIterator.hasNext()) {
            URI removingTenant = removingTenantsIterator.next();
            if (!existingTenants.contains(removingTenant)) {
                continue;
            }

            //Check if vCenter is in use for the removing tenant or not.
            //This checks for all the datacenters of this vcenter that belong to the
            //removing tenant and finds if the datacenter or it clusters or hosts
            //use the exports from the removing tenant or not.
            if (ComputeSystemHelper.isVcenterInUseForTheTenant(_dbClient, vcenter.getId(), removingTenant)) {
                TenantOrg tenant = _dbClient.queryObject(TenantOrg.class, removingTenant);
                tenantsInUse.add(tenant.getLabel());
            }
        }

        if(!CollectionUtils.isEmpty(tenantsInUse)) {
            throw APIException.badRequests.cannotRemoveTenant("vCener", vcenter.getLabel(), tenantsInUse);
        }
    }

    /**
     * Validates the acl assignment changes.
     * It is not valid acl assignment change, when an
     * acl entry contains more than one privilege or privileges
     * other than USE if the tenant provided in the acl entry
     * is not a valid tenant org.
     *
     * @param changes acl assignment changes to validated.
     */
    private void validateAclAssignments(ACLAssignmentChanges changes) {
        if(changes == null) {
            throw APIException.badRequests.requiredParameterMissingOrEmpty("ACLAssignmentChanges");
        }

        //Make sure at least one acl entry either in the add or remove
        //list.
        if (CollectionUtils.isEmpty(changes.getAdd()) &&
                CollectionUtils.isEmpty(changes.getRemove())) {
            throw APIException.badRequests.requiredParameterMissingOrEmpty("ACLAssignmentChanges");
        }

        validateAclEntries(changes.getAdd());
        validateAclEntries(changes.getRemove());
    }

    /**
     * Validates the individual list of acl entries.
     * It is not valid acl entries list, when an
     * acl entry contains more than one privilege or privileges
     * other than USE and if the tenant provided in the acl entry
     * is not a valid tenant org.
     *
     * @param aclEntries acl entries to be validated.
     */
    private void validateAclEntries(List<ACLEntry> aclEntries) {
        if (CollectionUtils.isEmpty(aclEntries)) {
            return;
        }

        Iterator<ACLEntry> aclEntryIterator = aclEntries.iterator();
        while (aclEntryIterator.hasNext()) {
            ACLEntry aclEntry = aclEntryIterator.next();

            //If more than one privileges provided the ACL Entry, it is not supported
            //for vCenter ACL. Only USE ACL can be provided.
            if (aclEntry.getAces().size() != 1) {
                throw APIException.badRequests.unsupportedNumberOfPrivileges(URI.create(aclEntry.getTenant()),
                        aclEntry.getAces());
            }

            if (!aclEntry.getAces().get(0).equalsIgnoreCase(ACL.USE.name())) {
                throw APIException.badRequests.unsupportedPrivilege(URI.create(aclEntry.getTenant()),
                        aclEntry.getAces().get(0));
            }

            //Validate if the provided tenant is a valid tenant or not.
            URI tenantId = URI.create(aclEntry.getTenant());
            TenantOrg tenant = queryObject(TenantOrg.class, tenantId, true);
            ArgValidator.checkEntity(tenant, tenantId, isIdEmbeddedInURL(tenantId));
        }
    }

    /**
     * Gets the current acl assignments of the requested vCenter.
     *
     * @param vcenterId
     * @return the list of acl assignments of the requested vCenter.
     */
    private ACLAssignments getAclAssignmentsResponse(URI vcenterId) {
        Vcenter vcenter = queryObject(Vcenter.class, vcenterId, true);
        ArgValidator.checkEntity(vcenter, vcenterId, isIdEmbeddedInURL(vcenterId));

        ACLAssignments response = new ACLAssignments();
        response.setAssignments(_permissionsHelper.convertToACLEntries(vcenter.getAcls()));

        return response;
    }

    /**
     * Checks if the user is authorized to view the vCenter.
     * Authorized if,
     * The user has SysAdmin or Restricted SysAdmin or SecAdmin role.
     * The user is a TenantAdmin of one of the that shares the vCenter.
     *
     * @param aclEntries the tenants list that shares the vCenter.
     * @param user the user to validated for authorization.
     */
    protected void verifyAuthorizedSystemOrTenantOrgUser(List<ACLEntry> aclEntries, StorageOSUser user) {
        if (isSystemOrRestrictedSystemAdmin() || isSecurityAdmin()) {
            return;
        }

        verifyAuthorizedInTenantOrg(aclEntries, user);
    }

    /**
     * Checks if the user is authorized to view the vCenter.
     * Authorized if,
     * The user a TenantOrg user of one the tenant that shares the vCenter.
     * The user is a TenantAdmin of one of the tenant that shares the vCenter.
     *
     * @param aclEntries the tenants list that shares the vCenter.
     * @param user the user to validated for authorization.
     */
    private void verifyAuthorizedInTenantOrg(List<ACLEntry> aclEntries, StorageOSUser user) {
        boolean isUserAuthorized = false;
        Iterator<ACLEntry> aclEntriesIterator = aclEntries.iterator();
        while (aclEntriesIterator.hasNext()) {
            ACLEntry aclEntry = aclEntriesIterator.next();
            if (aclEntry == null || !aclEntry.getTenant().equals(user.getTenantId())) {
                continue;
            }

            if (user.getTenantId().toString().equals(aclEntry.getTenant()) ||
                    _permissionsHelper.userHasGivenRole(user, URI.create(aclEntry.getTenant()), Role.TENANT_ADMIN)) {
                isUserAuthorized = true;
                break;
            }
        }

        if (!isUserAuthorized) {
            throw APIException.forbidden.insufficientPermissionsForUser(user.getName());
        }
    }

    /**
     * Checks if the user is authorized in the tenant org or not.
     * Authorized if,
     * The user is in the tenant org.
     * The user who is not in the tenant org, but is a TenantAdmin of the tenant org.
     *
     * @param tenantId the tenants list that shares the vCenter.
     * @return true if the user is authorized for the tenant org, false otherwise.
     */
    protected boolean verifyAuthorizedInTenantOrg(URI tenantId) {
        StorageOSUser user = getUserFromContext();
        if (tenantId.toString().equals(user.getTenantId()) && _permissionsHelper.userHasGivenRole(user,
                tenantId, Role.TENANT_ADMIN)) {
            return true;
        }
        return false;
    }

    /**
     * Check if the other tenants using the vCenter before deleting it.
     * SysAdmin deleting the vCenter is always allowed whereas, if the
     * vCenter is shared with multiple tenants then it cannot be deleted by
     * the Tenant Admin.
     *
     * @param vcenter to be deleted.
     */
    private void checkIfOtherTenantsUsingTheVcenter(Vcenter vcenter) {
        if (!isSystemAdmin()) {
            if (vcenter.getAcls().size() > 1) {
                throw APIException.forbidden.tenantAdminCannotDeleteVcenter(getUserFromContext().getName(), vcenter.getLabel());
            }
        }
    }
}
