/*
 * Copyright (c) 2008-2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.networkcontroller.impl;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.storageos.customconfigcontroller.CustomConfigConstants;
import com.emc.storageos.customconfigcontroller.DataSource;
import com.emc.storageos.customconfigcontroller.DataSourceFactory;
import com.emc.storageos.customconfigcontroller.impl.CustomConfigHandler;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.FCEndpoint;
import com.emc.storageos.db.client.model.FCZoneReference;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.NetworkSystem;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StorageProtocol;
import com.emc.storageos.db.client.model.DiscoveredDataObject.DataCollectionJobStatus;
import com.emc.storageos.db.client.model.DiscoveredDataObject.RegistrationStatus;
import com.emc.storageos.db.client.model.StorageProtocol.Transport;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.StringSetMap;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.util.DataObjectUtils;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.db.client.util.StringSetUtil;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.networkcontroller.NetworkFCZoneInfo;
import com.emc.storageos.networkcontroller.exceptions.NetworkDeviceControllerException;
import com.emc.storageos.networkcontroller.impl.mds.Zone;
import com.emc.storageos.networkcontroller.impl.mds.ZoneMember;
import com.emc.storageos.util.ConnectivityUtil;
import com.emc.storageos.util.NetworkLite;
import com.emc.storageos.util.NetworkUtil;
import com.emc.storageos.util.VPlexUtil;
import com.emc.storageos.volumecontroller.impl.utils.ExportMaskUtils;
import com.emc.storageos.volumecontroller.placement.BlockStorageScheduler;

/**
 * NetworkScheduler service for FC connections. Zoning is done based on the host
 * and storage volume port numbers.
 */
public class NetworkScheduler {
    protected static final Logger _log = LoggerFactory.getLogger(NetworkScheduler.class);
    @Autowired
    private DataSourceFactory dataSourceFactory;
    @Autowired
    private CustomConfigHandler customConfigHandler;

    private DbClient _dbClient;
    private static final String LSAN = "LSAN_";
    private static final int ZONE_NAME_LENGTH = 64;
    private static final int BROCADE_ZONE_NAME_IVR_LENGTH = 59;

    public void setDbClient(DbClient dbClient) {
        _dbClient = dbClient;
    }

    /**
     * Gets the network lite for a storage port when the port is in a
     * network that is active and registered. Returns null otherwise.
     * 
     * @param port the storage port
     * @return the network lite for a storage port when the port is in a
     *         network that is active and registered. Returns null otherwise.
     */
    public NetworkLite getStoragePortNetwork(StoragePort port) {
        if (NullColumnValueGetter.isNullURI(port.getNetwork())) {
            _log.info("Port {} is not in a network", port.getPortNetworkId());
        }
        NetworkLite portNetworkLite = NetworkUtil.getNetworkLite(port.getNetwork(), _dbClient);
        if (portNetworkLite == null || portNetworkLite.getInactive()) {
            _log.info("Port {} network cannot be found or is decativated", port.getPortNetworkId());
        } else if (!portNetworkLite.registered()) {
            _log.info("Port {} network {} is deregistered", port.getPortNetworkId(),
                    portNetworkLite.getNativeGuid());
        } else {
            return portNetworkLite;
        }
        return null;
    }

    /**
     * Assign a name to a zone using the current zone name configuration.
     * The name is stored in the fabricInfo.
     * The system-default name is composed of the following fields separated by underscores:
     * 1. The prefix "SDS"
     * 2. Hostname (maximum 32 characters)
     * 3. The last twelve characters of the WWPN of the Initiator (without colons, upper case)
     * 4. The last four digits of the Storage Array serial number.
     * 5. The Storage Array Port Name (maximum 9 characters) (nothing but alpha-numeric characters).
     * 
     * @param fabricInfo -- Contextual object for the operation. Contains the endpoints.
     * @param hostName -- The host name for the given initiator.
     * @param port -- The StoragePort used to find the StorageArray and get the PortName
     * @param lsanZone -- a flag that indicates if the zone to be created is an LSAN zone
     */
    private void nameZone(NetworkFCZoneInfo fabricInfo, String systemType, String hostName,
            String initiatorport, StoragePort port, boolean lsanZone) {
        // use 1st two endpoints in name
        if (fabricInfo.getEndPoints().size() < 2) {
            throw NetworkDeviceControllerException.exceptions.nameZoneNotEnoughEndPoints();
        }
        // Use the StoragePort to find the StorageSystem
        URI arrayUri = port.getStorageDevice();
        StorageSystem array = _dbClient.queryObject(StorageSystem.class, arrayUri);
        if (array == null) {
            throw NetworkDeviceControllerException.exceptions.portStorageDeviceNotFound(
                    port.getStorageDevice().toString(), port.getLabel());
        }

        Initiator initiator = NetworkUtil.findInitiatorInDB(initiatorport, _dbClient);
        DataSource dataSource = dataSourceFactory.createZoneNameDataSource(hostName,
                initiator, port, fabricInfo.getFabricId(), array);
        if (array.getSystemType().equals(DiscoveredDataObject.Type.vplex.name())) {
            dataSource.addProperty(CustomConfigConstants.ARRAY_PORT_NAME,
                    getVPlexPortName(port));
            dataSource.addProperty(CustomConfigConstants.ARRAY_SERIAL_NUMBER,
                    getVPlexClusterSerialNumber(port));
        }
        String resolvedZoneName = customConfigHandler.resolve(
                CustomConfigConstants.ZONE_MASK_NAME, systemType, dataSource);
        validateZoneNameLength(resolvedZoneName, lsanZone, systemType);
        String zoneName = customConfigHandler.getComputedCustomConfigValue(
                CustomConfigConstants.ZONE_MASK_NAME, systemType, dataSource);
        if (lsanZone && DiscoveredDataObject.Type.brocade.name().equals(systemType)) {
            zoneName = LSAN + zoneName;
        }
        fabricInfo.setZoneName(zoneName);
    }

    /**
     * Validates if zone name length is within the allowed character limit on switches.
     */
    private void validateZoneNameLength(String zoneName, boolean isIvrZone, String systemType) {
        // Checks for a different length for IVR zones as it should start with "LSAN" for brocade which is appended to zone name later
        if(isIvrZone && DiscoveredDataObject.Type.brocade.name().equals(systemType)) {
            if(zoneName.length() > BROCADE_ZONE_NAME_IVR_LENGTH) {
                throw NetworkDeviceControllerException.exceptions.nameZoneLongerThanAllowed(zoneName, BROCADE_ZONE_NAME_IVR_LENGTH);
            }
        } else {
            if(zoneName.length() > ZONE_NAME_LENGTH) {
                throw NetworkDeviceControllerException.exceptions.nameZoneLongerThanAllowed(zoneName, ZONE_NAME_LENGTH);
            }
        }
    }

    /**
     * Add the director digits to make the vplex port name unique within a vplex system
     * 
     * @param port StoragePort
     * @param system StorageSystem
     * @return nine character maximum string generated from director and port fields
     */
    private String getVPlexPortName(StoragePort port) {
        String directorDigits = port.getPortGroup().substring(port.getPortGroup().indexOf("-") + 1,
                port.getPortGroup().lastIndexOf("-"));
        return directorDigits + port.getPortName();
    }

