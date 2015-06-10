/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.api.service.impl.resource;

import static com.emc.storageos.api.mapper.DbObjectMapper.map;
import static com.emc.storageos.api.mapper.HostMapper.map;
import static com.emc.storageos.api.mapper.TaskMapper.toTask;

import java.net.URI;
import java.util.ArrayList;
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
import com.emc.storageos.db.client.model.Cluster;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.TenantOrg;
import com.emc.storageos.db.client.model.Vcenter;
import com.emc.storageos.db.client.model.VcenterDataCenter;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.model.BulkIdParam;
import com.emc.storageos.model.RelatedResourceRep;
import com.emc.storageos.model.ResourceOperationTypeEnum;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.TaskList;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.host.HostList;
import com.emc.storageos.model.host.cluster.ClusterList;
import com.emc.storageos.model.host.vcenter.VcenterBulkRep;
import com.emc.storageos.model.host.vcenter.VcenterDataCenterCreate;
import com.emc.storageos.model.host.vcenter.VcenterDataCenterList;
import com.emc.storageos.model.host.vcenter.VcenterDataCenterRestRep;
import com.emc.storageos.model.host.vcenter.VcenterParam;
import com.emc.storageos.model.host.vcenter.VcenterRestRep;
import com.emc.storageos.model.host.vcenter.VcenterUpdateParam;
import com.emc.storageos.security.authentication.StorageOSUser;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.volumecontroller.AsyncTask;
import com.emc.storageos.volumecontroller.ControllerException;


/**
 * A service that provides APIs for viewing, updating and deleting vcenters and their
 * data centers.
 *
 */
