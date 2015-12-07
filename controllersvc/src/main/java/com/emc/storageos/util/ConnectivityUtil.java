/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.util;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
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
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.AbstractChangeTrackingSet;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.DiscoveredDataObject.DiscoveryStatus;
import com.emc.storageos.db.client.model.DiscoveredDataObject.RegistrationStatus;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.ProtectionSystem;
import com.emc.storageos.db.client.model.RPSiteArray;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.VirtualPool.SystemType;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.db.client.util.StringSetUtil;
import com.emc.storageos.networkcontroller.impl.NetworkAssociationHelper;
import com.emc.storageos.volumecontroller.impl.StoragePoolAssociationHelper;
import com.emc.storageos.volumecontroller.impl.utils.attrmatchers.VPlexHighAvailabilityMatcher;
import com.emc.storageos.vplex.api.VPlexApiConstants;
import com.google.common.base.Joiner;

public class ConnectivityUtil {

    // Return values from getVPlexClusterOfPort
    public static final String CLUSTER1 = VPlexApiConstants.CLUSTER_1_ID;
    public static final String CLUSTER2 = VPlexApiConstants.CLUSTER_2_ID;
    public static final String CLUSTER_UNKNOWN = "unknown-cluster";

    public static enum StorageSystemType {
        BLOCK,
        FILE
    }

    private static Logger _log = LoggerFactory.getLogger(ConnectivityUtil.class);

    /**
     * Determines whether or not the passed in Storage System in a VPLEX by checking
     * the System Type.
     *
     * @param system The Storage System to check
     * @return boolean value of whether or not this is a VPLEX
     */
    public static boolean isAVPlex(StorageSystem system) {
        return (system.getSystemType().equals(DiscoveredDataObject.Type.vplex.name()));
    }

    /**
     * Determines if the passed storage port is on a VPLEX storage system.
     *
     * @param storagePort A reference to a storage port.
     * @param dbClient Reference to a DB client.
     *
     * @return true if the port is a VPLEX port, false otherwise.
     */
    public static boolean isAVplexPort(StoragePort storagePort, DbClient dbClient) {
        StorageSystem storagePortSystem = dbClient.queryObject(StorageSystem.class, storagePort.getStorageDevice());
        boolean isAVplexPort = ConnectivityUtil.isAVPlex(storagePortSystem);
        if (isAVplexPort) {
            _log.info("Storage port {} is a VPLEX port", storagePort.getId());
        }
        return isAVplexPort;
    }

    /**
     * Determines if the passed VPLEX storage port can be assigned the passed
     * virtual array. Presumes the passed storage port is a VPLEX storage port.
     *
     * @param storagePort A reference to a VPLEX storage port.
     * @param varrayId The id of a virtual array.
     * @param dbClient Reference to a DB client.
     *
     * @return true if the storage port can be assigned to the passed virtual
     *         array, false otherwise.
     */
    public static boolean vplexPortCanBeAssignedToVirtualArray(StoragePort storagePort,
            String varrayId, DbClient dbClient) {

        boolean canBeAssigned = true;

        // Get the VPLEX storage system id.
        URI vplexSystemURI = storagePort.getStorageDevice();
        _log.info("Storage port {} VPLEX is {}", storagePort.getId(), vplexSystemURI);

        // Get the VPLEX cluster id for the passed port
        String portClusterId = getVplexClusterOfPort(storagePort);
        _log.info("Storage port VPLEX cluster id is {}", portClusterId);

        // Get all storage ports manually assigned, rather than tagged, to this
        // virtual array. Use manually assigned here. The user could have just
        // assigned the network containing the port to one or more virtual
        // arrays and is now assigning specific ports to specific varrays. In
        // this case, other VPLEX ports could be implicitly connected to the
        // virtual arrays and will be subsequently assigned.
        URIQueryResultList queryResults = new URIQueryResultList();
        dbClient.queryByConstraint(AlternateIdConstraint.Factory
                .getAssignedVirtualArrayStoragePortsConstraint(varrayId), queryResults);
        Iterator<URI> resultsIter = queryResults.iterator();
        while (resultsIter.hasNext()) {
            StoragePort virtualArrayPort = dbClient.queryObject(StoragePort.class,
                    resultsIter.next());
            URI virtualArrayPortSystemURI = virtualArrayPort.getStorageDevice();
            _log.info("Storage port {} storage system is {}", virtualArrayPort.getId(), virtualArrayPortSystemURI);
            if (!virtualArrayPortSystemURI.equals(vplexSystemURI)) {
                continue;
            }

            _log.info("Storage ports are on the same VPLEX");
            String virtualArrayPortClusterId = getVplexClusterOfPort(virtualArrayPort);
            _log.info("Virtual array storage port VPLEX cluster id is {}", virtualArrayPortClusterId);
            if (!portClusterId.equals(virtualArrayPortClusterId)) {
                // The virtual array already contains a port from the other
                // cluster of the VPLEX, so this virtual array cannot be
                // assigned to this port.
                canBeAssigned = false;
                break;
            }
        }

        return canBeAssigned;
    }

    /**
     * Returns the Virtual Storage Array URIs for a given VPLEX system.
     *
     * @param dbClient -- Used by static method.
     * @param vplexSystemURI
     * @return List<URI> of Neighborhoods associated with this VPLEX.
     */
    public static List<URI> getVPlexSystemVarrays(DbClient dbClient, URI vplexSystemURI) {
        Set<String> vplexSystems = new HashSet<String>();
        vplexSystems.add(vplexSystemURI.toString());
        Map<String, List<String>> vplexToVarrays = getVPlexVarrays(dbClient, vplexSystems, null);
        List<URI> result = new ArrayList<URI>();
        List<String> varrays = vplexToVarrays.get(vplexSystemURI.toString());
        if (varrays != null) {
            for (String varray : varrays) {
                result.add(URI.create(varray));
            }
        }
        return result;
    }

