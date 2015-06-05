/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl;

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
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.Network;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.StoragePort.PortType;
import com.emc.storageos.util.ConnectivityUtil;

/**
 * This is a helper class to update the implicit associations between varrays and
 * storage pools. These associations exist when a varray has one or more transport
 * zones with end points that map to storage arrays. The pools of these arrays are 
 * considered implicitly associated with the varray. This is because volumes can
 * be created and exported to hosts in these varrays. These associations are changed
 * when networks are added/remove to varrays, storage ports are discovered, 
 * registered and de-registered, and when new pools are discovered. <em>Note</em> removal
 * of storage ports and storage pools is not yet supported.
 *
 */
public class StoragePoolAssociationHelper {

    private static final Logger _log = LoggerFactory.getLogger(StoragePoolAssociationHelper.class);


    /**
     * Update the storage pools that are affected by the network update. These are the pools
     * in storage systems that have ports in the network.
     * 
     * When this call is made, the update already took place. Further the list of endpoints and
     * varrays are filtered to what effectively added and not what was requested by the user.
     * 
     * @param network the network that was updated
     * @param addVarrays the varrays that were added to the network if any, otherwise null
     * @param remVarray the varrays that were removed from the network if any, otherwise null
     * @param ports the ports that were changed in the network if any, otherwise null
     * @param remPorts the ports that were removed to the network if any, otherwise null
     */
    public static void handleNetworkUpdated (Network network, Collection<URI> addVarrays, Collection<URI> remVarray,
            Collection<StoragePort> ports , Collection<StoragePort> remPorts, DbClient dbClient, CoordinatorClient coordinator) {
        StoragePortAssociationHelper.runUpdatePortAssociationsProcess(ports, remPorts, dbClient, coordinator,null);
    }
    
    
    /**
     * Method to get Storage Systems from Ports
     * @param ports
     * @param remPorts
     * @return
     */
    public static  HashSet<URI> getStorageSytemsFromPorts(Collection<StoragePort> ports , Collection<StoragePort> remPorts) {
         Map<URI, List<StoragePort>> systemsMap = getPortsBySystem(ports);
         Map<URI, List<StoragePort>> remPortsSystemsMap = getPortsBySystem(remPorts);
         // get all the system that were affected and update their virtual arrays 
         HashSet<URI> systemsToProcess = new HashSet<URI>(remPortsSystemsMap.keySet());
         systemsToProcess.addAll(systemsMap.keySet());
         return systemsToProcess;
    }
    
    /**
     * Extract Storage Pools from Storage Systems the ports belonging to.
     * @param dbClient
     * @param ports
     * @param remPorts
     * @return
     */
    public static List<StoragePool> getStoragePoolsFromPorts(DbClient dbClient,
        Collection<StoragePort> ports, Collection<StoragePort> remPorts) {
        return getStoragePoolsFromPorts(dbClient, ports, remPorts, false);
    }
    
    /**
     * Extract Storage Pools from Storage Systems the ports belonging to.
     * @param dbClient
     * @param ports
     * @param remPorts
     * @param getVplexConnected
     * @return
     */
    public static List<StoragePool> getStoragePoolsFromPorts(DbClient dbClient,
        Collection<StoragePort> ports, Collection<StoragePort> remPorts,
        boolean getVplexConnected) {
        
        List<StoragePool> storagePools = new ArrayList<StoragePool>();
        
        HashSet<URI> systemsToProcess = new HashSet<URI>();
        HashSet<URI> portSystems = getStorageSytemsFromPorts(ports, remPorts);
        for (URI systemUri : portSystems) {
            if (getVplexConnected) {
                StorageSystem system = dbClient.queryObject(StorageSystem.class, systemUri);
                if (DiscoveredDataObject.Type.vplex.name().equals(system.getSystemType())) {
                    Set<URI> connectedSystemURIs = ConnectivityUtil
                        .getStorageSystemAssociationsByNetwork(dbClient, systemUri,
                            PortType.backend);
                    for (URI connectedSystemURI : connectedSystemURIs) {
                        systemsToProcess.add(connectedSystemURI);
                    }
                } else {
                    systemsToProcess.addAll(portSystems);
                }
            } else {
                systemsToProcess.addAll(portSystems);
            }
        }
         
        for (URI systemUri : systemsToProcess) {
            URIQueryResultList storagePoolURIs = new URIQueryResultList();
            dbClient.queryByConstraint(ContainmentConstraint.Factory
                .getStorageDeviceStoragePoolConstraint(systemUri), storagePoolURIs);
            while (storagePoolURIs.iterator().hasNext()) {
                URI storagePoolURI = storagePoolURIs.iterator().next();
                StoragePool storagePool = dbClient.queryObject(StoragePool.class,
                    storagePoolURI);
                if (storagePool != null && !storagePool.getInactive()) {
                    storagePools.add(storagePool);
                }
            }
        }

        return storagePools;
    }
        