    /**
     * For the passed VPLEX port, determines the VPLEX cluster for the port
     * and then returns the serial number for that cluster. Called when
     * configuring the zone name for a VPLEX port.
     * 
     * @param port A reference to a VPLEX port.
     * 
     * @return The serial number for the port's cluster.
     */
    private String getVPlexClusterSerialNumber(StoragePort port) {
        URI systemURI = port.getStorageDevice();
        StorageSystem vplexSystem = _dbClient.queryObject(StorageSystem.class, systemURI);
        String portClusterId = ConnectivityUtil.getVplexClusterOfPort(port);
        return VPlexUtil.getVPlexClusterSerialNumber(portClusterId, vplexSystem);
    }

    /**
     * Select the network device and VSAN or Brocade Fabric for the host/volume zoning.
     * 
     * The selection is based on the end points (initiator and storage port) and
     * the availability of a network device that has ports connections to both
     * end points. If a fabric can't be identified with both endpoints, we fall back to
     * looking for a fabric with at least the storagePort discovered.
     * 
     * @param exportGroupUri Export Group URI
     * @param varrayUri Virtual Array URI
     * @param protocol String (FC for this to do anything)
     * @param initiatorPort The WWN of the initiator
     * @param storagePort The StoragePort object
     * @param hostName Used for generating the zone name.
     * @param existingZones a list of existing zones for the initiator
     * @param checkZones Flag to enable or disable zoning check on a Network System
     * 
     * @return NetworkFabricInfo configured for adding zones
     */
    private NetworkFCZoneInfo placeZones(URI exportGroupUri, URI varrayUri, String protocol,
            String initiatorPort, StoragePort storagePort, String hostName,
            List<Zone> existingZones, boolean checkZones) throws DeviceControllerException {
        initiatorPort = formatWWN(initiatorPort);
        String storagePortWwn = formatWWN(storagePort.getPortNetworkId());
        if (Transport.FC != StorageProtocol.block2Transport(protocol)) {
            return null;
        }

        _log.info("Placing a zone for initiator {} and port {}", initiatorPort, storagePortWwn);

        // do some validation
        NetworkLite iniNet = NetworkUtil.getEndpointNetworkLite(initiatorPort, _dbClient);
        NetworkLite portNet = getStoragePortNetwork(storagePort);
        if (iniNet == null || portNet == null ||
                !NetworkUtil.checkInitiatorAndPortConnected(iniNet, portNet)) {
            _log.debug(String.format(
                    "Initiator %s could not be paired with port %s",
                    initiatorPort, storagePortWwn));
            return null;
        }

        // Check whether to check zoning on the Network System
        // If True, we will check zoning on the Network System
        // False we will use the existing FCZoneReference info
        if (!checkZones) {
            _log.debug("Check Zones flag is false. Finding FCZoneReference for initiator {} and port {}",
                    initiatorPort, storagePortWwn);
            // Find the FCZoneReference in ViPR for the port-initiator key and the network
            String key = FCZoneReference.makeEndpointsKey(initiatorPort, storagePortWwn);
            List<FCZoneReference> fcZoneRefs = getFCZoneReferencesForKey(key);
            FCZoneReference refTemplate = DataObjectUtils.findByProperty(fcZoneRefs, "groupUri", exportGroupUri);
            if (refTemplate != null) {
                _log.info("Already existing FCZoneReferences for initiator {} and port {} will be replicated for new volumes.",
                        initiatorPort, storagePortWwn);
                return createZoneInfoForRef(refTemplate, null, initiatorPort, storagePortWwn,
                        NetworkUtil.getEndpointNetworkLite(initiatorPort, _dbClient));
            } else {
                _log.info("FCZoneReferences doesnt exist for initiator {} and port {} for replication.",
                        initiatorPort, storagePortWwn);
                return null;
            }
        } else {
            _log.debug("Check Zones flag is false. Placing a zone for initiator {} and port {}", initiatorPort, storagePortWwn);
            // If the zone already exists, just return its reference
            NetworkFCZoneInfo zoneInfo = getZoneInfoForExistingZone(iniNet, initiatorPort, storagePort.getPortNetworkId(), existingZones);
            if (zoneInfo != null) {
                _log.info("Already existing zone {} for initiator {} and port {} will be used.",
                        new Object[] { zoneInfo.getZoneName(), initiatorPort, storagePortWwn });
                return zoneInfo;
            }

            _log.debug("Could not find an existing zone for initiator {} and port {} to use." +
                    "A new zone will be created.",
                    new Object[] { initiatorPort, storagePortWwn });
            // Create a the list of end points -
            List<String> endPoints = Arrays.asList(new String[] { initiatorPort, storagePortWwn });
            List<NetworkSystem> networkSystems = getZoningNetworkSystems(iniNet, portNet);

            if (networkSystems.isEmpty()) {
                _log.info(String.format(
                        "Could not find a network system with connection to storage port %s",
                        storagePortWwn));
                throw DeviceControllerException.exceptions.cannotFindSwitchConnectionToStoragePort(storagePortWwn);
            }

            // 2. Select the network system to use
            NetworkSystem networkSystem = networkSystems.get(0);

            // 3. identify an alternate network device, if any
            _log.debug("Network system {} was selected to be the primary network system. " +
                    "Trying to select an alternate network system.", networkSystem.getNativeGuid());
            NetworkSystem altNetworkSystem = networkSystem;
            for (NetworkSystem system : networkSystems) {
                if (altNetworkSystem != system) {
                    altNetworkSystem = system;
                    _log.debug("Network system {} was selected to be the alternate network system.", altNetworkSystem.getNativeGuid());
                    break;
                }
            }

            // 4. create the response
            NetworkFCZoneInfo networkFabricInfo = null;
            if (networkSystem != null) {
                networkFabricInfo = new NetworkFCZoneInfo(networkSystem.getId(),
                        iniNet.getNativeId(), NetworkUtil.getNetworkWwn(iniNet));
                networkFabricInfo.getEndPoints().addAll(endPoints);
                networkFabricInfo.setAltNetworkDeviceId(URI.create(altNetworkSystem.getId().toString()));
                nameZone(networkFabricInfo, networkSystem.getSystemType(), hostName, initiatorPort, storagePort, !portNet.equals(iniNet));
            } else {
                // This should not happen unless the transport zones were manually entered
                throw DeviceControllerException.exceptions.cannotFindSwitchConnectionToInitiator();
            }
            return networkFabricInfo;
        }
    }

    /**
     * Looks at the varray to see if zoning is disabled, and looks to make
     * sure that there is at least one active NetworkSystem registered.
     * 
     * @param dbClient DbClient
     * @param varrayUri the URI of the virtual array
     * @return true if zoning required, false if not
     */
    public static boolean isZoningRequired(DbClient dbClient, URI varrayUri) {
        // If automatic zoning disabled, return false
        if (varrayUri != null) {
            VirtualArray nh = dbClient.queryObject(VirtualArray.class, varrayUri);
            if (nh != null) {
                return isZoningRequired(dbClient, nh);
            }
        }
        return false;
    }

