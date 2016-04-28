/*
 * Copyright (c) 2012-2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource;

import static com.emc.storageos.api.mapper.DbObjectMapper.toNamedRelatedResource;
import static com.emc.storageos.api.mapper.DbObjectMapper.toRelatedResource;
import static com.emc.storageos.api.mapper.ProtectionMapper.map;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.mapper.functions.MapProtectionSystem;
import com.emc.storageos.api.service.impl.resource.utils.AsyncTaskExecutorIntf;
import com.emc.storageos.api.service.impl.resource.utils.DiscoveredObjectTaskScheduler;
import com.emc.storageos.api.service.impl.response.BulkList;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.Constraint;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.DiscoveredDataObject.RegistrationStatus;
import com.emc.storageos.db.client.model.ProtectionSet;
import com.emc.storageos.db.client.model.ProtectionSystem;
import com.emc.storageos.db.client.model.RPSiteArray;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StorageSystem.Discovery_Namespaces;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.model.BulkIdParam;
import com.emc.storageos.model.ResourceOperationTypeEnum;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.TaskList;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.block.UnManagedCGList;
import com.emc.storageos.model.pools.VirtualArrayAssignments;
import com.emc.storageos.model.protection.ProtectionSystemBulkRep;
import com.emc.storageos.model.protection.ProtectionSystemConnectivityRestRep;
import com.emc.storageos.model.protection.ProtectionSystemConnectivitySiteRestRep;
import com.emc.storageos.model.protection.ProtectionSystemList;
import com.emc.storageos.model.protection.ProtectionSystemRequestParam;
import com.emc.storageos.model.protection.ProtectionSystemRestRep;
import com.emc.storageos.model.protection.ProtectionSystemUpdateRequestParam;
import com.emc.storageos.model.protection.RPClusterVirtualArrayAssignmentChanges;
import com.emc.storageos.protectioncontroller.ProtectionController;
import com.emc.storageos.protectioncontroller.RPController;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.volumecontroller.AsyncTask;
import com.emc.storageos.volumecontroller.ControllerException;

@Path("/vdc/protection-systems")
@DefaultPermissions(readRoles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR },
        writeRoles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
public class ProtectionSystemService extends TaskResourceService {

    private static final Logger _log = LoggerFactory.getLogger(ProtectionSystemService.class);
    private static final String EVENT_SERVICE_TYPE = "ProtectionSystem";

    @Override
    public String getServiceType() {
        return EVENT_SERVICE_TYPE;
    }

    private static class DiscoverJobExec implements AsyncTaskExecutorIntf {

        private final ProtectionController _controller;

        DiscoverJobExec(ProtectionController controller) {
            _controller = controller;
        }

        @Override
        public void executeTasks(AsyncTask[] tasks) throws ControllerException {
            _controller.discover(tasks);
        }

        @Override
        public ResourceOperationTypeEnum getOperation() {
            return ResourceOperationTypeEnum.DISCOVER_STORAGE_SYSTEM;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected URI getTenantOwner(URI id) {
        return null;
    }

    /**
     * Gets the protection system with the passed id from the database.
     * 
     * @param id the URN of a ViPR protection system
     * 
     * @return A reference to the registered ProtectionSystem.
     * 
     * @throws BadRequestException When the protection system is not
     *             registered.
     */
    @Override
    protected ProtectionSystem queryResource(URI id) {
        ArgValidator.checkUri(id);
        ProtectionSystem system = _dbClient.queryObject(ProtectionSystem.class, id);
        ArgValidator.checkEntityNotNull(system, id, isIdEmbeddedInURL(id));

        if (!RegistrationStatus.REGISTERED.toString().equalsIgnoreCase(
                system.getRegistrationStatus())) {
            throw APIException.badRequests.resourceAlreadyRegistered(ProtectionSystem.class.getSimpleName(), id);
        }
        return system;
    }

    /**
     * Allow the user to manually create a protection system.
     * 
     * @param param The protection system details.
     * 
     * @brief Create protection system
     * @return An asynchronous task corresponding to the discovery job scheduled for the new Protection System.
     * 
     * @throws BadRequestException When the system type is not valid or a
     *             protection system with the same native guid already exists.
     * @throws DatabaseException When an error occurs querying the database.
     * @throws ControllerException When an error occurs discovering the protection
     *             system.
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    public TaskResourceRep createProtectionSystem(ProtectionSystemRequestParam param)
            throws Exception {
        ProtectionSystem system = null;
        ProtectionSystem.Type systemType = ProtectionSystem.Type.valueOf(param.getSystemType());
        if (!systemType.equals(ProtectionSystem.Type.rp)) {
            throw APIException.badRequests.cannotRegisterSystemWithType(systemType.name());
        }

        system = new ProtectionSystem();
        system.setId(URIUtil.createId(ProtectionSystem.class));
        system.setSystemType(systemType.name());
        system.setRegistrationStatus(RegistrationStatus.REGISTERED.toString());
        system.setIpAddress(param.getIpAddress());
        system.setPortNumber(param.getPortNumber());
        system.setUsername(param.getUserName());
        system.setPassword(param.getPassword());
        system.setLabel(param.getLabel());
        system.setDiscoveryStatus(DiscoveredDataObject.DataCollectionJobStatus.CREATED
                .toString());
        _dbClient.createObject(system);

        auditOp(OperationTypeEnum.CREATE_PROTECTION_SYSTEM, true, null,
                param.getLabel(), systemType.name(), param.getIpAddress(), param.getPortNumber(),
                param.getUserName(), system.getId().toString());

        startProtectionSystem(system);

        ProtectionController controller = getController(RPController.class, ProtectionSystem._RP);
        ArrayList<AsyncTask> tasks = new ArrayList<AsyncTask>(1);
        String taskId = UUID.randomUUID().toString();
        tasks.add(new AsyncTask(ProtectionSystem.class, system.getId(), taskId));
        TaskList taskList = discoverProtectionSystems(tasks, controller);
        return taskList.getTaskList().listIterator().next();
    }

    /**
     * Allows the user to update credentials for a manually created protection systems.
     * 
     * @param id the URN of a ViPR protection system
     * @param param The protection system details to update.
     * 
     * @brief Update protection system credentials
     * @return A ProtectionSystemRestRep reference specifying the protection system
     *         data.
     * 
     * @throws InternalException When an error occurs discovering the protection
     *             system.
     */
    @PUT
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    public TaskResourceRep updateProtectionSystem(@PathParam("id") URI id,
            ProtectionSystemUpdateRequestParam param)
            throws InternalException {
        ProtectionSystem system = _dbClient.queryObject(ProtectionSystem.class, id);
        ArgValidator.checkEntityNotNull(system, id, isIdEmbeddedInURL(id));

        // If the IP Address is changing, this could be a brand new Protection System, reset the Version
        // and Compatibility Status.
        if (!system.getIpAddress().equals(param.getIpAddress())) {
            system.setMajorVersion("");
            system.setCompatibilityStatus(DiscoveredDataObject.CompatibilityStatus.UNKNOWN.toString());
        }

        // Update the IP, port, username, and password with the new incoming values
        system.setIpAddress(param.getIpAddress());
        system.setPortNumber(param.getPortNumber());
        system.setUsername(param.getUserName());
        system.setPassword(param.getPassword());

        // Must force a discover during an update.
        system.setLastDiscoveryRunTime(new Long(0));

        // Make necessary changes to the protection system's cluster->varray assignments
        modifyClusterVarrayAssignments(system, param.getVarrayChanges());

        // Persist the object changes
        _dbClient.persistObject(system);

        auditOp(OperationTypeEnum.UPDATE_PROTECTION_SYSTEM, true, null,
                system.getId().toString(), param.getIpAddress(), param.getPortNumber(), param.getUserName());

        startProtectionSystem(system);

        // execute discovery
        ProtectionController controller = getController(RPController.class, system.getSystemType());
        ArrayList<AsyncTask> tasks = new ArrayList<AsyncTask>(1);
        String taskId = UUID.randomUUID().toString();
        tasks.add(new AsyncTask(ProtectionSystem.class, system.getId(), taskId));
        TaskList taskList = discoverProtectionSystems(tasks, controller);
        return taskList.getTaskList().iterator().next();
    }

    /**
     * Validate and modify the protection system cluster varray assignments.
     * 
     * Validation errors:
     * 1. If an RP cluster ID doesn't exist in the protection set
     * 2. If a virtual array ID doesn't exist at all
     * 3. Missing an RP cluster ID (should be handled by JAXB)
     * 
     * Not Validation errors:
     * 1. Removing a virtual array that's already removed or not there
     * 2. Adding a virtual array that's already added
     * 
     * Not validated;
     * 1. Making sure you only included an RP cluster (at most) once
     * 
     * @param system protection system
     * @param varrayChanges virtual array cluster changes.
     */
    private void modifyClusterVarrayAssignments(ProtectionSystem system, Set<RPClusterVirtualArrayAssignmentChanges> varrayChanges) {
        if (varrayChanges == null || varrayChanges.isEmpty()) {
            return;
        }

        // Go through each cluster entry and process adds and removes
        for (RPClusterVirtualArrayAssignmentChanges varrayChange : varrayChanges) {
            if (varrayChange != null && varrayChange.getClusterId() == null) {
                throw APIException.badRequests.rpClusterVarrayNoClusterId(system.getLabel());
            }

            // Cluster ID can be the name or the ID, but we'll store the ID since that's unique
            String clusterId = varrayChange.getClusterId();
            if (system.getRpSiteNames().containsValue(clusterId)) {
                for (Map.Entry<String, String> entry : system.getRpSiteNames().entrySet()) {
                    if (entry.getValue().equalsIgnoreCase(clusterId)) {
                        clusterId = entry.getKey();
                    }
                }
            } else if (!system.getRpSiteNames().containsKey(clusterId)) {
                throw APIException.badRequests.rpClusterVarrayInvalidClusterId(system.getLabel());
            }

            if (varrayChange.hasAdded()) {
                VirtualArrayAssignments add = varrayChange.getAdd();
                if (add.getVarrays() != null) {
                    for (String varray : add.getVarrays()) {
                        // Validate the varray ID
                        try {
                            _dbClient.queryObject(VirtualArray.class, URI.create(varray));
                        } catch (Exception e) {
                            throw APIException.badRequests.rpClusterVarrayInvalidVarray(system.getLabel(), clusterId);
                        }

                        // Add the virtual array to this cluster in the protection system
                        system.addSiteAssignedVirtualArrayEntry(clusterId, varray);
                    }
                }
            }

            if (varrayChange.hasRemoved()) {
                VirtualArrayAssignments rem = varrayChange.getRemove();
                if (rem.getVarrays() != null) {
                    for (String varray : rem.getVarrays()) {
                        // Validate the varray ID
                        try {
                            _dbClient.queryObject(VirtualArray.class, URI.create(varray));
                        } catch (Exception e) {
                            throw APIException.badRequests.rpClusterVarrayInvalidVarray(system.getLabel(), clusterId);
                        }

                        // Add the virtual array to this cluster in the protection system
                        system.removeSiteAssignedVirtualArrayEntry(clusterId, varray);
                    }
                }
            }
        }
    }

    /**
     * Allows the user to manually discover all protection systems.
     * 
     * @brief Discover all protection systems
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    @Path("/discover")
    public TaskList discoverProtectionSystemsAll() {

        Iterator<URI> protectionIter = _dbClient.queryByType(ProtectionSystem.class, true).iterator();
        ArrayList<AsyncTask> tasks = new ArrayList<AsyncTask>();
        while (protectionIter.hasNext()) {
            URI protection = protectionIter.next();
            String taskId = UUID.randomUUID().toString();
            tasks.add(new AsyncTask(ProtectionSystem.class, protection, taskId));
        }
        ProtectionController controller = getController(RPController.class, ProtectionSystem._RP);
        return discoverProtectionSystems(tasks, controller);
    }

    private TaskList discoverProtectionSystems(List<AsyncTask> protectionTasks,
            ProtectionController controller) {
        DiscoveredObjectTaskScheduler scheduler = new DiscoveredObjectTaskScheduler(_dbClient, new DiscoverJobExec(controller));
        return scheduler.scheduleAsyncTasks(protectionTasks);
    }

    /**
     * Checks for valid Name space for discovery
     * Valid Name space for ALL & UNMANAGED_CGS
     *   
     * @param nameSpace namespace argument
     * @return true if valid namespace
     */
    private boolean validateNameSpace(String nameSpace) {
        boolean validNameSpace = false;

        if (nameSpace.equalsIgnoreCase(Discovery_Namespaces.UNMANAGED_CGS.toString()) ||
                    nameSpace.equalsIgnoreCase(Discovery_Namespaces.ALL.toString())) {
                    validNameSpace = true;
        }
        return validNameSpace;
    }

    /**
     * Allows the user to manually discover the registered protection system with
     * the passed id.
     * 
     * @param id the URN of a ViPR protection system.
     * @QueryParam namespace
     *             ProtectionSystem Auto Discovery is grouped into multiple namespaces.
     *             Namespace is used to discover specific parts of Storage System.
     * 
     *             Possible Values :
     *             UNMANAGED_CGS
     *             ALL
     * 
     *             UNMANAGED_CGS will discover all the consistency groups which are present in the
     *             Protection System (RPA).
     * 
     * @brief Discover protection system
     * @throws ControllerException When an error occurs discovering the protection
     *             system.
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    @Path("/{id}/discover")
    public TaskResourceRep discoverProtectionSystem(@PathParam("id") URI id,
            @QueryParam("namespace") String namespace) {

        ProtectionSystem protectionSystem = _dbClient.queryObject(ProtectionSystem.class, id);
        ArgValidator.checkEntity(protectionSystem, id, isIdEmbeddedInURL(id), true);
        // If Namespace is empty or null set it to ALL as default
        if (namespace == null || namespace.trim().length() < 1) {
            namespace = Discovery_Namespaces.ALL.toString();
        }

        if (!validateNameSpace(namespace)) {
            throw APIException.badRequests.invalidParameterProtectionSystemNamespace(namespace);
        }

        String deviceType = protectionSystem.getSystemType();
        ProtectionController controller = getController(RPController.class, deviceType);
        DiscoveredObjectTaskScheduler scheduler = new DiscoveredObjectTaskScheduler(_dbClient, new DiscoverJobExec(controller));
        ArrayList<AsyncTask> tasks = new ArrayList<AsyncTask>(1);
        String taskId = UUID.randomUUID().toString();
        tasks.add(new AsyncTask(ProtectionSystem.class, protectionSystem.getId(), taskId, namespace));
        TaskList taskList = scheduler.scheduleAsyncTasks(tasks);

        return taskList.getTaskList().listIterator().next();
    }

    /**
     * Gets the id, name, and self link for all registered protection systems.
     * 
     * @brief List protection systems
     * @return A reference to a ProtectionSystemList.
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR })
    public ProtectionSystemList getProtectionSystems() {
        ProtectionSystemList systemsList = new ProtectionSystemList();

        ProtectionSystem system = null;
        List<URI> ids = _dbClient.queryByType(ProtectionSystem.class, true);
        for (URI id : ids) {
            system = _dbClient.queryObject(ProtectionSystem.class, id);
            if (system != null
                    && RegistrationStatus.REGISTERED.toString().equalsIgnoreCase(
                            system.getRegistrationStatus())) {
                systemsList.getSystems().add(toNamedRelatedResource(system));
            }
        }
        return systemsList;
    }

    /**
     * Get information about the registered protection system with the passed id.
     * 
     * @param id the URN of a ViPR protection system.
     * 
     * @brief Show protection system
     * @return A reference to a ProtectionSystemRestRep
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR })
    public ProtectionSystemRestRep getProtectionSystem(@PathParam("id") URI id) {
        ArgValidator.checkFieldUriType(id, ProtectionSystem.class, "id");
        return map(queryResource(id));
    }

    /**
     * Get information about the connectivity of the registered protection system with the passed id.
     * 
     * @param id the URN of a ViPR protection system.
     * 
     * @brief Show protection system connectivity
     * @return A ProtectionSystemConnectivityRestRep object
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/connectivity")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR })
    public ProtectionSystemConnectivityRestRep getProtectionSystemConnectivity(
            @PathParam("id") URI id) {
        ArgValidator.checkFieldUriType(id, ProtectionSystem.class, "id");
        return getConnectivity(queryResource(id));
    }

    /**
     * checks for the existence of any volumes associated with a protection sysem
     * also compiles a list of empty protection sets associated with the protection system that can be deleted
     * 
     * @param system protection system
     * @param protectionSetsToDelete (return) empty list to be populated by this method
     * @return true if volumes exist; else false
     */
    private boolean checkForVolumes(URI id, List<ProtectionSet> protectionSetsToDelete) {
        boolean volumesExist = false;
        URIQueryResultList list = new URIQueryResultList();
        Constraint constraint = ContainmentConstraint.Factory.getProtectionSystemProtectionSetConstraint(id);
        _dbClient.queryByConstraint(constraint, list);
        Iterator<URI> it = list.iterator();
        while (it.hasNext()) {
            URI protectionSetId = it.next();
            ProtectionSet protectionSet = _dbClient.queryObject(ProtectionSet.class, protectionSetId);
            if (protectionSet != null && !protectionSet.getInactive()) {
                if (protectionSet.getVolumes() != null && !protectionSet.getVolumes().isEmpty()) {
                    for (String volId : protectionSet.getVolumes()) {
                        Volume vol = _dbClient.queryObject(Volume.class, URI.create(volId));
                        if (vol != null && !vol.getInactive()) {
                            volumesExist = true;
                            break;
                        }
                    }
                }
                if (!volumesExist) {
                    // there are no volumes in this protection set, we can delete it
                    protectionSetsToDelete.add(protectionSet);
                }
            }
        }
        return volumesExist;
    }

    /**
     * Deactivate protection system, this will move it to a "marked-for-delete" state.
     * It will be deleted in the next iteration of garbage collector
     * 
     * @param id the URN of a ViPR protection system
     * @brief Delete protection system
     * @return No data returned in response body
     */
    @POST
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/deactivate")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    public Response deleteProtectionSystem(@PathParam("id") URI id) {
        ArgValidator.checkFieldUriType(id, ProtectionSystem.class, "id");
        ProtectionSystem system = _dbClient.queryObject(ProtectionSystem.class, id);
        ArgValidator.checkEntityNotNull(system, id, isIdEmbeddedInURL(id));

        // Check to make sure there are no volumes associated with this protection system
        List<ProtectionSet> protectionSetsToDelete = new ArrayList<ProtectionSet>();
        if (checkForVolumes(id, protectionSetsToDelete)) {
            // don't allow the delete protection system if there are volumes
            throw APIException.badRequests.unableToDeactivateDueToDependencies(id);
        }

        // delete any empty protection sets
        _dbClient.markForDeletion(protectionSetsToDelete);

        // Side-effect: RPSiteArray entries need to be cleaned up so placement and connectivity feeds are correct
        // Mark all of the RPSiteArray entries associated with this protection system for deletion
        URIQueryResultList sitelist = new URIQueryResultList();
        _dbClient.queryByConstraint(AlternateIdConstraint.Factory
                .getRPSiteArrayProtectionSystemConstraint(id.toString()), sitelist);
        Iterator<URI> it = sitelist.iterator();
        while (it.hasNext()) {
            URI rpSiteArrayId = it.next();
            RPSiteArray siteArray = _dbClient.queryObject(RPSiteArray.class, rpSiteArrayId);
            if (siteArray != null) {
                _dbClient.markForDeletion(siteArray);
            }
        }

        _dbClient.markForDeletion(system);

        auditOp(OperationTypeEnum.DELETE_PROTECTION_SYSTEM, true, null,
                system.getId().toString());

        return Response.ok().build();
    }

    /**
     * Retrieve resource representations based on input ids.
     * 
     * @param param POST data containing the id list.
     * @brief List data of protection system resources
     * @return list of representations.
     * 
     * @throws DatabaseException When an error occurs querying the database.
     */
    @POST
    @Path("/bulk")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Override
    public ProtectionSystemBulkRep getBulkResources(BulkIdParam param) {
        return (ProtectionSystemBulkRep) super.getBulkResources(param);
    }

    /**
     * 
     * List all unmanaged cgs that are available for a protection system.  
     * Unmanaged cgs refers to cgs which are available within
     * underlying protection systems , but still not managed in ViPR.
     * As these cgs are not managed in ViPR, there will not be any ViPR specific
     * details associated such as, virtual array, virtual pool, or project.
     * 
     * @param id the URI of a ViPR protection system
     * @prereq none
     * @brief List of all unmanaged cgs available for a protection system
     * @return UnManagedCGList
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR })
    @Path("/{id}/unmanaged/cgs")
    public UnManagedCGList getUnManagedCGs(@PathParam("id") URI id) {
        ArgValidator.checkFieldUriType(id, ProtectionSystem.class, "id");
        UnManagedCGList unManagedCGList = new UnManagedCGList();
        URIQueryResultList result = new URIQueryResultList();
        _dbClient.queryByConstraint(ContainmentConstraint.Factory.getStorageDeviceUnManagedCGConstraint(id), result);
        while (result.iterator().hasNext()) {
            URI unManagedCGUri = result.iterator().next();
            unManagedCGList.getUnManagedCGs()
                    .add(toRelatedResource(ResourceTypeEnum.UNMANAGED_CGS, unManagedCGUri));
        }
        return unManagedCGList;
    }

    /**
     * This method will assemble a connectivity table that expresses all of the storage systems
     * that are connected via this protection system. We will mark which RP site each storage
     * system is visible on.
     * 
     * @param system protection system
     * @return rest response
     */
    private ProtectionSystemConnectivityRestRep getConnectivity(ProtectionSystem system) {

        ProtectionSystemConnectivityRestRep response = new ProtectionSystemConnectivityRestRep();

        // Dig through the RPSiteArray table for now and return connectivity
        Map<String, Set<URI>> siteStorageSystemMap = new HashMap<String, Set<URI>>();

        // Get the rp system's array mappings from the RP client
        URIQueryResultList sitelist = new URIQueryResultList();
        _dbClient.queryByConstraint(AlternateIdConstraint.Factory
                .getRPSiteArrayProtectionSystemConstraint(system.getId().toString()), sitelist);
        List<RPSiteArray> rpSiteArrays = new ArrayList<RPSiteArray>();
        Iterator<URI> it = sitelist.iterator();
        while (it.hasNext()) {
            URI rpSiteArrayId = it.next();
            RPSiteArray siteArray = _dbClient.queryObject(RPSiteArray.class, rpSiteArrayId);
            if (siteArray != null) {
                rpSiteArrays.add(siteArray);
            }
        }

        for (RPSiteArray rpSiteArray : rpSiteArrays) {
            _log.info("dicoverProtectionSystem(): analyzing rpsitearray: " + rpSiteArray.toString());

            if (siteStorageSystemMap.get(rpSiteArray.getRpSiteName()) == null) {
                siteStorageSystemMap.put(rpSiteArray.getRpSiteName(), new HashSet<URI>());
            }
            // Add this storage system associated with this RP Site
            siteStorageSystemMap.get(rpSiteArray.getRpSiteName()).add(rpSiteArray.getStorageSystem());
        }

        // Translate the primitive type into a presentable type
        for (String siteId : siteStorageSystemMap.keySet()) {
            ProtectionSystemConnectivitySiteRestRep site = new ProtectionSystemConnectivitySiteRestRep();
            site.setSiteID(siteId);
            Set<URI> addedStorageSystems = new HashSet<URI>();
            for (URI storageID : siteStorageSystemMap.get(siteId)) {
                if (!addedStorageSystems.contains(storageID)) {
                    StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class, storageID);
                    site.getStorageSystems().add(toRelatedResource(ResourceTypeEnum.STORAGE_SYSTEM, storageSystem.getId()));
                    addedStorageSystems.add(storageID);
                }
            }

            if (response.getProtectionSites() == null) {
                response.setProtectionSites(new ArrayList<ProtectionSystemConnectivitySiteRestRep>());
            }
            response.getProtectionSites().add(site);
        }

        response.setProtectionSystem(toNamedRelatedResource(ResourceTypeEnum.PROTECTION_SYSTEM, system.getId(), system.getLabel()));
        return response;
    }

    /**
     * Invoke connect protection. Once system is verified to be registered.
     * Statistics, Events will be collected for only registered systems.
     * 
     * @param system Protection system to start Metering & Monitoring.
     * @throws InternalException
     */
    private void startProtectionSystem(ProtectionSystem system) throws InternalException {
        ProtectionController controller = getProtectionController(system.getSystemType());
        controller.connect(system.getId());
    }

    /**
     * Invoke disconnect protection to stop events and statistics gathering of this
     * protection system.
     * 
     * @param protectionSystem A reference to the protection system.
     * @throws InternalException
     */
    private void stopProtectionSystem(ProtectionSystem protectionSystem) throws InternalException {
        ProtectionController controller = getProtectionController(protectionSystem.getSystemType());
        controller.disconnect(protectionSystem.getId());
    }

    /**
     * Return the protection controller for a given system type.
     * 
     * @param systemType The type of the protection system.
     * 
     * @return A reference to the protection controller
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    private ProtectionController getProtectionController(String systemType) {
        Class controllerClass = protectionSystemClass(systemType);
        ProtectionController controller = (ProtectionController) getController(controllerClass, systemType);
        return controller;
    }

    /**
     * Checks if this system supports FileShare Ops FIX ME -- hook this up into
     * the placement logic's supported protocols check
     * 
     * @param systemType
     * 
     * @return The file/block class of protection system
     */
    @SuppressWarnings("rawtypes")
    public static Class protectionSystemClass(String systemType) {
        // The only protection controller at this time.
        return RPController.class;
    }

    @Override
    protected ResourceTypeEnum getResourceType() {
        return ResourceTypeEnum.PROTECTION_SYSTEM;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Class<ProtectionSystem> getResourceClass() {
        return ProtectionSystem.class;
    }

    @Override
    public ProtectionSystemBulkRep queryBulkResourceReps(List<URI> ids) {

        Iterator<ProtectionSystem> _dbIterator =
                _dbClient.queryIterativeObjects(getResourceClass(), ids);
        return new ProtectionSystemBulkRep(BulkList.wrapping(_dbIterator, MapProtectionSystem.getInstance()));
    }

    @Override
    public ProtectionSystemBulkRep queryFilteredBulkResourceReps(List<URI> ids) {
        verifySystemAdmin();
        return queryBulkResourceReps(ids);
    }

}
