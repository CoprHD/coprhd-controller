/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource;

import static com.emc.storageos.api.mapper.HostMapper.map;
import static com.emc.storageos.api.mapper.TaskMapper.toTask;

import java.net.URI;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.storageos.api.mapper.HostMapper;
import com.emc.storageos.api.mapper.functions.MapIpInterface;
import com.emc.storageos.api.service.authorization.PermissionsHelper;
import com.emc.storageos.api.service.impl.response.BulkList;
import com.emc.storageos.api.service.impl.response.FilterIterator;
import com.emc.storageos.api.service.impl.response.ResRepFilter;
import com.emc.storageos.api.service.impl.response.SearchedResRepList;
import com.emc.storageos.computesystemcontroller.ComputeSystemController;
import com.emc.storageos.computesystemcontroller.impl.ComputeSystemHelper;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.model.DiscoveredDataObject.RegistrationStatus;
import com.emc.storageos.db.client.model.FileExport;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.db.client.model.IpInterface;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.util.EndpointUtility;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.model.BulkIdParam;
import com.emc.storageos.model.RelatedResourceRep;
import com.emc.storageos.model.ResourceOperationTypeEnum;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.host.IpInterfaceBulkRep;
import com.emc.storageos.model.host.IpInterfaceRestRep;
import com.emc.storageos.model.host.IpInterfaceUpdateParam;
import com.emc.storageos.model.search.SearchResultResourceRep;
import com.emc.storageos.model.search.SearchResults;
import com.emc.storageos.model.valid.Endpoint.EndpointType;
import com.emc.storageos.security.authentication.StorageOSUser;
import com.emc.storageos.security.authorization.ACL;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.resources.APIException;

/**
 * Service providing APIs for host ipinterfaces.
 */
@Path("/compute/ip-interfaces")
@DefaultPermissions(read_roles = { Role.TENANT_ADMIN, Role.SYSTEM_MONITOR, Role.SYSTEM_ADMIN },
        write_roles = { Role.TENANT_ADMIN },
        read_acls = { ACL.OWN, ACL.ALL })
public class IpInterfaceService extends TaskResourceService {

    private static Logger _log = LoggerFactory.getLogger(IpInterfaceService.class);

    private static final String EVENT_SERVICE_TYPE = "ip-interface";

    public String getServiceType() {
        return EVENT_SERVICE_TYPE;
    }

    @Autowired
    private HostService _hostService;

    @Override
    protected URI getTenantOwner(URI id) {
        IpInterface ipInterface = queryObject(IpInterface.class, id, false);
        Host host = queryObject(Host.class, ipInterface.getHost(), false);
        return host.getTenant();
    }

    @Override
    protected IpInterface queryResource(URI id) {
        return queryObject(IpInterface.class, id, false);
    }