    /**
     * Gets a list of the high availability Virtual Storage Arrays for the passed VPlex
     * storage systems.
     *
     * @param dbClient - Used to access DB by static method
     * @param vplexStorageSystemIds A set of VPlex storage system ids.
     * @param excludeVarray A varray to exclude from the list (or null to ignore).
     * @return A map of the high availability neighborhoods for each VPlex
     *         storage system.
     */
    public static Map<String, List<String>> getVPlexVarrays(
            DbClient dbClient, Set<String> vplexStorageSystemIds, URI excludeVarray) {
        // Initialize the map.
        Map<String, List<String>> vplexVarrayIdMap = new HashMap<String, List<String>>();

        // For all the requested storage systems...
        for (String vplexSystemId : vplexStorageSystemIds) {
            StringSet set = StoragePoolAssociationHelper.getVplexSystemConnectedVarrays(URI.create(vplexSystemId), dbClient);
            set.remove(excludeVarray);
            vplexVarrayIdMap.put(vplexSystemId, new ArrayList<String>(set));
        }
        return vplexVarrayIdMap;
    }

    /**
     * @see #getStorageSystemAssociationsByNetwork(DbClient, URI, com.emc.storageos.db.client.model.StoragePort.PortType, String)
     * @param dbClient
     * @param seedURI -- the StorageSystem we wish to find associations for
     * @param PortType (frontend or backend) to be matched against on seed array
     * @return Set<URI> -- StorageSystems of all types (VPlex/VNX/VMAX/etc.) sharing one or
     *         more network and virtual array with the Seed.
     */
    public static Set<URI> getStorageSystemAssociationsByNetwork(
            DbClient dbClient, URI seedURI, StoragePort.PortType seedPortType) {
        return getStorageSystemAssociationsByNetwork(dbClient, seedURI, seedPortType, null, null, null);
    }

    /**
     * Finds the associations of a seed storage system to others by determining they both have
     * StoragePorts in a common Network and virtual array. The method will not return an association to
     * the seed array. Chooses frontend or backend ports on associated systems as appropriate.
     *
     * @param dbClient
     * @param seedURI -- the StorageSystem we wish to find associations for
     * @param PortType (frontend or backend) to be matched against on seed array
     * @param systemType an optional filter when the caller wants a specific type of associated
     *            storage system, for example vplex. Null if all associated systems should be returned.
     * @param varrayUris an optional filter when the caller wants the system to be associated to the
     *            in specific varrays. Null if the association can be on any matched varray.
     * @param cluster an optional filter to limit if the caller wants the system to be associated
     *            in a specific vplex cluster
     * @return Set<URI> -- StorageSystems of all types (VPlex/VNX/VMAX/etc.) sharing one or
     *         more network and virtual array with the Seed.
     */
    public static Set<URI> getStorageSystemAssociationsByNetwork(
            DbClient dbClient, URI seedURI, StoragePort.PortType seedPortType,
            String systemType, Set<String> varrayUris, String vplexCluster) {
        _log.info("Checking storage system association for array {}", seedURI);
        // The results to be returned
        Set<URI> associatedSystemURIs = new HashSet<URI>();
        // A map for holding systems already retrieved from db to avoid unnecessary db hits
        Map<URI, StorageSystem> systemsMap = new HashMap<URI, StorageSystem>();

        // get all the ports in the storage system of the specified type, grouped by network
        Map<URI, List<StoragePort>> networkToPortMap = getStoragePortsOfType(dbClient, seedURI, seedPortType);
        // Add code here to include routed networks in the list
        // of networks for which we're looping
        for (URI networkUri : networkToPortMap.keySet()) {
            _log.info("Checking storage system association via network {}", networkUri);

            // get all the varrays for the seed storage system ports for this network
            StringSet networkVarrays = getStoragePortsVarrays(networkToPortMap.get(networkUri));

            // check some ports are in some varrays, or, when a specific varray is desired, check some ports are in it
            if (networkVarrays.isEmpty() || (varrayUris != null && Collections.disjoint(networkVarrays, varrayUris))) {
                _log.info("There are no varrays in this network or no varrays that matched the required ones.");
                continue;
            }

            // find the varrays that the ports should be in. If no varray is specified, match to any of the system varrays
            StringSet matchingVarrays = new StringSet();
            if (varrayUris != null) {
                matchingVarrays.addAll(varrayUris);
            } else {
                matchingVarrays.addAll(networkVarrays);
            }
            _log.info("Matching varrays in this network {}", matchingVarrays);
            // TODO - This is really expensive and I probably should look for other ways
            List<StoragePort> ports = NetworkAssociationHelper.
                    getNetworkConnectedStoragePorts(networkUri.toString(), dbClient);
            StorageSystem system = null;
            for (StoragePort port : ports) {
                if (port == null || port.getInactive() == true || port.getStorageDevice() == null) {
                    continue;
                }
                if (port.getStorageDevice().equals(seedURI)) {
                    continue;
                }
                if (associatedSystemURIs.contains(port.getStorageDevice())) {
                    continue;
                }
                if (!DiscoveredDataObject.CompatibilityStatus.COMPATIBLE.name()
                        .equals(port.getCompatibilityStatus())) {
                    continue;
                }
                if (false == port.getRegistrationStatus()
                        .equals(StoragePort.RegistrationStatus.REGISTERED.name())) {
                    continue;
                }
                if (!DiscoveryStatus.VISIBLE.name().equals(port.getDiscoveryStatus())) {
                    continue;
                }

                // Look the system in the saved systems map before querying the db
                if (systemsMap.containsKey(port.getStorageDevice())) {
                    system = systemsMap.get(port.getStorageDevice());
                } else {
                    system = dbClient.queryObject(
                            StorageSystem.class, port.getStorageDevice());
                    systemsMap.put(system.getId(), system);
                }

                // If a specified type of system is required, filter by it
                if (systemType == null || system.getSystemType().equals(systemType)) {
                    StoragePort.PortType portType = isAVPlex(system) ?
                            StoragePort.PortType.backend : StoragePort.PortType.frontend;
                    if (port.getPortType().equals(portType.toString()) &&
                            port.getTaggedVirtualArrays() != null &&
                            !Collections.disjoint(matchingVarrays, port.getTaggedVirtualArrays())) {
                        if (vplexCluster != null) {
                            if (vplexCluster.equals(getVplexClusterOfPort(port))) {
                                _log.info("Storage system {} is associated to vplex cluster {} via port {}",
                                        new Object[] { system.getNativeGuid(),
                                                vplexCluster, port.getNativeGuid() });
                                associatedSystemURIs.add(system.getId());
                            }
                        } else {
                            _log.info(String.format("Storage system %s is associated via port %s", system.getNativeGuid(),
                                    port.getNativeGuid()));
                            associatedSystemURIs.add(system.getId());
                        }
                    }
                }
            }
        }
        return associatedSystemURIs;
    }

