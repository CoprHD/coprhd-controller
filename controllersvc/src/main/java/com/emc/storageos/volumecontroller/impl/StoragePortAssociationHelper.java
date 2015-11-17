/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl;

import java.io.IOException;
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
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StoragePort.TransportType;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.VirtualNAS;
import com.emc.storageos.networkcontroller.impl.NetworkAssociationHelper;
import com.emc.storageos.util.ConnectivityUtil;
import com.emc.storageos.util.NetworkLite;
import com.emc.storageos.util.NetworkUtil;
import com.emc.storageos.volumecontroller.impl.utils.ImplicitPoolMatcher;

/**
 * A helper class to update Network/Storage Port as well as the VirtualArray/Storage Pool
 * associations when a storage port is discovered. There are two possible updates:
 * <ul>
 * 
 * <li>When a storage port is discovered, if its end point is found to already belong to a varray, the storage port is associated with the
 * network. The reverse is true when a storage port is removed however, this use case is not yet supported.</li>
 * 
 * <li>When the storage port is added or registered is associated with a network, and the network is associated with a varray, if the
 * network is the first in the varray that has storage ports from a new array, the pools of the array will be implicitly associated with the
 * varray. The reverse is true when a storage port is removed or de-registered.</li>
 * </ul>
 * 
 */
public class StoragePortAssociationHelper {

    private static final Logger _log = LoggerFactory.getLogger(StoragePortAssociationHelper.class);

    /**
     * TODO Need to be removed after VNXFile changes
     * 
     * Updates the Network/Storage Port as well as the Varray/Storage Pool associations
     * when ports are added.
     * This code assumes that an endpoint exists in one and only one network
     * returns true if any ports are associated with network.
     * 
     * @param ports the list of added ports
     * @param dbClient an instance of {@link DbClient} @ return boolean
     * @throws IOException when a database error occurs
     */
    public static void updatePortAssociations(Collection<StoragePort> ports, DbClient dbClient) throws IOException {
        // Find the networks that have the storage ports' end points.
        Map<NetworkLite, List<StoragePort>> networkPorts = getNetworksMap(ports, dbClient);

        if (networkPorts.isEmpty()) {
            _log.info("The storage ports are not in any network.");
            return; // nothing to do
        }
        for (Map.Entry<NetworkLite, List<StoragePort>> portsForNetwork : networkPorts.entrySet()) {
            NetworkAssociationHelper.updatePortAssociations(portsForNetwork.getKey(),
                    portsForNetwork.getValue(), dbClient);
        }

        StoragePoolAssociationHelper.updateVArrayRelations(ports, null, dbClient, null);
    }

    /**
     * This method bundles updation of
     * 1. Port to Network
     * 2. Pool to VArrays
     * 
     * @param ports
     * @param networkPorts
     * @param dbClient
     * @throws IOException
     */
    private static void updatePortAssociations(Collection<StoragePort> ports, Map<NetworkLite, List<StoragePort>> networkPorts,
            DbClient dbClient) throws IOException {
        updatePortToNetworkAssociation(networkPorts, dbClient);
        StoragePoolAssociationHelper.updateVArrayRelations(ports, null, dbClient, null);
    }

    /**
     * This method updates 1. Port to Network 2. Port to VArrays
     * 
     * @param networkPorts
     * @param dbClient
     */
    private static void updatePortToNetworkAssociation(Map<NetworkLite, List<StoragePort>> networkPorts, DbClient dbClient) {
        for (Map.Entry<NetworkLite, List<StoragePort>> portsForNetwork : networkPorts
                .entrySet()) {
            NetworkAssociationHelper.updatePortAssociations(portsForNetwork.getKey(),
                    portsForNetwork.getValue(), dbClient);
        }
    }

    /**
     * Group Ports by Network
     * 
     * @param ports
     * @param dbClient
     * @return
     */
    private static Map<NetworkLite, List<StoragePort>> groupPortsByNetwork(
            Collection<StoragePort> ports, DbClient dbClient) {
        return getNetworksMap(ports, dbClient);
    }

    /**
     * get Ids
     * 
     * @param pools
     * @return
     */
    private static Set<URI> getStoragePoolIds(List<StoragePool> pools) {
        Set<URI> poolUris = new HashSet<URI>();
        for (StoragePool pool : pools) {
            poolUris.add(pool.getId());
        }
        return poolUris;
    }