    /**
     * Search the list of existing zones for the initiator-port pair to decide which to use.
     * Preference is given to zones according to this priority:
     * <ol>
     * <li>The zone is in ViPR DB and was created by ViPR</li>
     * <li>The zone is in ViPR DB but was not created by ViPR</li>
     * <li>The zone follows the single initiator-target pair per zone</li>
     * <li>The last zone in the list</li>
     * </ol>
     * If no zone can be found for the initiator-port pair, null is returned.
     * 
     * @param network the network of the initiator
     * @param initiatorWwn the initiator WWN
     * @param portWwn the target WWN
     * @param existingZones a list of zones found on the network system for the initiator
     * @return an instance of Zone if one is found, otherwise null.
     */
    public Zone selectExistingZoneForInitiatorPort(NetworkLite network, String initiatorWwn, String portWwn,
            List<Zone> existingZones) {
        // If we did not find zones, we need to create zones even if we have FCZoneReference
        if (existingZones == null || existingZones.isEmpty()) {
            return null;
        }

        // initialize variables
        boolean existingZone = true;
        Zone foundZone = null;

        // Find the FCZoneReference in ViPR for the port-initiator key and the network
        String key = FCZoneReference.makeEndpointsKey(initiatorWwn, portWwn);
        List<FCZoneReference> fcZoneRefs = getFCZoneReferencesForKey(key);
        if (!fcZoneRefs.isEmpty()) {
            Zone matchedZone = null;
            _log.info("Found {} FCZoneReference for key {}", fcZoneRefs.size(), key);
            // try to re-use zones known to ViPR as a first preference
            for (FCZoneReference fcZoneRef : fcZoneRefs) {
                // make sure the FCZoneReference matches the network and its network system a
                if (network.getNetworkSystems().contains(fcZoneRef.getNetworkSystemUri().toString()) &&
                        network.getNativeId().equals(fcZoneRef.getFabricId())) {
                    _log.debug("Found an FCZoneReference for zone {}", fcZoneRef.getZoneName());
                    // do still have the zone on the network system
                    matchedZone = findZoneByNameAndPort(fcZoneRef.getZoneName(), portWwn, existingZones);
                    if (matchedZone != null) {
                        _log.debug("Found the zone for FCZoneReference {} in the initiator existing zones", fcZoneRef.getZoneName());
                        _log.debug(matchedZone.getLogString());
                        foundZone = matchedZone;
                        // if the zone was created by ViPR, the search ended
                        if (!fcZoneRef.getExistingZone()) {
                            existingZone = false;
                            _log.debug("Selected zone {} because it was created by ViPR", foundZone.getName());
                            break;
                        }
                    }
                }
            }
        }
        if (foundZone != null) {
            _log.debug("Selected existing Zone {} as it is already used by ViPR", foundZone.getName());
        } else {
            outer: for (Zone curZone : existingZones) {
                for (ZoneMember member : curZone.getMembers()) {
                    if (member.getAddress() != null && member.getAddress().equals(portWwn)) {
                        foundZone = curZone;
                        if (curZone.getMembers().size() == 2) {
                            // if the zone has only 2 members, the search ended
                            _log.debug("Selected existing Zone {} as it has only 2 members", foundZone.getName());
                            break outer;
                        }
                    }
                }
            }
        }
        if (foundZone != null) {
            foundZone.setExistingZone(existingZone);
        }
        return foundZone;
    }

    /**
     * Find the FCZoneReferences for a given zone reference key.
     * 
     * @param key - Endpoint key consisting of concatenated WWNs
     * @return List of FCZoneReference
     */
    public List<FCZoneReference> getFCZoneReferencesForKey(String key) {
        List<FCZoneReference> list = new ArrayList<FCZoneReference>();
        URIQueryResultList uris = new URIQueryResultList();
        _dbClient.queryByConstraint(AlternateIdConstraint.Factory.
                getFCZoneReferenceKeyConstraint(key), uris);
        list.addAll(_dbClient.queryObject(FCZoneReference.class,
                DataObjectUtils.iteratorToList(uris), true));
        return list;
    }

    /**
     * Looks at the varray to see if zoning is disabled, and looks to make
     * sure that there is at least one active NetworkSystem registered.
     * 
     * @param dbClient DbClient
     * @param nh Neighborhood
     * @return true if zoning required, false if not
     */
    public static boolean isZoningRequired(DbClient dbClient, VirtualArray nh) {
        // If automatic zoning disabled, return false
        if (nh.getAutoSanZoning() == false) {
            _log.info("SAN Zoning is not enabled for Neighborhood: "
                    + nh.getLabel());
            return false;
        }
        return NetworkUtil.areNetworkSystemDiscovered(dbClient);
    }

    /**
     * Finds all the network systems that have access to the initiators and '
     * storage port networks. When the initiators and storage port are in
     * different networks that are routed to each other, the assumption is that
     * there should exist and network system that can be used to managed both
     * networks.
     * 
     * @param iniNetwork the initiator network
     * @param portNetwork the storage port network
     * @return the network systems that can be used to managed the port and initiator
     *         networks.
     */
    List<NetworkSystem> getZoningNetworkSystems(NetworkLite iniNetwork,
            NetworkLite portNetwork) {
        List<NetworkSystem> orderedNetworkSystems = new ArrayList<NetworkSystem>();
        List<NetworkSystem> idleNetworkSystems = new ArrayList<NetworkSystem>();
        List<NetworkSystem> deRegisteredNetworkSystems = new ArrayList<NetworkSystem>();
        List<URI> iniNetSys = (iniNetwork == null) ?
                new ArrayList<URI>() : StringSetUtil.stringSetToUriList(new StringSet(iniNetwork.getNetworkSystems()));
        List<URI> portNetSys = (portNetwork == null) ?
                new ArrayList<URI>() : StringSetUtil.stringSetToUriList(new StringSet(portNetwork.getNetworkSystems()));

        // find the common network systems
        Collection<URI> allSys = new HashSet<URI>();
        if (iniNetSys != null) {
            allSys.addAll(iniNetSys);
        }
        if (portNetSys != null) {
            allSys.addAll(portNetSys);
        }
        if (!allSys.isEmpty()) {
            orderedNetworkSystems = _dbClient.queryObject(NetworkSystem.class, allSys, true);
            if (!orderedNetworkSystems.isEmpty()) {
                for (NetworkSystem networkSystem : orderedNetworkSystems) {
                    if (networkSystem.getRegistrationStatus().equals(RegistrationStatus.UNREGISTERED.toString())) {
                        _log.info("Network System {} is not used as it is not registered.", networkSystem.getLabel());
                        deRegisteredNetworkSystems.add(networkSystem);
                    } else if (networkSystem.getDiscoveryStatus().equals(DataCollectionJobStatus.ERROR.toString()) ||
                            networkSystem.getDiscoveryStatus().equals(DataCollectionJobStatus.CREATED.toString())) {
                        _log.info("Network System {} is moved to the end of Network System list as its discovery is not successful.",
                                networkSystem.getLabel());
                        idleNetworkSystems.add(networkSystem);
                    }
                }
                orderedNetworkSystems.removeAll(deRegisteredNetworkSystems);
                orderedNetworkSystems.removeAll(idleNetworkSystems);
                Collections.shuffle(orderedNetworkSystems);
                Collections.shuffle(idleNetworkSystems);
                orderedNetworkSystems.addAll(idleNetworkSystems);
            } else {
                _log.warn("Could not find any active network systems that can be used to zone.");
            }
        } else {
            _log.warn("Could not find any network systems that can be used to zone.");

        }
        return orderedNetworkSystems;
    }

