/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.util;

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

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.FSExportMap;
import com.emc.storageos.db.client.model.FileExport;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.Network;
import com.emc.storageos.db.client.model.NetworkSystem;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.svcs.errorhandling.resources.APIException;

public class NetworkUtil {
    private static final Logger _log = LoggerFactory.getLogger(NetworkUtil.class);

    /**
     * Given the URI for a Network, obtain its NetworkLite structure.
     * This is done without instantiating the endpoint data in the Network
     * by calling DbClient.queryObjectFields, which retrieves only certain
     * fields from the database.
     * 
     * @param networkURI
     * @param client
     * @return NetworkLite
     */
    static public NetworkLite getNetworkLite(URI networkURI, DbClient client) {
        List<URI> ids = new ArrayList<URI>();
        ids.add(networkURI);
        Set<String> fieldNames = new HashSet<String>();
        fieldNames.addAll(NetworkLite.getColumnNames());
        Collection<Network> networks =
                client.queryObjectFields(Network.class, fieldNames, ids);
        Iterator<Network> networkIter = networks.iterator();
        if (networkIter.hasNext()) {
            Network network = networkIter.next();
            return new NetworkLite(network);
        }
        throw DatabaseException.fatals.unableToFindEntity(networkURI);
    }

    /**
     * Get the network that has the endpoint
     * 
     * @param endpoint the formatted and validated endpoint
     * @param dbClient an instance if DbClient
     * @return a reference to the network that contains the endpoint.
     *         Null if the network is not found.
     * 
     */
    public static NetworkLite getEndpointNetworkLite(String endpoint, DbClient dbClient) {
        return getEndpointNetworkLite(endpoint, dbClient, null);
    }

    /**
     * Get the network that has the endpoint
     * 
     * @param endpoint the formatted and validated endpoint
     * @param dbClient an instance if DbClient
     * @param excludedNetworks do not get network with nativeId in the list
     * @return a reference to the network that contains the endpoint.
     *         Null if the network is not found.
     * 
     */
    public static NetworkLite getEndpointNetworkLite(String endpoint, DbClient dbClient, Set<String> excludedNetworks) {
        _log.debug("Finding networklite for endpoint {}", endpoint);
        URIQueryResultList networkList = new URIQueryResultList();
        Iterator<URI> iterator;
        URI networkUri = null;
        NetworkLite network;
        dbClient.queryByConstraint(AlternateIdConstraint.Factory.getEndpointNetworkConstraint(endpoint), networkList);
        iterator = networkList.iterator();
        while (iterator.hasNext()) {
            networkUri = iterator.next();
            network = getNetworkLite(networkUri, dbClient);

            // vsan id is in the excluded list, skip it
            if (excludedNetworks != null && excludedNetworks.contains(network.getNativeId())) {
                continue;
            }

            if (network != null && network.getInactive() == false) {
                _log.info(String.format("endpoint %s in network %s (%s)", endpoint, network.getLabel(), network.getId()));
                return network;
            } else {
                _log.info("networklite {} for endpoint {} was deleted or is inactive", networkUri, endpoint);
            }
        }
        _log.info("networklite could not be found for endpoint {}", endpoint);
        return null;
    }

    /**
     * Get all the networks that are connected to the endpoint. An endpoint
     * exists in one and only one network but can be visible by routing
     * from many other networks. This functions return all the networks
     * where this endpoint is visible inclusing the one that contains it.
     * 
     * @param endpoint the formatted and validated endpoint
     * @param dbClient an instance if DbClient
     * @return all the networks that are connected to the endpoint.
     *         An empty set if none is found.
     */
    public static Set<NetworkLite> getEndpointAllNetworksLite(String endpoint, DbClient dbClient) {
        Set<NetworkLite> networks = new HashSet<NetworkLite>();
        NetworkLite networkLite = getEndpointNetworkLite(endpoint, dbClient);
        if (networkLite != null) {
            networks.add(networkLite);
            networks.addAll(getNetworkLiteRoutedNetworks(networkLite, dbClient));
        }
        return networks;
    }