    /**
     * This method is responsible for
     * 1. Update pools to virtual arrays & system to virtual arrays in vplex case
     * 2. Run implicit Pool Matcher
     * 3. Run RP Connectivity Process
     * 
     * @param ports
     * @param remPorts
     * @param dbClient
     * @param coordinator
     * @throws IOException
     */
    public static void runUpdatePortAssociationsProcess(Collection<StoragePort> ports, Collection<StoragePort> remPorts,
            DbClient dbClient, CoordinatorClient coordinator, List<StoragePool> pools) {
        try {
            if (null == pools) {
                pools = new ArrayList<StoragePool>();
            }
            if (null == ports) {
                ports = new ArrayList<StoragePort>();
            }
            if (null != remPorts) {
                ports.addAll(remPorts);
            }
            // for better reading, added a method to group Ports by Network
            Map<NetworkLite, List<StoragePort>> portsByNetwork = groupPortsByNetwork(ports, dbClient);
            if (!portsByNetwork.isEmpty()) {
                updatePortAssociations(ports, portsByNetwork, dbClient);

                // if any ports are associated with network, then add pools to existing list and run matching pools
                Set<URI> poolUris = getStoragePoolIds(pools);
                List<StoragePool> modifiedPools = StoragePoolAssociationHelper.getStoragePoolsFromPorts(dbClient, ports, remPorts);
                for (StoragePool pool : modifiedPools) {
                    if (!poolUris.contains(pool.getId())) {
                        pools.add(pool);
                    }
                }
            }
            // Match the VPools to the StoragePools
            ImplicitPoolMatcher.matchModifiedStoragePoolsWithAllVirtualPool(pools, dbClient, coordinator);
            // get all the system that were affected and update their virtual
            // arrays
            HashSet<URI> systemsToProcess = StoragePoolAssociationHelper.getStorageSytemsFromPorts(ports, remPorts);
            // Now that pools have changed varrays, we need to update RP systems
            ConnectivityUtil.updateRpSystemsConnectivity(systemsToProcess, dbClient);
        } catch (Exception e) {
            _log.error("Update Port Association process failed", e);
        }
    }

    /**
     * it return VirtualNAS from database using NativeId
     * 
     * @param nativeId
     * @param dbClient
     * @return VirtualNAS based on nativeId
     */

    private static VirtualNAS findvNasByNativeId(String nativeId, DbClient dbClient) {
        URIQueryResultList results = new URIQueryResultList();
        VirtualNAS vNas = null;

        dbClient.queryByConstraint(
                AlternateIdConstraint.Factory.getVirtualNASByNativeGuidConstraint(nativeId),
                results);
        Iterator<URI> iter = results.iterator();
        while (iter.hasNext()) {
            VirtualNAS tmpVnas = dbClient.queryObject(VirtualNAS.class, iter.next());

            if (tmpVnas != null && !tmpVnas.getInactive()) {
                vNas = tmpVnas;
                _log.info("found virtual NAS {}", tmpVnas.getNativeGuid() + ":" + tmpVnas.getNasName());
                break;
            }
        }
        return vNas;

    }

    /**
     * This method is responsible for
     * Assign the virtual arrays of storage port to virtual nas
     * 
     * @param ports
     * @param remPorts
     * @param dbClient
     * @param coordinator
     * @throws IOException
     */
    public static void runUpdateVirtualNasAssociationsProcess(Collection<StoragePort> ports, Collection<StoragePort> remPorts,
            DbClient dbClient) {
        try {

            List<VirtualNAS> modifiedServers = new ArrayList<VirtualNAS>();

            if (ports != null && !ports.isEmpty()) {
                // for better reading, added a method to group Ports by Network
                Map<String, List<NetworkLite>> vNasNetworkMap = getVNasNetworksMap(ports, dbClient);
                if (!vNasNetworkMap.isEmpty()) {
                    for (Map.Entry<String, List<NetworkLite>> vNasEntry : vNasNetworkMap.entrySet()) {
                        String nativeId = vNasEntry.getKey();
                        VirtualNAS vNas = findvNasByNativeId(nativeId, dbClient);
                        if (vNas != null) {
                            for (NetworkLite network : vNasEntry.getValue()) {
                                Set<String> varraySet = new HashSet<String>(network.getAssignedVirtualArrays());
                                if (vNas.getAssignedVirtualArrays() == null) {
                                    vNas.setAssignedVirtualArrays(new StringSet());
                                }
                                vNas.getAssignedVirtualArrays().addAll(varraySet);
                                _log.info("found virtual NAS: {} and varrays: {}", vNas.getNasName(), varraySet.toString());

                            }
                            modifiedServers.add(vNas);
                        }

                    }
                }
            }

            if (!modifiedServers.isEmpty()) {
                dbClient.persistObject(modifiedServers);
            }
        } catch (Exception e) {
            _log.error("Update Port Association process failed", e);
        }
    }