    /**
     * Returns all the FCEndpoint instances discovered on a network system
     * filtered by an initiator and storage port
     * 
     * @param ns the network system
     * @param initiator the initiator
     * @param port the storage port
     * @return
     */
    private List<FCEndpoint> getFCEndpointsForNetworkSystem(NetworkSystem ns,
            String initiator, String port) {
        List<FCEndpoint> eps = new ArrayList<FCEndpoint>();
        URIQueryResultList result = new URIQueryResultList();
        _dbClient.queryByConstraint(
                ContainmentConstraint.Factory
                        .getNetworkSystemFCPortConnectionConstraint(ns.getId()), result);
        // loop thru the connections and get all those we need
        for (URI uriFcPortConnection : result) {
            FCEndpoint connection = _dbClient.queryObject(
                    FCEndpoint.class, uriFcPortConnection);
            if (connection == null) {
                continue;
            }
            if (port.equals(connection.getRemotePortName()) ||
                    initiator.equals(connection.getRemotePortName())) {
                eps.add(connection);
            }
        }
        return eps;
    }

    /**
     * Check that the zoning map has been initialized and has entries for all initiators
     * in the Export Mask.
     * 
     * @param varrayURI VirtualArray URI
     * @param mask ExportMask
     */
    private void checkZoningMap(ExportGroup exportGroup, ExportMask mask, Set<Initiator> initiators) {
        // Normally we don't want to generate a full zone map except in the rare case
        // where there is no zone map set and the Export Group zoneAllInitiators flag
        // is set to true. Currently only needed by RecoverPoint.
        if (mask.getZoningMap().isEmpty() && exportGroup.getZoneAllInitiators() == true) {
            generateFullZoningMap(_dbClient, exportGroup.getVirtualArray(), mask, initiators);
        }
        else {
            checkZoningMap(exportGroup.getVirtualArray(), mask, initiators);
        }
    }

    /**
     * Check that the zoning map has been initialized and has entries for a specified Collection of Initiators.
     * Otherwise info error message.
     * 
     * @param varrayURI VirtualArray URI
     * @param mask ExportMask
     * @param initiators Collection<Initiator>
     */
    private void checkZoningMap(URI varrayURI, ExportMask mask, Collection<Initiator> initiators) {
        StringSetMap zoningMap = mask.getZoningMap();
        for (Initiator initiator : initiators) {
            if (zoningMap == null || !zoningMap.containsKey(initiator.getId().toString())) {
                _log.info(String.format("No zoning map entry for initiator %s (%s), will not be zoned",
                        initiator.getInitiatorPort(), initiator.getId()));
            }
        }
    }

    /**
     * Given a list of storage ports, the find the ones that can be targets for
     * the initiator in a given virtual array. The target port must be tagged to
     * the virtual array and have connectivity to the initiator either via that
     * same network as the initiator or via a network that is routed to the
     * initiator's network. When a mix of local and routed targets are found,
     * only the local ones are returned.
     * 
     * @param varrayURI - VirtualArray URI
     * @param initiator Initiator
     * @param port StoragePort
     * @return a list of storage ports that can be the target of the initiator.
     */
    public static List<URI> findInitiatorTargetsInVarray(DbClient dbClient, URI varrayURI, Initiator initiator,
            Set<StoragePort> storagePorts) {
        NetworkLite iniNetwork = BlockStorageScheduler.lookupNetworkLite(dbClient, Transport.FC, initiator.getInitiatorPort());
        List<URI> targetPorts = new ArrayList<URI>();
        if (iniNetwork != null) {
            for (StoragePort storagePort : storagePorts) {
                if (iniNetwork.getId().equals(storagePort.getNetwork()) &&
                        storagePort.getTaggedVirtualArrays() != null &&
                        storagePort.getTaggedVirtualArrays().contains(varrayURI.toString())) {
                    targetPorts.add(storagePort.getId());
                }
            }
            if (targetPorts.isEmpty()) {
                for (StoragePort storagePort : storagePorts) {
                    if (iniNetwork.connectedToNetwork(storagePort.getNetwork()) &&
                            storagePort.getTaggedVirtualArrays() != null &&
                            storagePort.getTaggedVirtualArrays().contains(varrayURI.toString())) {
                        targetPorts.add(storagePort.getId());
                    }
                }
            }
        }
        return targetPorts;
    }

    /**
     * Generate the zoning targets for a newly created ExportGroup.
     * The group may include arbitrary numbers of initiators, volumes, ports, export masks, etc.
     * 
     * @param exportGroup ExportGroup
     * @param volumeURIs Collection of volumes to be generated
     * @param existingZonesMap a map of initiator ports WWN to its existing zones
     * @param checkZones Flag to enable or disable zoning check on a Network System
     * @param dbClient an instance of DbClient
     * @return List<NetworkFCZoneInfo> representing zones to be created
     * @throws DeviceControllerException
     */
    public List<NetworkFCZoneInfo> getZoningTargetsForExportMasks(
            ExportGroup exportGroup, List<URI> exportMaskURIs, Collection<URI> volumeURIs,
            Map<String, List<Zone>> existingZonesMap, boolean checkZones, DbClient dbClient) {
        List<NetworkFCZoneInfo> zoneInfos = new ArrayList<NetworkFCZoneInfo>();

        for (URI maskURI : exportMaskURIs) {
            ExportMask exportMask = ExportMaskUtils.getExportMask(_dbClient, maskURI);
            if (exportMask == null) {
                continue;
            }
            Collection<URI> filteredVolumesURIs = filterVolumes(volumeURIs, exportMask);
            if (filteredVolumesURIs.isEmpty()) {
                continue;
            }
            checkZoningMap(exportGroup, exportMask,
                    ExportMaskUtils.getInitiatorsForExportMask(_dbClient,
                            exportMask, Transport.FC));
            if (isZoningRequired(dbClient, exportGroup.getVirtualArray())) {
                _log.info(String.format("Generating zoning targets for ExportMask %s (%s)",
                        exportMask.getMaskName(), exportMask.getId()));
                zoneInfos.addAll(
                        generateRequestedZonesForExportMask(exportGroup.getVirtualArray(), exportGroup,
                                exportMask, filteredVolumesURIs, existingZonesMap, checkZones));
            }
            // If we're doing a VPlex export, it might use an alternate Varray (for HA export),
            // so check to see if we can add zones for the alternate Varray.
            if (exportGroup.hasAltVirtualArray(exportMask.getStorageDevice().toString())) {
                URI altVirtualArray = URI.create(exportGroup.getAltVirtualArrays()
                        .get(exportMask.getStorageDevice().toString()));
                if (isZoningRequired(dbClient, altVirtualArray)) {
                    zoneInfos.addAll(generateRequestedZonesForExportMask(altVirtualArray,
                            exportGroup, exportMask, filteredVolumesURIs, existingZonesMap, checkZones));
                }
            }
        }
        return zoneInfos;
    }

