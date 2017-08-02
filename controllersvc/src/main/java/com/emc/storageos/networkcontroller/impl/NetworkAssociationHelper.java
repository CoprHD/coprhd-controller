/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.networkcontroller.impl;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.Network;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.db.client.util.StringSetUtil;
import com.emc.storageos.util.NetworkLite;
import com.emc.storageos.util.NetworkUtil;
import com.emc.storageos.volumecontroller.impl.StoragePoolAssociationHelper;
import com.emc.storageos.volumecontroller.impl.StoragePortAssociationHelper;

/**
 * A helper class to manage/update the network associations in the following cases
 * <ul>
 * 
 * <li>The network is added to a varray. In this case, if the network is the first in the varray that has storage ports from a new array,
 * the pools of the array will be implicitly associated with the varray. The reverse is true when a network is removed from a varray.</li>
 * 
 * <li>New end points are added to the network and in this case the network will be associated with the storage ports the end points
 * correspond to when the storage ports that are already in the database. The reverse is true when an end point is removed from a network.
 * <em>Note</em> that this may also result in a change in the network's varray/storage pools associations described in the previous
 * paragraph.</li>
 * </ul>
 * 
 */
public class NetworkAssociationHelper {

    private static final Logger _log = LoggerFactory.getLogger(NetworkAssociationHelper.class);

    /**
     * When this call is made, the update already took place. Further the list of endpoints and
     * varrays are filtered to what was effectively added and not what was requested by the user.
     * 
     * @param network the network that was changed.
     * @param addVarrays the varrays added to the network
     * @param remVarray the varrays removed from the network
     * @param addEps the added endpoints
     * @param remEps the removed endpoints
     */
    public static void handleNetworkUpdated(Network network, Collection<URI> addVarrays, Collection<URI> remVarray,
            Collection<String> addEps, Collection<String> remEps, DbClient dbClient, CoordinatorClient coordinator) {

        // First update removed endpoints
        List<StoragePort> remPorts = getEndPointsStoragePorts(remEps, dbClient);
        if (!remPorts.isEmpty()) {
            // clear all network and varrays associations
            clearPortAssociations(remPorts, dbClient);
        }

        List<StoragePort> addPorts = getEndPointsStoragePorts(addEps, dbClient);
        if (!addPorts.isEmpty()) {
            // update the ports implicitly connected varrays
            updatePortAssociations(network, addPorts, dbClient);
        }

        List<StoragePort> createdAndUpdatedPorts = new ArrayList<StoragePort>(addPorts);
        if ((addVarrays != null && !addVarrays.isEmpty()) || (remVarray != null && !remVarray.isEmpty())) {
            // varray changed, update existing ports in the network
            List<StoragePort> updatedPorts = getNetworkStoragePorts(network.getId().toString(), addEps, dbClient);
            createdAndUpdatedPorts.addAll(updatedPorts);
            updatePortAssociations(network, updatedPorts, dbClient);
        }

        if (!remPorts.isEmpty() ||
                (addVarrays != null && !addVarrays.isEmpty()) || (remVarray != null && !remVarray.isEmpty())) {
            // when ports are removed or varrays changed, a full recompute of connected networks is needed
            setNetworkConnectedVirtualArrays(network, true, dbClient);
        } else if (!addPorts.isEmpty()) {
            // update the network implicitly connected varrays based on added ports
            updateConnectedVirtualArrays(network, addPorts, true, dbClient);
        }

        // now I need to handle pools
        StoragePoolAssociationHelper.handleNetworkUpdated(network, addVarrays, remVarray, createdAndUpdatedPorts, remPorts, dbClient,
                coordinator);

        // Update the virtual nas with network changes!!!
        StoragePortAssociationHelper.runUpdateVirtualNasAssociationsProcess(network, addPorts, remPorts, dbClient);
    }

