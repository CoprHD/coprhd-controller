/*
 * Copyright (c) 2012-2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.api.service.impl.resource;

import static com.emc.storageos.api.mapper.DbObjectMapper.toNamedRelatedResource;
import static com.emc.storageos.api.mapper.TaskMapper.toTask;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
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

import com.emc.storageos.api.mapper.functions.MapStoragePort;
import com.emc.storageos.api.service.impl.resource.utils.PurgeRunnable;
import com.emc.storageos.api.service.impl.response.BulkList;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.DiscoveredDataObject.DiscoveryStatus;
import com.emc.storageos.db.client.model.DiscoveredDataObject.RegistrationStatus;
import com.emc.storageos.db.client.model.DiscoveredDataObject.Type;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.Network;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StoragePort.TransportType;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.VirtualNAS;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.db.client.util.EndpointUtility;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.db.client.util.WWNUtility;
import com.emc.storageos.db.client.util.iSCSIUtility;
import com.emc.storageos.model.BulkIdParam;
import com.emc.storageos.model.ResourceOperationTypeEnum;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.pools.VirtualArrayAssignmentChanges;
import com.emc.storageos.model.pools.VirtualArrayAssignments;
import com.emc.storageos.model.ports.StoragePortBulkRep;
import com.emc.storageos.model.ports.StoragePortList;
import com.emc.storageos.model.ports.StoragePortRestRep;
import com.emc.storageos.model.ports.StoragePortUpdate;
import com.emc.storageos.model.valid.Endpoint.EndpointType;
import com.emc.storageos.model.varray.NetworkEndpointParam;
import com.emc.storageos.networkcontroller.impl.NetworkAssociationHelper;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.util.ConnectivityUtil;
import com.emc.storageos.volumecontroller.impl.StoragePoolAssociationHelper;
import com.emc.storageos.volumecontroller.impl.StoragePortAssociationHelper;
import com.emc.storageos.volumecontroller.impl.monitoring.RecordableBourneEvent;
import com.emc.storageos.volumecontroller.impl.monitoring.RecordableEventManager;
import com.emc.storageos.volumecontroller.impl.monitoring.cim.enums.RecordType;
import com.emc.storageos.volumecontroller.impl.utils.ExportMaskUtils;

/**
 * StoragePort resource implementation
 */
@Path("/vdc/storage-ports")
@DefaultPermissions(readRoles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR },
        writeRoles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
public class StoragePortService extends TaggedResource {

    private static Logger _log = LoggerFactory.getLogger(StoragePortService.class);

    protected static final String EVENT_SERVICE_SOURCE = "StoragePortService";
    protected static final String STORAGEPORT_UPDATED_DESCRIPTION = "Storage port Updated";
    protected static final String STORAGEPORT_DEREGISTERED_DESCRIPTION = "Storage Port Unregistered";

    @Autowired
    private RecordableEventManager _evtMgr;

    @Autowired
    private NetworkService networkSvc;

    private static final String EVENT_SERVICE_TYPE = "storageport";

    @Override
    public String getServiceType() {
        return EVENT_SERVICE_TYPE;
    }

    // how many times to retry a procedure before returning failure to the user.
    // Is used with "system delete" operation.
    private int _retry_attempts;

    public void setRetryAttempts(int retries) {
        _retry_attempts = retries;
    }

    @Override
    protected URI getTenantOwner(URI id) {
        return null;
    }

    /**
     * Gets the storage port with the passed id from the database.
     * 
     * @param id the URN of a ViPR storage port.
     * 
     * @return A reference to the registered StoragePool.
     * 
     * @throws BadRequestException When the storage port is not registered.
     */
    protected StoragePort queryRegisteredResource(URI id) {
        ArgValidator.checkUri(id);
        StoragePort port = _dbClient.queryObject(StoragePort.class, id);
        ArgValidator.checkEntity(port, id, isIdEmbeddedInURL(id));

        if (!RegistrationStatus.REGISTERED.toString().equalsIgnoreCase(
                port.getRegistrationStatus())) {
            throw APIException.badRequests.resourceNotRegistered(
                    StoragePort.class.getSimpleName(), id);
        }

        return port;
    }

    @Override
    protected StoragePort queryResource(URI id) {
        ArgValidator.checkUri(id);
        StoragePort port = _dbClient.queryObject(StoragePort.class, id);
        ArgValidator.checkEntity(port, id, isIdEmbeddedInURL(id));
        return port;
    }

    /**
     * Gets the ids and self links for all storage ports.
     * 
     * @brief List storage ports
     * @return A StoragePortList reference specifying the ids and self links for
     *         the storage ports.
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR })
    public StoragePortList getStoragePorts() {

        StoragePortList storagePorts = new StoragePortList();
        List<URI> ids = _dbClient.queryByType(StoragePort.class, true);
        for (URI id : ids) {
            StoragePort port = _dbClient.queryObject(StoragePort.class, id);
            if ((port != null)) {
                storagePorts.getPorts().add(
                        toNamedRelatedResource(port, port.getNativeGuid()));
            }
        }

        return storagePorts;
    }

    /**
     * Gets the data for a storage port.
     * 
     * @param id the URN of a ViPR storage port.
     * 
     * @brief Show storage port
     * @return A StoragePortRestRep reference specifying the data for the
     *         storage port with the passed id.
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR })
    public StoragePortRestRep getStoragePort(@PathParam("id") URI id) {
        ArgValidator.checkFieldUriType(id, StoragePort.class, "id");
        StoragePort storagePort = queryResource(id);
        return MapStoragePort.getInstance(_dbClient).toStoragePortRestRep(storagePort);
    }

    /**
     * Allows the user to deregister a registered storage port so that it
     * is no longer used by the system. This simply sets the
     * registration_status of the storage port to UNREGISTERED.
     * 
     * @param id the URN of a ViPR storage port.
     * 
     * @brief Unregister storage port
     * @return Status response indicating success or failure
     */
    @POST
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/deregister")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    public StoragePortRestRep deregisterStoragePort(@PathParam("id") URI id) {