    /**
     * Shows the data for an IP interface
     * 
     * @param id the URN of a ViPR IP interface
     * 
     * @prereq none
     * @brief Show IP interface
     * @return A IpInterfaceRestRep reference specifying the data for the
     *         IP interface with the passed id.
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}")
    @CheckPermission(roles = { Role.SYSTEM_MONITOR, Role.SYSTEM_ADMIN, Role.TENANT_ADMIN }, acls = { ACL.ANY })
    public IpInterfaceRestRep getIpInterface(@PathParam("id") URI id) {
        IpInterface ipInterface = queryResource(id);
        // verify permissions
        verifyUserPermisions(ipInterface);
        return HostMapper.map(ipInterface);
    }

    /**
     * Deactivate an IP interface.
     * 
     * @param id the URN of a ViPR IP interface
     * @prereq The IP interface must not have active exports
     * @brief Delete IP interface
     * @return OK if deactivation completed successfully
     * @throws DatabaseException
     */
    @POST
    @Path("/{id}/deactivate")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.TENANT_ADMIN })
    public TaskResourceRep deactivateIpInterface(@PathParam("id") URI id) throws DatabaseException {
        IpInterface ipInterface = queryResource(id);
        ArgValidator.checkEntity(ipInterface, id, isIdEmbeddedInURL(id));
        if (ipInterface.getIsManualCreation() != null && !ipInterface.getIsManualCreation()) {
            throw APIException.badRequests.ipInterfaceNotCreatedManuallyAndCannotBeDeleted();
        }

        String taskId = UUID.randomUUID().toString();
        Operation op = _dbClient.createTaskOpStatus(IpInterface.class, ipInterface.getId(), taskId,
                ResourceOperationTypeEnum.DELETE_HOST_IPINTERFACE);
        // Clean up File Export if host is in use
        if (ComputeSystemHelper.isHostIpInterfacesInUse(_dbClient, Collections.singletonList(ipInterface.getIpAddress()),
                ipInterface.getHost())) {
            ComputeSystemController controller = getController(ComputeSystemController.class, null);
            controller.removeIpInterfaceFromFileShare(ipInterface.getHost(), ipInterface.getId(), taskId);
        } else {
            _dbClient.ready(IpInterface.class, ipInterface.getId(), taskId);
            _dbClient.markForDeletion(ipInterface);
        }

        auditOp(OperationTypeEnum.DELETE_HOST_IPINTERFACE, true, null,
                ipInterface.auditParameters());
        return toTask(ipInterface, taskId, op);
    }

    /**
     * Update a host IP interface.
     * 
     * @param id the URN of a ViPR IP interface
     * @param updateParam the parameter containing the new attributes
     * @prereq none
     * @brief Update IP interface
     * @return the details of the updated host interface.
     * @throws DatabaseException when a DB error occurs
     */
    @PUT
    @Path("/{id}")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.TENANT_ADMIN })
    public IpInterfaceRestRep updateIpInterface(@PathParam("id") URI id,
            IpInterfaceUpdateParam updateParam) throws DatabaseException {
        IpInterface ipInterface = queryObject(IpInterface.class, id, true);
        _hostService.validateIpInterfaceData(updateParam, ipInterface);
        _hostService.populateIpInterface(updateParam, ipInterface);
        _dbClient.persistObject(ipInterface);
        auditOp(OperationTypeEnum.UPDATE_HOST_IPINTERFACE, true, null,
                ipInterface.auditParameters());
        return map(queryObject(IpInterface.class, id, false));
    }

    /**
     * List data of specified IP interfaces.
     * 
     * @param param POST data containing the id list.
     * @prereq none
     * @brief List data for IP interfaces
     * @return list of representations.
     */
    @POST
    @Path("/bulk")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Override
    public IpInterfaceBulkRep getBulkResources(BulkIdParam param) {
        return (IpInterfaceBulkRep) super.getBulkResources(param);
    }

    /**
     * Allows the user to deregister a registered IP interface so that it is no
     * longer used by the system. This simply sets the registration_status of
     * the IP interface to UNREGISTERED.
     * 
     * @param id the URN of a ViPR IP interface
     * 
     * @brief Unregister IP interface
     * @return Status response indicating success or failure
     */
    @POST
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/deregister")
    @CheckPermission(roles = { Role.TENANT_ADMIN })
    public IpInterfaceRestRep deregisterIpInterface(@PathParam("id") URI id) {

        IpInterface ipInterface = queryResource(id);
        ArgValidator.checkEntity(ipInterface, id, isIdEmbeddedInURL(id));
        if (ComputeSystemHelper.isHostIpInterfacesInUse(_dbClient, Collections.singletonList(ipInterface.getIpAddress()),
                ipInterface.getHost())) {
            throw APIException.badRequests.resourceHasActiveReferencesWithType(IpInterface.class.getSimpleName(), ipInterface.getId(),
                    FileExport.class.getSimpleName());
        }
        if (RegistrationStatus.REGISTERED.toString().equalsIgnoreCase(
                ipInterface.getRegistrationStatus())) {
            ipInterface.setRegistrationStatus(RegistrationStatus.UNREGISTERED.toString());
            _dbClient.persistObject(ipInterface);
            auditOp(OperationTypeEnum.DEREGISTER_HOST_IPINTERFACE, true, null,
                    ipInterface.getLabel(), ipInterface.getId().toString());
        }
        return map(ipInterface);
    }

    /**
     * Manually register the IP interface with the passed id.
     * 
     * @param id the URN of a ViPR IP interface
     * 
     * @brief Register IP interface
     * @return A reference to an IpInterfaceRestRep specifying the data for the
     *         IP interface.
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.TENANT_ADMIN })
    @Path("/{id}/register")
    public IpInterfaceRestRep registerIpInterface(@PathParam("id") URI id) {
        ArgValidator.checkFieldUriType(id, IpInterface.class, "id");
        IpInterface ipInterface = _dbClient.queryObject(IpInterface.class, id);
        ArgValidator.checkEntity(ipInterface, id, isIdEmbeddedInURL(id));

        if (RegistrationStatus.UNREGISTERED.toString().equalsIgnoreCase(
                ipInterface.getRegistrationStatus())) {
            ipInterface.setRegistrationStatus(RegistrationStatus.REGISTERED.toString());
            _dbClient.persistObject(ipInterface);
            auditOp(OperationTypeEnum.REGISTER_HOST_IPINTERFACE, true, null, ipInterface.getId().toString());
        }
        return map(ipInterface);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Class<IpInterface> getResourceClass() {
        return IpInterface.class;
    }

    @Override
    public IpInterfaceBulkRep queryBulkResourceReps(List<URI> ids) {

        Iterator<IpInterface> _dbIterator =
                _dbClient.queryIterativeObjects(getResourceClass(), ids);
        return new IpInterfaceBulkRep(BulkList.wrapping(_dbIterator, MapIpInterface.getInstance()));
    }

    @Override
    public IpInterfaceBulkRep queryFilteredBulkResourceReps(List<URI> ids) {
        Iterator<IpInterface> _dbIterator =
                _dbClient.queryIterativeObjects(getResourceClass(), ids);
        BulkList.ResourceFilter filter = new BulkList.HostInterfaceFilter(getUserFromContext(), _permissionsHelper);
        return new IpInterfaceBulkRep(BulkList.wrapping(_dbIterator, MapIpInterface.getInstance(), filter));
    }

    @Override
    protected ResourceTypeEnum getResourceType() {
        return ResourceTypeEnum.IPINTERFACE;
    }

    @Override
    protected boolean isZoneLevelResource() {
        return false;
    }

    @Override
    protected boolean isSysAdminReadableResource() {
        return true;
    }

    public static class IpInterfaceResRepFilter<E extends RelatedResourceRep>
            extends ResRepFilter<E> {
        public IpInterfaceResRepFilter(StorageOSUser user,
                PermissionsHelper permissionsHelper) {
            super(user, permissionsHelper);
        }

        @Override
        public boolean isAccessible(E resrep) {
            boolean ret = false;
            URI id = resrep.getId();

            IpInterface ipif = _permissionsHelper.getObjectById(id, IpInterface.class);
            if (ipif == null || ipif.getHost() == null) {
                return false;
            }

            Host obj = _permissionsHelper.getObjectById(ipif.getHost(), Host.class);
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
        return new IpInterfaceResRepFilter(user, permissionsHelper);
    }

    /**
     * Verify the user has read permission to the IP interface
     * 
     * @param ipInterface the IP interface to be verified
     */
    private void verifyUserPermisions(IpInterface ipInterface) {
        // check the user permissions for the tenant org
        Host host = queryObject(Host.class, ipInterface.getHost(), false);
        verifyAuthorizedInTenantOrg(host.getTenant(), getUserFromContext());
    }

    /**
     * 
     * parameter: 'ip_address' The ip address of the ipInterface
     * 
     * @return Return a list of ipinterfaces that contains the ip address specified
     *         or an empty list if no match was found.
     */
    @Override
    protected SearchResults getOtherSearchResults(Map<String, List<String>> parameters, boolean authorized) {
        SearchResults result = new SearchResults();

        if (!parameters.containsKey("ip_address")) {
            throw APIException.badRequests.invalidParameterSearchMissingParameter(getResourceClass().getName(), "ip_address");
        }

        for (Map.Entry<String, List<String>> entry : parameters.entrySet()) {
            if (!entry.getKey().equals("ip_address")) {
                throw APIException.badRequests.parameterForSearchCouldNotBeCombinedWithAnyOtherParameter(getResourceClass().getName(),
                        "ip_address", entry.getKey());
            }
        }

        String ip = parameters.get("ip_address").get(0);
        // Validate that the ip_address value is not empty
        ArgValidator.checkFieldNotEmpty(ip, "ip_address");

        // Validate the format of the initiator port.
        if (!EndpointUtility.isValidEndpoint(ip, EndpointType.IP)) {
            throw APIException.badRequests.invalidParameterInvalidIP("ip_address", ip);
        }

        SearchedResRepList resRepList = new SearchedResRepList(getResourceType());

        // Finds the IpInterface that includes the ip address specified, if any.
        _dbClient.queryByConstraint(AlternateIdConstraint.Factory.getIpInterfaceIpAddressConstraint(ip), resRepList);

        // Filter list based on permission
        if (!authorized) {
            Iterator<SearchResultResourceRep> _queryResultIterator = resRepList.iterator();
            ResRepFilter<SearchResultResourceRep> resRepFilter =
                    (ResRepFilter<SearchResultResourceRep>) getPermissionFilter(getUserFromContext(), _permissionsHelper);

            SearchedResRepList filteredResRepList = new SearchedResRepList();
            filteredResRepList.setResult(
                    new FilterIterator<SearchResultResourceRep>(_queryResultIterator, resRepFilter));

            result.setResource(filteredResRepList);
        } else {
            result.setResource(resRepList);
        }

        return result;
    }
}