    /**
     * Updates the implicitly connected virtual arrays for the passed
     * network, if necessary, based on the passed storage ports. This
     * update is needed when ports' varrays are changed or when ports
     * are added or removed from a network. If the affected network
     * is routed to other networks, this function will also check if
     * any updates are needed for the routed networks.
     * 
     * 
     * @param network A reference to the network.
     * @param storagePorts A list of the storage ports being added or removed
     *            or the list of storage ports that have had their virtual arrays
     *            changed.
     * @param isAdd true if the storage ports are being added or if their
     *            virtual arrays were added. In this case the new virtual arrays
     *            will be added to the network. This flag is false when ports
     *            are removed or when their virtual arrays are removed. In this
     *            case a new list of connected virtual arrays is computed and the
     *            a full set is performed on the network and its routed networks.
     *            This option clearly costs more.
     * @param dbClient A reference to the DB client.
     */
    public static void updateConnectedVirtualArrays(Network network,
            List<StoragePort> storagePorts, boolean isAdd, DbClient dbClient) {

        _log.info("Updating connected virtual arrays for network {}",
                network.getLabel());

        // Check whether ports are added/removed to/from the network.
        if (isAdd) {
            _log.info("Storage ports were added to network or have virtual arrays added.");
            Set<String> varraysToAdd = new HashSet<String>();
            StringSet networksConnectedVArrays = network.getConnectedVirtualArrays();
            _log.info("Current connected virtual arrays are {} ", networksConnectedVArrays);
            for (StoragePort storagePort : storagePorts) {
                _log.debug("Processing virtual arrays for storage port {}", storagePort.getNativeGuid());
                StringSet storagePortTaggedVArrays = storagePort.getTaggedVirtualArrays();
                if ((storagePortTaggedVArrays != null) && (!storagePortTaggedVArrays.isEmpty())) {
                    for (String storagePortTaggedVArray : storagePortTaggedVArrays) {
                        _log.debug("Storage port assigned to virtual array {}", storagePortTaggedVArray);
                        // If the storage port is being added to the network,
                        // then network is implicitly connected to the port's
                        // assigned virtual arrays, so add them if necessary.
                        if ((networksConnectedVArrays == null)
                                || (!networksConnectedVArrays.contains(storagePortTaggedVArray))) {
                            varraysToAdd.add(storagePortTaggedVArray);
                        }
                    }
                }
            }
            if (!varraysToAdd.isEmpty()) {
                addNetworkConnectedVarrays(network, varraysToAdd, true, dbClient);
            }
        } else {
            _log.info("Storage ports {} removed from network or have had virtual arrays removed");
            setNetworkConnectedVirtualArrays(network, true, dbClient);
        }
    }

    public static void setNetworkConnectedVirtualArrays(Network network, boolean cascade, DbClient dbClient) {
        // compute the new virtual arrays
        StringSet newSet = getNetworkConnectedVirtualArrays(network.getId(), network.getRoutedNetworks(),
                network.getAssignedVirtualArrays(), dbClient);
        // check the new list of virtual arrays is different from the old
        boolean changed = StringSetUtil.isChanged(network.getConnectedVirtualArrays(), newSet);
        if (changed) {
            _log.info("Updating connected virtual arrays for network {}. New virtual arrays {}",
                    network.getId(), newSet);
            network.replaceConnectedVirtualArrays(newSet);
            dbClient.updateAndReindexObject(network);

            if (cascade) {
                // now update the routed networks
                List<Network> routednetworks = getNetworkRoutedNetworksForUpdate(network, dbClient);
                for (Network routedNetwork : routednetworks) {
                    NetworkLite lite = NetworkUtil.getNetworkLite(routedNetwork.getId(), dbClient);
                    newSet = getNetworkConnectedVirtualArrays(routedNetwork.getId(), lite.getRoutedNetworks(),
                            lite.getAssignedVirtualArrays(), dbClient);
                    _log.info("Updating connected virtual arrays for routed network {}. New virtual arrays {}",
                            network.getId(), newSet);
                    routedNetwork.replaceConnectedVirtualArrays(newSet);
                    dbClient.updateAndReindexObject(routedNetwork);
                }
            }
        } else {
            _log.info("The new virtual arrays {} are the same as the existing ones. " +
                    "No update is needed for network {}",
                    newSet, network.getId());
        }
    }