    /**
     * Filter so as to return all the volumes that are on the same array as indicated by the Export Mask
     * 
     * @param volumeURIs
     * @param exportMask
     * @return Collection<URI> of volume ids
     */
    private Collection<URI> filterVolumes(Collection<URI> volumeURIs, ExportMask exportMask) {
        List<URI> volumes = new ArrayList<URI>();
        for (URI volumeURI : volumeURIs) {
            BlockObject volume = BlockObject.fetch(_dbClient, volumeURI);
            if (volume.getStorageController().equals(exportMask.getStorageDevice())) {
                volumes.add(volume.getId());
            }
        }
        return volumes;
    }

    /**
     * The ExportMask has a valid zoningMap, which identifies the zones that
     * the StoragePortsAssigner wanted created. So we create specifically those zones.
     * 
     * @param varrayURI Varray (Neighborhood) URI
     * @param exportGroup ExportGroup object
     * @param exportMask ExportMask object
     * @param volumeURIs - List of volume URIs using this ExportMask
     * @param existingZonesMap a map of initiator ports WWN to its existing zones
     * @param checkZones Flag to enable or disable zoning check on a Network System
     * @return List<NetworkFCZoneInfO representing zones to be created.
     * @throws DeviceControllerException
     */
    private List<NetworkFCZoneInfo> generateRequestedZonesForExportMask(
            URI varrayURI,
            ExportGroup exportGroup,
            ExportMask exportMask,
            Collection<URI> volumeURIs, Map<String, List<Zone>> existingZonesMap, boolean checkZones) throws DeviceControllerException {

        List<NetworkFCZoneInfo> zoneInfos = new ArrayList<NetworkFCZoneInfo>();

        if (exportMask.getZoningMap() == null) {
            _log.info(String.format("No zone map Export Mask %s (%s) systemCreated %s",
                    exportMask.getMaskName(), exportMask.getId(),
                    exportMask.getCreatedBySystem()));
            return zoneInfos;
        }

        Set<Initiator> initiators = ExportMaskUtils.getInitiatorsForExportMask(
                _dbClient, exportMask, Transport.FC);

        for (Initiator initiator : initiators) {
            StringSet portIds = exportMask.getZoningMap().get(initiator.getId().toString());
            if (portIds != null) {
                for (String portId : portIds) {
                    StoragePort sp = _dbClient.queryObject(StoragePort.class, URI.create(portId));
                    if (sp.getTaggedVirtualArrays() != null &&
                            sp.getTaggedVirtualArrays().contains(varrayURI.toString())) {
                        boolean placedZone = placeZone(zoneInfos, exportGroup, varrayURI, initiator, sp,
                                volumeURIs, existingZonesMap.get(initiator.getInitiatorPort()), checkZones);
                        if (placedZone == false && checkZones) {
                            throw DeviceControllerException.exceptions.cannotMatchSanStoragePortInitiatorForVolume(sp.getPortName(),
                                    formatWWN(initiator.getInitiatorPort()), volumeURIs.toString());
                        }
                    }
                }
            }
        }
        return zoneInfos;
    }

    /**
     * Place a zone described by its initiator and port, and add it to the zoneInfos list.
     * ZoneReferences and zones will be added for all volumes matching the storage port's device.
     * 
     * @param zoneInfos List<NetworkFCZoneInfo> list of zones being built
     * @param exportGroup ExportGroup
     * @param varrayURI VirtualArray (Neighborhood) URI
     * @param initiator Initiator
     * @param sp StoragePort
     * @param volumeURIs
     * @param existingZones zones that already exist on the network system
     * @param checkZones Flag to enable or disable zoning check on a Network System
     *
     * @return true if could place the zone
     */
    private boolean placeZone(
            List<NetworkFCZoneInfo> zoneInfos,
            ExportGroup exportGroup, URI varrayURI,
            Initiator initiator,
            StoragePort sp, Collection<URI> volumeURIs, List<Zone> existingZones, boolean checkZones) {
        boolean foundMatch = false;
        NetworkFCZoneInfo zoneInfo = placeZones(
                exportGroup.getId(),
                varrayURI,
                initiator.getProtocol(),
                formatWWN(initiator.getInitiatorPort()),
                sp, initiator.getHostName(), existingZones, checkZones);
        if (zoneInfo != null) {
            for (URI volumeURI : volumeURIs) {
                BlockObject volume = BlockObject.fetch(_dbClient, volumeURI);
                // If the volume is from a different device, don't create a reference.
                if (!volume.getStorageController().equals(sp.getStorageDevice())) {
                    continue;
                }
                NetworkFCZoneInfo volZoneInfo = zoneInfo.clone();
                volZoneInfo.setVolumeId(volumeURI);
                zoneInfos.add(volZoneInfo);
            }
            foundMatch = true;
        }
        return foundMatch;
    }

    /**
     * Search the list of existing zones for the initiator-port pair to decide which to use.
     * Preference is given to zones according to this priority:
     * <ol>
     * <li>The zone is in ViPR DB and was created by ViPR</li>
     * <li>The zone is in ViPR DB but was not created by ViPR</li>
     * <li>The zone follows the single initiator-target pair per zone</li>
     * <li>The last zone in the list</li>
     * </ol>
     * Create a new FCZoneInfo object from an existing zone, otherwise return a null.
     * 
     * @param network the initiator network
     * @param initiatorWwn the initiator WWN
     * @param portWwn the target WWN
     * @param existingZones a list of zones found on the network system for the initiator
     * @return an instance of FCZoneInfo
     */
    private NetworkFCZoneInfo getZoneInfoForExistingZone(NetworkLite network, String initiatorWwn, String portWwn,
            List<Zone> existingZones) {
        NetworkFCZoneInfo zoneInfo = null;
        Zone zone = selectExistingZoneForInitiatorPort(network, initiatorWwn, portWwn, existingZones);
        if (zone != null) {
            zoneInfo = new NetworkFCZoneInfo(
                    URI.create(network.getNetworkSystems().iterator().next()),
                    network.getNativeId(), NetworkUtil.getNetworkWwn(network));
            zoneInfo.setEndPoints(Arrays.asList(new String[] { initiatorWwn, portWwn }));
            zoneInfo.setZoneName(zone.getName());
            zoneInfo.setExistingZone(zone.getExistingZone());
        }
        return zoneInfo;
    }

    /**
     * Performs a simple search to find a zone in a collection by name and port WWN
     * 
     * @param name the name
     * @param portWwn the port WWN
     * @param zones the collection of zone
     * @return the matching if one is found, null otherwise.
     */
    private Zone findZoneByNameAndPort(String name, String portWwn, List<Zone> zones) {
        if (zones != null) {
            for (Zone zone : zones) {
                if (zone.getName().equals(name)) {
                    for (ZoneMember member : zone.getMembers()) {
                        if (member.getAddress() != null && member.getAddress().equals(portWwn)) {
                            _log.debug("Found a matching zone for name {} and port WWN {}", zone.getName(), portWwn);
                            return zone;
                        }
                    }
                }

            }
        }
        _log.debug("Could not find a matching zone for name {} and port WWN {}", name, portWwn);
        return null;
    }

