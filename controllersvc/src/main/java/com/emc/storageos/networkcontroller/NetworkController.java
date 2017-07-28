/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.networkcontroller;

import java.net.URI;
import java.util.List;
import java.util.Map;

import javax.ws.rs.QueryParam;

import com.emc.storageos.Controller;
import com.emc.storageos.networkcontroller.impl.mds.Zone;
import com.emc.storageos.networkcontroller.impl.mds.ZoneUpdate;
import com.emc.storageos.networkcontroller.impl.mds.ZoneWwnAlias;
import com.emc.storageos.networkcontroller.impl.mds.ZoneWwnAliasUpdate;
import com.emc.storageos.networkcontroller.impl.mds.Zoneset;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.volumecontroller.AsyncTask;
import com.emc.storageos.volumecontroller.ControllerException;

/**
 * The main API for managing network controller connections.
 * 
 * URI network: The following information will be available from network URI lookup.
 * ip: IP address of network controller.
 * credentials: Network controller access credentials.
 * profile: Network controller access profile.
 * 
 * URI pool: The following information will be available from pool URI lookup.
 * id: Pool identifier.
 * type: Pool type.
 */
public interface NetworkController extends Controller {
    /**
     * Connect to the network controller with the given address and credentials.
     * 
     * @param network URI for the network controller.
     */
    public void connectNetwork(URI network) throws InternalException;

    /**
     * Connect to the to the network controller with the given address and credentials.
     * 
     * @param network URI for the network controller.
     * @param taskId - Id of task
     * @throws InternalException
     */
    public void testCommunication(URI network,
            @QueryParam("task") String taskId) throws InternalException;

    /**
     * Disconnect from the network controller.
     * 
     * @param network URI of the network controller.
     */
    public void disconnectNetwork(URI network) throws InternalException;

    /**
     * 
     * @param tasks
     * @throws InternalException
     */
    public void discoverNetworkSystems(AsyncTask[] tasks) throws
            InternalException;

    /**
     * Gets the fabric identifiers of all fabrics managed by the device.
     * For Cisco MDS, this is the Vsan ID.
     * 
     * @param network URI for the network controller.
     * @return list of fabric ID strings.
     * @throws InternalException
     */
    public List<String> getFabricIds(URI network) throws InternalException;

    /**
     * Gets the list of zonesets for the specified network device and fabric ID (which is the Vsan ID for MDS).
     * 
     * @param network URI for the network controller
     * @param fabricId - Fabric or Vsan ID
     * @param fabricWwn = the WWN assigned by the principal switch for the fabric
     * @param zoneName - only returns zone which has zone name matched with given name. Return all zones, if not specified.
     * @param excludeMembers - true, do not include members with zone. Include members, if not specified.
     * @param excludeAliases - true, do not include aliases with zone. Include aliases, if not specified.
     * @return List<Zoneset> zonesets within that fabric. If zoneName is specified, and there is a match, then only one zone is returned.
     *         If excludeMembers is true, then only zone name is present.
     * @throws InternalException
     */
    public List<Zoneset> getZonesets(URI network, String fabricId, String fabricWwn, String zoneName, boolean excludeMembers,
    		 boolean excludeAliases) throws InternalException;

    /**
     * Asynchronous call to add SAN zones.
     * 
     * @param network URI for the network controller
     * @param fabricId - Fabric or Vsan ID
     * @param fabricWwn = the WWN assigned by the principal switch for the fabric
     * @param zones - list of Zones
     * @param activateZones - activate zones's active zoneset after zones are added
     * @param taskId - taskId to be updated
     * @throws InternalException
     */
    public void addSanZones(URI network, String fabricId, String fabricWwn, List<Zone> zones, boolean activateZones, String taskId)
            throws InternalException;
    
    /**
     * Asynchronous call to create Zones for the given initiator target pairs.
     * @param initiatorUris List of initiators
     * @param generatedIniToStoragePort Recommended initiator to Storage Port pairings
     * @param taskId - taskId to be updated
     * @throws ControllerException
     */
    public void createSanZones(List<URI> initiatorUris,  Map<URI, List<URI>> generatedIniToStoragePort, String taskId) throws ControllerException;

