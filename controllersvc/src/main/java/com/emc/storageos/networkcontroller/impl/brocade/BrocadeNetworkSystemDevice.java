/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.networkcontroller.impl.brocade;

import java.lang.reflect.Method;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.cim.CIMInstance;
import javax.cim.CIMObjectPath;
import javax.security.auth.Subject;
import javax.wbem.WBEMException;
import javax.wbem.client.PasswordCredential;
import javax.wbem.client.UserPrincipal;
import javax.wbem.client.WBEMClient;
import javax.wbem.client.WBEMClientConstants;
import javax.wbem.client.WBEMClientFactory;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.cimadapter.connections.cim.CimConstants;
import com.emc.storageos.cimadapter.connections.cim.CimObjectPathCreator;
import com.emc.storageos.common.failureinjector.InvokeTestFailure;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.FCEndpoint;
import com.emc.storageos.db.client.model.NetworkSystem;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.networkcontroller.exceptions.NetworkDeviceControllerException;
import com.emc.storageos.networkcontroller.impl.NetworkSystemDevice;
import com.emc.storageos.networkcontroller.impl.NetworkSystemDeviceImpl;
import com.emc.storageos.networkcontroller.impl.mds.Zone;
import com.emc.storageos.networkcontroller.impl.mds.ZoneMember;
import com.emc.storageos.networkcontroller.impl.mds.ZoneUpdate;
import com.emc.storageos.networkcontroller.impl.mds.ZoneWwnAlias;
import com.emc.storageos.networkcontroller.impl.mds.ZoneWwnAliasUpdate;
import com.emc.storageos.networkcontroller.impl.mds.Zoneset;
import com.emc.storageos.util.NetworkLite;
import com.emc.storageos.util.NetworkUtil;
import com.emc.storageos.volumecontroller.impl.BiosCommandResult;
import com.emc.storageos.volumecontroller.impl.ControllerUtils;