    /**
     * Of the passed list of virtual arrays, determines those for which an
     * active storage port in the passed network has been assigned. The
     * function ignores the passed ports, which may be in the process of
     * being removed from the virtual arrays or the network.
     * 
     * @param network A reference to the network.
     * @param varrayIds The id of the virtual arrays.
     * @param ignorePorts Storage ports to be ignored, or null.
     * @param dbClient A reference to a DB client.
     * 
     * @return The ids of the varrays for which a storage port in the passed network
     *         is assigned.
     */
    public static Set<String> getVArraysNotAssignedToStoragePortInNetwork(
            Network network, Set<String> varrayIds, List<URI> ignorePorts, DbClient dbClient) {

        _log.info("Getting varrays not assigned to any storage ports in network {}", network.getId());

        Set<String> varraysWithoutAssignedStoragePort = new HashSet<String>();

        // Get the URIs of the active storage ports in the passed network.
        List<URI> activeNetworkStoragePortURIs = new ArrayList<URI>();
        List<StoragePort> activeNetworkStoragePorts = CustomQueryUtility
                .queryActiveResourcesByAltId(dbClient, StoragePort.class, "network", network
                        .getId().toString());
        for (StoragePort networkStoragePort : activeNetworkStoragePorts) {
            activeNetworkStoragePortURIs.add(networkStoragePort.getId());
        }

        // Cycle over the passed virtual arrays to determine which are not
        // assigned to a storage port in the passed network.
        for (String varrayId : varrayIds) {
            boolean storagePortAssignedToVArray = false;
            // Get the storage ports assigned to the virtual array.
            URIQueryResultList queryResults = new URIQueryResultList();
            dbClient.queryByConstraint(AlternateIdConstraint.Factory
                    .getAssignedVirtualArrayStoragePortsConstraint(varrayId), queryResults);
            Iterator<URI> resultsIter = queryResults.iterator();
            while (resultsIter.hasNext()) {
                URI storagePortURI = resultsIter.next();
                _log.info("Checking virtual array storage port {}", storagePortURI);

                // Ignore the passed port if not null. This
                // could be a port we are in the process of
                // removing from the virtual array.
                if ((ignorePorts != null) && (ignorePorts.contains(storagePortURI))) {
                    _log.info("Ignoring port {}", storagePortURI);
                    continue;
                }

                // Check if the assigned virtual array storage
                // port is a storage port in the passed network.
                // If so, set the flag and break.
                if (activeNetworkStoragePortURIs.contains(storagePortURI)) {
                    storagePortAssignedToVArray = true;
                    break;
                }
            }

            if (!storagePortAssignedToVArray) {
                _log.info("Virtual array {} does not have an assigned storage port", varrayId);
                varraysWithoutAssignedStoragePort.add(varrayId);
            }
        }

        return varraysWithoutAssignedStoragePort;
    }

    /**
     * For a list of storage ports in a network, update the ports' implicit (connected) varray associations.
     * 
     * @param network the network the storage ports are in
     * @param ports the list of storage ports requiring update
     * @param dbClient an instance of the DB client
     */
    public static void updatePortAssociations(Network network, List<StoragePort> ports, DbClient dbClient) {
        Set<String> varraySet = network.getAssignedVirtualArrays() == null ? null : new HashSet<String>(network.getAssignedVirtualArrays());
        for (StoragePort port : ports) {
            if (!network.getId().equals(port.getNetwork())) {
                port.setNetwork(network.getId());
            }
            port.replaceConnectedVirtualArray(varraySet);
            _log.info("Setting the connected virtual arrays for added port {} to {}", port.getPortNetworkId(), varraySet);
        }
        dbClient.updateAndReindexObject(ports);
    }

