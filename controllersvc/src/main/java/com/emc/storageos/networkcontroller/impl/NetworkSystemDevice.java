/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.networkcontroller.impl;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.emc.storageos.db.client.model.FCEndpoint;
import com.emc.storageos.db.client.model.NetworkSystem;
import com.emc.storageos.networkcontroller.exceptions.NetworkDeviceControllerException;
import com.emc.storageos.networkcontroller.impl.mds.Zone;
import com.emc.storageos.networkcontroller.impl.mds.ZoneUpdate;
import com.emc.storageos.networkcontroller.impl.mds.ZoneWwnAlias;
import com.emc.storageos.networkcontroller.impl.mds.ZoneWwnAliasUpdate;
import com.emc.storageos.networkcontroller.impl.mds.Zoneset;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;
import com.emc.storageos.volumecontroller.ControllerException;
import com.emc.storageos.volumecontroller.impl.BiosCommandResult;

public interface NetworkSystemDevice {

    public static final String SUCCESS = "Success";
    public static final String NO_CHANGE = "No Change";
    public static final String ERROR = "Error";

    /**
     * Connect the device - called when a new device is added
     * 
     * @param storage storage device object
     * @return command result object
     * @throws ControllerException
     */
    public BiosCommandResult doConnect(NetworkSystem network);

    /**
     * Disconnect the device - called when a device is being removed
     * 
     * @param storage storage device object
     * @return command result object
     * @throws ControllerException
     */
    public BiosCommandResult doDisconnect(NetworkSystem network);

    /**
     * Returns the list of port connections reported by this network device.
     * 
     * @param network -- the NetworkSystem object.
     * @param routedEndpoints an IN-OUT parameter to collect routed endpoints to avoid
     *            repeated retrieval of topology database - The map key is the fabric
     *            WWN and its value is a list of routed endpoints WWN
     * @return list of FCPortConnections
     * @throws Exception
     */
    public List<FCEndpoint> getPortConnections(NetworkSystem network, Map<String, Set<String>> routedEndpoints) throws Exception;

    /**
     * Returns the list of port connections that are routed in the fabric with fabricId
     * 
     * @param networkSystem -- the NetworkSystem object.
     * @return list of FCPortConnections
     * @throws Exception
     */
    public Set<String> getRoutedEndpoints(NetworkSystem networkSystem, String fabricId, String fabricWwn) throws Exception;

    /**
     * Gets the fabric IDs in the network switch.
     * 
     * @return list of fabric Id strings
     * @throws Exception
     */
    public List<String> getFabricIds(NetworkSystem network) throws Exception;

    /**
     * Returns a map of fabric WWN to fabricId
     * 
     * @param network NetworkSystem entry
     * @return a map of fabric WWN to fabricId
     * @throws Exception
     */
    public Map<String, String> getFabricIdsMap(NetworkSystem network) throws Exception;

    /**
     * Returns a list of zonesets for the specified device and fabric (Vsan).
     * 
     * @param network NetworkSystem entry
     * @param fabricId String fabricId
     * @param fabricWwn String
     * @param zoneName - only returns zone which has zone name matched with given name. Return all zones, if not specified.
     * @param excludeMembers - true, do not include members with zone. Include members, if not specified.
     * @param excludeAliases - true, do not include aliases with zone. Include aliases, if not specified.
     * @return List<Zoneset> zonesets within that fabric. If zoneName is specified, and there is a match, then only one zone is returned.
     *         If excludeMembers is true, then only zone name is present.
     * @throws Exception
     */
    public List<Zoneset> getZonesets(NetworkSystem network, String fabricId, String fabricWwn, String zoneName, boolean excludeMembers,
    		 boolean excludeAliases) throws Exception;

    /**
     * Adds zones to a SAN fabric.
     * 
     * @param network NetworkSystem entry
     * @param zones List of Zones to be added.
     * @param fabricId String for MDS this is the Vsan, for Brocade the fabric name.
     * @param fabricWwn String representing the fabric's WWN identifier.
     * @param activateZones - activate zones'active zoneset after zones are added to fabric
     * @return BiosCommandResult which contains a map of zone-name-to-result in {@link BiosCommandResult#getObjectList()} and the
     *         {@link ServiceCode} in case of failure
     * @throws NetworkDeviceControllerException
     */
    public BiosCommandResult addZones(NetworkSystem network, List<Zone> zones, String fabricId, String fabricWwn,
            boolean activateZones)
            throws NetworkDeviceControllerException;

    /**
     * Remove zones from a SAN fabric.
     * 
     * @param network NetworkSystem entry
     * @param zones List of Zones to be added.
     * @param fabricId String fabricId, for MDS this is the Vsan, for Brocade the fabric name.
     * @param fabricWwn String
     * @param activateZones - activate zones'active zoneset after zones are removed from fabric
     * @return BiosCommandResult which contains a map of zone-name-to-result in {@link BiosCommandResult#getObjectList()} and the
     *         {@link ServiceCode} in case of failure
     * @throws NetworkDeviceControllerException
     */
    public BiosCommandResult removeZones(NetworkSystem network, List<Zone> zones, String fabricId, String fabricWwn,
            boolean activateZones)
            throws NetworkDeviceControllerException;