    /**
     * Asynchronous call to remove SAN zones.
     * 
     * @param network URI for the network controller
     * @param fabricId - Fabric or Vsan ID
     *            * @param fabricWwn = the WWN assigned by the principal switch for the fabric
     * @param zones - list of Zones
     * @param activateZones - activate zones's active zoneset after removing specified zones
     * @param taskId - taskId to be updated
     * @throws InternalException
     */
    public void removeSanZones(URI network, String fabricId, String fabricWwn, List<Zone> zones, boolean activateZones, String taskId)
            throws InternalException;

    /**
     * Asynchronous call to update SAN zones.
     * 
     * @param network URI for the network controller
     * @param fabricId - Fabric or Vsan ID
     *            * @param fabricWwn = the WWN assigned by the principal switch for the fabric
     * @param zones - list of Zones
     * @param activateZones - activate zones's active zoneset after zones are updated
     * @param taskId - taskId to be updated
     * @throws InternalException
     */
    public void
            updateSanZones(URI network, String fabricId, String fabricWwn, List<ZoneUpdate> zones, boolean activateZones, String taskId)
                    throws InternalException;

    /**
     * Asynchronous call to activate current activate zoneset of the given fabric
     * 
     * @param network URI for the network controller
     * @param fabricId - Fabric or Vsan ID
     * @param fabricWwn = the WWN assigned by the principal switch for the fabric
     * @param taskId - taskId to be updated
     * @throws InternalException
     */
    public void activateSanZones(URI network, String fabricId, String fabricWwn, String taskId)
            throws InternalException;

    /**
     * Asynchronous call to remove the network system's discovered connections.
     * 
     * @param network URI for the network controller
     * @param taskId - taskId to be updated
     * @throws InternalException
     */
    public void deleteNetworkSystem(URI network, String taskId)
            throws InternalException;

    /**
     * Gets the list of pwwn alias for the specified network device of the fabric ID (which is the Vsan ID for MDS).
     * If no fabric ID is specified, get all aliases in network device.
     * NOTE: for MDS, fabric ID will be ignore
     * 
     * @param network URI for the network controller
     * @param fabricId - Fabric or Vsan ID
     *            * @param fabricWwn - the WWN assigned by the principal switch for the fabric
     * @return List<ZoneWwnAlias> aliases within that network device / fabric
     * @throws InternalException
     */
    public List<ZoneWwnAlias> getAliases(URI network, String fabricId, String fabricWwn) throws InternalException;

    /**
     * Asynchronous call to add aliases to fabric of given network system. If network system is a MDS, ignore fabricId, and
     * just add them to network system
     * 
     * @param network URI for the network controller
     * @param fabricId - Fabric or Vsan ID
     * @param fabricWwn - the WWN assigned by the principal switch for the fabric
     * @param aliases - list of aliases
     * @param taskId - taskId to be updated
     * @throws InternalException
     */
    public void addAliases(URI network, String fabricId, String fabricWwn, List<ZoneWwnAlias> aliases, String taskId)
            throws InternalException;

    /**
     * Asynchronous call to remove aliases from a specified fabric of a given network system. If network system is a MDS, ignore fabricId,
     * and
     * just remove them to network system
     * 
     * @param network URI for the network controller
     * @param fabricId - Fabric or Vsan ID
     * @param fabricWwn - the WWN assigned by the principal switch for the fabric
     * @param aliases - list of aliases
     * @param taskId - taskId to be updated
     * @throws InternalException
     */
    public void removeAliases(URI network, String fabricId, String fabricWwn, List<ZoneWwnAlias> aliases, String taskId)
            throws InternalException;

    /**
     * Asynchronous call to update aliases from a specified fabric of a given network system. If network system is a MDS, ignore fabricId
     * 
     * @param network URI for the network controller
     * @param fabricId - Fabric or Vsan ID
     * @param fabricWwn - the WWN assigned by the principal switch for the fabric
     * @param updateAliases - list of aliases
     * @param taskId - taskId to be updated
     * @throws InternalException
     */
    public void updateAliases(URI network, String fabricId, String fabricWwn, List<ZoneWwnAliasUpdate> updateAliases, String taskId)
            throws InternalException;
}
