/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource;

import static com.emc.storageos.api.mapper.HostMapper.map;
import static com.emc.storageos.api.mapper.TaskMapper.toTask;

import java.net.URI;
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
import com.emc.storageos.api.mapper.functions.MapInitiator;
import com.emc.storageos.api.service.authorization.PermissionsHelper;
import com.emc.storageos.api.service.impl.resource.utils.ExportUtils;
import com.emc.storageos.api.service.impl.response.BulkList;
import com.emc.storageos.api.service.impl.response.FilterIterator;
import com.emc.storageos.api.service.impl.response.ResRepFilter;
import com.emc.storageos.api.service.impl.response.SearchedResRepList;
import com.emc.storageos.computesystemcontroller.ComputeSystemController;
import com.emc.storageos.computesystemcontroller.impl.ComputeSystemHelper;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.model.Cluster;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.DiscoveredDataObject.RegistrationStatus;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.util.EndpointUtility;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.model.BulkIdParam;
import com.emc.storageos.model.RelatedResourceRep;
import com.emc.storageos.model.ResourceOperationTypeEnum;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.block.export.ITLRestRepList;
import com.emc.storageos.model.host.InitiatorAliasRestRep;
import com.emc.storageos.model.host.InitiatorAliasSetParam;
import com.emc.storageos.model.host.InitiatorBulkRep;
import com.emc.storageos.model.host.InitiatorRestRep;
import com.emc.storageos.model.host.InitiatorUpdateParam;
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
import com.emc.storageos.volumecontroller.BlockController;
import com.emc.storageos.volumecontroller.impl.smis.vmax.VmaxExportOperations;
import com.emc.storageos.vplexcontroller.VPlexController;

/**
 * Service providing APIs for host initiators.
 */
@Path("/compute/initiators")
@DefaultPermissions(readRoles = { Role.TENANT_ADMIN, Role.SYSTEM_MONITOR, Role.SYSTEM_ADMIN },
        writeRoles = { Role.TENANT_ADMIN },
        readAcls = { ACL.OWN, ACL.ALL })
public class InitiatorService extends TaskResourceService {

    // Logger
    protected final static Logger _log = LoggerFactory.getLogger(InitiatorService.class);

    private static final String EVENT_SERVICE_TYPE = "initiator";
    private static final String ALIAS = "Alias-Operations";
    private static final String EMPTY_INITIATOR_ALIAS = "/";

    @Override
    public String getServiceType() {
        return EVENT_SERVICE_TYPE;
    }

    @Autowired
    private HostService _hostService;