    /**
     * For a list of storage ports in a network, update the ports' implicit (connected) varray associations.
     * 
     * @param network the network the storage ports are in
     * @param ports the list of storage ports requiring update
     * @param dbClient an instance of the DB client
     */
    public static void updatePortAssociations(NetworkLite network, List<StoragePort> ports, DbClient dbClient) {
        Set<String> varraySet = new HashSet<String>(network.getAssignedVirtualArrays());
        for (StoragePort port : ports) {
            port.setNetwork(network.getId());
            port.replaceConnectedVirtualArray(varraySet);
            _log.info("Setting the connected virtual arrays for added port {} to {}", port.getPortNetworkId(), varraySet);
        }
        dbClient.updateAndReindexObject(ports);
    }

    /**
     * Clear the ports' implicit (connected) varray associations
     * 
     * @param ports the list of storage ports requiring update
     * @param dbClient an instance of the DB client
     */
    public static void clearPortAssociations(List<StoragePort> ports, DbClient dbClient) {
        for (StoragePort port : ports) {
            port.setNetwork(NullColumnValueGetter.getNullURI());
            port.clearConnectedVirtualArray();
            _log.info("Cleared the connected virtual arrays for removed port {}", port.getPortNetworkId());
        }
        dbClient.updateAndReindexObject(ports);
    }

    /**
     * A short cut to {@link #handleNetworkUpdated(Network, Collection, Collection, Collection, Collection, DbClient, CoordinatorClient)}
     * that does not require all the parameters.
     * 
     * @param network the network where the endpoints were added
     * @param endpoints the endpoints that were added
     * @param dbClient an instance of the DB client
     * @param coordinator an instance of the coordinator service
     */
    public static void handleEndpointsAdded(Network network,
            Collection<String> endpoints, DbClient dbClient, CoordinatorClient coordinator) {
        NetworkAssociationHelper.handleNetworkUpdated(network, null, null, endpoints, null, dbClient, coordinator);
    }

    /**
     * Update the storage ports that correspond to the end points removed from the network to
     * diassociate them from the network and clear their implicit (connected) varay associations.
     * 
     * @param network the network where the endpoints are getting removed
     * @param dbClient an instance of {@link DbClient}
     */
    public static void handleEndpointsRemoved(Network network, Collection<String> endpoints,
            DbClient dbClient, CoordinatorClient coordinator) {

        // First update removed endpoints
        List<StoragePort> remPorts = getEndPointsStoragePorts(endpoints, dbClient);
        clearPortAssociations(remPorts, dbClient);

        // now I need to handle pools
        StoragePoolAssociationHelper.handleNetworkUpdated(network, null, null, null, remPorts, dbClient, coordinator);
    }

    /**
     * Finds the storage ports that correspond to the end points. Not all end points would have a matching storage port.
     * 
     * @param endpoints collection of endpoints
     * @param dbClient an instance of {@link DbClient}
     * @return a list containing the storage ports that could be matched to an endpoint in <code>endpoints</code>
     */
    public static List<StoragePort> getEndPointsStoragePorts(Collection<String> endpoints,
            DbClient dbClient) {
        List<StoragePort> sports = new ArrayList<StoragePort>();
        if (endpoints != null) {
            for (String endpoint : endpoints) {
                sports.addAll(getEndPointPorts(endpoint, dbClient));
            }
        }
        return sports;
    }

    /**
     * Remove the endpoints from their current networks and update the
     * the port-to-transport-zone and pool-to-varray associations as needed.
     * 
     * @param networkMap a map containing the current network for each endpoint
     * @param network the network to which the endpoint are moving
     */
    public static void handleRemoveFromOldNetworks(
            Map<String, Network> networkMap, Network network, DbClient dbClient, CoordinatorClient coordinator) {
        // rather then processing one end point at a time, try to bundle by network
        List<Network> processedTzs = new ArrayList<Network>();
        for (Network net : networkMap.values()) {
            if (!net.getId().equals(network.getId()) && !processedTzs.contains(net)) {
                // once we find a network that has not been handled, get all its endpoints in the map
                List<String> eps = new ArrayList<String>();
                for (String ep : networkMap.keySet()) {
                    if (networkMap.get(ep) == net) {
                        eps.add(ep);
                    }
                }
                // do the removal and update the associations
                _log.info("Removing endpoints {} from network {} in order to add them to {}",
                        new Object[] { eps.toArray(), net.getLabel(), network.getLabel() });
                net.removeEndpoints(eps);
                handleNetworkUpdated(net, null, null, null, eps, dbClient, coordinator);
                dbClient.updateAndReindexObject(net);
                processedTzs.add(net);
            }
        }
    }

