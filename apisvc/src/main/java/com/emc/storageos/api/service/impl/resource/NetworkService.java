/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.api.service.impl.resource;

import static com.emc.storageos.api.mapper.DbObjectMapper.toNamedRelatedResource;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

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

import com.emc.storageos.api.mapper.functions.MapNetwork;
import com.emc.storageos.api.service.impl.response.BulkList;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.DiscoveredDataObject.RegistrationStatus;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.IpInterface;
import com.emc.storageos.db.client.model.Network;
import com.emc.storageos.db.client.model.NetworkSystem;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StorageProtocol;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.db.client.util.EndpointUtility;
import com.emc.storageos.db.client.util.StringSetUtil;
import com.emc.storageos.db.client.util.iSCSIUtility;
import com.emc.storageos.model.BulkIdParam;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.host.InitiatorList;
import com.emc.storageos.model.host.IpInterfaceList;
import com.emc.storageos.model.network.NetworkBulkRep;
import com.emc.storageos.model.pools.VirtualArrayAssignmentChanges;
import com.emc.storageos.model.ports.StoragePortList;
import com.emc.storageos.model.valid.Endpoint.EndpointType;
import com.emc.storageos.model.varray.NetworkCreate;
import com.emc.storageos.model.varray.NetworkEndpointParam;
import com.emc.storageos.model.varray.NetworkList;
import com.emc.storageos.model.varray.NetworkRestRep;
import com.emc.storageos.model.varray.NetworkUpdate;
import com.emc.storageos.networkcontroller.impl.NetworkAssociationHelper;
import com.emc.storageos.security.authorization.CheckPermission;
import com.emc.storageos.security.authorization.DefaultPermissions;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.util.NetworkUtil;
import com.emc.storageos.volumecontroller.impl.monitoring.RecordableBourneEvent;
import com.emc.storageos.volumecontroller.impl.monitoring.RecordableEventManager;
import com.emc.storageos.volumecontroller.impl.monitoring.cim.enums.RecordType;

/**
 * Network service handles requests to create, update and remove a network.
 * 
 */
@Path("/vdc/networks")
@DefaultPermissions(readRoles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR },
        writeRoles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
public class NetworkService extends TaggedResource {

    private static Logger _log = LoggerFactory.getLogger(NetworkService.class);
    private static final String EVENT_SERVICE_TYPE = "networks";
    private static final String EVENT_SERVICE_SOURCE = "NetworksService";

    @Override
    public String getServiceType() {
        return EVENT_SERVICE_TYPE;
    }

    private RecordableEventManager eventManager;

    public void setEventManager(RecordableEventManager eventManager) {
        this.eventManager = eventManager;
    }