    /**
     * Returns the set of virtual arrays the storage ports are in. This is the union
     * of all the ports' virtual arrays.
     *
     * @param storagePorts a list of storage ports.
     * @return the union of all the tagged virtual arrays of all the ports.
     */
    public static StringSet getStoragePortsVarrays(List<StoragePort> storagePorts) {
        StringSet varrays = new StringSet();
        for (StoragePort port : storagePorts) {
            if (port.getTaggedVirtualArrays() != null) {
                varrays.addAll(port.getTaggedVirtualArrays());
            }
        }
        return varrays;
    }

    /**
     * Given a storage array URI, find all the VPlex systems associated with the array. Vplex
     * systems are associated when the storage array can be a backend system of the vplex.
     * This requires a connectivity such that backend ports on the vplex and frontend ports
     * on the storage array are in the same network and virtual array. This condition is necessary
     * to create the backend export group between the storage array and vplex.
     *
     * @param dbClient and instance of dbClient.
     * @param arrayURI the storage array whose vplex associations are requested.
     * @return a list of vplex devices that can use the storage array to create vplex volumes.
     */
    public static Set<URI> getVPlexSystemsAssociatedWithArray(DbClient dbClient, URI arrayURI) {
        Set<URI> associations = getStorageSystemAssociationsByNetwork(dbClient,
                arrayURI, StoragePort.PortType.frontend, DiscoveredDataObject.Type.vplex.name(), null, null);
        return associations;
    }

    /**
     * Given a storage array URI, find all the VPlex systems associated with the array. Vplex
     * systems are associated when the storage array can be a backend system of the vplex.
     * This requires a connectivity such that backend ports on the vplex and frontend ports
     * on the storage array are in the same network and virtual array. This condition is necessary
     * to create the backend export group between the storage array and vplex.
     *
     * @param dbClient and instance of dbClient.
     * @param arrayURI the storage array whose vplex associations are requested.
     * @param varrayUris an optional filter to limit the results by vplexes connected to the storage
     *            array in specific varrays
     * @param cluster an optional filter to limit the results of vplexes connected to the storage
     *            array in a specific vplex cluster
     * @return a list of vplex devices that can use the storage array to create vplex volumes.
     */
    public static Set<URI> getVPlexSystemsAssociatedWithArray(DbClient dbClient, URI arrayURI, Set<String> varrayUris, String cluster) {
        Set<URI> associations = getStorageSystemAssociationsByNetwork(dbClient,
                arrayURI, StoragePort.PortType.frontend, DiscoveredDataObject.Type.vplex.name(), varrayUris, cluster);
        return associations;
    }

    /**
     * Retrieve all the storage ports of a given StorageSystem and type in a Map by Network URI.
     *
     * @param storage -- StorageSystem URI
     * @param type -- frontend or backend (note normal array ports are frontend).
     * @return Map<URI, List<StoragePort>> -- A map of the Network URI the Storage Ports in that Network
     */
    public static Map<URI, List<StoragePort>> getStoragePortsOfType(DbClient dbClient,
            URI storage, StoragePort.PortType type) {
        Map<URI, List<StoragePort>> tzURItoStoragePortsMap = new HashMap<URI, List<StoragePort>>();
        List<StoragePort> ports = getStoragePortsForSystem(dbClient, storage);
        for (StoragePort port : ports) {
            if (port.getPortType().equals(type.name())) {
                URI transportZoneURI = port.getNetwork();
                // Don't use the port if not assigned to a transport zone
                if (NullColumnValueGetter.isNullURI(transportZoneURI)) {
                    continue;
                }
                if (tzURItoStoragePortsMap.get(transportZoneURI) == null) {
                    tzURItoStoragePortsMap.put(transportZoneURI, new ArrayList<StoragePort>());
                }
                tzURItoStoragePortsMap.get(transportZoneURI).add(port);
            }
        }
        return tzURItoStoragePortsMap;
    }