    /**
     * Returns an instance of NetworkLite for every network that is routed to the request network.
     * 
     * @param networkLite the networklite for which the routed networks are requested
     * @param dbClient an instance of dbClient
     * @return an instance of NetworkLite for every network that is routed to the request network
     */
    public static Set<NetworkLite> getNetworkLiteRoutedNetworks(NetworkLite networkLite, DbClient dbClient) {
        if (networkLite != null && networkLite.getRoutedNetworks() != null
                && !networkLite.getRoutedNetworks().isEmpty()) {
            return getNetworkLites(networkLite.getRoutedNetworks(), dbClient);
        }
        return new HashSet<NetworkLite>();
    }

    /**
     * Returns the network lites for a collection of network URIs
     * 
     * @param uris the network URIs in string form
     * @param dbClient an instance of DbClient
     * @return the network lites for a collection of network URIs
     */
    public static Set<NetworkLite> getNetworkLites(Collection<String> uris, DbClient dbClient) {
        Set<NetworkLite> networks = new HashSet<NetworkLite>();
        NetworkLite networkLite = null;
        for (String uri : uris) {
            networkLite = getNetworkLite(URI.create(uri), dbClient);
            if (networkLite != null) {
                networks.add(networkLite);
            }
        }
        return networks;
    }

    /**
     * Returns a NetworkLite set for a collection of network URIs
     * 
     * @param uris the network URIs in string form
     * @param dbClient an instance of DbClient
     * @return the NetworkLite set for a collection of network URIs
     */
    public static Set<NetworkLite> queryNetworkLites(Collection<URI> uris, DbClient dbClient) {
        Set<NetworkLite> networks = new HashSet<NetworkLite>();
        NetworkLite networkLite = null;
        for (URI uri : uris) {
            networkLite = getNetworkLite(uri, dbClient);
            if (networkLite != null) {
                networks.add(networkLite);
            }
        }
        return networks;
    }

    /**
     * Get the network the endpoint is associated with if any
     * 
     * @param endpoint
     * @param dbClient
     * @return a reference to a network
     *         Assumes endpoint formats have been validated.
     */
    public static Network getEndpointNetwork(String endpoint, DbClient dbClient) {
        _log.debug("Finding network for endpoint {}", endpoint);
        URIQueryResultList networkList = new URIQueryResultList();
        Iterator<URI> iterator;
        URI networkUri = null;
        Network network;
        dbClient.queryByConstraint(AlternateIdConstraint.Factory.getEndpointNetworkConstraint(endpoint), networkList);
        iterator = networkList.iterator();
        while (iterator.hasNext()) {
            networkUri = iterator.next();
            network = dbClient.queryObject(Network.class, networkUri);
            if (network != null && network.getInactive() == false) {
                _log.info("network {} for endpoint {} was found", networkUri, endpoint);
                return network;
            } else {
                _log.info("network {} for endpoint {} was deleted or is inactive", networkUri, endpoint);
            }
        }
        _log.info("network could not be found for endpoint {}", endpoint);
        return null;
    }

    /**
     * If the endpoint is used in an active Export Group, throws an exception
     * 
     * @param endpoint endpoint being added
     * @param dbClient
     *            Assumes endpoint formats have been validated.
     */
    public static void checkNotUsedByActiveExportGroup(String endpoint, DbClient dbClient) {

        if (endpoint != null && !"".equals(endpoint)) {

            Initiator initiator = getInitiator(endpoint, dbClient);

            if (initiator != null && initiator.getId() != null) {
                if (NetworkUtil.isInitiatorInUse(initiator.getId(), dbClient)) {
                    throw APIException.badRequests.endpointsCannotBeUpdatedActiveExport(
                            endpoint);
                }
            }

            List<StoragePort> ports = findStoragePortsInDB(endpoint, dbClient);
            for (StoragePort port : ports) {
                if (port != null && port.getId() != null) {
                    if (NetworkUtil.isBlockStoragePortInUse(port.getId(), dbClient)) {
                        throw APIException.badRequests.endpointsCannotBeUpdatedActiveExport(
                                endpoint);
                    }
                }
            }
        }
    }