    /**
     * Get info for network
     * 
     * @param id the URN of a ViPR Network
     * @brief Show network
     * @return Network details
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR })
    public NetworkRestRep getNetwork(@PathParam("id") URI id) {
        ArgValidator.checkFieldUriType(id, Network.class, "id");
        return MapNetwork.toNetworkRestRep(queryResource(id), _dbClient);
    }

    /**
     * This call returns a list of all the networks, regardless of whether or not
     * they are associated with a virtual array.
     * <p>
     * If network systems are discovered, fiber channel networks that are discovered are not initially associated with virtual array. The
     * discovered networks must be updated to associate then with virtual arrays.
     * 
     * @brief List Networks
     * @return a list of all networks
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR })
    public NetworkList getAllNetworks() {
        NetworkList tzlist = new NetworkList();
        List<URI> networks = _dbClient.queryByType(Network.class, true);
        List<Network> transportZones = _dbClient.queryObject(Network.class, networks);
        for (Network network : transportZones) {
            if (network == null || network.getInactive() == true) {
                continue;
            }
            tzlist.getNetworks().add(toNamedRelatedResource(ResourceTypeEnum.NETWORK,
                    network.getId(), network.getLabel()));
        }
        return tzlist;
    }

    @Override
    protected Network queryResource(URI id) {
        ArgValidator.checkFieldUriType(id, Network.class, "id");
        Network tzone = _dbClient.queryObject(Network.class, id);
        ArgValidator.checkEntityNotNull(tzone, id, isIdEmbeddedInURL(id));
        return tzone;
    }

    @Override
    protected URI getTenantOwner(URI id) {
        return null;
    }

    /**
     * Deactivate network, this will delete a manually created network. The
     * network must be deregistered and has no endpoints. When force is set to true
     * the network can be deleted even if it has endpoints.
     * 
     * @param id the URN of a ViPR Network
     * @param force if set to true will delete a network even if it has endpoints
     * @brief Delete Network
     * @return No data returned in response body
     */
    @POST
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/deactivate")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    public Response deleteNetwork(@PathParam("id") URI id,
            @QueryParam("force") boolean force) {
        ArgValidator.checkFieldUriType(id, Network.class, "id");
        Network network = _dbClient.queryObject(Network.class, id);
        ArgValidator.checkEntityNotNull(network, id, isIdEmbeddedInURL(id));
        if (!RegistrationStatus.UNREGISTERED.toString().equals(network.getRegistrationStatus())) {
            throw APIException.badRequests.invalidParameterCannotDeactivateRegisteredNetwork(network.getId());
        }
        if (network.getDiscovered()) {
            throw APIException.badRequests.invalidParameterCannotDeleteDiscoveredNetwork(network.getId());
        }

        if (network.getDiscovered() != true && force) {
            // dis-associated the storage port from the network before marking it for deletion
            NetworkAssociationHelper.handleEndpointsRemoved(network, network.retrieveEndpoints(), _dbClient, _coordinator);
            _dbClient.markForDeletion(network);
        } else if (network.getDiscovered() != true && network.retrieveEndpoints() != null
                && !network.retrieveEndpoints().isEmpty()) {
            throw APIException.badRequests.unableToDeleteNetworkContainsEndpoints();
        } else {
            NetworkAssociationHelper.handleEndpointsRemoved(network, network.retrieveEndpoints(), _dbClient, _coordinator);
            _dbClient.markForDeletion(network);
        }

        recordAndAudit(network, OperationTypeEnum.DELETE_NETWORK);
        return Response.ok().build();
    }

    /**
     * Add or remove end-point(s) to network.
     * <p>
     * For fiber channel, some Networks may be automatically created by discovering Network Systems. These Networks will have endpoints that
     * were discovered by a Network System, including endpoints that represent host initiator port WWNs as well as end points that represent
     * storage array port WWNs.
     * <p>
     * Discovered endpoints may not be deleted by the user. They will be updated periodically as the Network System refreshes its
     * information on the topology of the VSANs or Fabrics.
     * <p>
     * The user may still manually add endpoints to a discovered Network. The user is able to delete endpoints that were manually added. If
     * a manually entered endpoint is subsequently discovered by a a Network System, it becomes managed as if it were discovered originally,
     * and then may no longer be deleted.
     * <p>
     * This API is maintained for backward compatibility. Since the method is deprecated use /vdc/networks/{id} instead.
     * 
     * @see #updateNetwork(URI, NetworkUpdate)
     * @param id the URN of a ViPR Network
     * @param param Network endpoint parameters
     * @deprecated use {@link #updateNetwork(URI, NetworkUpdate)}
     * @brief Add or remove network end points
     * @return Network details
     */
    @PUT
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/endpoints")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    @Deprecated
    public NetworkRestRep updateNetworkEndpoints(@PathParam("id") URI id,
            NetworkEndpointParam param) {
        Network network = doUpdateEndpoints(id, param);
        recordAndAudit(network, OperationTypeEnum.UPDATE_NETWORK);
        return MapNetwork.toNetworkRestRep(network, _dbClient);
    }

    /**
     * Adds/removes endpoints to/from networks and handles all the related
     * updates needed for:
     * <ul>
     * <li>The storage ports corresponding to the endpoints if they exist</li>
     * <li>The networks, the one the endpoints are added into, and the old one when an endpoint is moving from one network to another</li>
     * <li>The storage pools when this operation results in the pools being implicitly associated with the new network's varray and
     * disassociated from the old ones.</li>
     * </ul>
     * When a port is added:
     * <ul>
     * <li>the port's endpoint is added to the network</li>
     * <li>The port's network is set to the new network</li>
     * <li>The pool-to-varray associations are updated if needed.</li>
     * <li>If the ports exists in another network, its endpoint is removed from the old network and the pool-to-varray associations are also
     * updated</li>
     * </ul>
     * When a port is removed:
     * <ul>
     * <li>the port's endpoint is removed from the network</li>
     * <li>The port network is set null</li>
     * <li>The pool-to-varray associations are updated if needed.</li>
     * </ul>
     * 
     * @param id the URN of a ViPR network
     * @param param the request object containing the endpoint to be added or removed
     *            as well as the operation type (add/remove).
     * @return the target networke
     */
    public Network doUpdateEndpoints(URI id, NetworkEndpointParam param) {
        // This I am not sure about -
        _log.info("doUpdateEndpoints START...");
        ArgValidator.checkUri(id);
        Network network = _dbClient.queryObject(Network.class, id);
        ArgValidator.checkEntity(network, id, isIdEmbeddedInURL(id));
        NetworkEndpointParam.EndpointOp op = NetworkEndpointParam.EndpointOp.valueOf(param.getOp());
        List<String> updatedEndoints = null;
        // create an update parameter
        if (op.equals(NetworkEndpointParam.EndpointOp.add)) {
            _log.info("doUpdateEndpoints: adding endpoints {} to network {}", param.getEndpoints(), network);
            updatedEndoints = checkAndFilterAddEndpoints(network, param.getEndpoints());
            network.addEndpoints(updatedEndoints, false);
        } else {
            _log.info("doUpdateEndpoints: removing endpoints {} from network {}", param.getEndpoints(), network);
            updatedEndoints = checkAndFilterRemoveEndPoints(network, param.getEndpoints());
            network.removeEndpoints(updatedEndoints);
        }
        _dbClient.updateAndReindexObject(network);
        _log.info("doUpdateEndpoints: update the port and pools associations following {} endpoints operation", op.name());
        updateEndpointsAssociation(network, updatedEndoints, op);
        return network;
    }

    /**
     * check to see if any endpoint is used in an active export.
     * 
     * @param endpoints a list of endpoints to check
     */
    private void checkEndPointsForExports(Collection<String> endpoints) {
        // check endpoints being added are NOT part of active exports
        for (String endpoint : endpoints) {
            if (StorageProtocol.isFCEndpoint(endpoint)
                    || iSCSIUtility.isValidIQNPortName(endpoint)
                    || iSCSIUtility.isValidEUIPortName(endpoint)) {
                _log.info("checkEndPointsForExports: checking endpoint {} is not in block export", endpoint);
                NetworkUtil.checkNotUsedByActiveExportGroup(endpoint, _dbClient);
            } else {
                NetworkUtil.checkNotUsedByActiveFileExport(endpoint, _dbClient);
                _log.info("checkEndPointsForExports: checking endpoint {} is not in file export", endpoint);
            }
        }
    }

    /**
     * Remove the endpoints from their current networks and update the
     * the port-to-network and pool-to-varray associations as needed.
     * 
     * @param networkMap a map containing the current network for each
     * @param network the network to which the endpoints are moving
     */
    private void handleRemoveFromOldNetworks(Map<String, Network> networkMap,
            Network network) {
        NetworkAssociationHelper.handleRemoveFromOldNetworks(networkMap, network, _dbClient, _coordinator);
    }

    /**
     * Check endpoints being added were not discovered to be in another network
     * 
     * @param networkMap a map containing the old network for each end point
     * @param network the network to which the endpoint are moving *
     */
    private void checkNotAddingDiscoveredEndpoints(Map<String, Network> networkMap, Network network) {
        List<String> discoveredEndpoints = new ArrayList<String>();
        _log.info("checkNotAddingDiscoveredEndpoints: checking the endpoints are not discovered in another network");
        for (String ep : networkMap.keySet()) {
            if (networkMap.get(ep).endpointIsDiscovered(ep) &&
                    !network.getId().equals(networkMap.get(ep).getId())) {
                discoveredEndpoints.add(ep);
            }
        }
        _log.info("checkNotAddingDiscoveredEndpoints: these endpoints were discovered in another network {}",
                discoveredEndpoints.toArray());
        if (!discoveredEndpoints.isEmpty()) {
            throw APIException.badRequests.endpointsCannotBeAdded(discoveredEndpoints.toArray().toString());
        }
    }

    /**
     * Checks the endpoints being removed were not discovered to be in the network
     * 
     * @param network Network the network from where the endpoints will be removed
     * @param endpoints List of String of endpoints being removed
     */
    private void checkNotRemovingDiscoveredEndpoints(Network network, List<String> endpoints) {
        List<String> discoveredEndpoints = new ArrayList<String>();
        _log.info("checkNotRemovingDiscoveredEndpoints: for {} ", endpoints);
        for (String ep : endpoints) {
            if (network.endpointIsDiscovered(ep)) {
                discoveredEndpoints.add(ep);
            }
        }
        _log.info("checkNotRemovingDiscoveredEndpoints: these endpoints were discovered in the network  {} ", discoveredEndpoints);
        if (!discoveredEndpoints.isEmpty()) {
            throw APIException.badRequests.endpointsCannotBeRemoved(discoveredEndpoints.toArray().toString());
        }
    }

    /**
     * this updates the StoragePort to Network relationship if the endpoints represent ports,
     * and then will affect StoragePool to varray associations.
     * 
     * @param network the network where endpoints are added/removed
     * @param endpoints a collection of added/removed endpoints
     * @param op the type of change: added/removed
     */
    private void updateEndpointsAssociation(Network network, Collection<String> endpoints,
            NetworkEndpointParam.EndpointOp op) {
        _log.info("updateEndpointsAssociation: update the port and pools associations following {} endpoints operation", op.name());
        if (op.equals(NetworkEndpointParam.EndpointOp.add)) {
            NetworkAssociationHelper.handleNetworkUpdated(network, null, null, endpoints, null, _dbClient, _coordinator);
        } else {
            NetworkAssociationHelper.handleNetworkUpdated(network, null, null, null, endpoints, _dbClient, _coordinator);
        }
    }

    /**
     * Create a network of type FC, IP or Ethernet. The network can optionally
     * be added to varrays and populated with endpoints.
     * <p>
     * When the network has endpoints and the endpoints are matched to storage ports, the storage ports become assigned to the network. When
     * the network is also added to virtual arrays, the storage ports' array pools are update to show they are connected to the networks'
     * varrays.
     * 
     * @param param object containing the request parameters
     * @brief Create Network
     * @return the details of the created network
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    public NetworkRestRep createNetwork(NetworkCreate param) {
        _log.info("createNetwork: started for param: name {} and type {}",
                param.getLabel(), param.getTransportType());

        // check for active network with same name
        ArgValidator.checkFieldNotEmpty(param.getLabel(), "label");
        checkDuplicateLabel(Network.class, param.getLabel());

        // check the type is supported
        StorageProtocol.Transport type = StorageProtocol.Transport.valueOf(param.getTransportType());

        // check VirtualArrays
        if (param.getVarrays() != null) {
            for (URI uri : param.getVarrays()) {
                queryObject(VirtualArray.class, uri, true);
            }
        }

        Network network = new Network();
        network.setId(URIUtil.createId(Network.class));
        network.setLabel(param.getLabel());
        network.setTransportType(type.name());
        network.setAssignedVirtualArrays(StringSetUtil.uriListToStringSet(param.getVarrays()));
        network.addEndpoints(checkAndFilterAddEndpoints(network, param.getEndpoints()), false);

        _dbClient.createObject(network);
        recordAndAudit(network, OperationTypeEnum.CREATE_NETWORK);

        _log.info("createNetwork: updating ports and pools associations ");
        NetworkAssociationHelper.handleNetworkUpdated(network, StringSetUtil.stringSetToUriList(network.getAssignedVirtualArrays()),
                null, network.getEndpointsMap().keySet(), null, _dbClient, _coordinator);

        return MapNetwork.toNetworkRestRep(network, _dbClient);
    }

    /**
     * Update a network's name, endpoints or varrays.
     * <p>
     * When endpoints are changed, added or removed, and the endpoints match some storage ports, the storage ports associations to the
     * network are updated accordingly. If the endpoints added exist is another network, they are first removed from their current network.
     * Discovered endpoints cannot be removed from their current networks or added to another one.
     * <p>
     * When the storage ports networks are changed, their corresponding storage pools are also update to reflect any change in varray
     * connectivity that may have resulted from the change.
     * <p>
     * For backward compatibility, this function still allows the varray changes to be done using {@link NetworkUpdate#getVarrays()}. The
     * value specified in the parameter will override the existing varrays to maintain the same behavior. Further, only zero or one varray
     * may be specified using this input field.
     * 
     * @param id the URN of a ViPR network
     * @param param the update request object
     * @brief Update network
     * @return the details of the updated network
     */
    @PUT
    @Path("/{id}")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    public NetworkRestRep updateNetwork(@PathParam("id") URI id,
            NetworkUpdate param) {
        _log.info("updateNetwork: started for network {}", id);
        ArgValidator.checkFieldUriType(id, Network.class, "id");
        Network network = queryResource(id);
        ArgValidator.checkEntity(network, id, isIdEmbeddedInURL(id));

        if (param.getName() != null && !network.getLabel().equalsIgnoreCase(
                param.getName())) {
            // check for active network with same name
            checkDuplicateLabel(Network.class, param.getName());
            _log.info("updateNetwork: changing network {} to {} ", network.getLabel(),
                    param.getName());
            network.setLabel(param.getName());
        }

        if (param.getVarrays() != null && !param.getVarrays().isEmpty() && param.getVarrays().size() != 1) {
            throw APIException.badRequests.networkCanOnlyBeAssociatedWithASingleVirtualArray(network.getId());
        }

        // define variable to hold changes
        List<URI> removedVarrays = null;
        List<URI> addedVarrays = null;
        List<String> removedEps = null;
        List<String> addedEps = null;

        // Update the varray association.
        if (param.getVarrayChanges() != null) {
            VirtualArrayAssignmentChanges varrayChanges = param.getVarrayChanges();
            if (varrayChanges.hasRemoved()) {
                removedVarrays = checkAndFilterRemoveVarrays(network,
                        varrayChanges.getRemove().getVarrays(),
                        varrayChanges.hasAdded() ? varrayChanges.getAdd().getVarrays() : null);
                _log.info("updateNetwork: these varrays will be removed {} ", removedVarrays);
            }
            if (param.getVarrayChanges().hasAdded()) {
                addedVarrays = checkAndFilterAddVarrays(network, varrayChanges.getAdd().getVarrays());
                _log.info("updateNetwork: these varrays will be added {} ", addedVarrays);
            }
        } else if (param.getVarrays() != null) {
            // the user is using the old style - still allow full overwrite of the varrays
            _log.info("updateNetwork: using the old style update for varrays param {}", param.getVarrays());
            if (param.getVarrays().isEmpty()) {
                if (network.getAssignedVirtualArrays() != null) {
                    removedVarrays = checkAndFilterRemoveVarrays(network,
                            network.getAssignedVirtualArrays(), null);
                    _log.info("updateNetwork: these varrays will be removed {} ", removedVarrays);
                }
            } else {
                addedVarrays = checkAndFilterAddVarrays(network, StringSetUtil.uriListToSet(param.getVarrays()));
                _log.info("updateNetwork: these varrays will be added {} ", addedVarrays);
            }
        }
        // Update the endpoints.
        if (param.getEndpointChanges() != null) {
            if (param.getEndpointChanges().hasRemoved()) {
                removedEps = checkAndFilterRemoveEndPoints(network, param.getEndpointChanges().getRemove());
                _log.info("updateNetwork: these endpoints will be removed {} ", removedEps);
            }
            if (param.getEndpointChanges().hasAdded()) {
                addedEps = checkAndFilterAddEndpoints(network, param.getEndpointChanges().getAdd());
                _log.info("updateNetwork: these endpoints will be added {} ", addedEps);
            }
        }

        if (removedVarrays != null) {
            network.removeAssignedVirtualArrays(StringSetUtil.uriListToSet(removedVarrays));
        }
        if (addedVarrays != null) {
            network.addAssignedVirtualArrays(StringSetUtil.uriListToSet(addedVarrays));
        }
        if (removedEps != null) {
            network.removeEndpoints(removedEps);
        }
        if (addedEps != null) {
            network.addEndpoints(addedEps, false);
        }
        _dbClient.updateAndReindexObject(network);
        recordAndAudit(network, OperationTypeEnum.UPDATE_NETWORK);
        _log.info("updateNetwork: updating ports and pools associations ");
        NetworkAssociationHelper.handleNetworkUpdated(network, addedVarrays, removedVarrays, addedEps, removedEps, _dbClient, _coordinator);
        return MapNetwork.toNetworkRestRep(network, _dbClient);
    }

    /**
     * Validates the varrays and filter out any varrays to which the network is already assigned.
     * 
     * @param network the network to update
     * @param varrays the varrays to add
     * @return a list of filtered varrays
     */
    private List<URI> checkAndFilterAddVarrays(Network network, Collection<String> varrays) {
        List<URI> addedVarray = new ArrayList<URI>();
        URI uri = null;
        for (String strUri : varrays) {
            if (network.getAssignedVirtualArrays() == null ||
                    !network.getAssignedVirtualArrays().contains(strUri.toString())) {
                uri = URI.create(strUri);
                queryObject(VirtualArray.class, uri, true);
                addedVarray.add(uri);
            }
        }
        return addedVarray;
    }

    /**
     * Validates the varrays and filter out any varrays to which the network is NOT already assigned.
     * The validation ensures that the network is getting unassigned from varrays if one or more
     * of the network's endpoints have exports in the varrays.
     * 
     * @param network the network to update
     * @param varrays the varrays to remove
     * @return a list of filtered varrays
     */
    private List<URI> checkAndFilterRemoveVarrays(Network network, Collection<String> remVarrays, Collection<String> addVarrays) {
        List<URI> removedVarray = new ArrayList<URI>();
        URI uri = null;
        for (String strUri : remVarrays) {
            if (network.getAssignedVirtualArrays() != null && // check it is already in the network
                    network.getAssignedVirtualArrays().contains(strUri.toString())
                    && (addVarrays == null ||  // also check it is not in the add list
                    !addVarrays.contains(strUri))) {
                uri = URI.create(strUri);
                removedVarray.add(uri);
            }
        }
        checkNetworkExportAssociations(network, removedVarray);
        return removedVarray;
    }

    /**
     * Validates the endpoints can be added to the network and filter out ones that are
     * already in the network. The endpoints can be added to the network when:
     * <ol>
     * <li>The endpoint type is compatible with the network type. In other words WWN for FC networks.</li>
     * <li>The endpoint is valid WWN or IQN.</li>
     * <li>The endpoint is not discovered in another network</li>
     * <li>The endpoint does not have any active file or block exports in their current networks</li>
     * </ol>
     * 
     * @param network the network to be updated
     * @param endpoints the endpoints to be added.
     * @return a list of filtered endpoints
     */
    private List<String> checkAndFilterAddEndpoints(Network network, List<String> endpoints) {
        List<String> addedEp = new ArrayList<String>();
        if (endpoints != null) {
            for (String endpoint : endpoints) {
                if (network.getEndpointsMap() == null ||
                        !network.getEndpointsMap().containsKey(endpoint)) {
                    if (network.getTransportType().equals(StorageProtocol.Transport.FC.name()) &&
                            !EndpointUtility.isValidEndpoint(endpoint, EndpointType.WWN)) {
                        throw APIException.badRequests.invalidEndpointExpectedFC(endpoint);
                    }
                    if (!network.getTransportType().equals(StorageProtocol.Transport.FC.name()) &&
                            EndpointUtility.isValidEndpoint(endpoint, EndpointType.WWN)) {
                        throw APIException.badRequests.invalidEndpointExpectedNonFC(endpoint);
                    }
                    addedEp.add(endpoint);
                }
            }
            // get the endpoints current networks as some may exist in other networks
            Map<String, Network> networkMap =
                    NetworkAssociationHelper.getNetworksMap(addedEp, _dbClient);
            // check that all the endpoints are not discovered in their current networks
            checkNotAddingDiscoveredEndpoints(networkMap, network);
            // check endpoints being added that were in other networks NOT part of active exports
            checkEndPointsForExports(networkMap.keySet());
            // remove from old networks
            handleRemoveFromOldNetworks(networkMap, network);
        }
        return addedEp;
    }

    /**
     * Validates the endpoints can be removed from the network and filter out ones that are
     * not already in the network. The endpoints can be removed from the network when:
     * <ol>
     * <li>The endpoint is not discovered in the network</li>
     * <li>The endpoint does not have any active file or block exports in the networks</li>
     * </ol>
     * 
     * @param network the network to be updated
     * @param endpoints the endpoints to be removed.
     * @return a list of filtered endpoints
     */
    private List<String> checkAndFilterRemoveEndPoints(Network network, List<String> remEps) {
        List<String> removedEp = new ArrayList<String>();
        for (String str : remEps) {
            if (network.getEndpointsMap() != null &&
                    network.getEndpointsMap().containsKey(EndpointUtility.changeCase(str))) {
                removedEp.add(str);
            }
        }
        // make sure the end points are not discovered in the current network
        checkNotRemovingDiscoveredEndpoints(network, removedEp);

        // check endpoints being removed are NOT part of active exports
        checkEndPointsForExports(removedEp);

        return removedEp;
    }

    /**
     * Allows the user to deregister a registered network so that it is no
     * longer used by the system. This simply sets the registration_status of
     * the network to UNREGISTERED.
     * 
     * @param id the URN of a ViPR network.
     * 
     * @brief Unregister network
     * @return Status response indicating success or failure
     */
    @POST
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Path("/{id}/deregister")
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    public NetworkRestRep deregisterNetwork(@PathParam("id") URI id) {

        Network network = queryResource(id);
        ArgValidator.checkEntity(network, id, isIdEmbeddedInURL(id));
        if (RegistrationStatus.REGISTERED.toString().equalsIgnoreCase(
                network.getRegistrationStatus())) {
            network.setRegistrationStatus(RegistrationStatus.UNREGISTERED.toString());
            _dbClient.persistObject(network);
            auditOp(OperationTypeEnum.DEREGISTER_NETWORK, true, null,
                    network.getLabel(), network.getId().toString());
        }

        return MapNetwork.toNetworkRestRep(network, _dbClient);
    }

    /**
     * Manually register the network with the passed id.
     * 
     * @param id the URN of a ViPR network.
     * 
     * @brief Register network
     * @return A reference to a StoragePoolRestRep specifying the data for the
     *         registered storage pool.
     */
    @POST
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.RESTRICTED_SYSTEM_ADMIN })
    @Path("/{id}/register")
    public NetworkRestRep registerNetwork(@PathParam("id") URI id) {
        ArgValidator.checkFieldUriType(id, Network.class, "id");
        Network network = _dbClient.queryObject(Network.class, id);
        ArgValidator.checkEntity(network, id, isIdEmbeddedInURL(id));

        if (RegistrationStatus.UNREGISTERED.toString().equalsIgnoreCase(
                network.getRegistrationStatus())) {
            if (network.getDiscovered()) {
                List<URI> registeredNetworkSystems = getRegisteredNetworkSystems(network, _dbClient);
                if (registeredNetworkSystems.isEmpty()) {
                    throw APIException.badRequests.invalidParameterCannotRegisterUnmanagedNetwork(network.getId());
                }
            }
            network.setRegistrationStatus(RegistrationStatus.REGISTERED.toString());
            _dbClient.persistObject(network);
            auditOp(OperationTypeEnum.REGISTER_NETWORK, true, null, network.getId().toString());
        }
        return MapNetwork.toNetworkRestRep(network, _dbClient);
    }

    /**
     * Returns the list of registered NetworkSystems that manage the given Network
     * 
     * @param network the Network
     * @param dbClient DbClient
     * @return list of registered NetworkSystems that manage the given Network
     */
    public static List<URI> getRegisteredNetworkSystems(Network network, DbClient dbClient) {
        List<URI> networkSystems = new ArrayList<URI>();
        if (network != null && network.getInactive() != true && network.getNetworkSystems() != null) {
            for (String networkSystemUri : network.getNetworkSystems()) {
                NetworkSystem networkSystem =
                        dbClient.queryObject(NetworkSystem.class, URI.create(networkSystemUri));
                if (networkSystem != null
                        && networkSystem.getInactive() != true
                        && RegistrationStatus.REGISTERED.toString().equalsIgnoreCase(
                                networkSystem.getRegistrationStatus())) {
                    networkSystems.add(networkSystem.getId());
                }
            }
        }
        return networkSystems;
    }

    /**
     * Retrieve resource representations based on input ids.
     * 
     * @param param POST data containing the id list.
     * @brief List data of network resources
     * @return list of representations.
     */
    @POST
    @Path("/bulk")
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Override
    public NetworkBulkRep getBulkResources(BulkIdParam param) {
        return (NetworkBulkRep) super.getBulkResources(param);
    }

    /**
     * A network varray association may have resulted in some ports being
     * implicitly associated with the varray. When the network-varray association
     * is being removed, we need to check that the implicitly associated ports
     * are not in any exports (block or file).
     * 
     * @param network Network to check
     * @param varrayUris the list of varrays to be removed for the network
     */
    private void checkNetworkExportAssociations(Network network, List<URI> varrayUris) {
        // do some paramters validation
        if (varrayUris == null || varrayUris.isEmpty() || network == null ||
                network.getConnectedVirtualArrays() == null || network.getConnectedVirtualArrays().isEmpty()) {
            return;
        }
        _log.info("Checking if varrays {} can be removed for network {}", varrayUris, network.getId());
        // get all the storage ports in the network
        List<StoragePort> storagePorts = CustomQueryUtility.queryActiveResourcesByConstraint(
                _dbClient, StoragePort.class,
                AlternateIdConstraint.Factory.getConstraint(
                        StoragePort.class, "network", network.getId().toString()));

        // For each that is using implicit (connected) varray associations
        // check if it is in an export
        for (StoragePort storagePort : storagePorts) {
            // if the port is using implicit assignment
            if (storagePort != null &&
                    (storagePort.getAssignedVirtualArrays() == null || storagePort.getAssignedVirtualArrays().isEmpty())) {
                _log.info("Port {} is using implicit varray assignment. Checking the port exports.", storagePort.getNativeGuid());
                if (EndpointUtility.isValidEndpoint(storagePort.getPortNetworkId(), EndpointType.SAN)) {
                    _log.info("The port is of type FC or iscsi. Checking if in use by an export group.");
                    List<ExportMask> masks = CustomQueryUtility.queryActiveResourcesByAltId(_dbClient, ExportMask.class,
                            "storagePorts", storagePort.getId().toString());
                    if (masks != null && !masks.isEmpty()) {
                        _log.info("The port is in use by {} masks. Checking the masks virtual arrays.", masks.size());
                        for (ExportMask mask : masks) {
                            if (!mask.getInactive()) {
                                List<ExportGroup> groups = CustomQueryUtility.queryActiveResourcesByRelation(_dbClient,
                                        mask.getId(), ExportGroup.class, "exportMasks");
                                for (ExportGroup group : groups) {
                                    if (!group.getInactive() && varrayUris.contains(group.getVirtualArray())) {
                                        _log.info("The port is in use by export group {} in virtual array {} ",
                                                group.getLabel(), group.getVirtualArray());
                                        throw APIException.badRequests.cannotUnassignNetworkInUse(network.getId(), group.getId(),
                                                "ExportGroup");
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
                        if (!fileShare.getInactive() && varrayUris.contains(fileShare.getVirtualArray())) {
                            _log.info("The port is in use by file share {} in virtual array {} ",
                                    fileShare.getLabel(), fileShare.getVirtualArray());
                            throw APIException.badRequests
                                    .cannotUnassignNetworkInUse(network.getId(), fileShare.getId(), "FileShareExport");
                        }
                    }
                }

            }
        }
    }

    /**
     * This call returns a list of all Storage Ports associated
     * with the Network end points.
     * <p>
     * 
     * @param id the URN of a ViPR network
     * @brief List storage ports
     * @return StoragePortList
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR })
    @Path("/{id}/storage-ports")
    public StoragePortList getStoragePorts(@PathParam("id") URI id) {

        ArgValidator.checkUri(id);
        Network tzone = _dbClient.queryObject(Network.class, id);
        ArgValidator.checkEntityNotNull(tzone, id, isIdEmbeddedInURL(id));

        StoragePortList registeredStoragePorts = new StoragePortList();

        URIQueryResultList storagePortURIs = new URIQueryResultList();
        _dbClient.queryByConstraint(AlternateIdConstraint.Factory
                .getNetworkStoragePortConstraint(id.toString()),
                storagePortURIs);

        Iterator<URI> storagePortURIsIter = storagePortURIs.iterator();

        while (storagePortURIsIter.hasNext()) {
            URI storagePortURI = storagePortURIsIter.next();
            StoragePort storagePort = _dbClient.queryObject(StoragePort.class,
                    storagePortURI);

            if (storagePort != null && !storagePort.getInactive()) {
                registeredStoragePorts.getPorts().add(toNamedRelatedResource(
                        storagePort, storagePort.getNativeGuid()));
            }
        }

        return registeredStoragePorts;
    }

    /**
     * This call returns a list of all Initiators associated
     * with the Network end points.
     * 
     * @param id the URN of a ViPR network
     * @brief List initiators
     * @return InitiatorList
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR })
    @Path("/{id}/initiators")
    public InitiatorList getInitiators(@PathParam("id") URI id) {

        InitiatorList registeredInitiators = new InitiatorList();

        ArgValidator.checkFieldUriType(id, Network.class, "id");
        Network tzone = queryResource(id);

        StringSet endpts = tzone.retrieveEndpoints();

        Iterator<String> endptsIter = endpts.iterator();
        URIQueryResultList resultsList = new URIQueryResultList();

        while (endptsIter.hasNext()) {

            String endpt = endptsIter.next();
            _dbClient.queryByConstraint(
                    AlternateIdConstraint.Factory.getInitiatorPortInitiatorConstraint(
                            endpt), resultsList);
            Iterator<URI> resultsIter = resultsList.iterator();

            while (resultsIter.hasNext()) {
                Initiator initiator = _dbClient.queryObject(
                        Initiator.class, resultsIter.next());

                if (initiator != null) {
                    registeredInitiators.getInitiators().add(
                            toNamedRelatedResource(
                                    initiator, initiator.getLabel()));
                }
            }

        }

        return registeredInitiators;
    }

    /**
     * This call returns a list of all IpInterfaces associated
     * with the Network end points.
     * 
     * @param id the URN of a ViPR network
     * @brief List ipInterfaces
     * @return IpInterfaceList
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @CheckPermission(roles = { Role.SYSTEM_ADMIN, Role.SYSTEM_MONITOR })
    @Path("/{id}/ip-interfaces")
    public IpInterfaceList getIpInterfaces(@PathParam("id") URI id) {

        IpInterfaceList registeredIpInterfaces = new IpInterfaceList();

        ArgValidator.checkFieldUriType(id, Network.class, "id");
        Network tzone = queryResource(id);

        if (StorageProtocol.Transport.IP.name().equalsIgnoreCase(
                tzone.getTransportType())) {

            StringSet endpts = tzone.retrieveEndpoints();

            Iterator<String> endptsIter = endpts.iterator();
            URIQueryResultList resultsList = new URIQueryResultList();

            while (endptsIter.hasNext()) {

                String endpt = endptsIter.next();

                _dbClient.queryByConstraint(
                        AlternateIdConstraint.Factory.getIpInterfaceIpAddressConstraint(
                                endpt.toUpperCase()), resultsList);

                Iterator<URI> resultsIter = resultsList.iterator();

                while (resultsIter.hasNext()) {
                    IpInterface ipInterface = _dbClient.queryObject(
                            IpInterface.class, resultsIter.next());

                    if (ipInterface != null) {
                        registeredIpInterfaces.getIpInterfaces().add(
                                toNamedRelatedResource(
                                        ipInterface, ipInterface.getLabel()));
                    }
                }

            }
        }

        return registeredIpInterfaces;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Class<Network> getResourceClass() {
        return Network.class;
    }

    @Override
    public NetworkBulkRep queryBulkResourceReps(List<URI> ids) {

        Iterator<Network> _dbIterator =
                _dbClient.queryIterativeObjects(getResourceClass(), ids);
        return new NetworkBulkRep(BulkList.wrapping(_dbIterator, MapNetwork.getInstance()));
    }

    @Override
    public NetworkBulkRep queryFilteredBulkResourceReps(List<URI> ids) {

        verifySystemAdmin();
        return queryBulkResourceReps(ids);
    }

    /**
     * Record Bourne Event for the completed operations
     * 
     * @param network
     * @param type
     * @param description
     */
    private void recordNetworkEvent(Network network, String type,
            String description) {
        RecordableBourneEvent event = new RecordableBourneEvent(
                /* String */type,
                /* tenant id */null,
                /* user id ?? */URI.create("ViPR-User"),
                /* project ID */null,
                /* CoS */null,
                /* service */EVENT_SERVICE_TYPE,
                /* resource id */network.getId(),
                /* description */description,
                /* timestamp */System.currentTimeMillis(),
                /* extensions */null,
                /* native guid */network.getNativeGuid(),
                /* record type */RecordType.Event.name(),
                /* Event Source */EVENT_SERVICE_SOURCE,
                /* Operational Status codes */"",
                /* Operational Status Descriptions */"");
        try {
            eventManager.recordEvents(event);
        } catch (Exception ex) {
            _log.error("Failed to record event. Event description: {}. Error: {}.",
                    description, ex);
        }
    }

    private void recordAndAudit(Network network, OperationTypeEnum typeEnum) {
        recordNetworkEvent(network, typeEnum.getEvType(true),
                typeEnum.getDescription());
        auditOp(typeEnum, true, null, network.getLabel(), network.getTransportType(),
                network.getVirtualArray(), network.getId().toString());

    }

    @Override
    protected ResourceTypeEnum getResourceType() {
        return ResourceTypeEnum.NETWORK;
    }
}