    /**
     * Returns a list of zoning targets for multiple export masks each adding their own
     * list of initiators.
     * 
     * @param exportGroup ExportGroup
     * @param exportMasksToInitiators Map of ExportMask URI to List of Initiator URIs
     * @param zonesMap a list of existing zones mapped by the initiator port WWN
     * @param dbClient
     * @return List of Zones (NetworkFCZoneInfo)
     * @throws DeviceControllerException
     */
    public List<NetworkFCZoneInfo> getZoningTargetsForInitiators(
            ExportGroup exportGroup, Map<URI, List<URI>> exportMasksToInitiators,
            Map<String, List<Zone>> zonesMap, DbClient dbClient)
            throws DeviceControllerException {
        List<NetworkFCZoneInfo> zones = new ArrayList<NetworkFCZoneInfo>();
        for (URI maskURI : exportMasksToInitiators.keySet()) {
            ExportMask mask = ExportMaskUtils.getExportMask(_dbClient, maskURI);
            if (mask == null) {
                continue;
            }
            List<Initiator> initiators = getInitiators(exportMasksToInitiators.get(maskURI));
            _log.info(String.format(
                    "Generating zoning targets for ExportMask: %s (%s) Initiators: %s",
                    mask.getMaskName(), mask.getId(), exportMasksToInitiators.get(maskURI).toString()));
            checkZoningMap(exportGroup.getVirtualArray(), mask, initiators);
            if (isZoningRequired(dbClient, exportGroup.getVirtualArray())) {
                zones.addAll(getZoningTargetsForInitiators(exportGroup, mask, exportGroup.getVirtualArray(), initiators, zonesMap));
            }
            // If we're doing a VPlex export, it might use an alternate Varray (for HA export),
            // so check to see if we can add zones for the alternate Varray.
            if (exportGroup.hasAltVirtualArray(mask.getStorageDevice().toString())) {
                URI altVirtualArray = URI.create(exportGroup.getAltVirtualArrays()
                        .get(mask.getStorageDevice().toString()));
                if (isZoningRequired(dbClient, altVirtualArray)) {
                    zones.addAll(getZoningTargetsForInitiators(exportGroup,
                            mask, altVirtualArray, initiators, zonesMap));
                }
            }
        }
        return zones;
    }

    /**
     * Returns a list of initiators from a list of Initiator URIs
     * 
     * @param initiatorURIs -- a List of Initiator URIs
     * @return List<Initiator> where each Initiator is active and type FC
     */
    private List<Initiator> getInitiators(List<URI> initiatorURIs) {
        List<Initiator> initiators = new ArrayList<Initiator>();
        List<Initiator> queryInitiators = _dbClient.queryObject(Initiator.class, initiatorURIs);
        for (Initiator initiator : queryInitiators) {
            if (initiator == null || initiator.getInactive() == true) {
                continue;
            }
            if (StorageProtocol.block2Transport(initiator.getProtocol())
                != Transport.FC) {
                continue;
            }
            initiators.add(initiator);
        }
        return initiators;
    }

    /**
     * This is called when ExportGroupService adds initiators.
     * Creates list of NetworkFabricInfo structures for zoning each volume
     * to the newly added Initiator.
     * 
     * @param exportGroup - The ExportGroup structure.
     * @param varrayUri - The URI of the virtual array, this can be the export group's
     *            virtual array or its alternate virtual array
     * @param exportMask - The ExportMask structure.
     * @param initiators - Contains the initiators
     * @param zonesMap a list of existing zones mapped by the initiator port WWN
     * @return List<NetworkFCZoneInfo> indicating zones and zone references to be created.
     * @throws IOException
     * @throws DeviceControllerException
     */
    private List<NetworkFCZoneInfo> getZoningTargetsForInitiators(
            ExportGroup exportGroup, ExportMask exportMask, URI varrayUri,
            List<Initiator> initiators, Map<String, List<Zone>> zonesMap)
            throws DeviceControllerException {
        List<NetworkFCZoneInfo> fabricInfos = new ArrayList<NetworkFCZoneInfo>();

        for (Initiator initiator : initiators) {
            // Determine storage ports.
            StringSet storagePorts = null;
            if (exportMask.getZoningMap() != null) {
                // Get the explicit zone assignments from the port assigner
                storagePorts = exportMask.getZoningMap().get(initiator.getId().toString());
            }
            if (storagePorts == null || storagePorts.isEmpty()) {
                continue;
            }

            if (StorageProtocol.block2Transport(initiator.getProtocol())
                != Transport.FC) {
                continue;
            }
            for (String storagePort : storagePorts) {
                StoragePort sp = _dbClient.queryObject(StoragePort.class, URI.create(storagePort));
                if (sp == null || sp.getTaggedVirtualArrays() == null ||
                        !sp.getTaggedVirtualArrays().contains(varrayUri.toString())) {
                    continue;
                }
                if (!exportMask.getStorageDevice().equals(sp.getStorageDevice())) {
                    continue;
                }
                try {
                    NetworkFCZoneInfo fabricInfo = placeZones(
                            exportGroup.getId(),
                            varrayUri,
                            initiator.getProtocol(),
                            formatWWN(initiator.getInitiatorPort()),
                            sp, initiator.getHostName(), zonesMap.get(initiator.getInitiatorPort()), true);
                    if (fabricInfo != null) {
                        for (String volId : exportMask.getVolumes().keySet()) {
                            NetworkFCZoneInfo volFabricInfo = fabricInfo.clone();
                            volFabricInfo.setVolumeId(URI.create(volId));
                            fabricInfos.add(volFabricInfo);
                        }
                    }
                } catch (DeviceControllerException ex) {
                    _log.info(String.format(
                            "Initiator %s could not be paired with port %s",
                            initiator.getInitiatorPort(), sp.getPortNetworkId()));
                }
            }
        }
        return fabricInfos;
    }

    public List<NetworkFCZoneInfo> getRemoveZoningTargetsForExportMasks(
            ExportGroup exportGroup, List<URI> exportMaskURIs, Collection<URI> volumeURIs) {
        List<NetworkFCZoneInfo> zoningTargets = new ArrayList<NetworkFCZoneInfo>();
        URI nbrUri = exportGroup.getVirtualArray();

        for (URI maskURI : exportMaskURIs) {
            ExportMask exportMask = ExportMaskUtils.getExportMask(_dbClient, maskURI);
            if (exportMask == null) {
                continue;
            }
            Set<Initiator> initiators = ExportMaskUtils.getInitiatorsForExportMask(
                    _dbClient, exportMask, Transport.FC);
            checkZoningMap(exportGroup, exportMask, initiators);
            _log.info(String.format("Generating remove zoning targets for ExportMask %s (%s)",
                    exportMask.getMaskName(), exportMask.getId()));

            for (Initiator initiator : initiators) {
                StringSet portSet = null;
                if (exportMask.getZoningMap() != null) {
                    portSet = exportMask.getZoningMap().get(initiator.getId().toString());
                }
                if (portSet != null) {
                    for (String portId : portSet) {
                        // Calculate the zone information
                        List<NetworkFCZoneInfo> zoneInfos = unexportVolumes(
                                nbrUri,
                                volumeURIs,
                                exportGroup.getId(),
                                URI.create(portId),
                                formatWWN(initiator.getInitiatorPort()));
                        if (zoneInfos != null) {
                            zoningTargets.addAll(zoneInfos);
                        }
                    }
                }
            }

        }
        return zoningTargets;
    }