    /**
     * Retrieve all the storage ports of a given StorageSystem and type in a Map by Network URI.
     *
     * @param storage -- StorageSystem URI
     * @param type -- frontend or backend (note normal array ports are frontend).
     * @param varrayURI -- URI of varray that must be in port's tagged varrays
     * @return Map<URI, List<StoragePort>> -- A map of the Network URI the Storage Ports in that Network
     */
    public static Map<URI, List<StoragePort>> getStoragePortsOfTypeAndVArray(
            DbClient dbClient, URI storage, StoragePort.PortType type, URI varrayURI) {
        String varray = varrayURI.toString();
        Map<URI, List<StoragePort>> netURIToStoragePortsMap =
                getStoragePortsOfType(dbClient, storage, type);

        // Filter out any ports that do not include the varray in their tagged list
        Set<URI> networks = new HashSet<URI>(netURIToStoragePortsMap.keySet());
        for (URI network : networks) {
            List<StoragePort> ports = netURIToStoragePortsMap.get(network);
            List<StoragePort> filteredPorts = new ArrayList<StoragePort>();
            for (StoragePort port : ports) {
                if (port.getTaggedVirtualArrays() != null
                        && port.getTaggedVirtualArrays().contains(varray)) {
                    filteredPorts.add(port);
                }
            }
            // Update the result map with the filtered ports, or
            // remove a network entry if no remaining ports in that network
            if (!filteredPorts.isEmpty()) {
                netURIToStoragePortsMap.put(network, filteredPorts);
            } else {
                netURIToStoragePortsMap.remove(network);
            }
        }
        return netURIToStoragePortsMap;
    }

    /**
     * Retrieve all the storage ports of a given StorageSystem and type.
     *
     * @param storage -- StorageSystem URI
     * @return List<StoragePort> -- A list of the the Storage Ports in that Network
     */
    public static List<StoragePort> getStoragePortsForSystem(DbClient dbClient, URI storage) {
        List<StoragePort> ports = new ArrayList<StoragePort>();
        URIQueryResultList storagePortURIs = new URIQueryResultList();
        dbClient.queryByConstraint(
                ContainmentConstraint.Factory.getStorageDeviceStoragePortConstraint(storage),
                storagePortURIs);
        Iterator<URI> storagePortsIter = storagePortURIs.iterator();
        while (storagePortsIter.hasNext()) {
            URI portURI = storagePortsIter.next();
            StoragePort port = dbClient.queryObject(StoragePort.class, portURI);
            if (port == null || port.getInactive() == true) {
                continue;
            }
            if (!DiscoveredDataObject.CompatibilityStatus.COMPATIBLE.name()
                    .equals(port.getCompatibilityStatus())) {
                continue;
            }
            if (false == port.getRegistrationStatus()
                    .equals(StoragePort.RegistrationStatus.REGISTERED.name())) {
                continue;
            }
            if (!DiscoveryStatus.VISIBLE.name().equals(port.getDiscoveryStatus())) {
                continue;
            }

            ports.add(port);
        }
        return ports;
    }

    /**
     * Get all protection systems associated with an array.
     *
     * @param dbClient - db client
     * @param storageSystem - storage array
     * @return list of URIs corresponding to rp systems
     */
    public static Set<URI> getProtectionSystemsAssociatedWithArray(
            DbClient dbClient, URI storageSystem) {
        Set<URI> rpSystemIds = new HashSet<URI>();

        // Get the Source Storage System in question
        StorageSystem sourceStorageSystem = dbClient.queryObject(
                StorageSystem.class, storageSystem);

        // Get all the RPSiteArrays associated to this Storage System
        URIQueryResultList sitelist = new URIQueryResultList();
        dbClient.queryByConstraint(AlternateIdConstraint.Factory
                .getRPSiteArrayByStorageSystemConstraint(storageSystem.toString()), sitelist);

        Iterator<URI> it = sitelist.iterator();
        while (it.hasNext()) {
            URI rpSiteArrayId = it.next();
            RPSiteArray rpSiteArray = dbClient.queryObject(RPSiteArray.class, rpSiteArrayId);

            if (rpSiteArray != null) {
                // Find source RPSiteArrays that qualify and get the Protection System
                if (sourceStorageSystem.getId().equals(
                        rpSiteArray.getStorageSystem())) {
                    rpSystemIds.add(rpSiteArray.getRpProtectionSystem());
                }
            }
        }
        return rpSystemIds;
    }