    /**
     * Update zones from a SAN fabric.
     * 
     * @param network NetworkSystem entry
     * @param zones List of Zones to be added.
     * @param fabricId String fabricId, for MDS this is the Vsan, for Brocade the fabric name.
     * @param fabricWwn String
     * @return BiosCommandResult which contains a map of zone-name-to-result in {@link BiosCommandResult#getObjectList()} and the
     *         {@link ServiceCode} in case of failure
     * @throws NetworkDeviceControllerException
     */
    public BiosCommandResult updateZones(NetworkSystem network, List<ZoneUpdate> zones, String fabricId, String fabricWwn,
            boolean activateZones)
            throws NetworkDeviceControllerException;

    /**
     * Activate current active zoneset of the given SAN fabric.
     * 
     * @param network NetworkSystem entry
     * @param fabricId String fabricId, for MDS this is the Vsan, for Brocade the fabric name.
     * @param fabricWwn String
     * @param activateZones - activate zones'active zoneset after zones are removed from fabric
     * @return BiosCommandResult
     * @throws NetworkDeviceControllerException
     */
    public BiosCommandResult activateZones(NetworkSystem network, String fabricId, String fabricWwn)
            throws NetworkDeviceControllerException;

    /**
     * Returns system software version for this network system.
     * 
     * @param network NetworkSystem entry
     * @return system software version
     * @throws Exception
     */
    public String getVersion(NetworkSystem network) throws Exception;

    /**
     * Returns system uptime for this network system.
     * 
     * @param network NetworkSystem entry
     * @return system uptime
     * @throws Exception
     */
    public String getUptime(NetworkSystem network) throws Exception;

    /**
     * Returns a list of alias for the specified device and fabric (Vsan).
     * 
     * @param network NetworkSystem entry
     * @param fabricId String fabricId. Ignore if network device is MDS
     * @param fabricWwn String. Ignore if network device is MDS
     * @return list of alias.
     * @throws Exception
     */
    public List<ZoneWwnAlias> getAliases(NetworkSystem network, String fabricId, String fabricWwn) throws Exception;

    /**
     * Adds aliases to a SAN fabric of given network system. If network system is a MDS, ignore fabriId and add them
     * to network system.
     * 
     * @param network NetworkSystem entry
     * @param aliases List of aliases to be added.
     * @param fabricId String for MDS this is the Vsan, for Brocade the fabric name.
     * @param fabricWwn String representing the fabric's WWN identifier.
     * @return BiosCommandResult which contains a map of alias-name-to-result in {@link BiosCommandResult#getObjectList()} and the
     *         {@link ServiceCode} in case of failure
     * @throws NetworkDeviceControllerException
     */
    public BiosCommandResult addAliases(NetworkSystem network, List<ZoneWwnAlias> aliases, String fabricId, String fabricWwn)
            throws NetworkDeviceControllerException;

    /**
     * Removes aliases from a SAN fabric of given network system. If network system is a MDS, ignore fabriId and remove them
     * from network system.
     * 
     * @param network NetworkSystem entry
     * @param aliases List of aliases to be removed.
     * @param fabricId String for MDS this is the Vsan, for Brocade the fabric name.
     * @param fabricWwn String representing the fabric's WWN identifier.
     * @return BiosCommandResult which contains a map of alias-name-to-result in {@link BiosCommandResult#getObjectList()} and the
     *         {@link ServiceCode} in case of failure
     * @throws NetworkDeviceControllerException
     */
    public BiosCommandResult removeAliases(NetworkSystem network, List<ZoneWwnAlias> aliases, String fabricId, String fabricWwn)
            throws NetworkDeviceControllerException;

    /**
     * Updates aliases from a SAN fabric of given network system. If network system is a MDS, ignore fabriId
     * 
     * @param network NetworkSystem entry
     * @param aliases List of aliases to be updated.
     * @param fabricId String for MDS this is the Vsan, for Brocade the fabric name.
     * @param fabricWwn String representing the fabric's WWN identifier.
     * @return BiosCommandResult which contains a map of alias-name-to-result in {@link BiosCommandResult#getObjectList()} and the
     *         {@link ServiceCode} in case of failure
     * @throws NetworkDeviceControllerException
     */
    public BiosCommandResult updateAliases(NetworkSystem network, List<ZoneWwnAliasUpdate> aliases, String fabricId, String fabricWwn)
            throws NetworkDeviceControllerException;

    /**
     * Finds the zones for a list of endpoints. For each endpoint, find all
     * the zones where the endpoint is a pwwn-type member. Return the results
     * as a map of endpoint-to-zones.
     * 
     * @param networkSystem the network system to use when finding the zones
     * @param fabricWwn the endpoints' fabric or VSAN WWN
     * @param nativeId the endpoints'
     * @param endpointsWwn the endpoints's WWNs
     * @return as a map of endpoint-to-zones.
     * @throws NetworkDeviceControllerException
     */
    public Map<String, List<Zone>> getEndpointsZones(NetworkSystem networkSystem,
            String fabricWwn, String nativeId, Collection<String> endpointsWwn)
            throws NetworkDeviceControllerException;

 
    /**
     * @param networkSystem NetworkSystem entry
     * @return  - Always true for Brocade switches, True for Cisco MDS if IVR feature is enabled, false otherwise
     */
    public boolean isCapableOfRouting(NetworkSystem networkSystem);
    
}