    public List<NetworkFCZoneInfo> getRemoveZoningTargetsForInitiators(
            ExportGroup exportGroup, Map<URI, List<URI>> exportMasksToInitiators)
            throws DeviceControllerException {
        List<NetworkFCZoneInfo> zones = new ArrayList<NetworkFCZoneInfo>();
        for (URI maskURI : exportMasksToInitiators.keySet()) {
            ExportMask mask = ExportMaskUtils.getExportMask(_dbClient, maskURI);
            if (mask == null) {
                continue;
            }
            List<Initiator> initiators = getInitiators(exportMasksToInitiators.get(maskURI));
            _log.info(String.format(
                    "Generating zoning remove targets for ExportMask: %s (%s) Initiators: %s",
                    mask.getMaskName(), mask.getId(), exportMasksToInitiators.get(maskURI).toString()));
            checkZoningMap(exportGroup.getVirtualArray(), mask, initiators);
            zones.addAll(getRemoveZoningTargetsForInitiators(exportGroup, mask, initiators));
        }
        return zones;
    }

    private List<NetworkFCZoneInfo> getRemoveZoningTargetsForInitiators(
            ExportGroup exportGroup, ExportMask exportMask, List<Initiator> initiators) {
        List<NetworkFCZoneInfo> zoningTargets = new ArrayList<NetworkFCZoneInfo>();
        try {
            for (Initiator initiator : initiators) {
                StringSet storagePorts = null;
                if (exportMask.getZoningMap() != null) {
                    storagePorts = exportMask.getZoningMap().get(initiator.getId().toString());
                }
                if (storagePorts == null) {
                    continue;
                }

                if (StorageProtocol.block2Transport(initiator.getProtocol())
                != Transport.FC) {
                    continue;
                }
                if (exportMask.getVolumes() != null) {
                    Set<URI> volumeUris = new HashSet<URI>();

                    URI nbrUri = null;
                    for (String volUri : exportMask.getVolumes().keySet()) {
                        URI volumeURI = URI.create(volUri);
                        volumeUris.add(volumeURI);
                        if (nbrUri == null) {
                            nbrUri = getNeighborhoodURIForVolume(volumeURI);
                        }
                    }

                    for (String portId : storagePorts) {
                        List<NetworkFCZoneInfo> zoneInfos = unexportVolumes(
                                nbrUri,
                                volumeUris,
                                exportGroup.getId(),
                                URI.create(portId),
                                formatWWN(initiator.getInitiatorPort()));
                        if (zoneInfos != null) {
                            zoningTargets.addAll(zoneInfos);
                        }
                    }
                }
            }
        } catch (Exception e) {
            // TODO Revisit exceptions
            _log.error(String.format("Error finding zones to remove for host initiators: %s",
                    initiators.toString()));
        }
        return zoningTargets;
    }

    /**
     * Make a String key from two URIs.
     * 
     * @param uri1
     * @param uri2
     * @return
     */
    private String make2UriKey(URI uri1, URI uri2) {
        String part1 = "null";
        String part2 = "null";
        if (uri1 != null) {
            part1 = uri1.toString();
        }
        if (uri2 != null) {
            part2 = uri2.toString();
        }
        return part1 + "+" + part2;
    }

    /**
     * Called from the unexportVolume call. and others. This method builds the NetworkFabricInfo to be passed to the
     * NetworkDeviceController for automatic unzoning.
     * 
     * @param volUris Collection of URIs for volumes whose references are to be deleted
     * @param exportGroupUri Reference to the export group containing the volume, can be null to export from volume
     * @param storagePortUri the URI of the StoragePort
     * @param initiatorPort String WWPN with colons
     * @param protocol StorageProtocol
     * @return List<NetworkFCZoneInfo> detailing zones to be removed or at least unreferenced
     * @throws IOException
     */
    @SuppressWarnings("deprecation")
    public List<NetworkFCZoneInfo> unexportVolumes(URI nbrUri, Collection<URI> volUris, URI exportGroupUri,
            URI storagePortUri, String initiatorPort) {
        List<NetworkFCZoneInfo> ourReferences = new ArrayList<NetworkFCZoneInfo>();
        VirtualArray nh = _dbClient.queryObject(VirtualArray.class, nbrUri);
        if (nh != null && nh.getAutoSanZoning() == false) {
            _log.info("Automatic SAN zoning is disabled in virtual array: " + nh.getLabel());
            return null;
        }

        initiatorPort = formatWWN(initiatorPort);
        // Get the StoragePort
        StoragePort port = null;
        try {
            port = _dbClient.queryObject(StoragePort.class, storagePortUri);
            if (port == null) {
                return null;
            }
        } catch (DatabaseException ex) {
            return null;
        }

        // See if we can find our zone references
        List<String> endPoints = new ArrayList<String>();
        endPoints.add(initiatorPort);
        endPoints.add(formatWWN(port.getPortNetworkId()));
        // Make the key for our endPoints
        String key = null;
        {
            NetworkFCZoneInfo fabricInfo = new NetworkFCZoneInfo();
            fabricInfo.setEndPoints(endPoints);
            key = fabricInfo.makeEndpointsKey();
        }

        // Create a map of the references keyed by volUri concatenated with export group URI.
        // This allows for multiple export groups to export the same volume, and the zone will not
        // be deleted until the volume's references are removed from all export groups.
        // Then we can tell if other volumes are using this.
        Map<String, FCZoneReference> volRefMap = makeExportToReferenceMap(key);

        // If there were no references at all, we don't do anything.
        if (volRefMap.isEmpty()) {
            return null;

        } else {
            // Loop through all the volumes we're removing, removing them from the set.
            for (URI volUri : volUris) {
                FCZoneReference ourReference = volRefMap.get(make2UriKey(volUri, exportGroupUri));
                if (ourReference == null) {
                    continue;
                }
                // We need a fabricInfo for each,
                // so as to remove the FCZoneReference that is keyed on volume/exportGroup.
                NetworkFCZoneInfo fabricInfo = createZoneInfoForRef(
                        ourReference, volUri, initiatorPort,
                        port.getPortNetworkId(), null);
                ourReferences.add(fabricInfo);
                volRefMap.remove(make2UriKey(volUri, exportGroupUri));
            }

            // See if all the remaining entries have been marked for deletion.
            boolean live = false;
            for (FCZoneReference ref : volRefMap.values()) {
                if (ref.getInactive() == false) {
                    // Here is an apparent live reference; look up the volume and make
                    // sure it's still active too.
                    BlockObject vol = BlockObject.fetch(_dbClient, ref.getVolumeUri());
                    ExportGroup group = _dbClient.queryObject(ExportGroup.class, ref.getGroupUri());
                    if (vol != null && vol.getInactive() == false && group != null && group.getInactive() == false) {
                        live = true;
                    } else {
                        // mark the errant reference inactive
                        _dbClient.markForDeletion(ref);
                    }
                }
            }

            // If there are still live references, can't delete the zone.
            // So leave _fabricInfo._isLastReference == false
            // Otherwise mark it true so the zone will be taken out.
            if (live == false) {
                for (NetworkFCZoneInfo fabricInfo : ourReferences) {
                    fabricInfo._isLastReference = true;

                    // Pick an alternate device, just in case
                    NetworkLite portNet = getStoragePortNetwork(port);
                    NetworkLite iniNet = BlockStorageScheduler.lookupNetworkLite(_dbClient,
                            StorageProtocol.block2Transport("FC"), initiatorPort);
                    List<NetworkSystem> networkSystems = getZoningNetworkSystems(iniNet, portNet);
                    for (NetworkSystem ns : networkSystems) {
                        if (!ns.getId().equals(fabricInfo.getNetworkDeviceId())) {
                            fabricInfo.setAltNetworkDeviceId(ns.getId());
                            break;
                        }
                    }
                }
            }
            return ourReferences;
        }
    }