    /**
     * Gets a list of the RP Protection systems for the passed pool.
     *
     * @param dbClient Reference to a DB client
     * @param storagePool Reference to a storage pool
     * @param varrayId Optional, filter by varray
     * @param isRPVPlex Optional, specifies whether or not this is an RP+VPLEX/MetroPoint request
     *
     * @return A list of the RP protection systems.
     */
    public static Set<ProtectionSystem> getProtectionSystemsForStoragePool(DbClient dbClient, StoragePool storagePool,
            URI varrayId, boolean isRPVPlex) {
        Set<ProtectionSystem> rpSystems = new HashSet<ProtectionSystem>();
        Set<ProtectionSystem> rpSystemsWithIsolatedVarrayEntry = new HashSet<ProtectionSystem>();
        List<String> isolatedRPSites = new ArrayList<String>();
        List<String> storageSystems = new ArrayList<String>();

        _log.info(String.format("Find Protection Systems using the Storage System of Storage pool [%s]", storagePool.getLabel()));

        // If this is a RP+VPLEX/MetroPoint request, we need only consider Protection Systems associated to
        // the VPLEX.
        if (isRPVPlex) {
            _log.info(String.format("RP+VPlex/MetroPoint - only consider Protection Systems associated to the VPLEX."));

            // Get the VPLEXs associated with the Storage Pool
            List<String> vplexSystemsForPool =
                    VPlexHighAvailabilityMatcher.getVPlexStorageSystemsForStorageSystem(dbClient, storagePool.getStorageDevice(), null);

            // Loop through all the VPLEXs
            for (String vplexId : vplexSystemsForPool) {
                if (varrayId != null) {
                    // Find the VPLEX(s) that are associated to this varray
                    List<URI> vplexVarrays = ConnectivityUtil
                            .getVPlexSystemVarrays(dbClient, URI.create(vplexId));
                    if (vplexVarrays.contains(varrayId)) {
                        _log.info(String.format("Candidate VPLEX Storage System [%s]", vplexId));
                        storageSystems.add(vplexId);
                    }
                }
                else {
                    _log.info(String.format("Candidate VPLEX Storage System [%s]", vplexId));
                    storageSystems.add(vplexId);
                }
            }
        }
        else {
            _log.info(String.format("Candidate Storage System [%s]", storagePool.getStorageDevice().toASCIIString()));
            storageSystems.add(storagePool.getStorageDevice().toASCIIString());
        }

        // Find all the Protection Systems for these Storage Systems
        for (String storageSystemId : storageSystems) {
            Set<URI> rpSystemURIs = ConnectivityUtil.
                    getProtectionSystemsAssociatedWithArray(dbClient, URI.create(storageSystemId));
            for (URI uri : rpSystemURIs) {
                ProtectionSystem ps = dbClient.queryObject(ProtectionSystem.class, uri);

                // Make sure the ProtectionSystem is active
                if (ps != null && !ps.getInactive()) {
                    // We could be isolating the varray in question to specific RPA clusters/sites.
                    // If there is an entry for this varray in the siteAssignedVirtualArrays field
                    // we need to honour that isolation and only return Protection Systems
                    // with an entry for that varray.
                    boolean varrayHasBeenIsolated = false;
                    if (varrayId != null) {
                        if (ps.getSiteAssignedVirtualArrays() != null
                                && !ps.getSiteAssignedVirtualArrays().isEmpty()) {

                            // Loop over all entries to see if this Protection System has an entry for this varray to be isolated
                            for (Map.Entry<String, AbstractChangeTrackingSet<String>> entry : ps.getSiteAssignedVirtualArrays().entrySet()) {
                                // Check to see if this entry contains the varray
                                if (entry.getValue().contains(varrayId.toString())) {
                                    // This varray has been isolated to a RP cluster/site
                                    varrayHasBeenIsolated = true;
                                    isolatedRPSites.add(entry.getKey());
                                    break;
                                }
                            }
                        }
                    }

                    _log.info(String.format("Found Protection System [%s]", ps.getLabel()));
                    if (varrayHasBeenIsolated) {
                        rpSystemsWithIsolatedVarrayEntry.add(ps);
                    }
                    else {
                        rpSystems.add(ps);
                    }
                } else {
                    _log.info(String.format("Excluding ProtectionSystem %s because it is inactive or invalid.", uri));
                }
            }
        }

        // If we have any RP Protection Systems with an entry indicating that we are isolating this varray,
        // then we can only return Protection Systems that have an isolation entry.
        // The default with no isolation entries, is to return all Protection Systems that have
        // connectivity.
        if (!rpSystemsWithIsolatedVarrayEntry.isEmpty()) {
            // Only use RP Systems that have an entry for this varray,
            // we will ignore the others.
            rpSystems = rpSystemsWithIsolatedVarrayEntry;

            StringBuffer logMsg = new StringBuffer();
            logMsg.append(String.format("Varray [%s] has been isolated to these RP Sites: %s %n",
                    varrayId.toString(), Joiner.on(',').join(isolatedRPSites)));
            logMsg.append("Therefore only these Protection Systems can be used: ");
            for (ProtectionSystem ps : rpSystems) {
                logMsg.append(ps.getLabel() + " ");
            }

            _log.info(logMsg.toString());
        }

        return rpSystems;
    }

    /**
     * Get all of the virtual arrays associated with the RP system. RP System ->
     * Storage System -> Storage Pools -> Virtual Arrays
     *
     * @param dbClient - db client
     * @param rpSystemId - URI of RP system
     * @return list of virtual array URIs
     */
    public static List<URI> getRPSystemVirtualArrays(DbClient dbClient,
            URI rpSystemId) {
        Set<URI> virtualArrayIdSet = new HashSet<URI>();
        List<URI> virtualArrayIdList = new ArrayList<URI>();

        // Get the rp system's array mappings from the RP client
        URIQueryResultList sitelist = new URIQueryResultList();
        dbClient.queryByConstraint(AlternateIdConstraint.Factory
                .getRPSiteArrayProtectionSystemConstraint(rpSystemId.toString()), sitelist);

        Iterator<URI> it = sitelist.iterator();
        while (it.hasNext()) {
            URI rpSiteArrayId = it.next();
            RPSiteArray siteArray = dbClient.queryObject(RPSiteArray.class, rpSiteArrayId);

            virtualArrayIdSet.addAll(findAllVirtualArraysForRPSiteArray(dbClient, siteArray));            
        }

        // Convert to a list
        virtualArrayIdList.addAll(virtualArrayIdSet);
        return virtualArrayIdList;
    }