    /**
     * Looks up the storage ports for a given end point. Returns empty list if a storage port could not be found.
     * 
     * @param endPoint
     * @param dbClient an instance of {@link DbClient}
     * @return
     */
    private static List<StoragePort> getEndPointPorts(String endPoint, DbClient dbClient) {
        URIQueryResultList portUriList = new URIQueryResultList();
        List<StoragePort> ports = new ArrayList<StoragePort>();
        dbClient.queryByConstraint(
                AlternateIdConstraint.Factory.getStoragePortEndpointConstraint(endPoint), portUriList);
        Iterator<URI> itr = portUriList.iterator();
        while (itr.hasNext()) {
            StoragePort port = dbClient.queryObject(StoragePort.class, itr.next());
            if (port != null && !port.getInactive()) {
                ports.add(port);
            }
        }
        return ports;
    }

    /**
     * Gets the networks of the storage ports organized in a map.
     * 
     * @param endpoints the ports
     * @param dbClient an instance of {@link DbClient}
     * @return a map of networks and the storage ports that are associated to them.
     */
    public static Map<String, Network> getNetworksMap(Collection<String> endpoints,
            DbClient dbClient) {
        Map<String, Network> networkEndPoints = new HashMap<String, Network>();
        Network network;
        // when a network is found, loop and add all endpoints to its list
        // this collection is used to track what is not accounted for yet
        List<String> remainingEndPoints = new ArrayList<String>(endpoints);
        for (String endpoint : endpoints) {
            // if the endpoint is not accounted for
            if (remainingEndPoints.contains(endpoint)) {
                // find its network
                network = NetworkUtil.getEndpointNetwork(endpoint, dbClient);
                if (network != null) {
                    for (String ep : endpoints) {
                        networkEndPoints.put(ep, network);
                        // remove from remainingEndPoints because it is accounted for
                        remainingEndPoints.remove(ep);
                    }
                }
            }
        }
        return networkEndPoints;
    }

    /**
     * Returns all the storage ports in the network except those in the
     * excludeEndpoints list
     * 
     * @param networkUri the network id
     * @param excludeEndpoints the port network ids that this function should
     *            filter out from the results/
     * @param dbClient an instance of dbClient
     * @return
     */
    public static List<StoragePort> getNetworkStoragePorts(String networkUri,
            Collection<String> excludeEndpoints, DbClient dbClient) {
        _log.debug("Finding storage ports for network {} excluding {}",
                networkUri, excludeEndpoints);
        URIQueryResultList storagePortURIs = new URIQueryResultList();
        dbClient.queryByConstraint(AlternateIdConstraint.Factory
                .getNetworkStoragePortConstraint(networkUri),
                storagePortURIs);
        Iterator<URI> storagePortURIsIter = storagePortURIs.iterator();
        Iterator<StoragePort> portsItr = null;
        portsItr = dbClient.queryIterativeObjects(
                StoragePort.class, CustomQueryUtility.iteratorToList(storagePortURIsIter), true);

        List<StoragePort> portsList = CustomQueryUtility.iteratorToList(portsItr);
        if (excludeEndpoints != null && !excludeEndpoints.isEmpty()) {
            // can't use portsItr to remove item (not supported). Get a new iterator
            Iterator<StoragePort> itr = portsList.iterator();
            while (itr.hasNext()) {
                StoragePort port = itr.next();
                for (String str : excludeEndpoints) {
                    if (port.getPortNetworkId().equals(str)) {
                        itr.remove();
                        break;
                    }
                }
            }
        }
        return portsList;
    }