    /**
     * List the exports of the initiator.
     * 
     * @prereq none
     * @brief List host initiator exports
     * @return A list of ITLs (Initiator/Target/Lun) for this initiator.
     * 
     * @throws DatabaseException When an error occurs querying the database.
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/exports")
    public ITLRestRepList getInitiatorExports(@PathParam("id") URI id) {
        Initiator initiator = queryObject(Initiator.class, id, false);
        // get the initiators' ITLs - permissions are also checked in ExportUtils
        return ExportUtils.getItlsForInitiator(initiator, _dbClient,
                _permissionsHelper, getUserFromContext());
    }

    /**
     * Show the information for an initiator.
     * 
     * @param id the URN of a ViPR initiator
     * 
     * @prereq none
     * @brief Show host initiator
     * @return A reference to an InitiatorRestRep specifying the initiator data.
     * 
     * @throws DatabaseException When an error occurs querying the database.
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}")
    public InitiatorRestRep getInitiator(@PathParam("id") URI id) {
        Initiator initiator = queryResource(id);
        // check the user has permissions
        verifyUserPermisions(initiator);
        return HostMapper.map(initiator);
    }

    /**
     * Update a host initiator.
     * 
     * @param id the URN of a ViPR initiator
     * @param updateParam the parameter containing the new attributes
     * @prereq none
     * @brief Update initiator.
     * @return the details of the updated host initiator.
     * @throws DatabaseException when a DB error occurs
     */
    @PUT
    @Path("/{id}")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.TENANT_ADMIN })
    public InitiatorRestRep updateInitiator(@PathParam("id") URI id,
            InitiatorUpdateParam updateParam) throws DatabaseException {
        Initiator initiator = queryObject(Initiator.class, id, true);
        _hostService.validateInitiatorData(updateParam, initiator);
        _hostService.populateInitiator(initiator, updateParam);
        _dbClient.persistObject(initiator);
        auditOp(OperationTypeEnum.UPDATE_HOST_INITIATOR, true, null,
                initiator.auditParameters());
        return map(queryObject(Initiator.class, id, false));
    }

    /**
     * Deactivate an initiator.
     * 
     * @param id the URN of a ViPR initiator
     * @prereq The initiator must not have active exports
     * @brief Delete host initiator
     * @return A Response indicating success or failure.
     * 
     * @throws DatabaseException When an error occurs querying the database.
     */
    @POST
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/deactivate")
    @CheckPermission(roles = { Role.TENANT_ADMIN })
    public TaskResourceRep deleteInitiator(@PathParam("id") URI id) {
        _log.info("Delete initiator {}", id);

        Initiator initiator = queryObject(Initiator.class, id, isIdEmbeddedInURL(id));
        if (!initiator.getIsManualCreation()) {
            throw APIException.badRequests.initiatorNotCreatedManuallyAndCannotBeDeleted();
        }

        ArgValidator.checkReference(Initiator.class, id, checkForDelete(initiator));

        String taskId = UUID.randomUUID().toString();
        Operation op = _dbClient.createTaskOpStatus(Initiator.class, initiator.getId(), taskId,
                ResourceOperationTypeEnum.DELETE_INITIATOR);

        Cluster cluster = getInitiatorCluster(initiator);

        if (ComputeSystemHelper.isInitiatorInUse(_dbClient, id.toString())) {
            if (cluster == null || cluster.getAutoExportEnabled()) {
                ComputeSystemController controller = getController(ComputeSystemController.class, null);
                controller.removeInitiatorFromExport(initiator.getHost(), initiator.getId(), taskId);
            } else {
                throw APIException.badRequests.initiatorInClusterWithAutoExportDisabled();
            }
        } else {
            _dbClient.ready(Initiator.class, initiator.getId(), taskId);
            _dbClient.markForDeletion(initiator);
        }

        auditOp(OperationTypeEnum.DELETE_HOST_INITIATOR, true, null,
                initiator.auditParameters());

        return toTask(initiator, taskId, op);
    }

    /**
     * List data of specified initiators.
     * 
     * @param param POST data containing the id list.
     * @prereq none
     * @brief List data of specified initiators
     * @return list of representations.
     * 
     * @throws DatabaseException When an error occurs querying the database.
     */
    @POST
    @Path("/bulk")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Override
    public InitiatorBulkRep getBulkResources(BulkIdParam param) {
        return (InitiatorBulkRep) super.getBulkResources(param);
    }

    /**
     * Allows the user to deregister a registered initiator so that it is no
     * longer used by the system. This simply sets the registration_status of
     * the initiator to UNREGISTERED.
     * 
     * @param id the URN of a ViPR initiator
     * 
     * @brief Unregister initiator
     * @return Status response indicating success or failure
     */
    @POST
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/deregister")
    @CheckPermission(roles = { Role.TENANT_ADMIN })
    public InitiatorRestRep deregisterInitiator(@PathParam("id") URI id) {

        Initiator initiator = queryResource(id);
        ArgValidator.checkEntity(initiator, id, isIdEmbeddedInURL(id));
        if (ComputeSystemHelper.isInitiatorInUse(_dbClient, id.toString())) {
            throw APIException.badRequests.resourceHasActiveReferencesWithType(Initiator.class.getSimpleName(), initiator.getId(),
                    ExportGroup.class.getSimpleName());
        }
        if (RegistrationStatus.REGISTERED.toString().equalsIgnoreCase(
                initiator.getRegistrationStatus())) {
            initiator.setRegistrationStatus(RegistrationStatus.UNREGISTERED.toString());
            _dbClient.persistObject(initiator);
            auditOp(OperationTypeEnum.DEREGISTER_INITIATOR, true, null,
                    initiator.getLabel(), initiator.getId().toString());
        }
        return map(initiator);
    }

    /**
     * Manually register the initiator with the passed id.
     * 
     * @param id the URN of a ViPR initiator
     * 
     * @brief Register initiator
     * @return A reference to an InitiatorRestRep specifying the data for the
     *         initiator.
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.TENANT_ADMIN })
    @Path("/{id}/register")
    public InitiatorRestRep registerInitiator(@PathParam("id") URI id) {
        ArgValidator.checkFieldUriType(id, Initiator.class, "id");
        Initiator initiator = _dbClient.queryObject(Initiator.class, id);
        ArgValidator.checkEntity(initiator, id, isIdEmbeddedInURL(id));

        if (RegistrationStatus.UNREGISTERED.toString().equalsIgnoreCase(
                initiator.getRegistrationStatus())) {
            initiator.setRegistrationStatus(RegistrationStatus.REGISTERED.toString());
            _dbClient.persistObject(initiator);
            auditOp(OperationTypeEnum.REGISTER_INITIATOR, true, null, initiator.getId().toString());
        }
        return map(initiator);
    }

    @Override
    public InitiatorBulkRep queryBulkResourceReps(List<URI> ids) {

        Iterator<Initiator> _dbIterator =
                _dbClient.queryIterativeObjects(getResourceClass(), ids);
        return new InitiatorBulkRep(BulkList.wrapping(_dbIterator, MapInitiator.getInstance()));
    }

    @Override
    public InitiatorBulkRep queryFilteredBulkResourceReps(List<URI> ids) {
        Iterator<Initiator> _dbIterator =
                _dbClient.queryIterativeObjects(getResourceClass(), ids);
        BulkList.ResourceFilter filter = new BulkList.HostInterfaceFilter(getUserFromContext(), _permissionsHelper);
        return new InitiatorBulkRep(BulkList.wrapping(_dbIterator, MapInitiator.getInstance(), filter));
    }

    /**
     * Shows the alias/initiator name for an initiator
     * if set on the Storage System
     * 
     * @param id the URN of a ViPR initiator
     * @param sid the pstorage system uri
     * @prereq none
     * @return A reference to an InitiatorRestRep representing the Initiator Alias if Set..
     * @throws Exception When an error occurs querying the VMAX Storage System.
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/alias/{sid}")
    public InitiatorAliasRestRep getInitiatorAlias(@PathParam("id") URI id, @PathParam("sid") URI systemURI) {
        // Basic Checks
        Initiator initiator = queryResource(id);
        verifyUserPermisions(initiator);
        ArgValidator.checkFieldUriType(systemURI, StorageSystem.class, "id");
        StorageSystem system = _permissionsHelper.getObjectById(systemURI, StorageSystem.class);
        ArgValidator.checkEntity(system, systemURI, isIdEmbeddedInURL(systemURI));

        _log.info("Retrieving alias for initiator {} on system {}", id, systemURI);

        String initiatorAlias = null;
        if (system != null && StorageSystem.Type.vmax.toString().equalsIgnoreCase(system.getSystemType())) {
            BlockController controller = getController(BlockController.class, system.getSystemType());
            //Actual Control
            try {
                initiatorAlias = controller.getInitiatorAlias(systemURI, id);
            } catch (Exception e) {
                _log.error("Unexpected error: Getting alias failed.", e);
                throw APIException.badRequests.unableToProcessRequest(e.getMessage());
            }
        } else {
            throw APIException.badRequests.operationNotSupportedForSystemType(ALIAS, system.getSystemType());
        }
        // If the Alias is empty, set it to "/".
        if (NullColumnValueGetter.isNullValue(initiatorAlias)) {
            initiatorAlias = EMPTY_INITIATOR_ALIAS;
        }
        // Update the initiator
        initiator.mapInitiatorName(system.getSerialNumber(), initiatorAlias);
        _dbClient.updateObject(initiator);

        return new InitiatorAliasRestRep(system.getSerialNumber(), initiatorAlias);
    }

    /**
     * Sets the alias/initiator name for an initiator
     * on the Storage System
     * 
     * @param id the URN of a ViPR initiator
     * @param aliasSetParam the parameter containing the storage system and alias attributes
     * @prereq none
     * @return A reference to an InitiatorRestRep representing the Initiator Alias after Set..
     * @throws Exception When an error occurs setting the alias on a VMAX Storage System.
     */
    @PUT
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/alias")
    public InitiatorAliasRestRep setInitiatorAlias(@PathParam("id") URI id, InitiatorAliasSetParam aliasSetParam) {
        //Basic Checks
        Initiator initiator = queryResource(id);
        verifyUserPermisions(initiator);

        URI systemURI = aliasSetParam.getSystemURI();
        ArgValidator.checkFieldUriType(systemURI, StorageSystem.class, "id");
        StorageSystem system = _permissionsHelper.getObjectById(systemURI, StorageSystem.class);
        ArgValidator.checkEntity(system, systemURI, isIdEmbeddedInURL(systemURI));

        String initiatorAlias = aliasSetParam.getInitiatorAlias();
        ArgValidator.checkFieldNotNull(initiatorAlias, "alias");

        _log.info("Setting alias- {} for initiator {} on system {}", initiatorAlias, id, systemURI);

        if (system != null && StorageSystem.Type.vmax.toString().equalsIgnoreCase(system.getSystemType())) {
            BlockController controller = getController(BlockController.class, system.getSystemType());
            try {
                //Actual Control
                controller.setInitiatorAlias(systemURI, id, initiatorAlias);
            } catch (Exception e) {
                _log.error("Unexpected error: Setting alias failed.", e);
                throw APIException.badRequests.unableToProcessRequest(e.getMessage());
            }
        } else {
            throw APIException.badRequests.operationNotSupportedForSystemType(ALIAS, system.getSystemType());
        }
        //Update the Initiator here..
        if (initiatorAlias.contains(EMPTY_INITIATOR_ALIAS)) {// If the Initiator Alias contains the "/" character, the user has supplied
                                                             // different node and port names.
            initiator.mapInitiatorName(system.getSerialNumber(), initiatorAlias);
        } else {// The user has set the same node and port names.
            initiatorAlias = String.format("%s%s%s", initiatorAlias, EMPTY_INITIATOR_ALIAS, initiatorAlias);
            initiator.mapInitiatorName(system.getSerialNumber(), initiatorAlias);
        }
        _dbClient.updateObject(initiator);
        return new InitiatorAliasRestRep(system.getSerialNumber(), initiatorAlias);
    }
    
    @Override
    protected Initiator queryResource(URI id) {
        return queryObject(Initiator.class, id, false);
    }

    @Override
    protected URI getTenantOwner(URI id) {
        return null;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Class<Initiator> getResourceClass() {
        return Initiator.class;
    }

    @Override
    protected ResourceTypeEnum getResourceType() {
        return ResourceTypeEnum.INITIATOR;
    }

    public static class InitiatorResRepFilter<E extends RelatedResourceRep>
            extends ResRepFilter<E> {
        public InitiatorResRepFilter(StorageOSUser user,
                PermissionsHelper permissionsHelper) {
            super(user, permissionsHelper);
        }

        @Override
        public boolean isAccessible(E resrep) {
            boolean ret = false;
            URI id = resrep.getId();

            Initiator ini = _permissionsHelper.getObjectById(id, Initiator.class);
            if (ini == null || ini.getHost() == null) {
                return false;
            }

            Host obj = _permissionsHelper.getObjectById(ini.getHost(), Host.class);
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
        return new InitiatorResRepFilter(user, permissionsHelper);
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
     * Verify the user has read permissions to the the initiator
     * 
     * @param initiator the initiator to be verified
     */
    private void verifyUserPermisions(Initiator initiator) {
        // check the user has permissions
        if (initiator.getHost() == null) {
            // this is a system-created initiator - should only be viewed by system admin or monitor
            verifySystemAdminOrMonitorUser();
        } else {
            // otherwise, check the user permissions for the tenant org
            Host host = queryObject(Host.class, initiator.getHost(), false);
            verifyAuthorizedInTenantOrg(host.getTenant(), getUserFromContext());
        }
    }

    private Cluster getInitiatorCluster(Initiator initiator) {
        URI hostId = initiator.getHost();
        Cluster cluster = null;
        URI clusterId = null;
        if (!NullColumnValueGetter.isNullURI(hostId)) {
            Host host = _dbClient.queryObject(Host.class, hostId);
            clusterId = host.getCluster();
        }
        if (!NullColumnValueGetter.isNullURI(clusterId)) {
            cluster = _dbClient.queryObject(Cluster.class, clusterId);
        }
        return cluster;
    }

    /**
     * 
     * parameter: 'initiator_port' The identifier of the initiator port.
     * 
     * @return Return a list of initiator that containts the initiator port specified
     *         or an empty list if no match was found.
     */
    @Override
    protected SearchResults getOtherSearchResults(Map<String, List<String>> parameters, boolean authorized) {
        SearchResults result = new SearchResults();

        if (!parameters.containsKey("initiator_port")) {
            throw APIException.badRequests.invalidParameterSearchMissingParameter(getResourceClass().getName(), "initiator_port");
        }

        for (Map.Entry<String, List<String>> entry : parameters.entrySet()) {
            if (!entry.getKey().equals("initiator_port")) {
                throw APIException.badRequests.parameterForSearchCouldNotBeCombinedWithAnyOtherParameter(getResourceClass().getName(),
                        "initiator_port", entry.getKey());
            }
        }

        String port = parameters.get("initiator_port").get(0);
        // Validate that the initiator_port value is not empty
        ArgValidator.checkFieldNotEmpty(port, "initiator_port");

        // Validate the format of the initiator port.
        if (!EndpointUtility.isValidEndpoint(port, EndpointType.SAN)) {
            throw APIException.badRequests.initiatorPortNotValid();
        }

        SearchedResRepList resRepList = new SearchedResRepList(getResourceType());

        // Finds the Initiator that includes the initiator port specified, if any.
        _dbClient.queryByConstraint(AlternateIdConstraint.Factory.getInitiatorPortInitiatorConstraint(port), resRepList);

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