    /**
     * Given the changes made to a list of ports' varray associations, update the pools associations.
     * 
     * @param ports the ports for which the varray associations were updated
     * @param remPorts the ports for which the varray associations were cleared
     * @param dbClient an instance of db client
     * @param coordinator an instance of coordinator service
     */
    public static void updateVArrayRelations (Collection<StoragePort> ports , Collection<StoragePort> remPorts, 
            DbClient dbClient, CoordinatorClient coordinator) {
        // get all the system that were affected and update their virtual arrays 
        HashSet<URI> systemsToProcess = getStorageSytemsFromPorts(ports, remPorts);
        // this is very crude still because it resets the connections for all the systems
        // what is needed is:
        //    If neighborhoods were changed, update all the systems
        //    If neighborhoods not changed, to get the removed, added and existing systems.
        //       only act on systems that exist only in add and only in remove.
        for (URI systemUri : systemsToProcess) {
            updateSystemVarrays(systemUri, dbClient);
        }
    }

    /**
     * When the ports-varray associations change, for vplex system, update the system-varray association.
     * For other storage systems, update the pools-varrays associations.
     * 
     * @param systemUri the system where the varray association has changed 
     * @param dbClient an instance of dbClient
     */
    private static void updateSystemVarrays (URI systemUri, DbClient dbClient) {
        StorageSystem system =  dbClient.queryObject(StorageSystem.class, systemUri);
        if (!system.getInactive()) {
            _log.info("Updating the virtual arrays for storage system {}", system.getNativeGuid());
            if (ConnectivityUtil.isAVPlex(system)) {
                StringSet varrays = getSystemConnectedVarrays(systemUri, PortType.frontend.name(), dbClient);
                _log.info("vplex system {} varrays will be set to {}", system.getNativeGuid(), varrays);
                if (system.getVirtualArrays() == null) {
                    system.setVirtualArrays(varrays);
                } else {
                    system.getVirtualArrays().replace(varrays);
                }
                dbClient.updateAndReindexObject(system);
                _log.info("Updated vplex system {} varrays to {}", system.getNativeGuid(), varrays);
            } else {
                StringSet varrays = getSystemConnectedVarrays(systemUri, null, dbClient);
                _log.info("The pools of storage system {} varrays will be set to {}", system.getNativeGuid(), varrays);
                List<StoragePool> pools = getSystemPools(systemUri, dbClient);
                for (StoragePool pool : pools) {
                    pool.replaceConnectedVirtualArray(varrays);
                    _log.info("Updated storage pool {} varrays to {}", pool.getNativeGuid(), varrays);
                }
                dbClient.updateAndReindexObject(pools);
            }
        }
    }

    /**
     * When a pool is newly discovered, set its varray associations.
     * 
     * @param systemUri the pool's system
     * @param storagePools the pool to be updated
     * @param dbClient an instance of db client
     */
    public static void setStoragePoolVarrays(URI systemUri, List<StoragePool> storagePools, DbClient dbClient) {
        StringSet varrayURIs = getSystemConnectedVarrays(systemUri, null, dbClient);
        for (StoragePool storagePool : storagePools) {
            if (storagePool.getStorageDevice().equals(systemUri)) {
                storagePool.replaceConnectedVirtualArray(varrayURIs);
            } else {
                _log.error("Pool {} does not belong to storage system {} and will not be updated",
                        storagePool.getNativeGuid(), systemUri );
            }
        }
        dbClient.updateAndReindexObject(storagePools);

        //now ensure any RP systems are getting their varray connections updated
        ConnectivityUtil.updateRpSystemsConnectivity( 
                java.util.Collections.singletonList(systemUri), dbClient);
    }