    /**
     * Find all the associated VSA URIs for the passed in RPSiteArray
     *
     * @param dbClient
     * @param siteArray
     * @return all the URIs of the associated VSAs
     */
    private static Set<URI> findAllVirtualArraysForRPSiteArray(
            DbClient dbClient, RPSiteArray siteArray) {

        Set<URI> ids = new HashSet<URI>();

        if (siteArray != null) {
            // Find all the Storage Pools associated to this RPSiteArray
            URIQueryResultList storagePoolURIs = new URIQueryResultList();
            dbClient.queryByConstraint(ContainmentConstraint.Factory
                    .getStorageDeviceStoragePoolConstraint(siteArray
                            .getStorageSystem()), storagePoolURIs);

            Iterator<URI> storagePoolIter = storagePoolURIs.iterator();
            while (storagePoolIter.hasNext()) {
                URI storagePoolURI = storagePoolIter.next();
                StoragePool storagePool = dbClient.queryObject(
                        StoragePool.class, storagePoolURI);

                // For each Storage Pool get all the connected VSAs
                if (storagePool != null && !storagePool.getInactive()
                        && storagePool.getConnectedVirtualArrays() != null) {
                    for (String vArrayId : storagePool
                            .getConnectedVirtualArrays()) {
                        ids.add(URI.create(vArrayId));
                    }
                }
            }
            
            // If the rpsite array storage system is vplex check virtual array
            // connectivity to rpsite using front end storage ports
            StorageSystem storageSystem = dbClient.queryObject(StorageSystem.class, siteArray.getStorageSystem());    	
            if (storageSystem != null && isAVPlex(storageSystem)) {            	
            	List<StoragePort> storagePorts = getStoragePortsForSystem(dbClient, storageSystem.getId());    		    		
            	for (StoragePort storagePort : storagePorts) {
            		// For each Storage Port get all the connected VSAs
            		if (storagePort != null && 
            				!storagePort.getInactive() &&
            				storagePort.getPortType() != null &&
            				storagePort.getPortType().equalsIgnoreCase(StoragePort.PortType.frontend.toString())) { 

            			if (storagePort.getConnectedVirtualArrays() != null) {
            				for (String vArrayId : storagePort.getConnectedVirtualArrays()) {
            					if (hasAssociatedBackendStorage(dbClient, storageSystem.getId(), vArrayId)) {
            						_log.info(String.format("Vplex System [%s] has connectvity to RP Site [%s]", storageSystem.getLabel(), siteArray.getRpSiteName()));
            						ids.add(URI.create(vArrayId));
            					}
            				}
            			}

            			if (storagePort.getAssignedVirtualArrays() != null) {
            				for (String vArrayId : storagePort.getAssignedVirtualArrays()) {            						
            					if (hasAssociatedBackendStorage(dbClient, storageSystem.getId(), vArrayId)) {
            						_log.info(String.format("Vplex System [%s] has connectvity to RP Site [%s]", storageSystem.getLabel(), siteArray.getRpSiteName()));
            						ids.add(URI.create(vArrayId));
            					}
            				}
            			}
            		}     			    			
            	}
            }                       
        }

        return ids;
    }
    
    /**
     * Check if the vplex has associated backend arrays within the virtual array
     * 
     * @param dbClient - db client
     * @param vplexURI - URI of vplex being checked for associated backend arrays
     * @param vArrayId - URI of virtual array in check
     * @return boolean indicating if the vplex has associated backend arrays within the virtual array
     */
    private static boolean hasAssociatedBackendStorage(DbClient dbClient, URI vplexURI, String vArrayId) {
    	StringSet connVA = new StringSet();
        connVA.add(vArrayId);
		Set<URI> associations = getStorageSystemAssociationsByNetwork(dbClient,
				vplexURI, StoragePort.PortType.backend, null, connVA, null);
		if (associations != null && ! associations.isEmpty()) {			
			return true;
		}
    	return false;
    }
    
    /**
     * Get all of the storage pools associated with the RP System
     *
     * @param dbClient db client
     * @param rpSystemId URI of the RP system
     * @return list of storage pool URIs
     */
    public static List<URI> getRPSystemStoragePools(DbClient dbClient, URI rpSystemId) {
        List<URI> poolIds = new ArrayList<URI>();

        // Get the rp system's array mappings from the RP client
        URIQueryResultList sitelist = new URIQueryResultList();
        dbClient.queryByConstraint(AlternateIdConstraint.Factory
                .getRPSiteArrayProtectionSystemConstraint(rpSystemId.toString()), sitelist);

        Iterator<URI> it = sitelist.iterator();

        while (it.hasNext()) {
            URI rpSiteArrayId = it.next();
            RPSiteArray siteArray = dbClient.queryObject(RPSiteArray.class, rpSiteArrayId);

            if (siteArray != null && !siteArray.getInactive()) {

                // Find all the Storage Pools associated to this RPSiteArray
                URIQueryResultList storagePoolURIs = new URIQueryResultList();
                dbClient.queryByConstraint(ContainmentConstraint.Factory
                        .getStorageDeviceStoragePoolConstraint(siteArray
                                .getStorageSystem()), storagePoolURIs);

                Iterator<URI> storagePoolIter = storagePoolURIs.iterator();
                while (storagePoolIter.hasNext()) {
                    URI storagePoolURI = storagePoolIter.next();
                    poolIds.add(storagePoolURI);
                }
            }
        }

        return poolIds;
    }

    /**
     * Refreshes the connected virtual arrays for the Recover Point (RP) systems that have sites for the storage systems.
     * This function:
     * <ol>
     * <li>Finds all the RP systems that have sites in one or more of the storage systems</li>
     * <li>for each RP system, find all the virtual arrays for all their connected storage systems' pools.</li>
     * </ol>
     *
     * @param storageSystemUris the storage systems that have some change in their varray associations
     * @param dbClient an instance of {@link DbClient}
     */
    public static void updateRpSystemsConnectivity(Collection<URI> storageSystemUris, DbClient dbClient) {

        if (storageSystemUris.isEmpty()) {
            return;
        }

        List<URI> connectedRPSystems = getConnectedRPSystems(storageSystemUris, dbClient);
        List<ProtectionSystem> rpSystems = dbClient.queryObject(ProtectionSystem.class, connectedRPSystems);
        for (ProtectionSystem rpSystem : rpSystems) {
            updateRpSystemConnectivity(rpSystem, dbClient);
        }
    }

    /**
     * Refreshes the list of connected varrays for an RP system.
     *
     * @param rpSystem
     */
    public static void updateRpSystemConnectivity(ProtectionSystem rpSystem, DbClient dbClient) {
        if (rpSystem.getInactive()) {
            return;
        }
        if (rpSystem.getVirtualArrays() == null) {
            rpSystem.setVirtualArrays(new StringSet());
        }
        rpSystem.getVirtualArrays().replace(
                StringSetUtil.uriListToSet(getRPSystemVirtualArrays(dbClient, rpSystem.getId())));
        dbClient.updateAndReindexObject(rpSystem);
    }