    /**
     * Makes a map from a volume/export group key to the FCZoneReference.
     * 
     * @param key - Endpoint key consisting of concatenated WWNs
     * @return Map of volume/export group key to FCZoneReference
     */
    public Map<String, FCZoneReference> makeExportToReferenceMap(String key) {
        Map<String, FCZoneReference> volRefMap = new HashMap<String, FCZoneReference>();
        List<FCZoneReference> refs = getFCZoneReferencesForKey(key);
        for (FCZoneReference ref : refs) {
            String uri2key = make2UriKey(ref.getVolumeUri(), ref.getGroupUri());
            volRefMap.put(uri2key, ref);
        }
        return volRefMap;
    }

    /**
     * Normalize WWN. Inserts colons if needed an converts to upper case.
     * 
     * @param wwn
     * @return
     */
    protected String formatWWN(String wwn) {
        if (wwn.contains(":")) {
            return wwn.toUpperCase();
        }
        char[] chars = wwn.toUpperCase().toCharArray();
        StringBuilder buf = new StringBuilder();
        for (int i = 0; i < chars.length;) {
            buf.append(chars[i++]);
            if (i < chars.length && (i % 2) == 0) {
                buf.append(":");
            }
        }
        return buf.toString().toUpperCase();
    }

    /**
     * Normalize endpoint collection.
     * 
     * @param endpoints
     * @return Collection<String>
     */
    protected Collection<String> formatEndpoints(Collection<String> endpoints) {
        List<String> eps = new ArrayList<String>();
        for (String endpoint : endpoints) {
            String ep = formatWWN(endpoint);
            eps.add(ep);
        }
        return eps;
    }

    /**
     * Get varray URI for a volume, be it a real volume, or a block snapshot
     * 
     * @param uri - URI of Volume or BlockSnapshot
     * @return
     */
    private URI getNeighborhoodURIForVolume(URI uri) {
        URI nhURI = null;
        // Volume is the normal case
        Volume volume = _dbClient.queryObject(Volume.class, uri);
        if (volume != null) {
            nhURI = volume.getVirtualArray();
        }
        // Check if snapshot
        else {
            BlockSnapshot snap = _dbClient.queryObject(BlockSnapshot.class, uri);
            if (snap != null) {
                volume = _dbClient.queryObject(Volume.class, snap.getParent().getURI());
                if (volume != null) {
                    nhURI = volume.getVirtualArray();
                }
            }
        }
        return nhURI;
    }

    /**
     * Generates a zoning map that maps all initiators to all ports on the same Network.
     * 
     * @param varrayURI - the ExportGroup varray
     * @param mask - the Export Mask
     * @param initiators - List of Initiators to be zoned
     */
    public static void generateFullZoningMap(DbClient dbClient, URI varrayURI, ExportMask mask, Collection<Initiator> initiators) {
        boolean changed = false;
        Set<StoragePort> storagePorts = ExportMaskUtils.getPortsForExportMask(
                dbClient, mask, Transport.FC);
        for (Initiator initiator : initiators) {
            if (mask.getZoningMap() == null || mask.getZoningMap().get(initiator.getId().toString()) == null) {
                _log.info(String.format("No zoning map entry for initiator %s (%s), will zone to all ports",
                        initiator.getInitiatorPort(), initiator.getId()));
                List<URI> targetPorts = findInitiatorTargetsInVarray(dbClient, varrayURI, initiator, storagePorts);
                if (!targetPorts.isEmpty()) {
                    changed = true;
                    mask.addZoningMapEntry(initiator.getId().toString(), StringSetUtil.uriListToStringSet(targetPorts));
                }
            }
        }
        if (changed) {
            // Update the mask to save the zoningMap entries.
            dbClient.persistObject(mask);
        }
    }

    /**
     * Creates a NetworkFCZoneInfo from a {@link FCZoneReference}
     * 
     * @param ourReference
     * @param volUri
     * @param initiator
     * @param port
     * @param network
     * @return
     */
    private NetworkFCZoneInfo createZoneInfoForRef(FCZoneReference ourReference, URI volUri,
            String initiator, String port, NetworkLite network) {
        if (ourReference == null) {
            return null;
        }
        // We need a fabricInfo for each,
        // so as to remove the FCZoneReference that is keyed on volume/exportGroup.
        NetworkFCZoneInfo fabricInfo = new NetworkFCZoneInfo();
        fabricInfo.setEndPoints(Arrays.asList(new String[] { initiator, port }));
        fabricInfo.setFcZoneReferenceId(ourReference.getId());
        fabricInfo.setNetworkDeviceId(ourReference.getNetworkSystemUri());
        fabricInfo.setFabricId(ourReference.getFabricId());
        fabricInfo.setZoneName(ourReference.getZoneName());
        fabricInfo.setVolumeId(volUri);
        fabricInfo.setExistingZone(ourReference.getExistingZone());
        if (network != null) {
            fabricInfo.setFabricWwn(NetworkUtil.getNetworkWwn(network));
        }
        return fabricInfo;
    }

    /**
     * Returns the flag settable by the user in the custom config that indicates if port allocation should
     * consider existing zones in port allocation logic or if it should proceed with allocations using
     * port metrics and hard redundancy only as criteria.
     * 
     * @param storageSystemType the type storage system of the ports
     * @param backend a flag to indicate if this is a host or backend export
     * 
     * @return true/false
     */
    public boolean portAllocationUseExistingZones(String storageSystemType, boolean backend) {
        if (backend) {
            return customConfigHandler.getComputedCustomConfigBooleanValue(
                    CustomConfigConstants.PORT_ALLOCATION_USE_PREZONED_PORT_BACKEND,
                    storageSystemType, null);
        } else {
            return customConfigHandler.getComputedCustomConfigBooleanValue(
                    CustomConfigConstants.PORT_ALLOCATION_USE_PREZONED_PORT_FRONTEND,
                    CustomConfigConstants.DEFAULT_KEY, null);
        }
    }
}