@DefaultPermissions( read_roles = {Role.TENANT_ADMIN, Role.SYSTEM_MONITOR, Role.SYSTEM_ADMIN}, 
                    write_roles = {Role.TENANT_ADMIN})
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
            return  ResourceOperationTypeEnum.DISCOVER_VCENTER;
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
     * @param id The URI of the vCenter.
     * @prereq none
     * @brief Discover vCenter
     * @return TaskResourceRep (asynchronous call)
     */
    @POST
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    @Path("/{id}/discover")
    @CheckPermission(roles = {Role.TENANT_ADMIN})
    public TaskResourceRep discoverVcenter(@PathParam("id") URI id) {
        ArgValidator.checkFieldUriType(id, Vcenter.class, "id");
        Vcenter vcenter = queryObject(Vcenter.class, id, true);

        return doDiscoverVcenter(vcenter);
    }
    
    /**
     * Vcenter Discovery
     * @param the Vcenter to be discovered.
     * provided, a new taskId is generated.
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
     * @param param the input parameter
     * @param vcenter the vcenter being updated in case of update operation.
     * This parameter must be null for create operations.
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
        verifyAuthorizedInTenantOrg (vcenter.getTenant(), getUserFromContext());
        return map(vcenter);
    }

    /**
     * List the hosts of a vCenter.
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
    	// check the user permissions for this tenant org
        verifyAuthorizedInTenantOrg (vcenter.getTenant(), getUserFromContext());
        // get the hosts
        HostList list = new HostList();
        List<NamedElementQueryResultList.NamedElement> datacenters = listChildren(id,
                VcenterDataCenter.class, "label", "vcenter");
        for (NamedElementQueryResultList.NamedElement datacenter : datacenters) {
            // add all hosts that are directly related to the data center
            list.getHosts().addAll(map(ResourceTypeEnum.HOST, listChildren(datacenter.id, Host.class, "label", "vcenterDataCenter")));
        }
        return list;
    }

    /**
     * List the clusters in a vCenter
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
    	// check the user permissions for this tenant org
        verifyAuthorizedInTenantOrg (vcenter.getTenant(), getUserFromContext());
        // get the clusters
        ClusterList list = new ClusterList();
        List<NamedElementQueryResultList.NamedElement> datacenters = listChildren(id,
                VcenterDataCenter.class, "label", "vcenter");
        for (NamedElementQueryResultList.NamedElement datacenter : datacenters) {
            // add all clusters that are directly related to the data center
            list.getClusters().addAll(map(ResourceTypeEnum.CLUSTER, (listChildren(datacenter.id, Cluster.class, "label", "vcenterDataCenter"))));
        }
        return list;
    }

    /**
     * Deactivates the vCenter, its vCenter data centers, clusters and hosts.
     * @param id the URN of a ViPR vCenter to be deactivated
     * @prereq none
     * @brief Delete vCenter
     * @return OK if deactivation completed successfully
     * @throws DatabaseException when a DB error occurs     
     */
    @POST
    @Path("/{id}/deactivate")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission( roles = {Role.TENANT_ADMIN})
    public TaskResourceRep deactivateVcenter(@PathParam("id") URI id,
    		@DefaultValue("false") @QueryParam("detach-storage") boolean detachStorage) throws DatabaseException {
        if (ComputeSystemHelper.isVcenterInUse(_dbClient, id) && !detachStorage) {
        	throw APIException.badRequests.resourceHasActiveReferences(Vcenter.class.getSimpleName(), id);
        } else {
        	Vcenter vcenter = queryObject(Vcenter.class, id, true);
            
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
     * @param id the URN of a ViPR vcenter
     * @brief Detach storage from vcenter
     * @return task
     * @throws DatabaseException when a DB error occurs     
     */
    @POST
    @Path("/{id}/detach-storage")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission( roles = { Role.TENANT_ADMIN })
    public TaskResourceRep detachStorage(@PathParam("id") URI id) throws DatabaseException {
        Vcenter vcenter = queryObject(Vcenter.class, id, true);
        ArgValidator.checkEntity(vcenter, id, true);
        
        String taskId = UUID.randomUUID().toString();
        Operation op = _dbClient.createTaskOpStatus(Vcenter.class, vcenter.getId(), taskId,
                ResourceOperationTypeEnum.DETACH_VCENTER_DATACENTER_STORAGE);
        ComputeSystemController controller = getController(ComputeSystemController.class, null);
        controller.detachVcenterStorage(vcenter.getId(), false, taskId);
        return toTask(vcenter, taskId, op);
    }

    /**
     * Creates a new vCenter data center.
     * @param id the URN of the parent vCenter
     * @param createParam the details of the data center
     * @prereq none
     * @brief Create vCenter data center
     * @return the details of the vCenter data center, including its id and link,
     * when creation completes successfully.
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
        VcenterDataCenter datacenter  = new VcenterDataCenter();
        datacenter.setId(URIUtil.createId(VcenterDataCenter.class));
        datacenter.setLabel(createParam.getName());
        datacenter.setVcenter(id);
        datacenter.setTenant(vcenter.getTenant());
        _dbClient.createObject(datacenter);
        auditOp(OperationTypeEnum.CREATE_VCENTER_DATACENTER, true, null,
                datacenter.auditParameters());
        return map(datacenter);
    }

    /**
     * List the vCenter data centers of the vCenter.
     * @param id the URN of a ViPR vCenter
     * @prereq none
     * @brief List vCenter data centers
     * @return All the list of vCenter data centers.
     * @throws DatabaseException when a DB error occurs.     
     */
    @GET
    @Path("/{id}/vcenter-data-centers")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public VcenterDataCenterList getVcenterDataCenters(@PathParam("id") URI id) throws DatabaseException {
        Vcenter vcenter = queryObject(Vcenter.class, id, false);
    	// check the user permissions for this tenant org
        verifyAuthorizedInTenantOrg (vcenter.getTenant(), getUserFromContext());
        //get the vcenters
        VcenterDataCenterList list = new VcenterDataCenterList();
        List<NamedElementQueryResultList.NamedElement> elements = listChildren(id,
                VcenterDataCenter.class, "label", "vcenter");
        list.setDataCenters(map(ResourceTypeEnum.VCENTERDATACENTER, id, elements));
        return list;
    }

    /**
     * Creates a new instance of vcenter.
     * @param tenant the vcenter parent tenant organization
     * @param param the input parameter containing the vcenter attributes
     * @return an instance of {@link Vcenter}
     */
    protected Vcenter createNewVcenter(TenantOrg tenant, VcenterParam param) {
        Vcenter vcenter = new Vcenter();
        vcenter.setId(URIUtil.createId(Vcenter.class));
        vcenter.setTenant(tenant.getId());
        populateVcenterData(vcenter, param);
        return vcenter;
    }

    /**
     * Populate an instance of vcenter with the provided vcenter parameter
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
    @Consumes({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
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
    protected ResourceTypeEnum getResourceType(){
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
            if (obj == null)
                return false;
            if (obj.getTenant().toString().equals(_user.getTenantId()))
                return true;
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

}