    /**
     * Queries and return a list of RP systems that have sites in one or more of the storage systems
     *
     * @param storageSystemUris a list of storage systems URIs
     * @param dbClient an instance of {@link DbClient}
     * @return a list of RP systems that have sites for in one or more of the storage systems
     */
    private static List<URI> getConnectedRPSystems(Collection<URI> storageSystemUris, DbClient dbClient) {
        List<URI> rpSystems = new ArrayList<URI>();
        for (URI storageSystemUri : storageSystemUris) {
            List<RPSiteArray> sites = CustomQueryUtility.queryActiveResourcesByRelation(dbClient,
                    storageSystemUri, RPSiteArray.class, "storageSystem");
            for (RPSiteArray site : sites) {
                if (!rpSystems.contains(site.getRpProtectionSystem())) {
                    rpSystems.add(site.getRpProtectionSystem());
                }
            }
        }
        return rpSystems;
    }

    /**
     * Returns all the varrays the initiator can have exports in. An initiator can have exports
     * to all storage ports that are in its network and all those that are in a network
     * that is routed to the initiator network. The list varrays is all the varrays containing
     * one or more such ports
     *
     * @param initiatorId the initiator port WWN
     * @param dbClient an instance of DbClient
     * @return all the varrays the initiator can have exports in.
     */
    public static Set<String> getInitiatorVarrays(String initiatorId, DbClient dbClient) {
        Set<String> varrayIds = new HashSet<String>();
        Set<NetworkLite> networks = NetworkUtil.getEndpointAllNetworksLite(initiatorId, dbClient);
        for (NetworkLite network : networks) {
            if (network.registered()) {
                varrayIds.addAll(network.fetchAllVirtualArrays());
            }
        }
        return varrayIds;
    }

    /**
     * Checks the connectivity between a network and a group of networks.
     * If the network is either in the collection or is routed to a network
     * in the collection, this method returns true
     *
     * @param networkUri the network being checked for connectivity
     * @param networkUris the networks being checked
     * @param dbClient an instance of dbClient
     * @return true the network is either in the collection or is routed to a network
     *         in the collection
     */
    public static boolean checkNetworkConnectedToAtLeastOneNetwork(URI networkUri, Collection<URI> networkUris, DbClient dbClient) {
        if (NullColumnValueGetter.isNullURI(networkUri) == false) {
            NetworkLite networkLite = NetworkUtil.getNetworkLite(networkUri, dbClient);
            if (networkLite != null) {
                return networkLite.connectedToAtLeastOneNetwork(networkUris);
            }
        }
        return false;
    }

    /**
     * Returns cluster1 or cluster2 according to which cluster a VPLEX StoragePort
     * is in. Only works for VPLEX ports.
     *
     * @param vplexPort StoragePort
     * @return "1" or "2". Returns "unknown-cluster" if error.
     *
     *         TODO: Move this method to VPlexUtil
     */
    public static String getVplexClusterOfPort(StoragePort vplexPort) {
        // after director- in this string determines vplex cluster
        if (vplexPort.getPortGroup() != null) {
            String[] tokens = vplexPort.getPortGroup().split("-");
            if (CLUSTER1.equals(tokens[1])) {
                return CLUSTER1;
            } else if (CLUSTER2.equals(tokens[1])) {
                return CLUSTER2;
            } else {
                _log.warn("Could not determine cluster for storageport:"
                        + vplexPort.getPortNetworkId() + " "
                        + vplexPort.getId() + " Port group is:"
                        + vplexPort.getPortGroup());
            }
        } else {
            _log.warn("Could not determine cluster for storageport:"
                    + vplexPort.getPortNetworkId() + " " + vplexPort.getId());
        }
        return CLUSTER_UNKNOWN;
    }

    /**
     * This method returns the VPLEX cluster information for the virtual array. The assumption here is that the passed
     * varrayURI will not have ports from both VPLEX clusters. This is true when its called for the varray
     * which has VPLEX volume created on it. Until VPLEX volume create is attempted on the varray we cannot use this
     * method as user can just assign the network and varray could get ports from both the VPLEX clusters
     *
     * @param varrayURI The URI of the virtaul array
     * @param vplexStorageSystemURI The URI of the VPLEX storage system
     * @param dbClient dbclient
     * @return "1" or "2". Returns "unknown-cluster" if error.
     *
     *         TODO: Move this method to VPlexUtil
     */
    public static String getVplexClusterForVarray(URI varrayURI, URI vplexStorageSystemURI, DbClient dbClient) {
        String vplexCluster = CLUSTER_UNKNOWN;
        URIQueryResultList storagePortURIs = new URIQueryResultList();
        dbClient
                .queryByConstraint(AlternateIdConstraint.Factory
                        .getVirtualArrayStoragePortsConstraint(varrayURI.toString()),
                        storagePortURIs);
        for (URI uri : storagePortURIs) {
            StoragePort storagePort = dbClient.queryObject(StoragePort.class, uri);
            if ((storagePort != null)
                    && DiscoveredDataObject.CompatibilityStatus.COMPATIBLE.name().equals(
                            storagePort.getCompatibilityStatus())
                    && (RegistrationStatus.REGISTERED.toString().equals(storagePort
                            .getRegistrationStatus()))
                    && DiscoveryStatus.VISIBLE.toString().equals(storagePort.getDiscoveryStatus())) {
                if (storagePort.getStorageDevice().equals(vplexStorageSystemURI)) {
                    // Assumption is this varray cannot have mix of CLuster 1
                    // and Cluster 2 ports from VPLEX so getting
                    // cluster information from one of the VPLEX port should
                    // work.
                    vplexCluster = getVplexClusterOfPort(storagePort);
                    break;
                }
            }
        }
        return vplexCluster;
    }