        ArgValidator.checkFieldUriType(id, StoragePort.class, "id");
        StoragePort port = queryResource(id);
        if (RegistrationStatus.REGISTERED.toString().equalsIgnoreCase(
                port.getRegistrationStatus())) {
            // Setting status to UNREGISTERED.
            port.setRegistrationStatus(RegistrationStatus.UNREGISTERED.toString());
            _dbClient.persistObject(port);

            // Record the storage port deregister event.
            recordStoragePortEvent(OperationTypeEnum.STORAGE_PORT_DEREGISTER,
                    STORAGEPORT_DEREGISTERED_DESCRIPTION, port.getId());

            auditOp(OperationTypeEnum.DEREGISTER_STORAGE_PORT, true, null,
                    port.getLabel(), port.getId().toString());
        }
        return MapStoragePort.getInstance(_dbClient).toStoragePortRestRep(port);
    }

    /**
     * Updates Network for the storage port with the passed
     * id and/or updates the virtual arrays to which the storage
     * port is assigned.
     * <p>
     * A port's network is used to determine to which initiators the port can be exported. It also determines the port's virtual arrays when
     * the port is not explicitly assigned to virtual arrays ( see {@link StoragePort#getAssignedVirtualArrays()}). In this case the port's
     * virtual arrays are the same as its networks virtual arrays (see {@link StoragePort#getConnectedVirtualArrays()}). Implicit virtual
     * arrays cannot be removed, they can only be overridden by an explicit assignment or automatically unassigned when the network is
     * unassigned from a virtual array. A port's effective virtual array assignment is {@link StoragePort#getTaggedVirtualArrays()}).
     * <p>
     * A port can be explicitly assigned to virtual arrays and this overrides the implicit assignment resulting from the network
     * association. If the explicit assignment is removed, the implicit assignment becomes effective again.
     * <p>
     * Managing ports virtual array assignments requires planning. In general, networks need not to be assigned to virtual arrays unless
     * implicit assignments of ports are desired.
     * 
     * @param id the URN of a ViPR storage port.
     * @param storagePortUpdates Specifies the updates to be made to the storage
     *            port
     * 
     * @brief Update storage port network and/or virtual array assignments.
     * @return A StoragePortRestRep specifying the updated storage port info.
     */
    @PUT
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    public StoragePortRestRep updateStoragePort(@PathParam("id") URI id,
            StoragePortUpdate storagePortUpdates) {

        // Get the storage port with the passed id.
        ArgValidator.checkFieldUriType(id, StoragePort.class, "id");
        StoragePort storagePort = queryResource(id);
        _log.info("Update called for storage port {}", id);

        // If the port is a VPLEX, then before any changes are
        // made for the port, get the storage pools for the systems
        // connected to the VPLEX. These pools and the vpools they
        // match may be impacted by the change to the VPLEX storage
        // port. We must get these ports now before any changes are
        // persisted for the port as the connected systems may
        // change and we would not get all potentially impacted pools.
        List<StoragePool> modifiedPools = null;
        URI systemURI = storagePort.getStorageDevice();
        StorageSystem system = _dbClient.queryObject(StorageSystem.class, systemURI);
        if (DiscoveredDataObject.Type.vplex.name().equals(system.getSystemType())) {
            modifiedPools = StoragePoolAssociationHelper.getStoragePoolsFromPorts(
                    _dbClient, Arrays.asList(storagePort), null, true);
        }

        // Get the old network as part of storage port.
        URI oldNetworkId = storagePort.getNetwork();
        // Update the storage port network assignment.
        URI newNetworkId = storagePortUpdates.getNetwork();
        _log.info("Checking for updates to storage port network.");
        boolean networkUpdated = updateStoragePortNetwork(storagePort, newNetworkId);
        if (networkUpdated) {
            _log.info("Storage port network has been modified.");
            // No need to update pool connectivity because the call to network service handles that
            // Get the updated reference.
            storagePort = queryResource(id);
        }

        // Update the storage port virtual array assignments.
        _log.info("Checking for updates to storage port virtual array assignments.");
        boolean virtualArraysUpdated = updateStoragePortVirtualArrays(storagePort,
                storagePortUpdates.getVarrayChanges());

        /**
         * This is applicable only for Cinder Storage System's port
         * as currently there is no API to discover it from Cinder.
         * So, it requires user to update the value for provisioning operations.
         */
        boolean portNetworkIdUpdated = updatePortNetworkId(storagePort,
                storagePortUpdates.getPortNetworkId());

        // If the port is a VPLEX, then after the changes are made
        // for the port, again get the storage pools for the systems
        // connected to the VPLEX. New pools and the vpools they
        // match may be impacted by the change to the VPLEX storage
        // port. We then get the union of these pools with the pools
        // determined before the changes were made. We then pass
        // these pools to the process that updates pool and port
        // associations when a storage port is modified.
        if (DiscoveredDataObject.Type.vplex.name().equals(system.getSystemType())) {
            List<StoragePool> pools = StoragePoolAssociationHelper.getStoragePoolsFromPorts(
                    _dbClient, Arrays.asList(storagePort), null, true);
            if ((modifiedPools == null) || (modifiedPools.isEmpty())) {
                modifiedPools = pools;
            } else {
                List<StoragePool> poolsToAdd = new ArrayList<StoragePool>();
                for (StoragePool pool : pools) {
                    URI poolURI = pool.getId();
                    boolean poolFound = false;
                    for (StoragePool modifiedPool : modifiedPools) {
                        if (poolURI.equals(modifiedPool.getId())) {
                            poolFound = true;
                            break;
                        }
                    }

                    if (!poolFound) {
                        poolsToAdd.add(pool);
                    }
                }
                modifiedPools.addAll(poolsToAdd);
            }
        }

        if (networkUpdated || portNetworkIdUpdated) {
            _log.info("Storage port was moved to other network.");
            // this method runs standard procedure for poolmatcher, rp connectivity
            StoragePortAssociationHelper.runUpdatePortAssociationsProcess(
                    Collections.singleton(storagePort), null, _dbClient,
                    _coordinator, modifiedPools);
        } else if (virtualArraysUpdated) {
            _log.info("Storage port virtual arrays have been modified.");
            // this method runs optimized procedure for poolmatcher, rp connectivity
            StoragePortAssociationHelper.runUpdatePortAssociationsProcessForVArrayChange(
                    storagePort, _dbClient,
                    _coordinator, modifiedPools, storagePortUpdates.getVarrayChanges());
        }

        // Update the virtual nas virtual arrays with network virtual arrays!!!
        if (DiscoveredDataObject.Type.vnxfile.name().equals(system.getSystemType())
        		|| DiscoveredDataObject.Type.isilon.name().equals(system.getSystemType())) {

            Network newNetwork = null;
            boolean removePort = false;
            if (networkUpdated) {
                if (!NullColumnValueGetter.isNullURI(newNetworkId)) {
                    _log.info("New network {} specified for vNAS storage port ", newNetworkId);
                    // Validate the new network exists and is active.
                    newNetwork = _dbClient.queryObject(Network.class, newNetworkId);
                } else if (!NullColumnValueGetter.isNullURI(oldNetworkId)) {
                    _log.info("Removing network {} from vNAS storage port ", oldNetworkId);
                    // Validate the new network exists and is active.
                    newNetwork = _dbClient.queryObject(Network.class, oldNetworkId);
                    removePort = true;
                }

                // Update the virtual nas virtual array assignments.
                _log.info("Checking for updates to virtual nas virtual array assignments.");
                boolean vNasVirtualArraysUpdated = updatevNasVirtualArrays(storagePort, newNetwork,
                        storagePortUpdates.getVarrayChanges(), removePort);
            }

        }

        // If there was a change, create the audit log entry and record the
        // event.
        if (networkUpdated || virtualArraysUpdated || portNetworkIdUpdated) {
            // Create the audit log entry.
            auditOp(OperationTypeEnum.UPDATE_STORAGE_PORT, true, null,
                    storagePort.getLabel(), id.toString());

            // Record the storage port update event.
            recordStoragePortEvent(OperationTypeEnum.STORAGE_PORT_UPDATE,
                    STORAGEPORT_UPDATED_DESCRIPTION, storagePort.getId());
        }

        return MapStoragePort.getInstance(_dbClient).toStoragePortRestRep(storagePort);
    }

    /**
     * Remove a storage port. The method would remove the deregistered storage port and all resources
     * associated with the storage port from the database.
     * Note they are not removed from the storage system physically,
     * but become unavailable for the user.
     * 
     * @param id the URN of a ViPR storage port to be removed.
     * 
     * @brief remove storage port from ViPR
     * @return Status indicating success or failure.
     */
    @POST
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/deactivate")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    public TaskResourceRep deleteStoragePort(@PathParam("id") URI id) {
        ArgValidator.checkFieldUriType(id, StoragePort.class, "id");
        StoragePort port = queryResource(id);

        if (!RegistrationStatus.UNREGISTERED.toString().equalsIgnoreCase(
                port.getRegistrationStatus()) ||
                DiscoveryStatus.VISIBLE.name().equalsIgnoreCase(
                        port.getDiscoveryStatus())) {
            throw APIException.badRequests.cannotDeactivateStoragePort();
        }

        String taskId = UUID.randomUUID().toString();
        Operation op = _dbClient.createTaskOpStatus(StoragePool.class, id,
                taskId, ResourceOperationTypeEnum.DELETE_STORAGE_PORT);

        PurgeRunnable.executePurging(_dbClient, _dbPurger,
                _asynchJobService.getExecutorService(), port,
                _retry_attempts, taskId, 60);
        return toTask(port, taskId, op);
    }

    /**
     * Updates the network for the passed storage port. If the passed new
     * network id is not null, then the storage port will be unassigned from its
     * current network, if any, and reassigned to the new network. Note however,
     * that a storage port that is currently a member of a discovered network
     * cannot be reassigned to a new network. Attempting to do so results in a
     * bad request exception. Attempts to reassign to the same network are
     * logged and ignored. If the passed new network id is not null, but
     * is instead "" or "null", and the storage port is currently assigned
     * to an undiscovered network, the port will be unassigned from the network.
     * 
     * @param storagePort A reference to the storage port.
     * @param newNetworkId The requested new network id.
     * 
     * @return true if the there was a network change made, false otherwise.
     */
    private boolean updateStoragePortNetwork(StoragePort storagePort, URI newNetworkId) {

        boolean networkUpdated = false;
        String portNetworkId = storagePort.getPortNetworkId();
        String portNativeId = storagePort.getNativeGuid();
        Network currentNetwork = null;
        Network newNetwork = null;

        // If the passed new network id is null, then just return. The user has not
        // specified a network change. Note that removal from it's current network
        // w/o reassigning to a new network is done by passing a new network id
        // of "" or "null".
        if (newNetworkId == null) {
            _log.info("New network id was not specified.");
            return false;
        }

        // If we're removing the port from an old Network, check it was not
        // discovered in it.
        URI currentNetworkId = storagePort.getNetwork();
        if (!NullColumnValueGetter.isNullURI(currentNetworkId)) {
            _log.info("Current storage port network is {}", currentNetworkId);
            currentNetwork = _dbClient.queryObject(Network.class, currentNetworkId);

            ArgValidator.checkEntity(currentNetwork, currentNetworkId, false);
            if (currentNetwork.endpointIsDiscovered(storagePort.getPortNetworkId())) {
                throw APIException.badRequests
                        .unableToUpdateDiscoveredNetworkForStoragePort();
            }
        }

        // If we're assigning a new Network, check it is a valid Network.
        if (!NullColumnValueGetter.isNullURI(newNetworkId)) {
            _log.info("New network {} specified for storage port ", newNetworkId, portNativeId);
            // Validate the new network exists and is active.
            ArgValidator.checkFieldUriType(newNetworkId, Network.class, "network");
            newNetwork = _dbClient.queryObject(Network.class, newNetworkId);
            ArgValidator.checkEntity(newNetwork, newNetworkId,
                    isIdEmbeddedInURL(newNetworkId));

            // If the Network is discovered, check that it already contains
            // the endpoint. This is just a warning.
            if (newNetwork.getDiscovered()) {
                if (false == newNetwork.retrieveEndpoints().contains(portNetworkId)) {
                    _log.info(String.format("Network does not contain "
                            + "endpoint for port %s wwpn %s", storagePort.getPortName(),
                            portNetworkId));
                }
            }
        }

        if ((currentNetwork == null && newNetwork == null)
                || (currentNetwork != null && newNetwork != null && currentNetworkId
                        .equals(newNetworkId))) {
            _log.info("The old and new Networks are the same, no change will be made.");
            return false;
        }

        if (newNetwork != null) {
            // If adding or changing network assignment.
            _log.info("Storage port {} will be assigned to network {}",
                    portNativeId, newNetwork.getLabel());
            updateNetworkEndpoint(newNetworkId, portNetworkId,
                    NetworkEndpointParam.EndpointOp.add);
            networkUpdated = true;
        } else if (currentNetwork != null) {
            // If removing from the current network assignment.
            _log.info("Storage port {} will be removed from network {}",
                    portNativeId, currentNetwork.getLabel());
            updateNetworkEndpoint(currentNetworkId, portNetworkId,
                    NetworkEndpointParam.EndpointOp.remove);
            networkUpdated = true;
        }

        return networkUpdated;
    }

    /**
     * Updates the virtual arrays to which the port is assigned.
     * 
     * @param storagePort A reference to the storage port.
     * @param varrayChanges The virtual array changes.
     * 
     * @return true if there was a virtual array assignment change, false otherwise.
     */
    private boolean updateStoragePortVirtualArrays(StoragePort storagePort,
            VirtualArrayAssignmentChanges varrayAssignmentChanges) {

        // Validate that the virtual arrays to be assigned to the storage port
        // reference existing virtual arrays in the database and add them to
        // the storage port.
        boolean varraysForPortUpdated = false;
        Set<String> varraysAddedToPort = new HashSet<String>();
        Set<String> varraysRemovedFromPort = new HashSet<String>();
        if (varrayAssignmentChanges != null) {
            _log.info("Update request has virtual array assignment changes for storage port {}",
                    storagePort.getId());
            // Verify the assignment changes in the request.
            verifyAssignmentChanges(storagePort, varrayAssignmentChanges);
            _log.info("Requested virtual array assignment changes verified.");

            VirtualArrayAssignments addAssignments = varrayAssignmentChanges.getAdd();
            if (addAssignments != null) {
                Set<String> addVArrays = addAssignments.getVarrays();
                if ((addVArrays != null) && (!addVArrays.isEmpty())) {
                    _log.info("Request specifies virtual arrays to be added.");
                    // Validate the requested URIs.
                    VirtualArrayService.checkVirtualArrayURIs(addVArrays, _dbClient);

                    // Iterate over the virtual arrays and assign them
                    // to the storage port.
                    StringSet currentAssignments = storagePort.getAssignedVirtualArrays();
                    Iterator<String> addVArraysIter = addVArrays.iterator();
                    while (addVArraysIter.hasNext()) {
                        String addVArrayId = addVArraysIter.next();
                        if ((currentAssignments != null) && (currentAssignments.contains(addVArrayId))) {
                            // Just ignore those already assigned
                            _log.info("Storage port already assigned to virtual array {}",
                                    addVArrayId);
                            continue;
                        }

                        varraysAddedToPort.add(addVArrayId);
                        varraysForPortUpdated = true;
                        _log.info("Storage port will be assigned to virtual array {}", addVArrayId);
                    }
                }
            }

            // Validate that the virtual arrays to be unassigned from the
            // storage port reference existing virtual arrays in the database
            // and remove them from the storage port.
            VirtualArrayAssignments removeAssignments = varrayAssignmentChanges.getRemove();
            if (removeAssignments != null) {
                Set<String> removeVArrays = removeAssignments.getVarrays();
                if ((removeVArrays != null) && (!removeVArrays.isEmpty())) {
                    _log.info("Request specifies virtual arrays to be removed.");
                    // Validate the requested URIs.
                    VirtualArrayService.checkVirtualArrayURIs(removeVArrays, _dbClient);

                    // Iterate over the virtual arrays and unassign from
                    // the storage port.
                    StringSet currentAssignments = storagePort.getAssignedVirtualArrays();
                    Iterator<String> removeVArraysIter = removeVArrays.iterator();
                    while (removeVArraysIter.hasNext()) {
                        String removeVArrayId = removeVArraysIter.next();
                        if ((currentAssignments == null) || (!currentAssignments.contains(removeVArrayId))) {
                            // Just ignore those not assigned.
                            _log.info("Storage port is not assigned to virtual array {}",
                                    removeVArrayId);
                            continue;
                        }

                        // storagePort.removeAssignedVirtualArray(removeVArrayId);
                        varraysRemovedFromPort.add(removeVArrayId);
                        varraysForPortUpdated = true;
                        _log.info("Storage port will be unassigned from virtual array {}",
                                removeVArrayId);
                    }
                }
            }
        }

        // Persist virtual array changes for storage port, if any.
        if (varraysForPortUpdated) {
            storagePort.addAssignedVirtualArrays(varraysAddedToPort);
            storagePort.removeAssignedVirtualArrays(varraysRemovedFromPort);
            // Check the new virtual array assignment does
            // not remove the port from varrays where it is used
            verifyPortNoInUseInRemovedVarrays(storagePort);
            _dbClient.updateAndReindexObject(storagePort);

            // Because the storage port virtual array assignments have
            // changed, we may have to update the implicit connected
            // virtual arrays for the storage port's network.
            URI storagePortNetworkURI = storagePort.getNetwork();
            if (storagePortNetworkURI != null) {
                // TODO - I need to find a way around using a full network retrieval
                Network storagePortNetwork = _dbClient.queryObject(Network.class, storagePortNetworkURI);
                if (storagePortNetwork != null) {
                    if (!varraysRemovedFromPort.isEmpty()) {
                        // if varrays were removed, it will be a full reset
                        // this will take care of both add and remove, but costly
                        NetworkAssociationHelper.updateConnectedVirtualArrays(storagePortNetwork,
                                Collections.singletonList(storagePort), false, _dbClient);
                    } else if (!varraysAddedToPort.isEmpty()) {
                        // if varrays were only added, do add only, cheaper
                        NetworkAssociationHelper.updateConnectedVirtualArrays(storagePortNetwork,
                                Collections.singletonList(storagePort), true, _dbClient);

                    }
                }
            }
        }

        return varraysForPortUpdated;
    }

    /**
     * It return VirtualNAS for given StoragePort
     * 
     * @param sp A reference to the storage port.
     * @return VirtualNAS for a storage Port
     */
    private VirtualNAS getVirtualNasForStoragePort(StoragePort sp) {

        URIQueryResultList vNasUriList = new URIQueryResultList();
        _dbClient.queryByConstraint(
                ContainmentConstraint.Factory.getVirtualNASContainStoragePortConstraint(sp.getId()), vNasUriList);

        Iterator<URI> vNasIter = vNasUriList.iterator();
        while (vNasIter.hasNext()) {
            VirtualNAS vNas = _dbClient.queryObject(VirtualNAS.class, vNasIter.next());
            if (vNas != null && !vNas.getInactive()) {
                return vNas;
            }
        }
        return null;

    }

    /**
     * Updates the virtual arrays to which the port of virtual nas is assigned.
     * 
     * @param storagePort A reference to the storage port.
     * @param varrayChanges The virtual array changes.
     * 
     * @return true if there was a virtual array assignment change, false otherwise.
     */
    private boolean updatevNasVirtualArrays(StoragePort storagePort, Network newNetwork,
            VirtualArrayAssignmentChanges varrayAssignmentChanges, boolean removePort) {

        // Validate that the virtual arrays to be assigned to the vnas
        // reference existing virtual arrays in the database and add them to
        // the vnas.
        Set<String> varraysAddedTovNas = new HashSet<String>();
        Set<String> varraysRemovedFromvNas = new HashSet<String>();
        boolean varraysForvNasUpdated = false;
        VirtualNAS vNas = getVirtualNasForStoragePort(storagePort);
        if (vNas == null) {
            _log.info("No Virtual NAS found for port {} ", storagePort.getLabel());
            return false;
        }

        // Update the vNas virtual arrays from network!!!
        if (newNetwork != null) {
            StringSet vArrays = newNetwork.getAssignedVirtualArrays();
            if (vArrays != null && !vArrays.isEmpty()) {
                if (!removePort) {
                    vNas.addAssignedVirtualArrays(vArrays);
                    varraysForvNasUpdated = true;
                } else { // Removing storage port from netwok!!!
                    StringSet vNasVarrys = new StringSet();
                    for (String sp : vNas.getStoragePorts()) {
                        if (!sp.equalsIgnoreCase(storagePort.getId().toString())) {
                            StoragePort vNasSp = _dbClient.queryObject(StoragePort.class, URI.create(sp));
                            vNasVarrys.addAll(vNasSp.getConnectedVirtualArrays());
                        }
                    }
                    // Remove storage varray from vnas virtual arrays,
                    // if other ports on vnas not belongs to same varray.
                    if (!vNasVarrys.contains(vArrays)) {
                        vNas.getAssignedVirtualArrays().removeAll(vArrays);
                        varraysForvNasUpdated = true;
                    }
                }
            }
        }

        if (varrayAssignmentChanges != null) {

            VirtualArrayAssignments addAssignments = varrayAssignmentChanges.getAdd();
            VirtualArrayAssignments removeAssignments = varrayAssignmentChanges.getRemove();
            StringSet currentAssignmentsForvNas = vNas.getAssignedVirtualArrays();

            if (addAssignments != null) {
                Set<String> addVArrays = addAssignments.getVarrays();
                if ((addVArrays != null) && (!addVArrays.isEmpty())) {
                    // Iterate over the virtual arrays and assign them
                    // to the virtual NAS.
                    Iterator<String> addVArraysIterForvNas = addVArrays.iterator();
                    while (addVArraysIterForvNas.hasNext()) {
                        String addVArrayId = addVArraysIterForvNas.next();
                        if ((currentAssignmentsForvNas != null) && (currentAssignmentsForvNas.contains(addVArrayId))) {
                            // Just ignore those already assigned
                            _log.info("Virtual Nas already assigned to virtual array {}",
                                    addVArrayId);
                            continue;
                        }

                        varraysAddedTovNas.add(addVArrayId);
                        _log.info("virtual nas will be assigned to virtual array {}", addVArrayId);
                    }

                    if (!varraysAddedTovNas.isEmpty()) {
                        vNas.addAssignedVirtualArrays(varraysAddedTovNas);
                        _log.info("virtual nas assigned with virtual arrays size {}", varraysAddedTovNas.size());
                        varraysForvNasUpdated = true;
                    }

                }

            }

            if (removeAssignments != null) {
                Set<String> removeVArrays = removeAssignments.getVarrays();
                if ((removeVArrays != null) && (!removeVArrays.isEmpty())) {

                    // Iterate over the virtual arrays and assign them
                    // to the virtual NAS.
                    Iterator<String> removeVArraysIterForvNas = removeVArrays.iterator();
                    while (removeVArraysIterForvNas.hasNext()) {
                        String removeVArrayId = removeVArraysIterForvNas.next();
                        if ((currentAssignmentsForvNas != null) && (!currentAssignmentsForvNas.contains(removeVArrayId))) {
                            // Just ignore those already assigned
                            _log.info("Virtual Nas not assigned to virtual array {}",
                                    removeVArrayId);
                            continue;
                        }

                        varraysRemovedFromvNas.add(removeVArrayId);
                        _log.info("virtual nas will be unassigned to virtual array {}", removeVArrayId);
                    }

                    if (!varraysRemovedFromvNas.isEmpty()) {
                        vNas.removeAssignedVirtualArrays(varraysRemovedFromvNas);
                        _log.info("virtual nas un-assigned with virtual arrays size {}", varraysRemovedFromvNas.size());
                        varraysForvNasUpdated = true;
                    }
                }
            }

        } else {
            _log.info("Ignored assignment of varray to virtual nas as the storage port not belongs to vnx file");
        }
        if (varraysForvNasUpdated) {
            _dbClient.persistObject(vNas);
        }
        return varraysForvNasUpdated;
    }

    /**
     * Checks that the storage port does not have any active exports (file or block) in any
     * of the varrays from which it is being removed.
     * 
     * @param storagePort
     */
    private void verifyPortNoInUseInRemovedVarrays(StoragePort storagePort) {
        _log.info("Checking port {} virtual array assignment can be changed", storagePort.getNativeGuid());
        if (EndpointUtility.isValidEndpoint(storagePort.getPortNetworkId(), EndpointType.SAN)) {
            _log.info("The port is of type FC or iscsi. Checking if in use by an export group.");
            List<ExportMask> masks = CustomQueryUtility.queryActiveResourcesByAltId(_dbClient, ExportMask.class,
                    "storagePorts", storagePort.getId().toString());
            if (masks != null && !masks.isEmpty()) {
                _log.info("The port is in use by {} masks. Checking the masks virtual arrays.", masks.size());
                for (ExportMask mask : masks) {
                    if (!mask.getInactive()) {
                        _log.info("checking ExportMask {}", mask.getMaskName());
                        List<ExportGroup> groups = CustomQueryUtility.queryActiveResourcesByRelation(_dbClient,
                                mask.getId(), ExportGroup.class, "exportMasks");
                        for (ExportGroup group : groups) {
                            // Determine the Varray of the ExportMask.
                            URI varray = group.getVirtualArray();
                            if (!ExportMaskUtils.exportMaskInVarray(_dbClient, mask, varray)) {
                                _log.info("not all target ports of {} are tagged for varray {}", mask, varray);
                                // See if in alternate virtual array
                                if (group.getAltVirtualArrays() != null) {
                                    String altVarray = group.getAltVirtualArrays().get(storagePort.getStorageDevice().toString());
                                    if (null != altVarray) {
                                        URI altVarrayUri = URI.create(altVarray);
                                        if (ExportMaskUtils.exportMaskInVarray(_dbClient, mask, altVarrayUri)) {
                                            _log.info("using the export group's alternate varray: {}", altVarrayUri);
                                            varray = altVarrayUri;
                                        }
                                    }
                                }
                            }
                            _log.info("the virtual array found for this port is {}", varray);

                            // only error if the ports ends up in a state where it can no longer be used in the varray
                            // if removing the varrays reverts the port to using implicit varrays which contains the
                            // export, then it is all good.
                            if (!group.getInactive() && !storagePort.getTaggedVirtualArrays().contains(varray.toString())) {
                                _log.info("The port is in use by export group {} in virtual array {} " +
                                        "which will no longer in the port's tagged varray",
                                        group.getLabel(), group.getVirtualArray().toString());
                                throw APIException.badRequests.cannotChangePortVarraysExportExists(
                                        storagePort.getNativeGuid(), group.getVirtualArray().toString(),
                                        group.getId().toString());
                            }
                        }
                    }
                }
            }
        } else {
            _log.info("The port is of type IP. Checking if in use by a file share.");
            List<FileShare> fileShares = CustomQueryUtility.queryActiveResourcesByRelation(
                    _dbClient, storagePort.getId(), FileShare.class, "storagePort");
            for (FileShare fileShare : fileShares) {
                // only error if the ports ends up in a state where it can no longer be used in the varray
                // if removing the varrays reverts the port to using implicit varrays which contains the
                // export, then it is all good.
                if (!fileShare.getInactive() && !storagePort.getTaggedVirtualArrays().contains(fileShare.getVirtualArray().toString())) {
                    _log.info("The port is in use by file share {} in virtual array {} " +
                            "which will no longer in the port's tagged varray",
                            fileShare.getLabel(), fileShare.getVirtualArray().toString());
                    throw APIException.badRequests.cannotChangePortVarraysExportExists(
                            storagePort.getNativeGuid(), fileShare.getVirtualArray().toString(),
                            fileShare.getId().toString());
                }
            }
        }
    }

    /**
     * Verifies the virtual array assignment changes in the update request are
     * valid, else throws a bad request exception.
     * 
     * @param storagePort A reference to a storage port.
     * @param varrayAssignmentChanges The virtual array assignment changes in a
     *            storage port update request.
     */
    private void verifyAssignmentChanges(StoragePort storagePort,
            VirtualArrayAssignmentChanges varrayAssignmentChanges) {
        // Verify the add/remove sets do not overlap.
        VirtualArrayAssignments addAssignments = varrayAssignmentChanges.getAdd();
        VirtualArrayAssignments removeAssignments = varrayAssignmentChanges.getRemove();
        if ((addAssignments != null) && (removeAssignments != null)) {
            Set<String> addVArrays = addAssignments.getVarrays();
            Set<String> removeVArrays = removeAssignments.getVarrays();
            if ((addVArrays != null) && (removeVArrays != null)) {
                Set<String> addSet = new HashSet<String>(addVArrays);
                Set<String> removeSet = new HashSet<String>(removeVArrays);
                addSet.retainAll(removeSet);
                if (!addSet.isEmpty()) {
                    _log.error("Request specifies the same virtual array(s) in both the add and remove lists {}", addSet);
                    throw APIException.badRequests.sameVirtualArrayInAddRemoveList();
                }
            }
        }

        // If there are add assignments and the port is a VPLEX port, make
        // sure the request does not try to add the VPLEX port to a virtual
        // array that contains ports from the other VPLEX cluster. The
        // ports for each cluster of VPLEX Metro must be in different
        // virtual arrays.
        // TODO: Any other add restrictions and/or remove restrictions.
        if (addAssignments != null) {
            Set<String> addVArrays = addAssignments.getVarrays();
            if ((addVArrays != null) && (!addVArrays.isEmpty()) &&
                    (ConnectivityUtil.isAVplexPort(storagePort, _dbClient))) {
                Iterator<String> addVArraysIterator = addVArrays.iterator();
                while (addVArraysIterator.hasNext()) {
                    String varrayId = addVArraysIterator.next();
                    if (!ConnectivityUtil.vplexPortCanBeAssignedToVirtualArray(storagePort, varrayId, _dbClient)) {
                        _log.error("VPLEX port {} cannot be assigned to virtual array {}",
                                storagePort.getId(), varrayId);
                        throw APIException.badRequests
                                .virtualArrayHasPortFromOtherVPLEXCluster(
                                        storagePort.getNativeGuid(), varrayId);
                    }
                }
            }
        }
    }

    /**
     * Record Bourne Event for the completed operations
     * 
     * @param type
     * @param type
     * @param description
     * @param storagePort
     */
    private void recordStoragePortEvent(OperationTypeEnum opType, String description,
            URI storagePort) {

        String evType;
        evType = opType.getEvType(true);

        RecordableBourneEvent event = new RecordableBourneEvent(
                /* String */evType,
                /* tenant id */null,
                /* user id ?? */URI.create("ViPR-User"),
                /* project ID */null,
                /* VirtualPool */null,
                /* service */EVENT_SERVICE_TYPE,
                /* resource id */storagePort,
                /* description */description,
                /* timestamp */System.currentTimeMillis(),
                /* extensions */"",
                /* native guid */null,
                /* record type */RecordType.Event.name(),
                /* Event Source */EVENT_SERVICE_SOURCE,
                /* Operational Status codes */"",
                /* Operational Status Descriptions */"");
        try {
            _evtMgr.recordEvents(event);
        } catch (Exception ex) {
            _log.error("Failed to record event. Event description: {}. Error: {}.",
                    description, ex);
        }
    }

    /**
     * Utility method to add/remove storage port endpoints to/from transport
     * zones. The {@link NetworkService#doUpdateEndpoints(URI, NetworkEndpointParam)} handles updating the transport zone as well as the
     * port and pool
     * associations. When a port is added:
     * <ul>
     * <li>the port's endpoint is added to the Network</li>
     * <li>The port Network is set to the new Network</li>
     * <li>The pool-to-varray associations are updated if needed.</li>
     * <li>If the ports exists in another Network, its endpoint is removed from the old Network and the pool-to-varray associations are also
     * updated</li>
     * </ul>
     * When a port is removed:
     * <ul>
     * <li>the port's endpoint is removed from the Network</li>
     * <li>The port Network is set null</li>
     * <li>The pool-to-varray associations are updated if needed.</li>
     * </ul>
     * 
     * @param transportZoneURI The URI of the Network.
     * @param endpointNetworkId The network id of the storage port.
     * @param op The operation, add/remove
     */
    private void updateNetworkEndpoint(URI transportZoneURI, String endpointNetworkId,
            NetworkEndpointParam.EndpointOp op) {
        // Set up the parameters to add/remove the storage port endpoint
        NetworkEndpointParam param = new NetworkEndpointParam();
        List<String> endpoints = new ArrayList<String>();
        endpoints.add(endpointNetworkId);
        param.setEndpoints(endpoints);
        param.setOp(op.name());
        // Add/Remove the storage port endpoint to/from the Network.
        Network network = networkSvc.doUpdateEndpoints(transportZoneURI, param);
        _dbClient.updateAndReindexObject(network);
    }

    /**
     * Update port network id.
     * This is applicable only for Cinder Storage System's port.
     * 
     * @param storagePort the storage port
     * @param portNetworkId the port network id
     * @return true, if successful
     */
    private boolean updatePortNetworkId(StoragePort storagePort,
            String portNetworkId) {
        boolean portNetworkIdUpdated = false;
        if (portNetworkId != null && !portNetworkId.isEmpty()) {
            /** check if the request is for OpenStack type */
            URI systemURI = storagePort.getStorageDevice();
            StorageSystem storageSystem = _dbClient.queryObject(
                    StorageSystem.class, systemURI);
            if (!Type.openstack.name().equals(storageSystem.getSystemType())) {
                throw APIException.badRequests.parameterIsOnlyApplicableTo(
                        "port_network_id update", "openstack storage system ports");
            }

            checkValidPortNetworkId(storagePort.getTransportType(), portNetworkId);

            /** check for duplicate port network id within the system */
            checkForDuplicatePortNetworkIdWithinSystem(_dbClient, portNetworkId,
                    storagePort.getStorageDevice());

            /** check that the port is not part of active masks */
            List<ExportMask> masks = CustomQueryUtility
                    .queryActiveResourcesByAltId(_dbClient, ExportMask.class,
                            "storagePorts", storagePort.getId().toString());
            if (!masks.isEmpty()) {
                throw APIException.badRequests
                        .parameterValueCannotBeUpdated("port_network_id",
                                "since it is part of active Exports");
            }

            storagePort.setPortNetworkId(portNetworkId);
            _dbClient.persistObject(storagePort);
            _log.info("updated Storage port's network id to {}.", portNetworkId);

            portNetworkIdUpdated = true;
        }
        return portNetworkIdUpdated;
    }

    /**
     * Check if the passed port network id value is a valid one for the given transport type.
     * 
     * @param transportType the transport type
     * @param portNetworkId the port network id
     */
    public static void checkValidPortNetworkId(String transportType,
            String portNetworkId) {
        if (TransportType.FC.name().equalsIgnoreCase(transportType)) {
            /** check if the value passed is valid WWN */
            if (!WWNUtility.isValidWWN(portNetworkId)) {
                throw APIException.badRequests
                        .parameterValueIsNotValid("port_network_id");
            }
        } else if (TransportType.IP.name().equalsIgnoreCase(transportType)) {
            /** check if the value passed is valid IQN or EUI */
            if (!iSCSIUtility.isValidIQNPortName(portNetworkId) &&
                    !iSCSIUtility.isValidEUIPortName(portNetworkId)) {
                throw APIException.badRequests
                        .parameterValueIsNotValid("port_network_id");
            }
        }
    }

    /**
     * Check if a storage port with the same portNetworkId exists for the passed storage system.
     * 
     * @param dbClient the db client
     * @param portNetworkId the port network id
     * @param systemURI the system uri
     */
    public static void checkForDuplicatePortNetworkIdWithinSystem(
            DbClient dbClient, String portNetworkId, URI systemURI) {

        URIQueryResultList portUriList = new URIQueryResultList();
        dbClient.queryByConstraint(
                AlternateIdConstraint.Factory.getStoragePortEndpointConstraint(portNetworkId), portUriList);
        Iterator<URI> storagePortIter = portUriList.iterator();
        while (storagePortIter.hasNext()) {
            StoragePort port = dbClient.queryObject(StoragePort.class, storagePortIter.next());
            if (port != null && !port.getInactive() && port.getStorageDevice().toString().equals(systemURI.toString())) {
                throw APIException.badRequests.duplicateEntityWithField("StoragePort", "portNetworkId");
            }
        }
    }

    /**
     * Retrieve resource representations based on input ids.
     * 
     * @param param POST data containing the id list.
     * @brief List data of storage port resources
     * @return list of representations.
     */
    @POST
    @Path("/bulk")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Override
    public StoragePortBulkRep getBulkResources(BulkIdParam param) {
        return (StoragePortBulkRep) super.getBulkResources(param);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Class<StoragePort> getResourceClass() {
        return StoragePort.class;
    }

    @Override
    public StoragePortBulkRep queryBulkResourceReps(List<URI> ids) {
        Iterator<StoragePort> _dbIterator = _dbClient.queryIterativeObjects(
                getResourceClass(), ids);
        return new StoragePortBulkRep(BulkList.wrapping(_dbIterator,
                MapStoragePort.getInstance(_dbClient)));
    }

    @Override
    public StoragePortBulkRep queryFilteredBulkResourceReps(List<URI> ids) {
        verifySystemAdmin();
        return queryBulkResourceReps(ids);
    }

    @Override
    protected ResourceTypeEnum getResourceType() {
        return ResourceTypeEnum.STORAGE_PORT;
    }

}