    /**
     * Looks up the storage port for a given end point. Returns null if a storage port could not be found.
     * 
     * @param endPoint
     * @param dbClient an instance of {@link DbClient}
     * @return A reference to a port.
     */
    public static StoragePort getStoragePort(String endPoint, DbClient dbClient) {
        URIQueryResultList portUriList = new URIQueryResultList();
        dbClient.queryByConstraint(
                AlternateIdConstraint.Factory.getStoragePortEndpointConstraint(endPoint), portUriList);
        Iterator<URI> itr = portUriList.iterator();
        while (itr.hasNext()) {
            StoragePort port = dbClient.queryObject(StoragePort.class, itr.next());
            if (port != null && port.getInactive() == false) {
                return port;
            }
        }
        return null;
    }

    /**
     * Get an initiator as specified by the initiator's network port.
     * 
     * @param networkPort The initiator's port WWN or IQN.
     * @return A reference to an initiator.
     */
    public static Initiator getInitiator(String networkPort, DbClient dbClient) {
        Initiator initiator = null;
        URIQueryResultList resultsList = new URIQueryResultList();

        // find the initiator
        dbClient.queryByConstraint(AlternateIdConstraint.Factory.getInitiatorPortInitiatorConstraint(
                networkPort), resultsList);
        Iterator<URI> resultsIter = resultsList.iterator();
        while (resultsIter.hasNext()) {
            initiator = dbClient.queryObject(Initiator.class, resultsIter.next());
            // there should be one initiator, so return as soon as it is found
            if (initiator != null && !initiator.getInactive()) {
                return initiator;
            }
        }
        return null;
    }