    public static List<StoragePort> getNetworkConnectedStoragePorts(String networkUri, DbClient dbClient) {
        List<StoragePort> ports = getNetworkStoragePorts(networkUri, null, dbClient);
        NetworkLite lite = NetworkUtil.getNetworkLite(URI.create(networkUri), dbClient);
        if (lite.getRoutedNetworks() != null) {
            for (String str : lite.getRoutedNetworks()) {
                ports.addAll(getNetworkStoragePorts(str, null, dbClient));
            }
        }
        return ports;
    }

    /**
     * Add a list of virtual arrays to a network connected virtual array. Optionally
     * cascade this update to the network's routed virtual arrays
     * 
     * @param network the network to update
     * @param varraysToAdd the virtual arrays to be added
     * @param cascade if true the update will also be made to any routed networks
     * @param dbClient an instance of DbClient
     */
    public static void addNetworkConnectedVarrays(Network network,
            Set<String> varraysToAdd, boolean cascade, DbClient dbClient) {
        // update the network
        _log.info("Adding implicit connected virtual arrays {} for network {}",
                network.getId(), varraysToAdd);
        network.addConnectedVirtualArrays(varraysToAdd);
        dbClient.updateAndReindexObject(network);

        // if updating routed networks is also required
        if (cascade) {
            // get all the network's routed networks and make a list of all networks to update
            List<Network> routedNetworks = getNetworkRoutedNetworksForUpdate(network,
                    dbClient);
            // and update all of them
            for (Network net : routedNetworks) {
                _log.info("Adding implicit connected virtual arrays {} for routed network {}",
                        net.getId(), varraysToAdd);
                net.addConnectedVirtualArrays(varraysToAdd);
            }
            dbClient.updateAndReindexObject(routedNetworks);
        }
    }

    /**
     * Computes the list of connected virtual arrays for a network based on the
     * network's storage ports assigned virtual array, plus those of the network's
     * routed networks
     * 
     * @param networkUri the id of the network
     * @param routedNetworks the ids of the routed network
     * @param assignedVarrays
     * @param dbClient and instance of DbClient
     * @return
     */
    public static StringSet getNetworkConnectedVirtualArrays(URI networkUri,
            Set<String> routedNetworks, Set<String> assignedVarrays, DbClient dbClient) {
        StringSet set = new StringSet();
        if (assignedVarrays != null) {
            set.addAll(assignedVarrays);
        }
        // TODO - One day when dbClient stops sending inactive objects, I can improve this and retrieve
        // only the assignedVirtualArrays and reduce the memory footprint
        List<StoragePort> allPorts = getNetworkStoragePorts(networkUri.toString(), null,
                dbClient);
        if (routedNetworks != null) {
            for (String strUri : routedNetworks) {
                allPorts.addAll(getNetworkStoragePorts(strUri, null, dbClient));
            }
        }
        for (StoragePort port : allPorts) {
            if (port != null && port.getInactive() == false && port.getTaggedVirtualArrays() != null) {
                set.addAll(port.getTaggedVirtualArrays());
            }
        }

        _log.debug("Found virtual arrays {} to be implicitly connected to network {}",
                set, networkUri);
        return set;
    }

    /**
     * This function returns the routed networks for a given network. It however
     * only retrieves one specific field for each. This is done to reduce the
     * memory footprint and because NetworkLite cannot be used for updates.
     * 
     * @param network the network for which the routed networks are requested
     * @param dbClient an instance of DbClient
     * @return the routed networks for the network.
     */
    private static List<Network> getNetworkRoutedNetworksForUpdate(Network network, DbClient dbClient) {
        List<Network> networks = new ArrayList<Network>();
        if (network.getRoutedNetworks() != null && !network.getRoutedNetworks().isEmpty()) {
            Iterator<Network> networksItr = dbClient.queryIterativeObjects(Network.class,
                    StringSetUtil.stringSetToUriList(
                            network.getRoutedNetworks()));
            while (networksItr.hasNext()) {
                Network net = networksItr.next();
                if (net != null) {
                    networks.add(net);
                }
            }
        }
        _log.debug("Found {} routed networks for network {}",
                networks.size(), network.getId());
        return networks;
    }
}