    /**
     * Gets the networks of the storage ports organized in a map.
     * This code assumes that an end point exists in one and only one network.
     * 
     * @param sports the ports
     * @param dbClient an instance of {@link DbClient}
     * @return a map of networks and the storage ports that are associated to them.
     */
    private static Map<NetworkLite, List<StoragePort>> getNetworksMap(
            Collection<StoragePort> sports, DbClient dbClient) {
        Map<NetworkLite, List<StoragePort>> networkPorts = new HashMap<NetworkLite, List<StoragePort>>();
        NetworkLite network;
        List<StoragePort> list = null;
        for (StoragePort sport : sports) {
            network = NetworkUtil.getEndpointNetworkLite(sport.getPortNetworkId(), dbClient);
            if (network != null && network.getInactive() == false
                    && network.getTransportType().equals(sport.getTransportType())) {
                list = networkPorts.get(network);
                if (list == null) {
                    list = new ArrayList<StoragePort>();
                    networkPorts.put(network, list);
                }
                list.add(sport);
            }
        }
        return networkPorts;
    }

    /**
     * it return VirtualNAS contains the given StoragePort
     * 
     * @param sp StorgaePort
     * @param dbClient
     * @return VirtualNAS associated with StorgaePort
     */
    private static List<VirtualNAS> getStoragePortVirtualNAS(StoragePort sp, DbClient dbClient) {
        List<VirtualNAS> virtualNASList = new ArrayList<VirtualNAS>();
        URIQueryResultList vNasUriList = new URIQueryResultList();
        dbClient.queryByConstraint(
                ContainmentConstraint.Factory.getVirtualNASContainStoragePortConstraint(sp.getId()), vNasUriList);

        Iterator<URI> vNasIter = vNasUriList.iterator();
        while (vNasIter.hasNext()) {
            VirtualNAS vNas = dbClient.queryObject(VirtualNAS.class, vNasIter.next());
            if (vNas != null && !vNas.getInactive()) {
                virtualNASList.add(vNas);
                _log.info("found virtual NAS: {} for storageport: {}", vNas.getNasName(), sp.getLabel());
            }
        }
        return virtualNASList;

    }

    /**
     * Gets the networks of the virtual nas and organized in a map.
     * This code assumes that an end point exists in one and only one network.
     * 
     * @param sports the ports
     * @param dbClient an instance of {@link DbClient}
     * @return a map of networks and the virtual nas that are associated to them.
     */
    private static Map<String, List<NetworkLite>> getVNasNetworksMap(
            Collection<StoragePort> sports, DbClient dbClient) {

        Map<String, List<NetworkLite>> vNasNetwork = new HashMap<>();

        NetworkLite network;
        List<VirtualNAS> vNasList = null;
        List<NetworkLite> list = null;

        for (StoragePort sport : sports) {
            if (TransportType.IP.name().equalsIgnoreCase(sport.getTransportType())) {
                StorageSystem system = dbClient.queryObject(StorageSystem.class, sport.getStorageDevice());
                if (DiscoveredDataObject.Type.vnxfile.name().equals(system.getSystemType()) || DiscoveredDataObject.Type.isilon.name().equals(system.getSystemType())) {
                    network = NetworkUtil.getEndpointNetworkLite(sport.getPortNetworkId(), dbClient);
                    vNasList = getStoragePortVirtualNAS(sport, dbClient);
                    if (network != null && network.getInactive() == false
                            && network.getTransportType().equals(sport.getTransportType())
                            && vNasList != null && !vNasList.isEmpty()) {
                        for(VirtualNAS vNas : vNasList) {
                            list = vNasNetwork.get(vNas.getNativeGuid());
                            if (list == null) {
                                list = new ArrayList<NetworkLite>();
                                vNasNetwork.put(vNas.getNativeGuid(), list);
                            }
                            list.add(network);
                        }
                    }
                }
            }
        }
        return vNasNetwork;
    }
}