public class BrocadeNetworkSystemDevice extends NetworkSystemDeviceImpl
        implements NetworkSystemDevice {
    private static final Logger _log = LoggerFactory.getLogger(BrocadeNetworkSystemDevice.class);
    private static final int ZONE_NAME_MAX_LENGTH = 64;

    private DbClient _dbClient;
    private CoordinatorClient _coordinator;

    BrocadeNetworkSMIS _smisHelper = new BrocadeNetworkSMIS();

    public void setDbClient(DbClient dbClient) {
        if (_dbClient == null) {
            _dbClient = dbClient;
        }
    }

    public void setCoordinator(CoordinatorClient coordinator) {
        if (_coordinator == null) {
            _coordinator = coordinator;
        }
    }

    @Override
    public BiosCommandResult doConnect(NetworkSystem network) {
        BiosCommandResult result = BiosCommandResult.createSuccessfulResult();
        return result;
    }

    @Override
    public BiosCommandResult doDisconnect(NetworkSystem network) {
        BiosCommandResult result = BiosCommandResult.createSuccessfulResult();
        return result;
    }

    @Override
    public List<FCEndpoint> getPortConnections(NetworkSystem network, Map<String, Set<String>> routedConnections)
            throws Exception {
        try {
            WBEMClient client = getNetworkDeviceClient(network);
            return _smisHelper.getPortConnections(client, routedConnections, discoverEndpointsByFabric());
        } catch (WBEMException ex) {
            String exMsg = ex.getLocalizedMessage();
            if (exMsg.equals("Unable to connect")) {
                exMsg = "Unable to connect on port " + network.getSmisPortNumber()
                        + ", please check that the port is active";
            }
            String msg = MessageFormat.format("Cannot get port connections for network device {0}: {1}",
                    network.getLabel(), exMsg);
            _log.error(msg);
            throw NetworkDeviceControllerException.exceptions.getPortConnectionsFailed(network.getLabel(), exMsg, ex);
        }
    }

    @Override
    public Map<String, String> getFabricIdsMap(NetworkSystem network) throws Exception {
        try {
            WBEMClient client = getNetworkDeviceClient(network);
            return _smisHelper.getFabricIdsMap(client);
        } catch (WBEMException ex) {
            _log.error("Cannot get fabric ids map for network device "
                    + network.getLabel() + ": " + ex.getLocalizedMessage());
            throw ex;
        }
    }

    @Override
    public List<String> getFabricIds(NetworkSystem network) throws Exception {
        try {
            WBEMClient client = getNetworkDeviceClient(network);
            return _smisHelper.getFabricIds(client);
        } catch (WBEMException ex) {
            _log.error("Cannot get fabricIds for network device "
                    + network.getLabel() + ": " + ex.getLocalizedMessage());
            throw ex;
        }
    }

    @Override
    public List<Zoneset> getZonesets(NetworkSystem network, String fabricId, String fabricWwn, String zoneName, boolean excludeMembers,
    		boolean excludeAliases) throws Exception {
        try {
            validateFabric(network, fabricWwn, fabricId);
            return _smisHelper.getZoneSets(getNetworkDeviceClient(network), fabricId, fabricWwn, zoneName, excludeMembers, excludeAliases);
        } catch (Exception ex) {
            _log.error("Cannot get zonesets for fabricId " + fabricId
                    + " on network device  " + network.getLabel() + ": "
                    + ex.getLocalizedMessage());
            throw ex;
        }
    }

    @Override
    public BiosCommandResult addZones(NetworkSystem networkSystem, List<Zone> zones,
            String fabricId, String fabricWwn, boolean activateZones) throws NetworkDeviceControllerException {
        BiosCommandResult result = null;

        // a zone-name-to-result map to hold the results for each zone
        Map<String, String> addZonesResults = new HashMap<String, String>();
        try {
            validateFabric(networkSystem, fabricWwn, fabricId);
            Map<NetworkLite, List<Zone>> zonesPerFabric = getAllZonesForZones(zones, false, fabricId, fabricWwn);
            WBEMClient client = getNetworkDeviceClient(networkSystem);
            for (NetworkLite network : zonesPerFabric.keySet()) {
                addZonesResults.putAll(addZonesStrategy(
                        client, zonesPerFabric.get(network),
                        network.getNativeId(), NetworkUtil.getNetworkWwn(network), activateZones));
            }
            _log.info(toMessage(addZonesResults));
            result = getBiosCommandResult(addZonesResults);
        } catch (NetworkDeviceControllerException ex) {
            _log.error("Cannot add zones: " + ex.getLocalizedMessage());
            throw ex;
        }
        return result;
    }

    /**
     * For a list of zones requested by clients, check is routing is needed and created
     * the required zone to support both use cases: switched and routed endpoints. For
     * switched endpoints the same list of zone is returned. For routed endpoints,
     * for each zone in the primary fabrics, an identical zone in the target fabrics
     * will also be created.
     * 
     * @param zones the zones to be created or deleted.
     * 
     * @return a map of zones to be created grouped by fabric. When the zone members
     *         are in different fabrics (i.e. routed), there will be a zone to create in each
     *         member's fabric
     */
    private Map<NetworkLite, List<Zone>> getAllZonesForZones(List<Zone> zones,
            boolean delete, String fabricId, String fabricWwn) {
        Map<NetworkLite, List<Zone>> allZones = new HashMap<NetworkLite, List<Zone>>();
        // we need to ensure that all endpoints belong to one network
        // or two networks that are routed to each other
        for (Zone zone : zones) {
            Map<ZoneMember, NetworkLite> epNetworks = getEndpointNetworks(zone, fabricId, fabricWwn);
            if (!epNetworks.isEmpty()) {
                Set<NetworkLite> networks = new HashSet<NetworkLite>(epNetworks.values());
                if (networks.size() == 2) { // need to create zone for
                    // ensure the other members can be routed as well
                    NetworkLite network = networks.iterator().next();
                    for (ZoneMember member : epNetworks.keySet()) {
                        if (network.getId().equals(epNetworks.get(member).getId())
                                || (network.hasRoutedNetworks(
                                        epNetworks.get(member).getId()))) {
                            _log.info("Verified zone {} endpoints are connected",
                                    zone.getName());
                        } else {
                            if (!delete) { // only error on create. For delete, always try.
                                _log.info(
                                        "Cannot create zone {} because the member are found to be not connected",
                                        zone.getName());
                                throw NetworkDeviceControllerException.exceptions
                                        .zoneEndpointsNotConnected(zone.getName());
                            }
                        }
                    }
                    // If this code is reached from the API test interface for creating
                    // zones, the zone may not be properly named.
                    if (!delete) {
                        if (zone.getName() != null
                                && !zone.getName().toLowerCase().startsWith("lsan_")) {
                            String name = "lsan_"
                                    + (zone.getName() == null ? "" : zone.getName());
                            if (name.length() > ZONE_NAME_MAX_LENGTH) {
                                name = name.substring(0, ZONE_NAME_MAX_LENGTH - 1);
                            }
                            zone.setName(name);
                        }
                    }
                }
                for (NetworkLite network : networks) {
                    List<Zone> netZones = allZones.get(network);
                    if (netZones == null) {
                        netZones = new ArrayList<Zone>();
                        allZones.put(network, netZones);
                    }
                    netZones.add(zone);
                }
            } else if (delete) {
                // if delete zone does not have member or member that are not in end point list
                // include them anyway for deletion
                NetworkLite networkLite = NetworkUtil.getNetworkLiteByFabricId(fabricId, fabricWwn, _dbClient);
                if (networkLite != null) {
                    List<Zone> zoneList = allZones.get(networkLite);
                    if (zoneList == null) {
                        zoneList = new ArrayList<Zone>();
                        allZones.put(networkLite, zoneList);
                    }
                    zoneList.add(zone);
                }
            }
        }
        return allZones;
    }

    /**
     * This is function is used at the time an 'add zone' request is received when we need
     * to decide if the zone request requires creating an LSAN zone or a regular zone. For
     * each member find the network the member is in. Note, zones can be created for WWNs
     * that are not logged into the switch, or for aliases. In this case, we assume only
     * regular zones will be created.
     * 
     * @param zone the zone to be added.
     * @return A map of zoneMember-to-network
     */
    private Map<ZoneMember, NetworkLite> getEndpointNetworks(Zone zone, String fabricId, String fabricWwn) {
        NetworkLite network = NetworkUtil.getNetworkLiteByFabricId(fabricId, fabricWwn, _dbClient);
        Map<ZoneMember, NetworkLite> epNetworks = new HashMap<ZoneMember, NetworkLite>();
        NetworkLite loopNetwork = null;
        for (ZoneMember member : zone.getMembers()) {
            // TODO - At this time routed zones are supported for zones with WWN-type members.
            // For alias-type members, more investigation is needed.
            if (!StringUtils.isEmpty(member.getAddress())) {
                loopNetwork = NetworkUtil.getEndpointNetworkLite(member.getAddress(), _dbClient);
            }

            // If the zone member is not logged into any network or is an alias, then assume
            // it belongs to the request network.
            if (loopNetwork == null && network != null) {
                loopNetwork = network;
            }

            if (loopNetwork != null) {
                epNetworks.put(member, loopNetwork);
            }
        }
        return epNetworks;
    }

    public BiosCommandResult removeZones(NetworkSystem networkSystem,
            List<Zone> zones, String fabricId, String fabricWwn, boolean activateZones)
            throws NetworkDeviceControllerException {
        BiosCommandResult result = null;
        Map<String, String> removedZoneResults = new HashMap<String, String>();
        try {
            validateFabric(networkSystem, fabricWwn, fabricId);
            Map<NetworkLite, List<Zone>> zonesPerFabric = getAllZonesForZones(zones, true, fabricId, fabricWwn);

            for (NetworkLite network : zonesPerFabric.keySet()) {
                WBEMClient client = getNetworkDeviceClient(networkSystem);
                removedZoneResults.putAll(removeZonesStrategy(
                        client, zonesPerFabric.get(network),
                        network.getNativeId(), NetworkUtil.getNetworkWwn(network), activateZones));
            }
            result = getBiosCommandResult(removedZoneResults);
            _log.info("Remove zone results {}", toMessage(removedZoneResults));
        } catch (NetworkDeviceControllerException ex) {
            _log.error("Cannot remove zones: " + ex.getLocalizedMessage());
            throw ex;
        }
        return result;
    }

    private WBEMClient getNetworkDeviceClient(NetworkSystem network) throws NetworkDeviceControllerException {
        return getWBEMClient(network.getSmisProviderIP(), network
                .getSmisPortNumber().toString(), network.getSmisUserName(),
                network.getSmisPassword(), network.getSmisUseSSL());

    }

    private WBEMClient getWBEMClient(String ipaddress, String smisport,
            String username, String password, boolean useSSL) throws NetworkDeviceControllerException {
        try {
            InvokeTestFailure.internalOnlyInvokeTestFailure(InvokeTestFailure.ARTIFICIAL_FAILURE_049);
            WBEMClient client = WBEMClientFactory
                    .getClient(WBEMClientConstants.PROTOCOL_CIMXML);
            String protocol = useSSL ? CimConstants.SECURE_PROTOCOL : CimConstants.DEFAULT_PROTOCOL;
            CIMObjectPath path = CimObjectPathCreator.createInstance(protocol, ipaddress,
                    smisport.toString(), BrocadeNetworkSMIS.getNamespace(),
                    null, null);
            final Subject subject = new Subject();
            subject.getPrincipals().add(new UserPrincipal(username));
            subject.getPrivateCredentials().add(
                    new PasswordCredential(password));
            client.initialize(path, subject, new Locale[] { Locale.US });
            return client;
        } catch (WBEMException ex) {
            _log.info("Failed to connect to Brocade at IP: " + ipaddress + " because: " + ex.getLocalizedMessage());
            throw NetworkDeviceControllerException.exceptions.getWBEMClientFailed(ipaddress, ex);
        }
    }

    /**
     * This function creates one or more zones in the active zoneset.
     * 
     * @param client
     *            - the WBEMClient of the SMI-S provider
     * @param zones
     *            - Zones to be created
     * @param fabricId
     *            - the fabric where the zones will be created
     * @return a map that contains the outcome for each zone keyed by zone name
     * @throws NetworkDeviceControllerException
     */
    public Map<String, String> addZonesStrategy(WBEMClient client, List<Zone> zones,
            String fabricId, String fabricWwn, boolean activateZones) throws NetworkDeviceControllerException {
        // a zone-name-to-result map to hold the results for each zone
        Map<String, String> addedZonesResult = new HashMap<String, String>();
        if (zones.isEmpty()) {
            throw DeviceControllerException.exceptions.entityNullOrEmpty("zones");
        }
        CIMInstance zoneServiceIns = null;
        try {
            _log.info("add zones started.");
            _log.info("Attempting to start a zoning session");

            zoneServiceIns = _smisHelper.startSession(client, fabricId, fabricWwn);
            if (zoneServiceIns == null) {
                _log.info("Failed to start a zoning session.");
                throw NetworkDeviceControllerException.exceptions.startZoningSessionFailed();
            }
            // First determine if there is an active zoneset.
            CIMObjectPath zonesetPath = null;
            CIMInstance activeZonesetIns = _smisHelper
                    .getActiveZonesetInstance(client, fabricId, fabricWwn);

            // There is no active zoneset. So we'll throw an exception.
            if (activeZonesetIns == null) {
                _log.info("No active zoneset fabrics: " + fabricId);
                throw NetworkDeviceControllerException.exceptions.noActiveZonesetForFabric(fabricId);
            } else {
                // For Brocade, the active zoneset is a copy of a configuration zoneset. To make a change, we
                // need to modify the configuration zoneset and activate it. Get the configuration zoneset.
                zonesetPath = _smisHelper.getShadowZonesetPath(client, fabricId, fabricWwn, activeZonesetIns);
            }

            for (Zone zone : zones) {
                try {
                    if (checkAndCreateZone(client, zoneServiceIns, fabricId, fabricWwn, zonesetPath, zone, activateZones)) {
                        addedZonesResult.put(zone.getName(), SUCCESS);
                    } else {
                        addedZonesResult.put(zone.getName(), NO_CHANGE);
                    }
                } catch (Exception ex) {
                    addedZonesResult.put(zone.getName(), ERROR + ": " + ex.getMessage());
                    handleZonesStrategyException(ex, activateZones);
                }
            }

            _log.info("Attempting to close zoning session.");
            // If there are no zones that need to be added, just close the session without commit and return.
            if (!hasResult(addedZonesResult, SUCCESS)) {
                _log.info("No zones were added. Closing the session with no commit");
                if (!_smisHelper.endSession(client, zoneServiceIns, false)) {
                    _log.info("Failed to terminate zoning session. Ignoring as session may have expired.");
                }
                return addedZonesResult;
            } else {
                // if zones were added, commit them before ending the session
                if (_smisHelper.endSession(client, zoneServiceIns, true)) {
                    if (activateZones) {
                        _log.info("Attempting to activate the zoneset.");
                        if (_smisHelper.activateZoneSet(client, zoneServiceIns,
                                zonesetPath, true)) {
                            _log.info("The zoneset was activated succcessfully.");
                        } else {
                            _log.info("Failed to activate the zoneset");
                        }
                    }
                } else {
                    throw NetworkDeviceControllerException.exceptions.addZonesStrategyFailedZoneCommit();
                }
            }

            _log.info("Add zone completed successfully.");

        } catch (Exception e1) {
            try {
                if (zoneServiceIns != null) {
                    _log.info("Attempting to terminate zoning session.");
                    _smisHelper.endSession(client, zoneServiceIns, false);
                }
            } catch (WBEMException e) {
                _log.error("Failed to terminate zoning session."
                        + e.getLocalizedMessage(), e);
            }
            _log.error("Failed to create zones: "
                    + e1.getLocalizedMessage(), e1);
            throw NetworkDeviceControllerException.exceptions.addZonesStrategyFailed(e1);
        }
        return addedZonesResult;
    }

    /**
     * Checks if a zone with the same name already exists before the zone is created. The
     * rules for creating a zone are:
     * <ul>
     * <li>If an active zone with the same name exists, ensure that all the desired members are in the zone. If this is true, consider the
     * zone created. If not, error because the application is not going to modify an existing zone.</li>
     * <li>If an inactive zone with the same name exists, delete the inactive zone and then create the new one.</li>
     * </ul>
     * This function assumes the zoning session is already acquired.
     * 
     * @param client an instance of the SMI client
     * @param zoneServiceIns the instance of SMI zoneServices
     * @param fabricId the fabric id where the zone should created
     * @param fabricWwn the fabric WWN where the zone should created
     * @param zonesetPath the SMI path of the active zoneset for the fabric
     * @param zone the zone to be created
     * @return a boolean to indicated if a zone was created or not.
     * @throws WBEMException
     */
    private boolean checkAndCreateZone(WBEMClient client, CIMInstance zoneServiceIns,
            String fabricId, String fabricWwn, CIMObjectPath zonesetPath,
            Zone zone, boolean activateZones) throws WBEMException {
        boolean added = false;
        _log.info("Starting create zone with name " + zone.getName());
        // check if an active zone with the same name exists
        Zone zoneInFabric = _smisHelper.getZone(client, zone.getName(), fabricWwn, true, true, true);
        if (zoneInFabric != null) {
            _log.info("Found an active zone with the name " + zone.getName());
            // I have a active zone with the same name but it does not have all the members we
            // need - at this time we're not modifying zones that already exist
            // and this one cannot be used without adding the missing members, so error
            if (!sameMembers(zoneInFabric, zone)) {
                throw NetworkDeviceControllerException.exceptions.activeZoneWithSameNameExists(zone.getName());
            }
        } else {
            // check if an inactive zone with the same name exists
            zoneInFabric = _smisHelper.getZone(client, zone.getName(), fabricWwn, false, true, true);
            if (zoneInFabric != null) {
                _log.info("Found an inactive zone with the name " + zone.getName());
                if (activateZones) {
                    // This is the export path - delete the zone we will create the new one
                    removeZone(client, fabricId, fabricWwn, zoneInFabric);
                    // create the new zone
                    createZone(client, zoneServiceIns, fabricId, fabricWwn, zonesetPath, zone);
                    added = true;
                } else {
                    // This is the API path - Error and let the caller provide the right input
                    throw NetworkDeviceControllerException.exceptions.inactiveZoneWithSameNameExists(zone.getName());
                }
            } else {
                // create the new zone
                added = createZone(client, zoneServiceIns, fabricId, fabricWwn, zonesetPath, zone);
            }
        }
        return added;
    }

    /**
     * Creates the zone in the fabric's active zoneset
     * 
     * @param client an instance of the SMI client
     * @param zoneServiceIns the instance of SMI zoneServices
     * @param fabricId the fabric id where the zone should created
     * @param fabricWwn the fabric WWN where the zone should created
     * @param zonesetPath the SMI path of the active zoneset for the fabric
     * @param zone the zone to be created
     * @return a boolean to indicated if a zone was created or not.
     * @throws WBEMException
     */
    private boolean createZone(WBEMClient client, CIMInstance zoneServiceIns,
            String fabricId, String fabricWwn, CIMObjectPath zonesetPath,
            Zone zone) throws WBEMException {
        _log.info("Creating a new zone " + zone.getName());
        CIMObjectPath zonePath = _smisHelper.addZone(client,
                zoneServiceIns, zonesetPath, zone.getName(), fabricId, fabricWwn);
        if (zonePath != null) {
            boolean success = false;
            String name = null;
            for (ZoneMember member : zone.getMembers()) {
                name = member.getAlias() == null ? member.getAddress() :
                        member.getAlias();
                _log.info("Creating zone member: "
                        + name + " zone: "
                        + zone.getName());
                success = _smisHelper.addZoneOrAliasMember(client, zoneServiceIns,
                        fabricWwn, zonePath, name);
                if (!success) {
                    _log.info("Failed to create memeber " + name + " for zone : " + zone.getName());
                    throw NetworkDeviceControllerException.exceptions.addZonesMemberFailedPath(zone.getName(), name);
                }
            }
        } else {
            _log.info("Failed to create zone : " + zone.getName());
            throw NetworkDeviceControllerException.exceptions.addZonesStrategyFailedPath();
        }
        return true;
    }

    /**
     * Remove a zone from the network system
     * 
     * @param client an instance of the SMI client
     * @param fabricId the if of the zone's fabric
     * @param fabricWwn the WWN of the zone's fabric
     * @param zone the zone to be deleted. It must contain the SMI object path
     * @throws WBEMException
     */
    private void removeZone(WBEMClient client, String fabricId,
            String fabricWwn, Zone zone) throws WBEMException {
        _log.info("Removing zone: " + zone.getName() + " for fabric: "
                + fabricId == null ? fabricWwn : fabricId);
        _smisHelper.removeZone(client, zone);
    }

    /**
     * This function removed one or more zones from the active zoneset. This function will not
     * error if a zone to be removed was not found.
     * <p>
     * Removing zones typical flow is:
     * <ol>
     * <li>find the zones that can be deleted</li>
     * <li>get the session lock</li>
     * <li>delete the zones</li>
     * <li>commit which releases the lock</li>
     * <li>activate if requested</li>
     * </ol>
     * This flow is different when we're removing the last zones in a zoneset. If the zoneset becomes empty, it needs to be removed too or
     * commit would fail. In order to remove the zoneset, it has to first be deactivated. The flow for removing the last zones in a zoneset
     * is
     * <ol>
     * <li>find the zones that can be deleted</li>
     * <li>deactivate the zoneset</li>
     * <li>get the session lock</li>
     * <li>delete the zones</li>
     * <li>delete the zoneset</li>
     * <li>commit which releases the lock</li>
     * </ol>
     * 
     * @param client an instance of the SMI client
     * @param zones the list if zones to be deleted.
     * @param fabricId the id of the fabric where the zones will be removed
     * @param activateZones a boolean that indicates if immediate activation is requested.
     * @return a map that contains the outcome for each zone keyed by zone name
     * @throws NetworkDeviceControllerException
     */
    public Map<String, String> removeZonesStrategy(WBEMClient client,
            List<Zone> zones, String fabricId, String fabricWwn, boolean activateZones)
            throws NetworkDeviceControllerException {
        long start = System.currentTimeMillis();
        // a zone-name-to-result map to hold the results for each zone
        Map<String, String> removedZoneResults = new HashMap<String, String>();
        CIMInstance zoneServiceIns = null;
        boolean wasDeactivated = false;
        CIMObjectPath shadowZonsetPath = null;
        boolean empty = false;
        try {
            _log.info("Remove zones started.");

            zoneServiceIns = _smisHelper.getZoneServiceInstance(client, fabricId, fabricWwn);
            if (zoneServiceIns == null) {
                _log.info("Failed to get zoning service.");
                throw NetworkDeviceControllerException.exceptions.removeZonesStrategyFailedSvc();
            }

            // get active zoneset.
            CIMInstance activeZonesetIns = _smisHelper
                    .getActiveZonesetInstance(client, fabricId, fabricWwn);
            if (activeZonesetIns == null) {
                String defaultZonesetName = getDefaultZonesetName(fabricId);

                // if no active zone set, get pending default active zone set
                activeZonesetIns = _smisHelper.getZoneset(client, fabricId, fabricWwn, defaultZonesetName);
                if (activeZonesetIns == null) {
                    _log.warn("No active/default zoneset found: " + defaultZonesetName);
                    throw NetworkDeviceControllerException.exceptions.noActiveZonesetForFabric(fabricId);
                }
            }

            // The actual work should be done on an inactive
            shadowZonsetPath = _smisHelper.getShadowZonesetPath(client, fabricId, fabricWwn, activeZonesetIns);
            Map<String, Zone> zonesInFabric = _smisHelper.getZones(client, getZoneNames(zones), fabricWwn, false, true, true);

            // Find the set of zones to be actually deleted.
            // We don't attempt to delete zones that are already gone.
            // And we don't delete zones that Bourne didn't create.
            List<Zone> zonesToBeDeleted = getZonesToBeDeleted(zones, zonesInFabric.values(), new Integer[1], removedZoneResults);

            // check if we need to deactivate
            if (!zonesToBeDeleted.isEmpty()) {
                empty = !_smisHelper.zonesetHasMore(client, shadowZonsetPath, zonesToBeDeleted.size());
                if (empty) {
                    _log.info("All zones will be removed so deactivate the zoneset");
                    _log.info("Attempting to deactivate the zoneset.");
                    wasDeactivated = _smisHelper.activateZoneSet(client, zoneServiceIns,
                            activeZonesetIns.getObjectPath(), false);
                }

                // now start removing zones
                _log.info("Attempting to start a zoning session");
                zoneServiceIns = _smisHelper.startSession(client, fabricId, fabricWwn);
                for (Zone curZone : zonesToBeDeleted) {
                    try {
                        _log.info("Removing zone: " + curZone.getName() + " fabric: "
                                + fabricId);
                        _smisHelper.removeZone(client, curZone);
                        removedZoneResults.put(curZone.getName(), SUCCESS);
                    } catch (Exception ex) {
                        removedZoneResults.put(curZone.getName(), ERROR + " : " + ex.getMessage());
                        handleZonesStrategyException(ex, activateZones);
                    }
                }
                // get the current state of the zoneset to make sure it is indeed empty
                empty = _smisHelper.isEmptyZoneset(client, shadowZonsetPath);
                if (empty) {
                    client.deleteInstance(shadowZonsetPath);
                }
            }

            // first close the session, commit if we have successful deletes, otherwise rollback
            _log.info("Attempting to close zoning session.");
            if (_smisHelper.endSession(client, zoneServiceIns,
                    hasResult(removedZoneResults, SUCCESS))) {
                // last activate/deactivate as needed
                // we want to activate if the zoneset is not empty and we either has deactivated it or
                // we actually deleted some zones and the caller requested re-activation.
                boolean shouldActivate = ((activateZones && hasResult(removedZoneResults, SUCCESS)) || wasDeactivated) && !empty;
                if (shouldActivate) {
                    _log.info("Attempting to activate the zoneset.");
                    _smisHelper.activateZoneSet(client, zoneServiceIns, shadowZonsetPath, true);
                }
            } else {
                if (hasResult(removedZoneResults, SUCCESS)) {
                    // only throw an exception if we were trying to commit changes
                    throw NetworkDeviceControllerException.exceptions.removeZonesStrategyFailedCommit();
                } else {
                    _log.info("Failed to terminate zoning session. Ignoring as the session may have expired.");
                }
            }

            _log.info("Remove zone completed successfully and took " + (System.currentTimeMillis() - start));
            return removedZoneResults;
        } catch (Exception e1) {
            try {
                if (zoneServiceIns != null) {
                    _log.info("Attempting to terminate zoning session.");
                    _smisHelper.endSession(client, zoneServiceIns, false);
                    if (shadowZonsetPath != null && wasDeactivated) {
                        _log.info("Attempting to re-activate the zoneset because it was deactivated earlier.");
                        _smisHelper.activateZoneSet(client, zoneServiceIns, shadowZonsetPath, true);
                    }
                }
            } catch (Exception ex) {
                _log.error("Failed terminate the zoning session and to reactivate the zoneset.");
            }
            _log.error("Failed to remove zones " + e1.getMessage());
            throw NetworkDeviceControllerException.exceptions.removeZonesStrategyFailed(e1);
        }
    }

    @Override
    public String getVersion(NetworkSystem network) throws Exception {
        try {
            WBEMClient client = getNetworkDeviceClient(network);
            return _smisHelper.getVersion(client);
        } catch (WBEMException ex) {
            String exMsg = ex.getLocalizedMessage();
            if ((exMsg != null) && exMsg.equals("Unable to connect")) {
                exMsg = "Unable to connect to device " + network.getLabel() + ": Unable to connect to ip "
                        + network.getIpAddress() + " on port " + network.getSmisPortNumber();
            }
            String msg = MessageFormat.format("Failed to get version: {0}", exMsg);
            _log.error(msg);

            throw NetworkDeviceControllerException.exceptions.getVersionFailed(exMsg, ex);
        }
    }

    @Override
    public String getUptime(NetworkSystem network) throws Exception {
        try {
            WBEMClient client = getNetworkDeviceClient(network);
            return _smisHelper.getUptime(client);
        } catch (WBEMException ex) {
            String exMsg = ex.getLocalizedMessage();
            if (exMsg.equals("Unable to connect")) {
                exMsg = "Unable to get uptime for device " + network.getLabel() + ": Unable to connect to ip "
                        + network.getIpAddress() + " on port " + network.getSmisPortNumber();
            } else {
                exMsg = "Unable to get uptime for device " + network.getLabel();
            }

            String msg = MessageFormat.format("Failed to get uptime: {0}", exMsg);
            _log.error(msg);

            throw NetworkDeviceControllerException.exceptions.getUptimeFailed(exMsg, ex);
        }
    }

    @Override
    public Set<String> getRoutedEndpoints(NetworkSystem networkSystem,
            String fabricId, String fabricWwn) throws Exception {
        WBEMClient client = getNetworkDeviceClient(networkSystem);
        return _smisHelper.getRoutedEndpoints(client, fabricId, fabricWwn);
    }

    @Override
    public BiosCommandResult updateZones(NetworkSystem networkSystem, List<ZoneUpdate> zoneUpdates,
            String fabricId, String fabricWwn, boolean activateZoneset)
            throws NetworkDeviceControllerException {
        BiosCommandResult cmdResults = null;
        try {
            WBEMClient client = getNetworkDeviceClient(networkSystem);
            validateFabric(networkSystem, fabricWwn, fabricId);
            Map<String, String> results = updateZonesStrategy(client, zoneUpdates, fabricId, fabricWwn, activateZoneset);
            cmdResults = getBiosCommandResult(results);
            _log.info("Update zone results {}", toMessage(results));
        } catch (NetworkDeviceControllerException ex) {
            _log.error("Cannot update zones: " + ex.getLocalizedMessage());
            throw ex;
        }
        return cmdResults;
    }

    /**
     * Updates one or more zones by adding/removing members as requested for each zone.
     * 
     * @param client and instance of {@link WBEMClient} connected to the provider
     * @param zones the list of zone update requests
     * @param fabricId the name of the fabric where the zones exist
     * @param fabricWwn the WWN of the fabric where the zones exist
     * @param activateZones a boolean to indicate if the zoneset should be activated
     *            following successful updates
     * @return a map of the update results by zone keyed by zone name
     * @throws NetworkDeviceControllerException
     */
    public Map<String, String> updateZonesStrategy(WBEMClient client, List<ZoneUpdate> zones,
            String fabricId, String fabricWwn, boolean activateZones) throws NetworkDeviceControllerException { // to do - Make sure fabric
                                                                                                                // id and fabric wwn are not
                                                                                                                // null or only request
                                                                                                                // needed params
        // a zone-name-to-result map to hold the results for each zone
        Map<String, String> zoneUpdateResults = new HashMap<String, String>();
        if (zones.isEmpty()) {
            throw DeviceControllerException.exceptions.entityNullOrEmpty("zones");
        }
        CIMInstance zoneServiceIns = null;
        try {
            _log.info("Update zones started.");
            _log.info("Attempting to start a zoning session");

            if (fabricWwn == null) {
                fabricWwn = _smisHelper.getFabricWwn(client, fabricId);
            }

            zoneServiceIns = _smisHelper.startSession(client, fabricId, fabricWwn);
            if (zoneServiceIns == null) {
                _log.info("Failed to start a zoning session.");
                throw NetworkDeviceControllerException.exceptions.startZoningSessionFailed();
            }
            // First determine if there is an active zoneset.
            CIMObjectPath zonesetPath = null;
            CIMInstance activeZonesetIns = _smisHelper
                    .getActiveZonesetInstance(client, fabricId, fabricWwn);

            // There is no active zoneset, error
            if (activeZonesetIns == null) {
                _log.info("Cannot find active zoneset.");
                throw NetworkDeviceControllerException.exceptions.noActiveZonesetForFabric(fabricId);
            } else {
                // For Brocade, the active zoneset is a copy of a configuration zoneset. To make a change, we
                // need to modify the configuration zoneset and activate it. Get the configuration zoneset.
                zonesetPath = _smisHelper.getShadowZonesetPath(client, fabricId, fabricWwn, activeZonesetIns);
            }

            Map<String, Zone> zonesInFabric = _smisHelper.getZones(client, getZoneNames(zones), fabricWwn, false, true, true);
            for (ZoneUpdate zone : zones) {
                try {
                    if (checkAndUpdateZone(client, zoneServiceIns, fabricId, fabricWwn, zonesetPath, zonesInFabric, zone)) {
                        zoneUpdateResults.put(zone.getName(), SUCCESS);
                    } else {
                        zoneUpdateResults.put(zone.getName(), NO_CHANGE);
                    }
                } catch (Exception ex) {
                    zoneUpdateResults.put(zone.getName(), ERROR + " : " + ex.getMessage());
                    handleZonesStrategyException(ex, activateZones);
                }
            }

            _log.info("Attempting to close zoning session.");
            // If there were no zones updated, just close the session without commit and return.
            if (!hasResult(zoneUpdateResults, SUCCESS)) {
                _log.info("No zones were updates. Closing the session with no commit");
                if (!_smisHelper.endSession(client, zoneServiceIns, false)) {
                    _log.info("Failed to terminate zoning session. Ignoring as session may have expired.");
                }
            } else {
                // if zones were updated, commit them before ending the session
                if (_smisHelper.endSession(client, zoneServiceIns, true)) {
                    if (activateZones) {
                        _log.info("Attempting to activate the zoneset.");
                        if (_smisHelper.activateZoneSet(client, zoneServiceIns,
                                zonesetPath, true)) {
                            _log.info("The zoneset was activated succcessfully.");
                        } else {
                            _log.info("Failed to activate the zoneset");
                        }
                    }
                } else {
                    throw NetworkDeviceControllerException.exceptions.updateZonesStrategyFailedCommit();
                }
            }

            _log.info("Update zones strategy completed successfully.");

        } catch (Exception e1) {
            try {
                if (zoneServiceIns != null) {
                    _log.info("Attempting to terminate zoning session.");
                    _smisHelper.endSession(client, zoneServiceIns, false);
                }
            } catch (WBEMException e) {
                _log.error("Failed to terminate zoning session."
                        + e.getLocalizedMessage(), e);
            }
            _log.error("Failed to update zones: "
                    + e1.getLocalizedMessage(), e1);
            throw NetworkDeviceControllerException.exceptions.updateZonesStrategyFailed(e1);
        }
        return zoneUpdateResults;
    }

    /**
     * Add and remove members from/to a zone. This function will not fail if
     * a member to be removed is not in the zone or if a member that will be
     * added is already in the zone.
     * <p>
     * A member of type alias can be remove using its alias or its WWN.
     * <p>
     * If the zone already has the WWN of an alias and now the user is trying to add the alias into the zone, the alias will not be added.
     * The WWN has to be removed first.
     * <p>
     * Replacing a WWN with its alias should be possible in a single call by specifying the WWN in the remove list and alias in the add
     * list.
     * <p>
     * Note this function will delete the zone if the zone has no remaining members.
     * 
     * @param client an instance of WBEMClient
     * @param zoneServiceIns an instance of ZoneService
     * @param fabricId the fabric name or vsan id
     * @param fabricWwn the fabric WWN
     * @param zonesetPath CIM path of the zoneset
     * @param zonesInFabric a map of all zones in the zoneset
     * @param zoneUpdate the changes to be made to the zone
     * @return true if the update completed successfully.
     */
    private boolean checkAndUpdateZone(WBEMClient client,
            CIMInstance zoneServiceIns, String fabricId, String fabricWwn,
            CIMObjectPath zonesetPath, Map<String, Zone> zonesInFabric, ZoneUpdate zoneUpdate) {
        boolean success = false;
        ZoneMember curMember = null;
        try {
            if (zonesInFabric.containsKey(zoneUpdate.getName())) {
                _log.info("Start update zone {}", zoneUpdate.getName());
                Zone zone = zonesInFabric.get(zoneUpdate.getName());
                Map<String, ZoneMember> members = getZoneMembersMap(client, zone.getName(),
                        (CIMObjectPath) zone.getCimObjectPath());

                // handle removed members
                if (zoneUpdate.getRemoveZones() != null) {
                    for (ZoneMember remMember : zoneUpdate.getRemoveZones()) {
                        curMember = members.containsKey(remMember.getAlias()) ?
                                members.get(remMember.getAlias()) : members.get(remMember.getAddress());
                        if (curMember != null && curMember.isAliasType() &&
                                !StringUtils.isEmpty(remMember.getAlias())) {
                            _log.info("Removing alia smember {}", remMember.getAlias());
                            _smisHelper.removeZoneOrAliasMember(client,
                                    (CIMObjectPath) curMember.getCimAliasPath(),
                                    (CIMObjectPath) zone.getCimObjectPath(), true);
                            members.remove(curMember.getAlias());
                            members.remove(curMember.getAddress());
                            success = true;
                        } else if (curMember != null && !curMember.isAliasType() &&
                                !StringUtils.isEmpty(remMember.getAddress())) {
                            _log.info("Removing WWN member {}", remMember.getAddress());
                            _smisHelper.removeZoneOrAliasMember(client,
                                    (CIMObjectPath) curMember.getCimObjectPath(),
                                    (CIMObjectPath) zone.getCimObjectPath(), false);
                            members.remove(curMember.getAlias());
                            members.remove(curMember.getAddress());
                            success = true;
                        } else {
                            _log.warn("Did not remove zone member with alias " + remMember.getAlias() + " and WWN " +
                                    remMember.getAddress() + " because it was not found.");
                        }
                    }
                }

                // handle added members
                if (zoneUpdate.getAddZones() != null) {
                    for (ZoneMember addMember : zoneUpdate.getAddZones()) {
                        curMember = members.containsKey(addMember.getAlias()) ?
                                members.get(addMember.getAlias()) : members.get(addMember.getAddress());
                        if (curMember == null) {
                            String name = addMember.hasAlias() ? addMember.getAlias() : addMember.getAddress();
                            _log.info("Adding zone member {} ", name);
                            _smisHelper.addZoneOrAliasMember(client, zoneServiceIns, fabricWwn,
                                    (CIMObjectPath) zone.getCimObjectPath(), name);
                            members.put(name, addMember);
                            success = true;
                        } else {
                            _log.warn("Did not add zone member with alias " + addMember.getAlias() + " and WWN " +
                                    addMember.getAddress() + " because it already exists.");
                        }
                    }
                }

                // check to see if the zone is now empty
                if (members.isEmpty()) {
                    _log.error("Deleting Zone "
                            + zoneUpdate.getName() + " because it is now empty.");
                    _smisHelper.removeZone(client, zone);
                }
            } else {
                _log.error("Failed to update zones: "
                        + zoneUpdate.getName() + ". The zone was not found in the active zoneset");
                throw NetworkDeviceControllerException.exceptions.updateZonesStrategyFailedNotFound(zoneUpdate.getName());
            }
        } catch (WBEMException ex) {
            _log.error("Failed to update zone: "
                    + zoneUpdate.getName() + ". Error message" + ex.getLocalizedMessage(), ex);
            throw NetworkDeviceControllerException.exceptions.updateZonesStrategyFailed(ex);
        }
        return success;
    }

    /**
     * Returns a map of the zone members keyed by either alias or WWN
     * depending on the member type. For members of type alias, the
     * member will appear in the map twice once keyed by alias and
     * once by WWN to facilitate the look up.
     * 
     * @param client and instance of WBEMClient
     * @param name the zone name
     * @param path CIM path of the zone
     * @return a map of the zone members keyed by either alias or WWN
     * @throws WBEMException
     */
    private Map<String, ZoneMember> getZoneMembersMap(WBEMClient client,
            String name, CIMObjectPath path) throws WBEMException {
        Map<String, ZoneMember> map = new HashMap<String, ZoneMember>();
        try {
            List<ZoneMember> members = _smisHelper.getZoneMembers(client, path, true);
            for (ZoneMember member : members) {
                if (member.hasAlias()) {
                    map.put(member.getAlias(), member);
                }
                map.put(member.getAddress(), member);
            }
            return map;
        } catch (WBEMException e) {
            _log.error("Failed to get the zone members for zone " + name
                    + ". Error message" + e.getLocalizedMessage());
            throw e;
        }
    }

    @Override
    public BiosCommandResult activateZones(NetworkSystem networkSystem, String fabricId,
            String fabricWwn) throws NetworkDeviceControllerException {
        BiosCommandResult result = null;
        NetworkDeviceControllerException exception = null;
        try {
            WBEMClient client = getNetworkDeviceClient(networkSystem);
            CIMInstance zonesetIns = _smisHelper.getActiveZonesetInstance(client, fabricId, fabricWwn);
            if (zonesetIns != null) {
                CIMObjectPath shadowZonesetPath = _smisHelper.getShadowZonesetPath(client, fabricId, fabricWwn, zonesetIns);
                CIMInstance zoneServiceIns = _smisHelper.getZoneServiceInstance(client, fabricId, fabricWwn);

                boolean activate = !_smisHelper.isEmptyZoneset(client, shadowZonesetPath);
                if (_smisHelper.activateZoneSet(client, zoneServiceIns, zonesetIns.getObjectPath(), activate)) {
                    _log.info("The active zoneset for fabric " + fabricId + " was " +
                            (activate ? "re-activated" : "deactivated"));
                } else {
                    _log.error("Failed to re-activate zoneset");
                    exception = NetworkDeviceControllerException.exceptions.zonesetActivationFailed(fabricId, new Throwable());
                }
            } else {
                exception = NetworkDeviceControllerException.exceptions.noActiveZonesetForFabric(fabricId);
            }
            result = BiosCommandResult.createSuccessfulResult();
        } catch (Exception ex) {
            _log.error("Cannot re-activate zoneset: " + ex.getLocalizedMessage());
            exception = NetworkDeviceControllerException.exceptions.zonesetActivationFailed(fabricId, ex);
        }
        if (exception != null) {
            throw exception;
        }
        return result;
    }

    protected String getDefaultZonesetName(String fabricId) {
        return "Zoneset_" + fabricId.replace("-", "_").replaceAll("[^A-Za-z0-9_]", "");
    }

    @Override
    public List<ZoneWwnAlias> getAliases(NetworkSystem network, String fabricId,
            String fabricWwn) throws Exception {
        WBEMClient client = getNetworkDeviceClient(network);
        validateFabric(network, fabricWwn, fabricId);
        return _smisHelper.getAliases(client, fabricId, fabricWwn);
    }

    @Override
    public BiosCommandResult addAliases(NetworkSystem networkSystem, List<ZoneWwnAlias> aliases,
            String fabricId, String fabricWwn)
            throws NetworkDeviceControllerException {
        return executeInSession(networkSystem, aliases, fabricId, fabricWwn,
                "checkAndCreateAlias", "add aliases");
    }

    /**
     * Check if an alias exists before creating a new one. If the alias exists and for
     * the same WWN, then nothing will be done but if the alias name is used for another
     * WWN, then this is an error condition.
     * 
     * @param client an instance of WBEMClient
     * @param zoneServiceIns the zone service holding the zoning session
     * @param fabricId the fabric name
     * @param fabricWwn the fabric WWN
     * @param alias the alias to be created
     * @return true if an alias was indeed created
     * @throws WBEMException
     */
    public boolean checkAndCreateAlias(WBEMClient client,
            CIMInstance zoneServiceIns, String fabricId,
            String fabricWwn, ZoneWwnAlias alias) throws WBEMException {
        boolean added = false;
        _log.info("Starting create alias with name " + alias.getName());

        // check if an alias with the same name exists
        ZoneWwnAlias existingAlias = _smisHelper.getAlias(client, alias.getName(), fabricWwn, true);
        if (existingAlias != null) {
            _log.info("Found alias {}", alias.getName());
            if (StringUtils.equalsIgnoreCase(existingAlias.getAddress(), alias.getAddress())) {
                // alias already exists - this is not an error unless it is for different member
                _log.info("The existing alias is for the same WWN {}. Nothing to do.", alias.getAddress());
            } else {
                throw NetworkDeviceControllerException.exceptions.aliasWithSameNameExists(alias.getName(),
                        existingAlias.getAddress(), alias.getAddress());
            }
        } else {
            // create the new alias
            added = _smisHelper.addZoneAlias(client, zoneServiceIns, fabricId, fabricWwn, alias) != null;
        }
        return added;
    }

    /**
     * Check if an alias exists before updating it. If the alias does not exist
     * then nothing will be done.
     * 
     * @param client an instance of WBEMClient
     * @param zoneServiceIns the zone service holding the zoning session
     * @param fabricId the fabric name
     * @param fabricWwn the fabric WWN
     * @param alias the alias to be created
     * @return true if an alias was indeed created
     * @throws WBEMException
     */
    public boolean checkAndRemoveAlias(WBEMClient client,
            CIMInstance zoneServiceIns, String fabricId,
            String fabricWwn, ZoneWwnAlias alias) throws WBEMException {
        boolean removed = false;
        _log.info("Starting remove alias with name {}", alias.getName());
        // check if an alias with the same name exists
        ZoneWwnAlias existingAlias = null;
        if (alias.getAddress() != null && alias.getAddress().length() > 0) {
            // we need to verify that the alias member matches before removing it
            existingAlias = _smisHelper.getAlias(client, alias.getName(), fabricWwn, true);
            if (existingAlias != null && !StringUtils.equalsIgnoreCase(existingAlias.getAddress(), alias.getAddress())) {
                _log.info("The existing alias has a WWN other than the expected {}. It will not be removed.", alias.getAddress());
                throw NetworkDeviceControllerException.exceptions.aliasWithDifferentWwnExists(alias.getName(),
                        existingAlias.getAddress(), alias.getAddress());
            }
        } else {
            existingAlias = _smisHelper.getAlias(client, alias.getName(), fabricWwn, false);
        }
        if (existingAlias != null) {
            _log.info("Found alias {}. The alias will be removed.", alias.getName());
            _smisHelper.removeInstance(client, (CIMObjectPath) existingAlias.getCimObjectPath());
            removed = true;
        } else {
            _log.info("Did not find alias {}. Nothing to do.", alias.getName());
        }
        return removed;
    }

    /**
     * Check if an alias exists before removing it. If the alias does not exist
     * then nothing will be done.
     * 
     * @param client an instance of WBEMClient
     * @param zoneServiceIns the zone service holding the zoning session
     * @param fabricId the fabric name
     * @param fabricWwn the fabric WWN
     * @param updateAlias the alias to be created
     * @return true if an alias was indeed created
     * @throws WBEMException
     */
    public boolean checkAndUpdateAlias(WBEMClient client,
            CIMInstance zoneServiceIns, String fabricId,
            String fabricWwn, ZoneWwnAlias alias) throws WBEMException {
        boolean success = false;
        ZoneWwnAliasUpdate updateAlias = (ZoneWwnAliasUpdate) alias;
        _log.info("Starting update alias {}", updateAlias.getName());
        // check if the alias exists
        ZoneWwnAlias existingAlias = _smisHelper.getAlias(client, updateAlias.getName(), fabricWwn, true);
        if (existingAlias != null) {
            _log.info("Found alias {}", updateAlias.getName());

            // check if the alias is how we expect it to be
            if (!StringUtils.isEmpty(updateAlias.getNewName())) {
                _log.warn("Rename alias is request and is not supported for Brocade.", existingAlias.getName());
                if (StringUtils.equals(existingAlias.getName(), updateAlias.getNewName())) {
                    _log.info("The existing alias already has the requested name {}. Ignoring.", existingAlias.getName());
                } else {
                    _log.error("A request is made to update alias name from {} to {}. Rename is not supported for Brocade",
                            existingAlias.getName(), updateAlias.getNewName());
                    throw NetworkDeviceControllerException.exceptions.renameAliasNotSupported(alias.getName());
                }
            }

            // check if the alias is how we expect it to be
            if (!StringUtils.isEmpty(updateAlias.getAddress()) &&
                    !StringUtils.equalsIgnoreCase(existingAlias.getAddress(), updateAlias.getAddress())) {
                _log.info("The existing alias has a WWN other than the expected {}. It will not be updated.", updateAlias.getAddress());
                throw NetworkDeviceControllerException.exceptions.aliasWithDifferentWwnExists(alias.getName(),
                        existingAlias.getAddress(), updateAlias.getAddress());
            }

            if (!StringUtils.isEmpty(updateAlias.getNewAddress())) {
                if (StringUtils.equalsIgnoreCase(existingAlias.getAddress(), updateAlias.getNewAddress())) {
                    _log.info("The existing alias already has the requested WWN {}. WWN will not change.", existingAlias.getAddress());
                } else {
                    _log.info("Updating alias member from {} to {}", existingAlias.getAddress(), updateAlias.getNewAddress());
                    _smisHelper.removeZoneOrAliasMember(client, (CIMObjectPath) existingAlias.getCimMemberPath(),
                            (CIMObjectPath) existingAlias.getCimObjectPath(), false);
                    success = _smisHelper.addZoneOrAliasMember(client, zoneServiceIns, fabricWwn,
                            (CIMObjectPath) existingAlias.getCimObjectPath(), updateAlias.getNewAddress());
                    // If member change fails, exit this function
                    if (!success) {
                        return success;
                    }
                }
            }
        } else {
            throw NetworkDeviceControllerException.exceptions.aliasNotFound(updateAlias.getName());
        }
        return success;
    }

    @Override
    public BiosCommandResult removeAliases(NetworkSystem networkSystem, List<ZoneWwnAlias> aliases,
            String fabricId, String fabricWwn) throws NetworkDeviceControllerException {
        return executeInSession(networkSystem, aliases, fabricId, fabricWwn, "checkAndRemoveAlias", "remove aliases");
    }

    @Override
    public BiosCommandResult updateAliases(NetworkSystem networkSystem, List<ZoneWwnAliasUpdate> aliases,
            String fabricId, String fabricWwn) throws NetworkDeviceControllerException {
        List<ZoneWwnAlias> newList = new ArrayList<ZoneWwnAlias>();
        newList.addAll(aliases);
        return executeInSession(networkSystem, newList, fabricId, fabricWwn,
                "checkAndUpdateAlias", "update aliases");
    }

    /**
     * Common code for opening sessions and executing alias-type commands
     * 
     * @param networkSystem the network system when the commands will execute
     * @param aliases a list of aliases to be created/updated/deleted
     * @param fabricId the name of fabric where the aliases will be changed
     * @param fabricWwn the WWN of fabric where the aliases will be changed
     * @param methodName the method to be executed
     * @param methodLogName the method name to be used for logging
     * @return the command that contains a map of results-per-alias keyed
     *         by alias name
     * @throws NetworkDeviceControllerException
     */
    private BiosCommandResult executeInSession(NetworkSystem networkSystem, List<ZoneWwnAlias> aliases,
            String fabricId, String fabricWwn, String methodName, String methodLogName)
            throws NetworkDeviceControllerException {
        // a alias-name-to-result map to hold the results for each alias
        Map<String, String> aliasUpdateResults = new HashMap<String, String>();
        if (aliases.isEmpty()) {
            throw DeviceControllerException.exceptions.entityNullOrEmpty("aliases");
        }
        WBEMClient client = getNetworkDeviceClient(networkSystem);

        CIMInstance zoneServiceIns = null;
        try {
            if (fabricWwn == null) {
                fabricWwn = _smisHelper.getFabricWwn(client, fabricId);
            } else {
                validateFabric(networkSystem, fabricWwn, fabricId);
            }
            _log.info("{} started.", methodLogName);
            _log.info("Attempting to start a zoning session");
            zoneServiceIns = _smisHelper.startSession(client, fabricId, fabricWwn);
            if (zoneServiceIns == null) {
                _log.info("Failed to start a zoning session.");
                throw NetworkDeviceControllerException.exceptions.startZoningSessionFailed();
            }

            Method method = getClass().getMethod(methodName, new Class[] { WBEMClient.class,
                    CIMInstance.class, String.class, String.class, ZoneWwnAlias.class });
            for (ZoneWwnAlias alias : aliases) {
                try {
                    if ((Boolean) method.invoke(this, client, zoneServiceIns, fabricId, fabricWwn, alias)) {
                        aliasUpdateResults.put(alias.getName(), SUCCESS);
                    } else {
                        aliasUpdateResults.put(alias.getName(), NO_CHANGE);
                    }
                } catch (Exception ex) {
                    aliasUpdateResults.put(alias.getName(), ERROR + " : " + ex.getMessage());
                    _log.info("Exception was encountered but will try the rest of the batch. Error message: ", ex);
                }
            }
            _log.info("Attempting to close zoning session.");
            // If no aliases were changed, just close the session without commit and return.
            if (!hasResult(aliasUpdateResults, SUCCESS)) {
                _log.info("{} was not successful for any entity. Closing the session with no commit", methodLogName);
                if (!_smisHelper.endSession(client, zoneServiceIns, false)) {
                    _log.info("Failed to terminate zoning session. Ignoring as session may have expired.");
                }
            } else {
                // if aliases were changed, commit them before ending the session
                if (!_smisHelper.endSession(client, zoneServiceIns, true)) {
                    throw NetworkDeviceControllerException.exceptions.zoneSessionCommitFailed(fabricId);
                }
            }
            _log.info("{} completed successfully.", methodLogName);

        } catch (Exception e1) {
            try {
                if (zoneServiceIns != null) {
                    _log.info("Attempting to terminate zoning session.");
                    _smisHelper.endSession(client, zoneServiceIns, false);
                }
            } catch (WBEMException e) {
                _log.error("Failed to terminate zoning session."
                        + e.getLocalizedMessage(), e);
            }
            _log.error("Failed to " + methodLogName + ": "
                    + e1.getLocalizedMessage(), e1);
            throw NetworkDeviceControllerException.exceptions.operationFailed(methodLogName, e1);
        }
        _log.info(methodLogName + " results: " + toMessage(aliasUpdateResults));
        return getBiosCommandResult(aliasUpdateResults);
    }

    private <T extends Zone> List<String> getZoneNames(List<T> zones) {
        List<String> names = new ArrayList<String>();
        if (zones != null) {
            for (Zone zone : zones) {
                names.add(zone.getName());
            }
        }
        return names;
    }

    private void validateFabric(NetworkSystem networkSystem, String fabricWwn, String fabricId) throws NetworkDeviceControllerException {
        try {
            Map<String, String> map = getFabricIdsMap(networkSystem);
            if (!map.containsKey(fabricWwn) && !map.containsValue(fabricId)) {
                throw NetworkDeviceControllerException.exceptions.fabricNotFoundInNetwork(fabricId, networkSystem.getLabel());
            }
        } catch (Exception ex) {
            _log.info("Failed to get fabrics : " + ex.getMessage());
            throw NetworkDeviceControllerException.exceptions.failedToGetFabricsInNetwork(networkSystem.getLabel(), ex.getMessage());
        }
    }

    @Override
    public Map<String, List<Zone>> getEndpointsZones(NetworkSystem networkSystem, String fabricWwn,
            String nativeId, Collection<String> endpointsWwn) {
        Map<String, List<Zone>> zones = new HashMap<String, List<Zone>>();
        Map<CIMObjectPath, Zone> cachedZones = new HashMap<CIMObjectPath, Zone>();
        try {
            WBEMClient client = getNetworkDeviceClient(networkSystem);
            for (String endpointWwn : endpointsWwn) {
                _log.info("getting zones for endpoint {} in network {} ", endpointWwn,
                        nativeId == null ? fabricWwn : nativeId);
                zones.put(endpointWwn, _smisHelper.getEndpointZones(client, fabricWwn, endpointWwn, cachedZones));
            }
        } catch (Exception ex) {
            _log.info("Failed to get zones for endpoints {} : ", endpointsWwn, ex.getMessage());
            throw NetworkDeviceControllerException.exceptions.failedToGetEndpointZones(
                    endpointsWwn, networkSystem.getLabel(), ex.getMessage());
        }
        return zones;
    }

    /**
     * Get the value of the system configuration that governs how endpoints will be retrieved
     * from the SMIS provider.
     * <ol>
     * <li>The default way of getting TopologyView instances is by fabric. This can be slow for some configurations</li>
     * <li>The alternative way is to get all TopologyView instances and group them into theit fabrics using DependentSystem property. This
     * does not work well for FDMI hosts as multiple associations need to be traversed. This mode should only be used by customer not
     * deploying FDMI</li>
     * </ol>
     * 
     * @return
     */
    private boolean discoverEndpointsByFabric() {
        boolean byFabric = false; // default to true
        try {
            byFabric = Boolean.valueOf(ControllerUtils.getPropertyValueFromCoordinator(
                    _coordinator, "controller_ns_brocade_discovery_by_fabric_association"));
        } catch (Exception ex) {
            _log.warn("Failed to get the values for controller_ns_brocade_discovery_by_fabric_association from system configurations "
                    + ex.getMessage());
        }
        return byFabric;
    }

    @Override
	public boolean isCapableOfRouting(NetworkSystem networkSystem) {
		return true;
	}

	@Override
	public void determineRoutedNetworks(NetworkSystem networkSystem) {	
		//Currently, this method just returns for Brocade as there is nothing to compute here.
		return;
	}
}