    /**
     * Given an initiator and StorageSystem object, find if the initiator is connect to the StorageSystem
     * through one of its network associations. Optionally checks that the port is in one of the specified varrays.
     *
     * @param initiator [in] Initiator object representing the initiator for which we would like
     *            to check the connection.
     * @param storageSystem [in] StorageSystem object representing the array we want to check the connection to.
     * @param varrayURIs [in] [optional] If not null, will check that the ports tagged varrays intersect the passed varrays
     * @param dbClient [in] DbClient
     * @return true - iff, there is at least one network found that is shared by the initiator and one of the
     *         StorageSystems StoragePorts.
     */
    public static boolean isInitiatorConnectedToStorageSystem(Initiator initiator, StorageSystem storageSystem,
            List<URI> varrayURIs, DbClient dbClient) {
        Set<String> varrays = new HashSet<String>();
        if (varrayURIs != null) {
            for (URI varray : varrayURIs) {
                varrays.add(varray.toString());
            }
        }
        if (initiator == null || storageSystem == null || dbClient == null) {
            _log.info(String.format("isInitiatorConnectedToStorageSystem - Invalid parameters"));
            return false;
        }
        _log.info(String.format("isInitiatorConnectedToStorageSystem(%s, %s) -- Entered",
                initiator.getInitiatorPort(), storageSystem.getNativeGuid()));
        NetworkLite networkLite = NetworkUtil.getEndpointNetworkLite(initiator.getInitiatorPort(), dbClient);
        if (networkLite == null) {
            _log.info(String.format("isInitiatorConnectedToStorageSystem(%s, %s) -- Initiator is not associated with any network",
                    initiator.getInitiatorPort(), storageSystem.getNativeGuid()));
            return false;
        }
        URI networkUri = networkLite.getId();
        List<StoragePort> ports = NetworkAssociationHelper.
                getNetworkConnectedStoragePorts(networkUri.toString(), dbClient);
        _log.info(String.format("isInitiatorConnectedToStorageSystem(%s, %s) -- Checking for port connections on %s network",
                initiator.getInitiatorPort(), storageSystem.getNativeGuid(), networkLite.getLabel()));
        for (StoragePort port : ports) {
            if (storageSystem.getId().equals(port.getStorageDevice())) {
                if (varrays.isEmpty() ||
                        (port.getTaggedVirtualArrays() != null && !Collections.disjoint(varrays, port.getTaggedVirtualArrays()))) {
                    _log.info(String
                            .format("isInitiatorConnectedToStorageSystem(%s, %s) -- Found one port in the same network as initiator, %s (%s). Returning true.",
                                    initiator.getInitiatorPort(), storageSystem.getNativeGuid(), port.getNativeGuid(),
                                    port.getId()));
                    return true;
                }
            }
        }
        _log.info(String
                .format("isInitiatorConnectedToStorageSystem(%s, %s) -- Could not find any ports in the same networks as the initiator. Returning false.",
                        initiator.getInitiatorPort(), storageSystem.getNativeGuid()));
        return false;
    }

    /**
     * Find a storage system based on its serial number. Keep in mind, we may
     * have 2 storage systems with the same serial number if both block and file
     * are registered. Use the fileOnly param to distinguish between the
     * system type that's wanted.
     *
     * @param serialNumber
     *            StorageSystem serial number
     * @param dbClient
     *            DB Client
     * @param fileOnly
     *            true if only file storage systems should be considered,
     *            false otherwise.
     * @return The storage system URI found or null if none found
     */
    public static URI findStorageSystemBySerialNumber(String serialNumber,
            DbClient dbClient, StorageSystemType systemType) {
        URI foundStorageSystemURI = null;

        // Find the storage system ID associated with this serial number
        URIQueryResultList result = new URIQueryResultList();
        dbClient.queryByConstraint(AlternateIdConstraint.Factory
                .getStorageDeviceSerialNumberConstraint(serialNumber), result);

        if (result != null && result.iterator() != null && result.iterator().hasNext()) {
            Iterator<URI> resultItr = result.iterator();
            while (resultItr.hasNext()) {
                foundStorageSystemURI = resultItr.next();
                if (foundStorageSystemURI != null) {
                    StorageSystem storageSystem = dbClient.queryObject(StorageSystem.class, foundStorageSystemURI);
                    boolean isFileTypeSystem = SystemType.isFileTypeSystem(storageSystem.getSystemType());
                    if ((systemType == StorageSystemType.BLOCK && !isFileTypeSystem) ||
                            (systemType == StorageSystemType.FILE && isFileTypeSystem)) {
                        // If file system types only has been specified and this is a file system type then
                        // use that. Otherwise, if non-file system types has been specified and this is
                        // not a file system type then use that.
                        break;
                    }
                }
            }
        }

        // Sometimes we have a serial number from the RP appliances, and for the most part, that works
        // with a Constraint Query, but in the case of VPLEX, the serial number object for distributed
        // VPLEX clusters will contain two serial numbers, not just one. So we need a long-form
        // way of finding those VPLEXs as well.
        if (foundStorageSystemURI == null) {
            // Get all the Storage System IDs
            List<URI> storageSystemIDs = dbClient.queryByType(StorageSystem.class, true);

            // Get all the Storage System objects loaded
            List<StorageSystem> storageSystems = null;
            if (storageSystemIDs != null) {
                storageSystems = dbClient.queryObject(StorageSystem.class,
                        storageSystemIDs);
            }

            if (storageSystems != null && !storageSystems.isEmpty()) {
                for (StorageSystem storageSystem : storageSystems) {
                    if (NullColumnValueGetter.isNotNullValue(storageSystem.getSerialNumber())
                            && storageSystem.getSerialNumber().contains(serialNumber)) {
                        foundStorageSystemURI = storageSystem.getId();
                        break;
                    }
                }
            }
        }

        return foundStorageSystemURI;
    }
}