    /**
     * If any endpoint is used in an active File Export, throws an exception
     * 
     * @param endpoints endpoints being added
     * @param varrays endpoints belong to
     * 
     *            Assumes endpoint formats have been validated.
     */
    public static void checkNotUsedByActiveFileExport(String endpoint, DbClient dbClient) {
        Network network = NetworkUtil.getEndpointNetwork(endpoint, dbClient);
        if (network != null) {
            Set<String> netVArrayIds = network.getConnectedVirtualArrays();
            if ((netVArrayIds != null) && (!netVArrayIds.isEmpty())) {
                Iterator<String> netVArrayIdsIter = netVArrayIds.iterator();
                while (netVArrayIdsIter.hasNext()) {
                    String varrayId = netVArrayIdsIter.next();
                    List<FileShare> fileShares = CustomQueryUtility
                            .queryActiveResourcesByConstraint(dbClient, FileShare.class,
                                    AlternateIdConstraint.Factory.getConstraint(FileShare.class,
                                            "varray", varrayId));
                    for (FileShare fileShare : fileShares) {
                        FSExportMap fsExports = fileShare.getFsExports();
                        if (fsExports != null) {
                            Iterator<FileExport> it = fsExports.values().iterator();
                            while (it.hasNext()) {
                                FileExport fileExport = it.next();
                                if (fileExport.getClients().contains(endpoint)
                                        || fileExport.getStoragePort().contains(endpoint)) {
                                    throw APIException.badRequests
                                            .endpointsCannotBeUpdatedActiveExport(endpoint);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Checks if an initiator in use by an export groups
     * 
     * @param initId the initiator URI being checked
     * @param dbClient
     * @return true if the initiator in use by export groups
     */
    public static boolean isInitiatorInUse(URI initId, DbClient dbClient) {

        if (initId != null) {

            List<ExportGroup> exportGroups = CustomQueryUtility.queryActiveResourcesByConstraint(
                    dbClient, ExportGroup.class,
                    AlternateIdConstraint.Factory.getConstraint(
                            ExportGroup.class, "initiators", initId.toString()));
            return (exportGroups != null && !exportGroups.isEmpty());
        }
        return false;
    }

    /**
     * Checks if an port in use by an export masks
     * 
     * @param portId the port URI being checked
     * @param dbClient
     * @return true if the port in use by export masks
     */
    public static boolean isBlockStoragePortInUse(URI portId, DbClient dbClient) {

        if (portId != null) {
            List<ExportMask> exportMasks = CustomQueryUtility.queryActiveResourcesByConstraint(
                    dbClient, ExportMask.class,
                    AlternateIdConstraint.Factory.getConstraint(
                            ExportMask.class, "storagePorts", portId.toString()));
            return (exportMasks != null && !exportMasks.isEmpty());
        }
        return false;
    }

    /**
     * Parses the WWN from the network's Native GUID
     * 
     * @param network the network
     * @return the network WWN
     */
    public static String getNetworkWwn(NetworkLite network) {
        return parseNetworkWwn(network == null ? "" : network.getNativeGuid());
    }

    /**
     * Parses the WWN from the network's Native GUID
     * 
     * @param network the network
     * @return the network WWN
     */
    public static String getNetworkWwn(Network network) {
        return parseNetworkWwn(network.getNativeGuid());
    }

    private static String parseNetworkWwn(String nativeGuid) {
        if (nativeGuid == null || nativeGuid.length() == 0) {
            return "";
        }
        String[] strs = nativeGuid.split("\\+");
        if (strs.length > 1) {
            return strs[strs.length - 1];
        }
        return "";
    }

    /**
     * Given the initiator and port networks, check that they are connected that is either
     * in the same network or in different networks but routable.
     * 
     * @param iniNetwork the initiator network
     * @param portNet the port network
     * @return true if the port and initiator network are connected.
     */
    public static boolean checkInitiatorAndPortConnected(NetworkLite iniNetwork, NetworkLite portNet) {
        if (iniNetwork.getId().equals(portNet.getId())) {
            _log.info("Both the port and initiator are in the same network {}", iniNetwork.getNativeGuid());
            return true;
        } else if (iniNetwork.hasRoutedNetworks(portNet.getId())) {
            _log.info("The port and initiators are in different but routed networks: {} and {}",
                    new Object[] { iniNetwork.getNativeGuid(), portNet.getNativeGuid() });
            return true;
        }
        _log.info("The port and initiator are not connected.");
        return false;
    }

    /**
     * Given the fabric name for a Network, obtain its NetworkLite structure.
     * This is done without instantiating the endpoint data in the Network
     * by calling DbClient.queryObjectFields, which retrieves only certain
     * fields from the database.
     * 
     * @param fabricId
     * @param client
     * @return NetworkLite
     */
    static public NetworkLite getNetworkLiteByFabricId(String fabricId, String fabricWWN, DbClient client) {
        if (fabricId != null && fabricId.length() > 0) {
            URIQueryResultList networkList = new URIQueryResultList();
            client.queryByConstraint(AlternateIdConstraint.Factory.getConstraint(Network.class,
                    "nativeId", fabricId), networkList);
            for (URI uri : networkList) {
                return getNetworkLite(uri, client);     // TODO -need to add check for inactive networks - Need to add code to try using WWN
            }
        }
        return null;
    }

    /**
     * This method returns storage ports if they exist in database.
     * Returns empty list if a storage port could not be found.
     * 
     * @param pwwn storage port pwwn
     * @return storagePort object or null
     */
    public static List<StoragePort> findStoragePortsInDB(String pwwn, DbClient dbClient) {
        List<StoragePort> ports = new ArrayList<StoragePort>();
        _log.info("Looking for storage port {} in database", pwwn);
        URIQueryResultList portUriList = new URIQueryResultList();
        dbClient.queryByConstraint(
                AlternateIdConstraint.Factory.getStoragePortEndpointConstraint(pwwn), portUriList);
        Iterator<URI> itr = portUriList.iterator();
        while (itr.hasNext()) {
            StoragePort port = dbClient.queryObject(StoragePort.class, itr.next());
            if (port != null && !port.getInactive()) {
                _log.info("Found storage port {}", pwwn);
                ports.add(port);
            }
        }
        return ports;
    }

    /**
     * This method returns initiator if it exist in database
     * Returns null if a initiator could not be found.
     * 
     * @param pwwn Initiator pwwn
     * @return initiator object or null
     */
    public static Initiator findInitiatorInDB(String pwwn, DbClient dbClient) {
        Initiator initiator = null;
        _log.info("Looking for initiator {} in database", pwwn);
        URIQueryResultList queryResults = new URIQueryResultList();
        dbClient.queryByConstraint(
                AlternateIdConstraint.Factory.getInitiatorPortInitiatorConstraint(pwwn), queryResults);
        Iterator<URI> resultsIter = queryResults.iterator();
        if (resultsIter.hasNext()) {
            _log.info("Found initiator {}", pwwn);
            initiator = dbClient.queryObject(Initiator.class, resultsIter.next());
        }
        return initiator;
    }

    /**
     * Checks if any network systems have been discovered.
     * 
     * @param dbClient an instance of {@link DbClient}
     * @return true if at least one active network system exists regardless of the
     *         its discovery or registered status.
     */
    public static boolean areNetworkSystemDiscovered(DbClient dbClient) {
        // if no network systems are registered, the zoning is not required
        List<URI> uriNetworkDevices = dbClient.queryByType(NetworkSystem.class, true);
        if (uriNetworkDevices != null && uriNetworkDevices.iterator().hasNext()) {
            return true;
        }
        _log.info("SAN Zoning is disabled because there are no NetworkSystems");
        return false;
    }

    /**
     * Creates a map of initiators grouped and keyed by their network.
     * Initiators which are not in any network are not returned.
     * 
     * @param initiators the initiators
     * @param client
     * @return a map of network-to-initiators
     */
    public static Map<NetworkLite, List<Initiator>> getInitiatorsByNetwork(Collection<Initiator> initiators, DbClient dbClient) {
        Map<NetworkLite, List<Initiator>> map = new HashMap<NetworkLite, List<Initiator>>();
        NetworkLite network = null;
        List<Initiator> netInitiators = null;
        for (Initiator initiator : initiators) {
            network = NetworkUtil.getEndpointNetworkLite(initiator.getInitiatorPort(), dbClient);
            if (network != null) {
                netInitiators = map.get(network);
                if (netInitiators == null) {
                    netInitiators = new ArrayList<Initiator>();
                    map.put(network, netInitiators);
                }
                netInitiators.add(initiator);
            }
        }
        return map;
    }

    /**
     * Returns the ports in the given network in a port-wwn-to-port map.
     * 
     * @param networkLite the network
     * @param ports the ports
     * @return a map of port-wwn-to-port
     */
    public static Map<String, StoragePort> getPortsInNetworkMap(NetworkLite networkLite, Collection<StoragePort> ports) {
        Map<String, StoragePort> map = new HashMap<String, StoragePort>();
        if (networkLite != null) {
            for (StoragePort port : ports) {
                if (port.getNetwork() != null &&
                        port.getNetwork().equals(networkLite.getId())) {
                    map.put(port.getPortNetworkId(), port);
                }
            }
            if (map.isEmpty() && networkLite.getRoutedNetworks() != null) {
                for (StoragePort port : ports) {
                    if (port.getNetwork() != null &&
                            networkLite.getRoutedNetworks().contains(port.getNetwork().toString())) {
                        map.put(port.getPortNetworkId(), port);
                    }
                }
            }
        }
        return map;
    }

    /**
     * Returns a Map of networkURI => [set of endpoints connected].
     * 
     * @param dbClient
     * @param initiators
     * @return
     */
    public static Map<URI, Set<String>> getNetworkToInitiators(DbClient dbClient, List<Initiator> initiators) {
        Map<URI, Set<String>> networkToEndPoints = new HashMap<URI, Set<String>>();
        for (Initiator initiator : initiators) {
            Set<NetworkLite> networkLites = getEndpointAllNetworksLite(initiator.getInitiatorPort(), dbClient);
            if (null == networkLites || networkLites.isEmpty()) {
                _log.info(String.format("getNetworkToInitiators(%s) -- Initiator is not associated with any network",
                        initiator.getInitiatorPort()));
            } else {
                for (NetworkLite networkLite : networkLites) {
                    URI networkUri = networkLite.getId();
                    _log.info(String.format("Adding initiator, network (%s, %s) to map", initiator.getInitiatorPort(),
                            networkLite.getLabel()));
                    Set<String> endPoints = networkToEndPoints.get(networkUri);
                    if (null == endPoints) {
                        endPoints = new HashSet<String>();
                    }
                    endPoints.add(initiator.getInitiatorPort());
                    networkToEndPoints.put(networkUri, endPoints);
                }
            }
        }

        return networkToEndPoints;
    }
}