    /**
     * Create a map of storage system to ports. The logic of associating/dis-associating 
     * needs to look at the aggregate data to make decisions.
     * 
     * @param portSet the list of ports
     * @return
     */
    private static Map<URI, List<StoragePort>> getPortsBySystem(Collection<StoragePort> portSet) {
        Map<URI, List<StoragePort>> systemPorts = new HashMap<URI, List<StoragePort>>();
        if (portSet != null) {
            List<StoragePort>ports = null;
            for (StoragePort port : portSet) {
                ports = (ArrayList<StoragePort>) systemPorts.get(port.getStorageDevice());
                if (ports == null) {
                    ports = new ArrayList<StoragePort>();
                    systemPorts.put(port.getStorageDevice(), ports);
                }
                ports.add(port);
            }
        }
        return systemPorts;
    }

    private static List<StoragePort> getSystemPorts(URI systemUri, DbClient dbClient) {
        List<StoragePort> ports = new ArrayList<StoragePort>();
        URIQueryResultList storagePortURIs = new URIQueryResultList();
        dbClient.queryByConstraint(ContainmentConstraint.Factory
                .getStorageDeviceStoragePortConstraint(systemUri),
                storagePortURIs);
        Iterator<URI> storagePortURIsIter = storagePortURIs.iterator();
        while (storagePortURIsIter.hasNext()) {
            URI storagePortURI = storagePortURIsIter.next();
            StoragePort storagePort = dbClient.queryObject(StoragePort.class,
                    storagePortURI);
            if (storagePort != null && !storagePort.getInactive()) {
                ports.add(storagePort);
            } else {
                _log.error("Can't find storage port {} in the database " +
                        "or the port is marked for deletion",
                        storagePortURI);
            }
        }
        return ports;
    }

    private static List<StoragePool> getSystemPools(URI systemUri, DbClient dbClient) {
        List<StoragePool> pools = new ArrayList<StoragePool>();
        URIQueryResultList storagePoolsURIs = new URIQueryResultList();
        dbClient.queryByConstraint(ContainmentConstraint.Factory
                .getContainedObjectsConstraint(systemUri, StoragePool.class, "storageDevice"),
                storagePoolsURIs);
        Iterator<URI> storagePortURIsIter = storagePoolsURIs.iterator();
        while (storagePortURIsIter.hasNext()) {
            URI storagePoolURI = storagePortURIsIter.next();
            StoragePool storagePool = dbClient.queryObject(StoragePool.class,
                    storagePoolURI);
            if (storagePool != null && !storagePool.getInactive()) {
                pools.add(storagePool);
            } else {
                _log.error("Can't find storage pool {} in the database " +
                        "or the pool is marked for deletion",
                        storagePoolURI);
            }
        }
        return pools;
    }

    /**
     * Returns a list of varrays that have connectivity to the storage 
     * system. Connectivity is determined by the presence in one of the 
     * varray's networks of an end point that corresponds to a
     * storage port that was discovered on the array.
     * 
     * @param systemUri the storage system uri
     * @param portType frontend or backend. Return all types when this param is null
     * @param dbClient and instance of {@link DbClient}
     * @return a list of varray URIs
     */
    private static StringSet getSystemConnectedVarrays(URI systemUri, String portType, DbClient dbClient) {
        List<StoragePort> ports = getSystemPorts(systemUri, dbClient);
        StringSet varrays = new StringSet();
        for (StoragePort port : ports) {
            if (port.getTaggedVirtualArrays() != null && 
                    (portType == null || port.getPortType().equals(portType))) {
                varrays.addAll(port.getTaggedVirtualArrays());
            }
        }
        return varrays;
    }

    /**
     * Returns a list of varrays that have connectivity to a vplex system
     * 
     * @param systemUri the vplex system uri
     * @param dbClient and instance of {@link DbClient}
     * @return a list of varray URIs
     */
    public static StringSet getVplexSystemConnectedVarrays(URI systemUri, DbClient dbClient) {
        return getSystemConnectedVarrays(systemUri, PortType.frontend.name(), dbClient);
    }

}
