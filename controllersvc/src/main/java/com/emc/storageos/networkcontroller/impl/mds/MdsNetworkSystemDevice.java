/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.networkcontroller.impl.mds;

import java.net.URI;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.math.IntRange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.FCEndpoint;
import com.emc.storageos.db.client.model.Network;
import com.emc.storageos.db.client.model.NetworkSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.networkcontroller.SSHSession;
import com.emc.storageos.networkcontroller.exceptions.NetworkDeviceControllerException;
import com.emc.storageos.networkcontroller.impl.NetworkSystemDevice;
import com.emc.storageos.networkcontroller.impl.NetworkSystemDeviceImpl;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.util.InvokeTestFailure;
import com.emc.storageos.util.NetworkLite;
import com.emc.storageos.util.NetworkUtil;
import com.emc.storageos.volumecontroller.ControllerException;
import com.emc.storageos.volumecontroller.impl.BiosCommandResult;
import com.emc.storageos.volumecontroller.impl.ControllerUtils;
import com.google.common.collect.Sets;

public class MdsNetworkSystemDevice extends NetworkSystemDeviceImpl implements NetworkSystemDevice {
    private static final Logger _log = LoggerFactory.getLogger(MdsNetworkSystemDevice.class);
    private static final String MDS_ROUTED_INDICATOR = "Virtual Device";
    private final String wwnRegex = "([0-9A-Fa-f][0-9A-Fa-f]:){7}[0-9A-Fa-f][0-9A-Fa-f]";

    private static volatile CoordinatorClient _coordinator;
    private static volatile DbClient _dbClient;

    /**
     * Sets up a session. Gets session parameters from the NetworkSystem.
     * 
     * @param networkSystem NetworkSystem
     * @return MDSDialog representing the session
     * @throws NetworkDeviceControllerException
     */
    private MDSDialog setUpDialog(NetworkSystem networkSystem) throws NetworkDeviceControllerException {
        try {
            SSHSession session = new SSHSession();
            session.connect(networkSystem.getIpAddress(), networkSystem.getPortNumber(), networkSystem.getUsername(), networkSystem.getPassword());
            MDSDialog dialog = new MDSDialog(session, getDefaultTimeout());
            dialog.initialize();
            return dialog;
        } catch (Exception ex) {
            String exMsg = ex.getLocalizedMessage();
            if (exMsg.equals("Auth fail")) {
                exMsg = "Authorization Failed";
            }
            if (exMsg.equals("timeout: socket is not established")) {
                exMsg = "Connection Failed";
            }
            String msg = MessageFormat.format("Could not connect to device {0}: {1}", networkSystem.getLabel(), exMsg);
            _log.error(msg);
            throw NetworkDeviceControllerException.exceptions.setUpDialogFailed(networkSystem.getLabel(), exMsg, ex);
        }
    }

    /**
     * Disconnect a session. Sends an "exit" command to log out.
     * 
     * @param dialog
     */
    private void disconnect(MDSDialog dialog) {
        if (dialog != null) {
            dialog.send("exit\n");
            dialog.getSession().disconnect();
        }
    }

    @Override
    public BiosCommandResult doConnect(NetworkSystem network) {
        BiosCommandResult result = null;
        MDSDialog dialog = null;
        try {
            dialog = setUpDialog(network);
            String[] versInfo = dialog.showVersion();
            result = BiosCommandResult.createSuccessfulResult();
            if (versInfo[0].startsWith("MDS") == false && versInfo[0].startsWith("Nexus") == false) {
                ServiceError svcError = NetworkDeviceControllerException.errors.doConnectFailedNotMds(
                        network.getLabel());
                result = BiosCommandResult.createErrorResult(svcError);
            }
        } catch (Exception ex) {
            ServiceError svcError = NetworkDeviceControllerException.errors.doConnectFailed(
                    network.getLabel());
            result = BiosCommandResult.createErrorResult(svcError);
        } finally {
            disconnect(dialog);
        }
        _log.info("Connetwork to NetworkSystem " + (result.isCommandSuccess() ? "successful " : "failed ") + result.getMessage());
        return result;
    }

    @Override
    public BiosCommandResult doDisconnect(NetworkSystem network) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public List<FCEndpoint> getPortConnections(NetworkSystem network, Map<String, Set<String>> routedEndpoints) throws Exception {
        MDSDialog dialog = null;
        try {
            dialog = setUpDialog(network);
            List<FCEndpoint> connections = dialog.showFcnsDatabase(null);
            // removing all the endpoints that are associated by routing
            Iterator<FCEndpoint> itr = connections.iterator();
            FCEndpoint ep = null;
            while (itr.hasNext()) {
                ep = itr.next();
                if (MDS_ROUTED_INDICATOR.equalsIgnoreCase(ep.getSwitchInterface()) && ep.getFabricWwn() != null) {
                    Set<String> netRoutedEndpoints = routedEndpoints.get(ep.getFabricWwn());
                    if (netRoutedEndpoints == null) {
                        netRoutedEndpoints = new HashSet<String>();
                        routedEndpoints.put(ep.getFabricWwn(), netRoutedEndpoints);
                    }
                    netRoutedEndpoints.add(ep.getRemotePortName());
                    itr.remove();
                }
            }
            dialog.populateConnectionByIvrZone(routedEndpoints);
            return connections;
        } catch (Exception ex) {
            _log.error("Cannot read FCNS database from device: " + network.getLabel() + ": " + ex.getLocalizedMessage());
            throw ex;
        } finally {
            disconnect(dialog);
        }
    }

    @Override
    public List<String> getFabricIds(NetworkSystem network) throws Exception {
        MDSDialog dialog = null;
        try {
            dialog = setUpDialog(network);
            Map<Integer, Vsan> vsanMap = dialog.showVsan(false);
            List<String> fabricIds = new ArrayList<String>();
            for (Integer vsanId : vsanMap.keySet()) {
                fabricIds.add(vsanId.toString());
            }
            return fabricIds;
        } catch (Exception ex) {
            _log.error("Cannot read fabric ids: " + ex.getLocalizedMessage());
            throw ex;
        } finally {
            disconnect(dialog);
        }
    }

    @Override
    public Map<String, String> getFabricIdsMap(NetworkSystem network) throws Exception {
        Map<String, String> fabricIdsMap = new HashMap<String, String>();
        MDSDialog dialog = null;
        try {
            dialog = setUpDialog(network);
            Map<Integer, String> vsanWwnMap = dialog.getVsanWwns(null);
            for (Integer v : vsanWwnMap.keySet()) {
                fabricIdsMap.put(vsanWwnMap.get(v).toUpperCase(), String.valueOf(v));
            }
        } catch (Exception ex) {
            _log.error("Cannot get fabric ids map for network device "
                    + network.getLabel() + ": " + ex.getLocalizedMessage());
            throw ex;
        } finally {
            disconnect(dialog);
        }
        return fabricIdsMap;
    }

    @Override
    public List<Zoneset> getZonesets(NetworkSystem network, String fabricId, String fabricWwn, String zoneName, boolean excludeMembers,
    		boolean excludeAliases) throws Exception {
        MDSDialog dialog = null;
        try {
            dialog = setUpDialog(network);
            Integer vsanId = checkVsanFabric(dialog, fabricId, fabricWwn);
            List<Zoneset> zonesets = dialog.showZoneset(vsanId, true, zoneName, excludeMembers, excludeAliases);
            return zonesets;
        } catch (Exception ex) {
            _log.error("Cannot get zones: " + ex.getLocalizedMessage());
            throw ex;
        } finally {
            disconnect(dialog);
        }
    }

    /**
     * Checks to see if the fabricId matches the fabricWwn. If a fabricWwn is supplied,
     * and it can be matched to a vsan, that fabricId will be used.
     * 
     * @param fabricId - Normally the VSAN id as a string
     * @param fabricWwn - optional fabric WWN
     * @return vsanId
     */
    private Integer checkVsanFabric(MDSDialog dialog, String fabricId, String fabricWwn)
            throws NetworkDeviceControllerException {
        if (fabricWwn != null && fabricWwn.matches(wwnRegex)) {
            Map<Integer, String> vsanWwnMap = null;
            try {
                // Optimal case:
                // Both fabricId and fabricWwn supplied; fabricId matches vsan containing WWN
                Integer vsanId = new Integer(fabricId);
                vsanWwnMap = dialog.getVsanWwns(new Integer(fabricId));
                String vsanwwn = vsanWwnMap.get(vsanId);
                if (null != vsanwwn && vsanwwn.equalsIgnoreCase(fabricWwn)) {
                    return vsanId;
                }
            } catch (Exception ex) {
                _log.warn("Exception while getting vsan wwns for {}", fabricId, ex);
            }
            // fabricId mal-formated (i.e. not a Vsan number) or doesn't match WWN
            vsanWwnMap = dialog.getVsanWwns(null);
            for (Integer v : vsanWwnMap.keySet()) {
                if (fabricWwn.equalsIgnoreCase(vsanWwnMap.get(v))) {
                    return v;
                }
            }
            throw NetworkDeviceControllerException.exceptions.checkVsanFabricFailedNotFound(
                    fabricId, fabricWwn);
        }
        try {
            return new Integer(fabricId);
        } catch (NumberFormatException ex) {
            throw NetworkDeviceControllerException.exceptions.checkVsanFabricFailed(fabricId, ex);
        }
    }

    @Override
    public BiosCommandResult addZones(NetworkSystem networkSystem, List<Zone> zones, String fabricId, String fabricWwn,
            boolean activateZones) throws NetworkDeviceControllerException {
        BiosCommandResult result = null;
        MDSDialog dialog = null;
        Map<String, String> addedZoneNames = new HashMap<String, String>();
        try {
            dialog = setUpDialog(networkSystem);
            Integer vsanId = checkVsanFabric(dialog, fabricId, fabricWwn);

            List<IvrZone> addingIvrZones = new ArrayList<IvrZone>();
            List<Zone> addingZones = new ArrayList<Zone>();

            for (Zone zone : zones) {
                IvrZone routedZone = getRoutedZone(dialog, zone, networkSystem);

                // if zone is routed, handle it as routed network. Otherwise, handle it
                // as normal zone
                if (routedZone != null) {
                    addingIvrZones.add(routedZone);
                } else {
                    addingZones.add(zone);
                }
            }

            if (!addingZones.isEmpty()) {
                addedZoneNames.putAll(addZonesStrategy(dialog, addingZones, vsanId, activateZones));
            }

            if (!addingIvrZones.isEmpty()) {
                addedZoneNames.putAll(addIvrZonesStrategy(dialog, addingIvrZones));
            }

            _log.info("Add SAN zones results: " + toMessage(addedZoneNames));
            String msg = "Vsan: " + fabricId + ": Successfully added zones: " + addedZoneNames.toString();
            if (addedZoneNames.size() == 0) {
                msg = "Vsan: " + fabricId + ": No zones were added";
            }

            _log.info(msg);
            result = getBiosCommandResult(addedZoneNames);
        } catch (Exception ex) {
            _log.error("Cannot add zones: " + (ex.getCause() != null ? ex.getCause().getMessage() : ex.getLocalizedMessage()));
            throw ex;
        } finally {
            disconnect(dialog);
        }
        return result;
    }

    @Override
    public BiosCommandResult removeZones(NetworkSystem network, List<Zone> zones, String fabricId, String fabricWwn,
            boolean activateZones) throws NetworkDeviceControllerException {
        BiosCommandResult result = null;
        MDSDialog dialog = null;
        Map<String, String> removedZoneNames = new HashMap<String, String>();
        try {
            dialog = setUpDialog(network);
            Integer vsanId = checkVsanFabric(dialog, fabricId, fabricWwn);

            List<IvrZone> removingIvrZones = new ArrayList<IvrZone>();
            List<Zone> removingZones = new ArrayList<Zone>();
            for (Zone zone : zones) {
                IvrZone routedZone = getRoutedZone(dialog, zone, network);

                // if zone is routed, handle it as routed network. Otherwise, handle it
                // as normal zone
                if (routedZone != null) {
                    removingIvrZones.add(routedZone);

                } else {
                    removingZones.add(zone);
                }
            }
            
            //Throw artificial exception here to simulate FOD for MDS same as creating alias
            InvokeTestFailure.internalOnlyInvokeTestFailure(InvokeTestFailure.ARTIFICIAL_FAILURE_057);

            if (!removingZones.isEmpty()) {
                removedZoneNames.putAll(removeZonesStrategy(dialog, removingZones, vsanId, activateZones));
            }

            if (!removingIvrZones.isEmpty()) {
                removedZoneNames.putAll(removeIvrZonesStrategy(dialog, removingIvrZones));
            }

            _log.info("Remove VSAN zone results: " + toMessage(removedZoneNames));
            result = getBiosCommandResult(removedZoneNames);
        } catch (Exception ex) {
            _log.error("Cannot remove zones: " + (ex.getCause() != null ? ex.getCause().getMessage() : ex.getLocalizedMessage()));
            throw ex;
        } finally {
            disconnect(dialog);
        }
        return result;
    }

    /**
     * Given a dialog, add one or more zones to the active zoneset of the specified vsan.
     * This method is callable from with Bourne or from MDSDialogTest for stand-alone testing.
     * For now the only type of zone members supported are pwwn.
     * 
     * @param dialog - An MDSDialog, containing dialog state to the device
     * @param zones - List of zones to be created. Zone names will be overwritten.
     * @param vsanId - Integer vsanId
     * @param activateZones - activate active zoneset after specified zones are added
     * @return a map that contains the outcome for each zone keyed by zone name
     * @throws ControllerException
     */
    public Map<String, String> addZonesStrategy(MDSDialog dialog, List<Zone> zones, Integer vsanId, boolean activateZones)
            throws NetworkDeviceControllerException {
        waitForSession(dialog, vsanId);
        Long time = System.currentTimeMillis();

        // a zone-name-to-result map to hold the results for each zone
        Map<String, String> addedZoneNames = new HashMap<String, String>();

        // First determine if there is an active zoneset.
        Zoneset activeZoneset = getActiveZoneset(dialog, vsanId);

        // There is no active zone set. So we'll create one. TBD
        if (activeZoneset == null) {
            _log.info("No active zoneset vsan: " + vsanId);
            throw NetworkDeviceControllerException.exceptions.noActiveZonesetForFabric(vsanId.toString());
        }

        List<Zone> fabricZones = dialog.showFabricZones(vsanId);

        try {
            // Go into config mode. This allows us to change the configuration.
            dialog.config();
            boolean doZonesetClone = false;
            for (Zone zone : zones) {
                try {
                    if (createZone(dialog, zone, vsanId, fabricZones, activeZoneset)) {
                        addedZoneNames.put(zone.getName(), SUCCESS);
                        doZonesetClone = true;
                    } else {
                        addedZoneNames.put(zone.getName(), NO_CHANGE);
                    }
                } catch (Exception ex) {
                    addedZoneNames.put(zone.getName(), ERROR + ": " + ex.getMessage());
                    handleZonesStrategyException(ex, activateZones);
                }
            }
            
            //If there was any changes to the zones, do a clone of the zoneset.
            if (doZonesetClone) {
            	zonesetClone(dialog, vsanId, activeZoneset);
            }

            // if there were normal zones created, commit them
            if (hasResult(addedZoneNames, SUCCESS)) {
                // Now add all the zones to the active zoneset.
                dialog.zonesetNameVsan(activeZoneset.getName(), vsanId, false);
                for (String zoneName : addedZoneNames.keySet()) {
                    if (SUCCESS.equals(addedZoneNames.get(zoneName))) {
                        dialog.zonesetMember(zoneName, false);
                    }
                }

                dialog.exitToConfig();
                commitZones(dialog, vsanId, activateZones ? activeZoneset : null);
                dialog.copyRunningConfigToStartupFabric();
            }

            dialog.endConfig();
            time = System.currentTimeMillis() - time;
            _log.info("Zone add time (msec): " + time.toString());

            return addedZoneNames;

        } catch (Exception ex) {
            throw NetworkDeviceControllerException.exceptions.addZonesStrategyFailed(ex);
        } finally {
            safeExitSession(dialog, vsanId);
        }
    }

    /**
     * Add one or more ivr zones to the active zoneset of the specified vsan.
     * For now the only type of zone members supported are pwwn.
     * 
     * @param ivrZones - List of zones to be created. Zone names will be overwritten.
     * @param vsanId - Integer vsanId
     * @return a map that contains the outcome for each zone keyed by zone name
     * @throws ControllerException
     */
    private Map<String, String> addIvrZonesStrategy(MDSDialog dialog, List<IvrZone> ivrZones) throws NetworkDeviceControllerException {
        // list to hold ivr zone names which are added to fabric
        Map<String, String> addedIvrZoneNames = new HashMap<String, String>();
        Long time = System.currentTimeMillis();

        for (IvrZone ivrZone : ivrZones) {
            if (addIvrZone(dialog, ivrZone)) {
                addedIvrZoneNames.put(ivrZone.getName(), SUCCESS);
            } else {
                addedIvrZoneNames.put(ivrZone.getName(), NO_CHANGE);
            }
        }

        time = System.currentTimeMillis() - time;
        _log.info("Ivr Zone add time (msec): " + time.toString());

        return addedIvrZoneNames;

    }

    protected String getDefaultZonesetName(String vsanId) {
        return "Zoneset_" + vsanId;
    }

    private Zoneset createActiveZoneset(MDSDialog dialog, Integer vsanId) {
        String zonesetName = getDefaultZonesetName(vsanId.toString());
        _log.info("Attempting to create zoneset: " + zonesetName + " vsan: " + vsanId);
        try {
            dialog.config();
            dialog.zonesetNameVsan(zonesetName, vsanId, false);
            dialog.exitToConfig();
            if (dialog.isInSession()) {
                dialog.zoneCommit(vsanId);
                dialog.waitForZoneCommit(vsanId);
            }
            dialog.endConfig();
            return new Zoneset(zonesetName);
        } catch (NetworkDeviceControllerException ex) {
            _log.info("Unable to create zoneset: " + zonesetName);
            throw NetworkDeviceControllerException.exceptions.addZonesStrategyFailedNotFound(
                    vsanId.toString(), ex);
        } finally {
            safeExitSession(dialog, vsanId);
        }
    }

    /**
     * Creates the zone in the fabric's active zoneset.
     * Checks if the zone with the same name already exists before the zone is created. The
     * rules for creating a zone are:
     * If an active zone with the same name exists, ensure that all the desired members
     * are in the zone. If this is true, consider the zone created. If not, error because
     * the application is not going to modify an existing zone.
     * If an inactive zone with the same name exists, delete the inactive zone and then
     * create the new one.
     * 
     * @param dialog an MDSDialog, containing dialog state to the device
     * @param zone the zone to be created
     * @param vsanId vsan Id
     * @param zonesInFabric a list containing all the zones in the fabric,
     *            both active an inactive
     * @param activeZoneset the active zoneset in which the zone will be created
     * @throws NetworkDeviceControllerException
     */
    private boolean createZone(MDSDialog dialog, Zone zone, Integer vsanId,
            List<Zone> zonesInFabric, Zoneset activeZoneset)
            throws NetworkDeviceControllerException {

        _log.info("Creating zone: " + zone.getName() + " vsan: " + vsanId);
        boolean added = false;
        // check if an active zone with the same name exists
        Zone zoneInFabric = getZoneInFabric(zone.getName(), activeZoneset.getZones());
        if (zoneInFabric != null) {
            // if an active zone is found, but have different member size, throw exception
            if (!sameMembers(zoneInFabric, zone)) {
                throw NetworkDeviceControllerException.exceptions.activeZoneWithSameNameExists(zone.getName());
            }

            _log.info("Found existing active zone with the name " + zone.getName() + ".  No create necessary");
        } else {
            // check if an inactive zone with the same name exists
            zoneInFabric = getZoneInFabric(zone.getName(), zonesInFabric);
            if (zoneInFabric != null) {
                // delete the zone
                _log.info("Found an inactive zone with the name " + zone.getName());
                dialog.zoneNameVsan(zoneInFabric.getName(), vsanId, true);
                _log.info("Deleted inactive zone with the name " + zone.getName());
            }
            // create the new zone
            dialog.zoneNameVsan(zone.getName(), vsanId, false);

            try {
                for (ZoneMember member : zone.getMembers()) {
                    if (!StringUtils.isEmpty(member.getAlias())) {
                        dialog.zoneMemberAlias(member.getAlias());
                    } else {
                        dialog.zoneMemberPwwn(member.getAddress());
                    }
                }
            } finally {
                // be sure to exit add zone member mode
                dialog.exitToConfig();
            }
            added = true;
        }
        return added;
    }

    /**
     * Searches the collection of zone for a zone that matches name and active state.
     * 
     * @param name the zone name
     * @param zonesInFabric the list of zones to search
     * @return a zone that is matched by name. Null if a zone was not found.
     */
    private Zone getZoneInFabric(String name, List<Zone> zonesInFabric) {
        for (Zone zone : zonesInFabric) {
            if (zone.getName().equalsIgnoreCase(name)) {
                return zone;
            }
        }
        return null;
    }

    /**
     * Given an MDS dialog, removes one or more zones from the active zoneset in the specified vsan.
     * This method is callable from with Bourne or from MDSDialogTest for stand-alone testing.
     * For now the only type of zone members supported are pwwn.
     * 
     * @param dialog
     * @param zones List<Zone> - name is ignored, members are checked against existing zones
     * @param vsanId
     * @return a map that contains the outcome for each zone keyed by zone name
     * @throws NetworkControllerException
     */
    public Map<String, String> removeZonesStrategy(MDSDialog dialog, List<Zone> zones, Integer vsanId, boolean activateZones)
            throws NetworkDeviceControllerException {
        waitForSession(dialog, vsanId);
        Long time = System.currentTimeMillis();
        
        // a zone-name-to-result map to hold the results for each zone
        Map<String, String> removedZoneNames = new HashMap<String, String>();

        // First determine if there is an active zone.
        Zoneset activeZoneset = getActiveZoneset(dialog, vsanId);
        if (activeZoneset == null) {
            // if no active or default zoneset presents, consider none is removed
            String defaultZonesetName = getDefaultZonesetName(vsanId.toString());
            _log.warn("No active/default zoneset found: " + defaultZonesetName);
            throw NetworkDeviceControllerException.exceptions.noActiveZonesetForFabric(vsanId.toString());
        }

        // Find the set of zones to be actually deleted.
        // We don't attempt to delete zones that are already gone.
        // And we don't delete zones that Bourne didn't create.
        Integer[] remainingZones = new Integer[1];
        List<Zone> zonesToBeDeleted = getZonesToBeDeleted(zones, activeZoneset.getZones(), remainingZones, removedZoneNames);

        // If all zones already deleted, return.
        if (zonesToBeDeleted.isEmpty()) {
	        deleteUnassignedZones(dialog, zones, vsanId, removedZoneNames);
            return removedZoneNames;
        }
        
        try {
            dialog.config();            
            boolean doZonesetClone = zonesetClone(dialog, vsanId, activeZoneset);     
            if (doZonesetClone) {
            	dialog.zonesetNameVsan(activeZoneset.getName(), vsanId, false);
            }
                       
            for (Zone zone : zonesToBeDeleted) {  
            	 String zoneName = zone.getName();
            	 //If zoneset clones are stored on the switch, and we cannot simply delete the zone, since it will delete that zone from all the zonesets, including cloned ones.
            	 //Cloned backups would not really be backups in that case.   
            	 //Remove the zone member from the active zoneset if clones are enabled, otherwise delete the zones. 
            	if (doZonesetClone) {	               
	                _log.info("Removing zone: " + zoneName + " from zoneset: " + activeZoneset.getName() +  " in vsan: " + vsanId);
	                try {
	                	dialog.zonesetMember(zone.getName(), true);
	                    removedZoneNames.put(zoneName, SUCCESS);
	                } catch (Exception ex) {
	                    removedZoneNames.put(zoneName, ERROR + " : " + ex.getMessage());
	                    handleZonesStrategyException(ex, activateZones);
	                }	               	                	               
            	} else {
            		  _log.info("Deleting zone: " + zoneName + " in vsan: " + vsanId);
  	                try {
  	                	dialog.zoneNameVsan(zoneName, vsanId, true);
  	                    removedZoneNames.put(zoneName, SUCCESS);
  	                } catch (Exception ex) {
  	                    removedZoneNames.put(zoneName, ERROR + " : " + ex.getMessage());
  	                    handleZonesStrategyException(ex, activateZones);
  	                }            		
            	}            	            
            }
            
            dialog.exitToConfig();
                        
            if (activateZones) {
                dialog.zonesetActivate(activeZoneset.getName(), vsanId, ((remainingZones[0] == 0) ? true : false));
            }

            if (dialog.isInSession()) {
                dialog.zoneCommit(vsanId);
                dialog.waitForZoneCommit(vsanId);
            }
            dialog.copyRunningConfigToStartupFabric();
            dialog.endConfig();
            time = System.currentTimeMillis() - time;
                                       
	        deleteUnassignedZones(dialog, zones, vsanId, removedZoneNames);  
            _log.info("Zone remove time (msec): " + time.toString());
            
            return removedZoneNames;
        } catch (Exception ex) {
            throw NetworkDeviceControllerException.exceptions.removeZonesStrategyFailed(ex);
        } finally {
            safeExitSession(dialog, vsanId);
        }
    }

	/**
	 * Unassigned zones are those zones that do not belong to any zoneset. These zonesets occupy space in the zoning database.
	 * This method looks at the "show zone analysis" for a given VSAN and removes the zones if the zones were part of the delete operation. 
	 *  
	 * @param dialog - handle to dialog
	 * @param zones - list of zones that was requested to be deleted. 
	 * @param vsanId - vsan 
	 * @param removedZoneNames - list of zones that were removed.
	 */
	private void deleteUnassignedZones(MDSDialog dialog, List<Zone> zones, Integer vsanId,
						Map<String, String> removedZoneNames) {
		//Delete unassigned zones.
		//Zones that are removed, but end up in the "show zone analysis" under Unassigned zones are zones that do not belong 
		//to any zonesets. These zones can be removed. We dont remove all such zones, but only those zones that are requested to be 
		//deleted in the first place but are now not part of any zonesets.
		dialog.config();
		List<String> unassignedZones = showZoneAnalysisVsan(dialog, vsanId);   	
		for (Zone zone : zones) {
				_log.info(zone.getName() + " was requested to be deleted");
				if (unassignedZones.contains(zone.getName())) {
					_log.info("Unassigned zone: " + zone.getName() +  "matched");
					 try {	
		            	dialog.zoneNameVsan(zone.getName(), vsanId, true);
		                removedZoneNames.put(zone.getName(), SUCCESS);
		        	 } catch (Exception ex) {
		                _log.info("Could not remove stale zone : " +  zone.getName());
		             }            	
				}
			}            
			
		 if (dialog.isInSession()) {
		     dialog.zoneCommit(vsanId);
		     dialog.waitForZoneCommit(vsanId);
		 }
	}
    
    private List<String> showZoneAnalysisVsan(MDSDialog dialog, Integer vsanId) {
    	       List<String> unassignedZones = new ArrayList<>();
    	       unassignedZones = dialog.zoneAnalysisVsan(vsanId);
    	       return unassignedZones;
    	    }

    /**
     * 
     * 
     * @param removingIvrZones
     * @param vsanId
     * @return a map that contains the outcome for each zone keyed by zone name
     */
    private Map<String, String> removeIvrZonesStrategy(MDSDialog dialog, List<IvrZone> removingIvrZones) {
        Long time = System.currentTimeMillis();

        // a zone-name-to-result map to hold the results for each zone
        Map<String, String> removedZoneNames = new HashMap<String, String>();

        for (IvrZone ivrZone : removingIvrZones) {
            if (removeIvrZone(dialog, ivrZone)) {
                removedZoneNames.put(ivrZone.getName(), SUCCESS);
            } else {
                removedZoneNames.put(ivrZone.getName(), NO_CHANGE);
            }
        }

        time = System.currentTimeMillis() - time;
        _log.info("Ivr Zone remove time (msec): " + time.toString());
        return removedZoneNames;
    }

    /**
     * Verify whether an ivrZone is in any of ivr zonesets
     * 
     * @param removingIvrZones
     * @param ivrZonesetInFabric
     * @return list of ivr zone to be deleted
     */
    private boolean isInZonesets(IvrZone ivrZone, List<IvrZoneset> ivrZonesetInFabric) {
        boolean inZoneset = false;
        for (IvrZoneset ivrZoneset : ivrZonesetInFabric) {
            inZoneset = ivrZoneset.contains(ivrZone);
            if (inZoneset) {
                break;
            }
        }

        return inZoneset;
    }

    /**
     * Check to see if the Vsan is already in a session. If so wait until it might be cleared,
     * or throw an NetworkControllerException. We don't want to assume control of a pre-existing
     * session.
     * 
     * @param dialog
     * @param fabricId
     * @throws NetworkDeviceControllerException
     */
    private void waitForSession(MDSDialog dialog, Integer vsanId)
            throws NetworkDeviceControllerException {
        boolean isInSession = dialog.isSessionInProgress(vsanId);

        /*
         * compute retry attempts based on the configured timeout.
         * will retry in every SLEEP_TIME_PER_RETRY until exceeded the timeout value
         * Add one more attempt to ensure timeout value is reached
         */
        int retryAttempts = getDefaultTimeout() / MDSDialogProperties.SLEEP_TIME_PER_RETRY + 1;

        for (int retrys = 0; isInSession == true && retrys < retryAttempts; retrys++) {
            try {
                Thread.sleep(MDSDialogProperties.SLEEP_TIME_PER_RETRY);
            } catch (InterruptedException ex) {
                _log.warn(ex.getLocalizedMessage());
            }
            isInSession = dialog.isSessionInProgress(vsanId);
        }
        if (isInSession) {
            throw NetworkDeviceControllerException.exceptions.waitForSessionFailedTimeout(vsanId.toString());
        }
    }

    @Override
    public String getVersion(NetworkSystem network) throws NetworkDeviceControllerException {
        MDSDialog dialog = null;
        String[] versInfo = null;
        try {
            dialog = setUpDialog(network);
            versInfo = dialog.showVersion();
        } catch (Exception ex) {
            throw NetworkDeviceControllerException.exceptions.getVersionFailed(
                    ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage(), ex);
        } finally {
            disconnect(dialog);
        }
        return versInfo[1];
    }

    @Override
    public String getUptime(NetworkSystem network) throws NetworkDeviceControllerException {
        MDSDialog dialog = null;
        String systemUptime = null;
        try {
            dialog = setUpDialog(network);
            systemUptime = dialog.showSystemUptime();
        } catch (Exception ex) {
            throw NetworkDeviceControllerException.exceptions.getUptimeFailed(
                    ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage(), ex);
        } finally {
            disconnect(dialog);
        }
        return systemUptime;
    }

    @Override
    public Set<String> getRoutedEndpoints(NetworkSystem networkSystem,
            String fabricId, String fabricWwn) throws Exception {

        MDSDialog dialog = null;
        Set<String> routedEndpoints = Sets.newHashSet();

        try {
            dialog = setUpDialog(networkSystem);
            List<FCEndpoint> fcEndpoints = dialog.showFcnsDatabase(Integer.valueOf(fabricId));

            if (fcEndpoints != null) {
                for (FCEndpoint fcEndpoint : fcEndpoints) {
                    if (MDS_ROUTED_INDICATOR.equalsIgnoreCase(fcEndpoint.getSwitchInterface())) {
                        routedEndpoints.add(fcEndpoint.getRemotePortName());
                    }
                }
            }
        } finally {
            if (dialog != null) {
                disconnect(dialog);
            }
        }
        return routedEndpoints;

    }

    /**
     * Creates the ivr zone in the fabric's active zoneset.
     * Checks if the zone with the same name already exists before the zone is created. The
     * rules for creating a zone are:
     * If an active ivr zone with the same name exists, ensure that all the desired members
     * are in the ivr zone. If this is true, consider the ivr zone created. If not, error because
     * the application is not going to modify an existing zone.
     * If an inactive ivr zone with the same name exists, delete the inactive zone and then
     * create the new one.
     * 
     * @param dialog an MDSDialog, containing dialog state to the device
     * @param ivrZone the zone to be created
     * @param vsanId vsan Id
     * @param ivrZonesInFabric a list containing all the zones in the fabric,
     *            both active an inactive
     * @param activeIvrZoneset the active ivr zoneset in which the zone will be created
     * @throws NetworkDeviceControllerException
     */
    private boolean createIvrZone(MDSDialog dialog, IvrZone ivrZone, List<IvrZone> ivrZonesInFabric, IvrZoneset activeIvrZoneset)
            throws NetworkDeviceControllerException {
        _log.info("Creating ivr zone: " + ivrZone.getName());
        boolean added = false;
        // check if an active zone with the same name exists
        IvrZone activeIvrZone = getIvrZoneInFabric(ivrZone.getName(), activeIvrZoneset.getZones());
        if (activeIvrZone != null) {
            _log.info("Found an active ivr zone with the name " + activeIvrZone.getName());
            // if an active zone is found, get its members
            if (activeIvrZone.getMembers().size() != ivrZone.getMembers().size()) {
                throw NetworkDeviceControllerException.exceptions.activeZoneWithSameNameExists(activeIvrZone.getName());
            }
            for (IvrZoneMember member : ivrZone.getMembers()) {
                if (!activeIvrZone.contains(member)) {
                    // I have a active zone with the same name but it does not have all the members we
                    // need - at this time we're not modifying zones that already exist
                    // and this one cannot be used without adding the missing members, so error
                    _log.info("Zone member pwwn/vsanId: " + member.getPwwn() + "/" + member.getVsanId()
                            + " was not found in the active zone.");
                    throw NetworkDeviceControllerException.exceptions.activeZoneWithSameNameExists(activeIvrZone.getName());
                }
            }
        } else {
            // check if an inactive zone with the same name exists
            IvrZone ivrZoneInFabric = getIvrZoneInFabric(ivrZone.getName(), ivrZonesInFabric);
            if (ivrZoneInFabric != null) {
                // delete the zone
                _log.info("Found an inactive zone with the name " + ivrZoneInFabric.getName());
                dialog.ivrZoneName(ivrZoneInFabric.getName(), true);
                _log.info("Deleted inactive zone with the name " + ivrZoneInFabric.getName());
            }

            // create the new zone
            dialog.ivrZoneName(ivrZone.getName(), false);
            for (IvrZoneMember member : ivrZone.getMembers()) {
                dialog.ivrZoneMember(member.getPwwn(), member.getVsanId(), false);
            }

            dialog.exitToConfig();
            added = true;
        }
        return added;
    }

    /**
     * Zones were previously added into fabric, thus need to add them into appropriate vsan as memebers
     * then activate the vsan.
     * 
     * @param dialog
     * @param zoneNames list of zone to add to vsan
     * @param vsanId
     * @param activeZoneset
     */
    private void commitZones(MDSDialog dialog, Integer vsanId, Zoneset activeZoneset) {
        // Activate the active zoneset.
        if (activeZoneset != null) {
            dialog.zonesetActivate(activeZoneset.getName(), vsanId, false);
        } 
        
        // dialog.exitToConfig(); -- no need for exitToConfig, because activate zoneset would exit
        // If enhanced zoning is enabled, we will be in a session, and we must commit.
        if (dialog.isInSession()) {
            dialog.zoneCommit(vsanId);
            dialog.waitForZoneCommit(vsanId);
        }
    }

	/**
	 * Perform a "zoneset clone" of the zoneset in the zoning operation
	 * @param dialog
	 * @param vsanId
	 * @param activeZoneset
	 */
	private boolean zonesetClone(MDSDialog dialog, Integer vsanId, Zoneset activeZoneset) {
		boolean doZonesetClone = true;
		boolean allowZonesIfZonesetCloneFails = true;
		try {
			doZonesetClone = Boolean.valueOf(ControllerUtils.getPropertyValueFromCoordinator(_coordinator,
                "controller_mds_clone_zoneset")) ;
			allowZonesIfZonesetCloneFails = Boolean.valueOf(ControllerUtils.getPropertyValueFromCoordinator(_coordinator,
                "controller_mds_allow_zoneset_commit")) ;
		} catch (Exception e) {
			_log.warn("Zoneset clone properties not set");
		}
        
        if (doZonesetClone) {
        	_log.info(String.format("Cloning zoneset %s", activeZoneset.getName()));
        	try {
        		dialog.zonesetClone(vsanId, activeZoneset.getName());
        	} catch (NetworkDeviceControllerException nde) {
        		_log.info("Failed to create zoneset clone. Reason : ", nde.getMessage());
        		if (!allowZonesIfZonesetCloneFails) {        			
        			throw nde;
        		}
        	}        	
        } else {
        	_log.info(String.format("controller_mds_clone_zoneset is false, NOT Cloning zoneset %s", activeZoneset.getName()));
        }
        return doZonesetClone;
	}

    /**
     * Ivr zones were previously added to fabric, add them to active zone set, then activate it.
     * 
     * @param dialog
     * @param activeIvrZoneset
     */
    private void commitIvrZones(MDSDialog dialog, IvrZoneset activeIvrZoneset) {
        // activate zone set
        dialog.ivrZonesetName(activeIvrZoneset.getName(), true);
        dialog.ivrCommit();

        dialog.waitForIvrZonesetActivate();
    }

    /**
     * Create an ivr zone set with embed switch's wwn, and activate it.
     * 
     * @param dialog
     * @return
     */
    private IvrZoneset createActiveIvrZoneset(MDSDialog dialog) {
        String zonesetName = "IVR_Zoneset_" + dialog.showSwitchWwn().replace(':', '_');
        _log.info("Attempting to create active ivr zoneset: " + zonesetName);
        try {
            dialog.config();
            dialog.ivrZonesetName(zonesetName, true);
            dialog.exitToConfig();
            if (dialog.isInSession()) {
                dialog.ivrCommit();
            }
            dialog.endConfig();
            return new IvrZoneset(zonesetName);
        } catch (NetworkDeviceControllerException ex) {
            _log.info("Unable to create zoneset: " + zonesetName);
            throw NetworkDeviceControllerException.exceptions.addZonesStrategyFailedNotFound(
                    zonesetName, ex);
        } finally {
            if (dialog.isInSession()) {
                dialog.endConfig();
                dialog.config();
                dialog.ivrAbort();
                dialog.endConfig();
            }
        }
    }

    /**
     * Check if given ivr zone name is existed in list of ivr zone
     * 
     * @param name the zone name
     * @param ivrZones the list of zones to search
     * @return an ivr zone that is matched by name. Null if a zone was not found.
     */
    private IvrZone getIvrZoneInFabric(String name, List<IvrZone> ivrZones) {
        for (IvrZone zone : ivrZones) {
            if (zone.getName().equalsIgnoreCase(name)) {
                return zone;
            }
        }
        return null;
    }

    /**
     * Get corresponded ivr switch for the given network
     * 
     * @param dialog - dialog session for borderNetworkSystem
     * @param borderNetworkSystem - switch which has a dialog session attached
     * @param networkLite
     * @return
     */
    private NetworkSystem getIvrNetworkSystem(MDSDialog dialog, NetworkSystem borderNetworkSystem, NetworkLite networkLite) {
        NetworkSystem ivrNetworkSystem = null;
        if (networkLite != null && networkLite.getNetworkSystems() != null) {
            for (String networkSystemId : networkLite.getNetworkSystems()) {
                ivrNetworkSystem = _dbClient.queryObject(NetworkSystem.class, URI.create(networkSystemId));

                // if potential ivrNetworkSystem is the same as borderNetworksystem, then use the attached dialog session to
                // verified whether is is an IVR or not. This check is to avoid open a new dialog session of the borderNetworkSystem.
                boolean isIvrEnabled = StringUtils.equals(borderNetworkSystem.getId().toString(), ivrNetworkSystem.getId().toString()) ?
                        dialog.isIvrEnabled() : isIvrEnabled(ivrNetworkSystem);
                if (isIvrEnabled) {
                    break;
                } else {
                    ivrNetworkSystem = null;
                }
            }
        }

        return ivrNetworkSystem;
    }

    /**
     * Verify if vsan in the list are the same
     * 
     * @param ivrZoneMembers
     * @return true if all vsan in the list are the same, false otherwise
     */
    private boolean areIvrZoneMembersInSameNetwork(List<IvrZoneMember> ivrZoneMembers) {
        boolean sameNetwork = true;
        if (ivrZoneMembers != null && ivrZoneMembers.size() > 1) {

            /*
             * check if 2 ports are routed if they are in different networks
             */
            Integer baseVsan = ivrZoneMembers.get(0).getVsanId();
            for (IvrZoneMember ivrZoneMember : ivrZoneMembers) {
                if (!baseVsan.equals(ivrZoneMember.getVsanId())) {
                    sameNetwork = false;
                    break;
                }
            }
        }

        return sameNetwork;

    }

    /**
     * Construct an in memory ivr zone, if applied.
     * 
     * @param dialog dialog session to borderNetworkSystem
     * @param zone
     * @param borderNetworkSystem ivr border network which has a dialog session open
     * @return an ivr zone
     */
    private IvrZone getRoutedZone(MDSDialog dialog, Zone zone, NetworkSystem borderNetworkSystem) {
        IvrZone ivrZone = null;

        Set<String> vsanIds = Sets.newHashSet(); // member vsan in ivr zone
        if (zone != null && zone.getMembers() != null && zone.getMembers().size() == 2) {
            ivrZone = new IvrZone(zone.getName());

            List<IvrZoneMember> ivrZoneMembers = ivrZone.getMembers();
            // Map<String, String> aliasDatabase = dialog.showDeviceAliasDatabase();

            // map zone member address to corresponded network system
            Map<String, NetworkLite> networkLiteMap = new HashMap<String, NetworkLite>();

            // traverse each pwwn in given zone. If pwwn 's network is routed, added pwwn and its network
            // to list of ivr zone member.
            for (ZoneMember zoneMember : zone.getMembers()) {

                // if zone member pwwn was not set, resolve it. If not resolvable,
                // skip it
                // TODO - need to revisit if ViPR decides to support alias for IVR
                if (StringUtils.isEmpty(zoneMember.getAddress())) {
                    break;
                }

                NetworkLite networkLite = NetworkUtil.getEndpointNetworkLite(zoneMember.getAddress().trim(), _dbClient, vsanIds);

                if (networkLite != null) {
                    // cached networklite for later reference
                    networkLiteMap.put(zoneMember.getAddress(), networkLite);

                    // if pwwn 's parent network is a routed network, construct an ivr zone member
                    Set<String> routedNetworks = networkLite.getRoutedNetworks();
                    if (routedNetworks != null && !routedNetworks.isEmpty()) {
                        Integer networkVsanId = Integer.valueOf(networkLite.getNativeId());
                        ivrZoneMembers.add(new IvrZoneMember(zoneMember.getAddress(), networkVsanId));
                        vsanIds.add(networkVsanId.toString());
                    }
                }
            }

            /*
             * if ivr zone members are in the same network, then they are not routed.
             * Then, null out ivr zone.
             */
            if (areIvrZoneMembersInSameNetwork(ivrZoneMembers) || ivrZoneMembers.size() <= 1) {
                ivrZoneMembers.clear();
                ivrZone = null;
            } else {
                // if it is an ivr zone,
                // loop through to get ivr networksystem to later perform ivr cli on
                for (IvrZoneMember ivrZoneMember : ivrZoneMembers) {

                    // if no ivr network system was set for ivr zone, find and set it
                    if (ivrZone.getIvrNetworkSystem() == null) {
                        NetworkLite networkLite = networkLiteMap.get(ivrZoneMember.getPwwn());
                        ivrZone.setIvrNetworkSystem(getIvrNetworkSystem(dialog, borderNetworkSystem, networkLite));
                    }

                    // if an ivr network system is set, exit loop
                    if (ivrZone.getIvrNetworkSystem() != null) {
                        break;
                    }
                }
            }
        }

        return ivrZone;
    }

    /**
     * Check the given vsan is contained in ivr topology
     * 
     * @param dialog
     * @param vsanId
     * @return
     */
    private boolean isIvrVsan(MDSDialog dialog, Integer vsanId) {
        boolean isIvrVsan = false;
        List<IvrVsanConfiguration> ivrVsansList = dialog.showIvrVsanTopology();
        for (IvrVsanConfiguration ivrVsans : ivrVsansList) {
            if (ivrVsans.isIvrVsan(vsanId)) {
                isIvrVsan = true;
                break;
            }
        }

        return isIvrVsan;
    }

    /**
     * Verify if given network system is ivr enabled
     * 
     * @param networkSystem
     * @return
     * @throws NetworkDeviceControllerException
     */
    private boolean isIvrEnabled(NetworkSystem networkSystem) throws NetworkDeviceControllerException {
        MDSDialog dialog = null;
        try {
            dialog = setUpDialog(networkSystem);
            return dialog.isIvrEnabled();
        } catch (NetworkDeviceControllerException ex) {
            _log.error("Cannot remove zones: " + (ex.getCause() != null ? ex.getCause().getMessage() : ex.getLocalizedMessage()));
            throw ex;
        } finally {
            disconnect(dialog);
        }
    }

    /**
     * remove an ivrZone for vsanId. If is being removed, return true. If edge switch and
     * ivr switch is the same, use existing dialog session. Otherwise create new session on
     * ivr switch to process ivr zone
     * 
     * @param edgeDialog - created dialog session from edge switch
     * @param ivrZone
     * @param vsanId
     * @return
     */
    private boolean removeIvrZone(MDSDialog edgeDialog, IvrZone ivrZone) {
        // check if edge and ivr switch are the same switch
        boolean isSameHost = ivrZone.getIvrNetworkSystem().getIpAddress().equals(edgeDialog.getSession().getSession().getHost());

        // use edgeDialog if edge and ivr are the same host
        MDSDialog dialog = isSameHost ? edgeDialog : null;
        try {
            if (dialog == null) {
                dialog = setUpDialog(ivrZone.getIvrNetworkSystem());
            }

            List<IvrZoneset> ivrZonesets = dialog.showIvrZonesets(false);
            if (ivrZonesets == null || !isInZonesets(ivrZone, ivrZonesets)) {
                return false;
            }

            dialog.config();

            String zoneName = ivrZone.getName();
            _log.info("Removing ivr zone: " + zoneName);

            // config ivr zone mode
            dialog.ivrZoneName(zoneName, false);

            // remove member from IvrZone
            for (IvrZoneMember ivrZoneMember : ivrZone.getMembers()) {
                dialog.ivrZoneMember(ivrZoneMember.getPwwn(), ivrZoneMember.getVsanId(), true);
            }

            // remove IvrZone from fabric
            dialog.ivrZoneName(zoneName, true);

            // zone set config mode
            IvrZoneset activeIvrZoneset = dialog.showActiveIvrZoneset();
            dialog.ivrZonesetName(activeIvrZoneset.getName(), false);

            // remove zone member from zone set
            dialog.ivrZonesetMember(zoneName, true);

            // activate zone set
            commitIvrZones(dialog, activeIvrZoneset);

            dialog.copyRunningConfigToStartupFabric();
            dialog.endConfig();

            return true;
        } catch (Exception ex) {
            throw NetworkDeviceControllerException.exceptions.removeZonesStrategyFailed(ex);
        } finally {
            if (dialog.isInSession()) {
                dialog.endConfig();
                dialog.config();
                dialog.ivrAbort();
                dialog.endConfig();
            }

            // only disconnect dialog session if new session was created in this method
            if (!isSameHost) {
                disconnect(dialog);
            }
        }
    }

    /**
     * add an ivrZone. If is being added, return true. If edge switch and
     * ivr switch is the same, use existing dialog session. Otherwise create new session on
     * ivr switch to process ivr zone
     * 
     * @param edgeDialog - created dialog session from edge switch
     * @param ivrZone
     * @param vsanId
     * @return
     */
    private boolean addIvrZone(MDSDialog edgeDialog, IvrZone ivrZone) {
        // check if edge and ivr switch are the same switch
        boolean isSameHost = ivrZone.getIvrNetworkSystem().getIpAddress().equals(edgeDialog.getSession().getSession().getHost());

        boolean added = false;
        // use edgeDialog if edge and ivr are the same host
        MDSDialog dialog = isSameHost ? edgeDialog : null;
        try {
            // initiate dialog if not same host
            if (dialog == null) {
                dialog = setUpDialog(ivrZone.getIvrNetworkSystem());
            }

            List<IvrZone> fabricIvrZones = dialog.showIvrZones(false);

            // create zone only if its end points are ivr connected
            IvrZoneset activeIvrZoneset = dialog.showActiveIvrZoneset();
            if (activeIvrZoneset == null) {
                _log.info("No active ivr zoneset...create one");
                activeIvrZoneset = createActiveIvrZoneset(dialog);
            }

            // Go into config mode. This allows us to change the configuration.
            dialog.config();

            // create IVR Zone
            added = createIvrZone(dialog, ivrZone, fabricIvrZones, activeIvrZoneset);
            if (added) {
                // zone set config mode
                dialog.ivrZonesetName(activeIvrZoneset.getName(), false);

                // add zone as member to
                dialog.ivrZonesetMember(ivrZone.getName(), false);

                commitIvrZones(dialog, activeIvrZoneset);

                dialog.copyRunningConfigToStartupFabric();
            }
            ;

            dialog.endConfig();

        } catch (Exception ex) {
            throw NetworkDeviceControllerException.exceptions.addZonesStrategyFailed(ex);
        } finally {
            if (dialog.isInSession()) {
                dialog.endConfig();
                dialog.config();
                dialog.ivrAbort();
                dialog.endConfig();
            }

            // only disconnect dialog if it was created in this method
            if (!isSameHost) {
                disconnect(dialog);
            }
        }

        return added;
    }

    /**
     * Convenient method to get Cisco MDS default timeout value from system configuration file.
     * The config value is in second, then convert to milliseconds
     * 
     * @return timeout value in milliseconds
     */
    private int getDefaultTimeout() {
        int defaultTimeout = 300 * 1000; // default to 5 minutes

        try {
            defaultTimeout = Integer.valueOf(ControllerUtils.getPropertyValueFromCoordinator(_coordinator,
                    "controller_mds_communication_timeout")) * 1000;
        } catch (Exception e) {
            _log.warn(e.getMessage());
        }
        return defaultTimeout;
    }

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
    public BiosCommandResult updateZones(NetworkSystem network, List<ZoneUpdate> updateZones,
            String fabricId, String fabricWwn, boolean activateZones) throws NetworkDeviceControllerException {
        MDSDialog dialog = null;
        Map<String, String> updatedZoneNames = null;
        try {
            dialog = setUpDialog(network);
            Integer vsanId = checkVsanFabric(dialog, fabricId, fabricWwn);

            if (!updateZones.isEmpty()) {
                updatedZoneNames = updateZonesStrategy(dialog, updateZones, vsanId, activateZones);
            }

            _log.info("Update zone results " + toMessage(updatedZoneNames));
        } catch (Exception ex) {
            _log.error("Cannot update zones: " + (ex.getCause() != null ? ex.getCause().getMessage() : ex.getLocalizedMessage()));
            throw ex;
        } finally {
            disconnect(dialog);
        }
        return getBiosCommandResult(updatedZoneNames);
    }

    /**
     * Given a dialog, update one or more zones of the specified vsan.
     * This method is callable from with Bourne or from MDSDialogTest for stand-alone testing.
     * For now the only type of zone members supported are pwwn.
     * 
     * @param dialog - An MDSDialog, containing dialog state to the device
     * @param updateZones - List of zones to be created. Zone names will be overwritten.
     * @param vsanId - Integer vsanId
     * @param activagteZone - true, activate current active zoneset
     * @return a map that contains the outcome for each zone keyed by zone name
     * @throws ControllerException
     */
    private Map<String, String> updateZonesStrategy(MDSDialog dialog, List<ZoneUpdate> updateZones, Integer vsanId, boolean activateZones)
            throws NetworkDeviceControllerException {
        waitForSession(dialog, vsanId);
        Long time = System.currentTimeMillis();

        // a zone-name-to-result map to hold the results for each zone
        Map<String, String> updatedZoneNames = new HashMap<String, String>();

        // First determine if there is an active zoneset.
        Zoneset activeZoneset = getActiveZoneset(dialog, vsanId);

        List<Zone> fabricZones = dialog.showFabricZones(vsanId);

        try {
            // Go into config mode. This allows us to change the configuration.
            dialog.config();
            zonesetClone(dialog, vsanId, activeZoneset);
            for (ZoneUpdate zone : updateZones) {
                try {
                    if (updateZone(dialog, zone, vsanId, fabricZones, activateZones)) {
                        updatedZoneNames.put(zone.getName(), SUCCESS);
                    } else {
                        updatedZoneNames.put(zone.getName(), NO_CHANGE);
                    }
                } catch (Exception ex) {
                    updatedZoneNames.put(zone.getName(), ERROR + " : " + ex.getMessage());
                    handleZonesStrategyException(ex, activateZones);
                }
            }

            if (!updatedZoneNames.isEmpty()) {
                commitZones(dialog, vsanId, activateZones ? activeZoneset : null);
                dialog.copyRunningConfigToStartupFabric();
            }

            dialog.endConfig();

            time = System.currentTimeMillis() - time;
            _log.info("Zone update time (msec): " + time.toString());

            return updatedZoneNames;

        } catch (Exception ex) {
            throw NetworkDeviceControllerException.exceptions.updateZonesStrategyFailed(ex);
        } finally {
            safeExitSession(dialog, vsanId);
        }
    }

    /**
     * Update the zone of the given fabric
     * Checks if the zone with the same name already exists before update. The
     * rules for updating a zone are:
     * It must be exist in fabric
     * 
     * @param dialog an MDSDialog, containing dialog state to the device
     * @param updateZone the zone to be update
     * @param vsanId vsan Id
     * @param zonesInFabric a list containing all the zones in the fabric,
     *            both active an inactive
     * @throws NetworkDeviceControllerException
     */
    private boolean updateZone(MDSDialog dialog, ZoneUpdate updateZone, Integer vsanId,
            List<Zone> zonesInFabric, boolean activateZones)
            throws NetworkDeviceControllerException {

        _log.info("Updating zone: " + updateZone.getName() + " vsan: " + vsanId);
        boolean updated = false;

        // check if an active zone with the same name exists
        Zone zoneInFabric = getZoneInFabric(updateZone.getName(), zonesInFabric);

        if (zoneInFabric != null) {
            dialog.zoneNameVsan(updateZone.getName(), vsanId, false);
            try {
                Collection<String> memberAddresses = getWwnsInZone(zoneInFabric);
                Collection<String> memberAliases = getAliasesInZone(zoneInFabric);

                // remove zone members
                for (ZoneMember zoneMember : updateZone.getRemoveZones()) {
                    if (!StringUtils.isEmpty(zoneMember.getAddress()) && memberAddresses.contains(zoneMember.getAddress())) {
                        dialog.zoneMemberPwwn(zoneMember.getAddress(), true);
                        _log.info("Zone member : " + zoneMember.getAddress() + " was removed.");
                    } else if (!StringUtils.isEmpty(zoneMember.getAlias()) && memberAliases.contains(zoneMember.getAlias())) {
                        dialog.zoneMemberAlias(zoneMember.getAlias(), true);
                        _log.info("Zone member of type alias : " + zoneMember.getAlias() + " was removed.");
                    }
                }

                // add zone members
                for (ZoneMember zoneMember : updateZone.getAddZones()) {
                    if (!StringUtils.isEmpty(zoneMember.getAddress())) {
                        dialog.zoneMemberPwwn(zoneMember.getAddress());
                        _log.info("Zone member : " + zoneMember.getAddress() + " was added.");
                    } else if (!StringUtils.isEmpty(zoneMember.getAlias())) {
                        dialog.zoneMemberAlias(zoneMember.getAlias());
                        _log.info("Zone member of type alias : " + zoneMember.getAlias() + " was added.");
                    }
                }

                _log.info("Updated zone: " + updateZone.getName() + " vsan: " + vsanId);
                updated = true;
            } finally {
                dialog.exitToConfig();     // exit zone config mode, since zoneNameVsan() got in to it
            }
        } else {
            throw NetworkDeviceControllerException.exceptions.zoneNotFoundInFabric(updateZone.getName(), vsanId.toString());
        }
        return updated;
    }

    @Override
    public BiosCommandResult activateZones(NetworkSystem network, String fabricId, String fabricWwn)
            throws NetworkDeviceControllerException {
        BiosCommandResult result = null;
        MDSDialog dialog = null;
        try {
            dialog = setUpDialog(network);
            Integer vsanId = checkVsanFabric(dialog, fabricId, fabricWwn);

            String activatedZonesetName = activateZonesStrategy(dialog, vsanId);

            String msg = "";
            if (activatedZonesetName == null) {
                msg = "Vsan: " + fabricId + ": No zoneset was activated";
            } else {
                msg = "Vsan: " + fabricId + ": Successfully activated zoneset: " + activatedZonesetName;
            }
            _log.info(msg);
            result = BiosCommandResult.createSuccessfulResult();
        } catch (NetworkDeviceControllerException ex) {
            _log.error("Cannot activate zoneset: " + (ex.getCause() != null ? ex.getCause().getMessage() : ex.getLocalizedMessage()));
            throw ex;
        } finally {
            disconnect(dialog);
        }
        return result;
    }

    /**
     * Given a dialog, activate current active zoneset of the specified vsan. Error out if vsan have
     * have a empty zoneset.
     * 
     * @param dialog - An MDSDialog, containing dialog state to the device
     * @param vsanId - Integer vsanId
     * @return list of zone names that were activated
     * @throws ControllerException
     */
    private String activateZonesStrategy(MDSDialog dialog, Integer vsanId) throws NetworkDeviceControllerException {
        waitForSession(dialog, vsanId);
        Long time = System.currentTimeMillis();

        // First determine if there is an active zoneset.
        Zoneset activeZoneset = getActiveZoneset(dialog, vsanId);

        // There is no non-empty active zone set. Throw exception
        if (activeZoneset == null || activeZoneset.getZones().isEmpty()) {
            _log.error("Activate zone requires vsan: " + vsanId + " to have a non-empty active zoneset.");
            throw NetworkDeviceControllerException.exceptions.noActiveZonesetForFabric(vsanId.toString());
        }

        try {
            // Go into config mode. This allows us to change the configuration.
            dialog.config();
            zonesetClone(dialog, vsanId, activeZoneset);
            commitZones(dialog, vsanId, activeZoneset);
            dialog.copyRunningConfigToStartupFabric();
            dialog.endConfig();

            time = System.currentTimeMillis() - time;
            _log.info("Zoneset: " + activeZoneset.getName() + " activate time (msec): " + time.toString());

            return activeZoneset.getName();

        } catch (Exception ex) {
            throw NetworkDeviceControllerException.exceptions.activateZonesStrategyFailed(ex);
        } finally {
            safeExitSession(dialog, vsanId);
        }
    }

    private Zoneset getActiveZoneset(MDSDialog dialog, Integer vsanId) {
        // First determine if there is an active zoneset.
        return dialog.showActiveZoneset(vsanId);
    }

    private void safeExitSession(MDSDialog dialog, Integer vsanId) {
        if (dialog != null && dialog.isInSession()) {
            dialog.endConfig();
            dialog.config();
            dialog.noZoneCommit(vsanId);
            dialog.endConfig();
        }
    }

    @Override
    public List<ZoneWwnAlias> getAliases(NetworkSystem network, String fabricId, String fabricWwn) throws Exception {
        // First determine if there is an active zoneset.
        MDSDialog dialog = null;
        List<ZoneWwnAlias> aliases = new ArrayList<>();
        try {
            dialog = setUpDialog(network);
            Map<String, String> aliasMap = dialog.showDeviceAliasDatabase();
            for (Map.Entry<String, String> aliasEntry : aliasMap.entrySet()) {
                aliases.add(new ZoneWwnAlias(aliasEntry.getKey(), aliasEntry.getValue()));
            }
            return aliases;
        } catch (Exception ex) {
            _log.error("Cannot get aliases: " + ex.getLocalizedMessage());
            throw ex;
        } finally {
            disconnect(dialog);
        }
    }

    @Override
    public BiosCommandResult addAliases(NetworkSystem network,
            List<ZoneWwnAlias> addingAliases, String fabricId, String fabricWwn)
            throws NetworkDeviceControllerException {
        BiosCommandResult result = null;
        MDSDialog dialog = null;
        Map<String, String> addedAliasesName = new HashMap<String, String>();
        try {
            dialog = setUpDialog(network);

            if (!addingAliases.isEmpty()) {
                addedAliasesName.putAll(addAliasesStrategy(dialog, addingAliases));
            }

            String msg = "Successfully added aliases: " + addedAliasesName.toString();
            if (!hasResult(addedAliasesName, SUCCESS)) {
                msg = "Network System: " + network.getLabel() + ": No aliases were added";
            }

            _log.info(msg);
            result = getBiosCommandResult(addedAliasesName);
        } catch (Exception ex) {
            _log.error("Cannot add aliases: " + (ex.getCause() != null ? ex.getCause().getMessage() : ex.getLocalizedMessage()));
            throw ex;
        } finally {
            disconnect(dialog);
        }
        return result;
    }

    /**
     * Given a dialog, add one or more aliases to the network system.
     * 
     * @param dialog - An MDSDialog, containing dialog state to the device
     * @param addingAliases - List of aliases to be added.
     * @return a map that contains the outcome for each alias keyed by alias name
     * @throws ControllerException
     */
    private Map<String, String> addAliasesStrategy(MDSDialog dialog, List<ZoneWwnAlias> addingAliases)
            throws NetworkDeviceControllerException {
        Long time = System.currentTimeMillis();

        // a alias-name-to-result map to hold the results for each alias
        Map<String, String> addedAliasesName = new HashMap<String, String>();

        try {
            // Go into config mode. This allows us to change the configuration.
            dialog.config();
            dialog.deviceAliasConfig();

            Map<String, String> aliasMap = dialog.showDeviceAliasDatabase();

            for (ZoneWwnAlias wwnAlias : addingAliases) {
                String name = wwnAlias.getName();
                String wwn = wwnAlias.getAddress();

                _log.info("Starting create alias with name " + name);

                // get aliass wwn address from database
                String currentWwn = aliasMap.get(name);

                try {
                    // if alias address is null or already in system, considered as added
                    if (StringUtils.isEmpty(wwn) || StringUtils.isEmpty(name) || StringUtils.equalsIgnoreCase(wwn, currentWwn)) {
                        if (!StringUtils.isEmpty(currentWwn)) {
                            // alias already exists - this is not an error unless it is for different member
                            _log.info("The existing alias {} is found with the same WWN {}. Nothing to do.", name, currentWwn);
                        }

                        addedAliasesName.put(name, NO_CHANGE);
                        continue;
                    } else if (aliasMap.containsKey(name)) {
                        // if alias already exists with different address, throw exception
                        throw NetworkDeviceControllerException.exceptions.aliasWithSameNameExists(name, currentWwn, wwn);
                    } else if (aliasMap.containsValue(wwn)) {
                        // if wwn is already assigned to another alias, throw exception
                        throw NetworkDeviceControllerException.exceptions.wwnAssignedToAnotherAlias(wwn, name,
                                getAliasForWwn(aliasMap, wwn));
                    }

                    // add alias to device
                    dialog.deviceAliasName(name, wwn, false);
                    addedAliasesName.put(name, SUCCESS);
                    aliasMap.put(name, wwn);
                } catch (Exception ex) {
                    addedAliasesName.put(name, ERROR + ": " + ex.getMessage());
                    _log.warn("Exception was encountered but will try the rest of the batch. " +
                            "Error message: " + ex.getMessage());
                }
            }

            // if there was any alias added, commit them
            if (!addedAliasesName.isEmpty()) {
                dialog.deviceAliasCommit();
                dialog.copyRunningConfigToStartupFabric();
            } else {
                dialog.exitToConfig();
            }

            time = System.currentTimeMillis() - time;
            _log.info("Aliases add time (msec): " + time.toString());

            return addedAliasesName;

        } catch (Exception ex) {
            dialog.deviceAliasAbort();
            throw NetworkDeviceControllerException.exceptions.addAliasesStrategyFailed(ex);
        } finally {
            dialog.endConfig();
        }
    }

    @Override
    public BiosCommandResult removeAliases(NetworkSystem network,
            List<ZoneWwnAlias> removingAliases, String fabricId, String fabricWwn)
            throws NetworkDeviceControllerException {
        BiosCommandResult result = null;
        MDSDialog dialog = null;
        Map<String, String> removedAliasesName = new HashMap<String, String>();
        try {
            dialog = setUpDialog(network);

            if (!removingAliases.isEmpty()) {
                removedAliasesName.putAll(removeAliasesStrategy(dialog, removingAliases));
            }

            String msg = "Successfully removed aliases: " + removedAliasesName.toString();
            if (!hasResult(removedAliasesName, SUCCESS)) {
                msg = "Network System: " + network.getLabel() + ": No aliases were removed";
            }

            _log.info(msg);
            result = getBiosCommandResult(removedAliasesName);
        } catch (Exception ex) {
            _log.error("Cannot remove aliases: " + (ex.getCause() != null ? ex.getCause().getMessage() : ex.getLocalizedMessage()));
            throw ex;
        } finally {
            disconnect(dialog);
        }
        return result;
    }

    /**
     * Given a dialog, remove one or more aliases from the network system.
     * 
     * @param dialog - An MDSDialog, containing dialog state to the device
     * @param removingAliases - List of aliases to be removed.
     * @return a map that contains the outcome for each alias keyed by alias name
     * @throws ControllerException
     */
    private Map<String, String> removeAliasesStrategy(MDSDialog dialog, List<ZoneWwnAlias> removingAliases)
            throws NetworkDeviceControllerException {
        Long time = System.currentTimeMillis();

        // a alias-name-to-result map to hold the results for each alias
        Map<String, String> removedAliasesName = new HashMap<String, String>();

        try {
            // Go into config mode. This allows us to change the configuration.
            dialog.config();
            dialog.deviceAliasConfig();

            Map<String, String> aliasMap = dialog.showDeviceAliasDatabase();

            for (ZoneWwnAlias wwnAlias : removingAliases) {
                String name = wwnAlias.getName();
                String wwn = wwnAlias.getAddress();

                _log.info("Starting remove alias with name {}", name);

                // get aliass wwn address from database
                String currentWwn = aliasMap.get(name);

                try {
                    // if address not found in device, implied that alias is already removed or not
                    // exist in system, ignore it and continue on
                    if (StringUtils.isEmpty(currentWwn)) {
                        _log.info("Did not find alias {}. Nothing to do.", name);
                        removedAliasesName.put(name, NO_CHANGE);
                        continue;
                    } else if (StringUtils.isEmpty(wwn) || StringUtils.equalsIgnoreCase(wwn, currentWwn)) {
                        _log.info("Found alias {}. The alias will be removed.", name);

                        // if address is not specified, or it is specified and the same as
                        // the current alias' wwn, remove it.
                        dialog.deviceAliasName(name, currentWwn, true);
                        removedAliasesName.put(name, SUCCESS);

                        // remove alias from the map, if it was delete successfully
                        aliasMap.remove(name);
                    } else {
                        // if removing alias and the provided address do not match in system, throw exception
                        _log.info("The existing alias {} has a WWN other than the expected {}. It will not be removed.", name, wwn);
                        throw NetworkDeviceControllerException.exceptions.aliasWithDifferentWwnExists(name, currentWwn, wwn);
                    }
                } catch (Exception ex) {
                    removedAliasesName.put(name, ERROR + " : " + ex.getMessage());
                    _log.warn("Exception was encountered but will try the rest of the batch. " +
                            "Error message: " + ex.getMessage());
                }
            }

            // if there was any alias added, commit them
            if (!removedAliasesName.isEmpty()) {
                dialog.deviceAliasCommit();
                dialog.copyRunningConfigToStartupFabric();
            } else {
                dialog.exitToConfig();
            }

            time = System.currentTimeMillis() - time;
            _log.info("Aliases remove time (msec): " + time.toString());

            return removedAliasesName;

        } catch (Exception ex) {
            dialog.deviceAliasAbort();
            throw NetworkDeviceControllerException.exceptions.removeAliasesStrategyFailed(ex);
        } finally {
            dialog.endConfig();
        }
    }

    @Override
    public BiosCommandResult updateAliases(NetworkSystem network,
            List<ZoneWwnAliasUpdate> updatingAliases, String fabricId, String fabricWwn)
            throws NetworkDeviceControllerException {
        BiosCommandResult result = null;
        MDSDialog dialog = null;
        Map<String, String> updatedAliasesName = new HashMap<String, String>();
        try {
            dialog = setUpDialog(network);

            if (!updatingAliases.isEmpty()) {
                updatedAliasesName.putAll(updateAliasesStrategy(dialog, updatingAliases));
            }

            String msg = "Successfully updated aliases: " + updatedAliasesName.toString();
            if (!hasResult(updatedAliasesName, SUCCESS)) {
                msg = "Network System: " + network.getLabel() + ": No aliases were updated";
            }

            _log.info(msg);
            result = getBiosCommandResult(updatedAliasesName);
        } catch (Exception ex) {
            _log.error("Cannot updated aliases: " + (ex.getCause() != null ? ex.getCause().getMessage() : ex.getLocalizedMessage()));
            throw ex;
        } finally {
            disconnect(dialog);
        }
        return result;
    }

    /**
     * Given a dialog, update one or more aliases from the network system.
     * 
     * @param dialog - An MDSDialog, containing dialog state to the device
     * @param updatingAliases - List of aliases to be updated.
     * @return a map that contains the outcome for each alias keyed by alias name
     * @throws ControllerException
     */
    private Map<String, String> updateAliasesStrategy(MDSDialog dialog, List<ZoneWwnAliasUpdate> updatingAliases)
            throws NetworkDeviceControllerException {
        Long time = System.currentTimeMillis();

        // a alias-name-to-result map to hold the results for each alias
        Map<String, String> updatedAliasesName = new HashMap<String, String>();

        try {
            // Go into config mode. This allows us to change the configuration.
            dialog.config();
            dialog.deviceAliasConfig();

            Map<String, String> aliasMap = dialog.showDeviceAliasDatabase();

            for (ZoneWwnAliasUpdate updatingAlias : updatingAliases) {
                String name = updatingAlias.getName();
                String newName = updatingAlias.getNewName();
                String oldWwn = updatingAlias.getAddress();
                String newWwn = updatingAlias.getNewAddress();

                _log.info("Starting update alias {}", name);

                // get aliass wwn address from database
                String currentWwn = aliasMap.get(name);
                String updateStatus = NO_CHANGE;
                try {
                    // if address not found in device, implied that alias is already removed or not
                    // exist in system, ignore it and continue on
                    if (StringUtils.isEmpty(newWwn) && StringUtils.isEmpty(newName)) {
                        _log.info("No new alias or WWN were specified.  Nothing to update");
                    } else if (StringUtils.equals(name, newName) && !StringUtils.isEmpty(newWwn)
                            && StringUtils.equalsIgnoreCase(oldWwn, newWwn)) {
                        _log.info("Old and new name {} are the same. Nothing to do.", name, currentWwn);
                        _log.info("Old and new WWN {} are the same. Nothing to do.", newWwn, currentWwn);
                    } else if (StringUtils.isEmpty(currentWwn)) {
                        _log.info("Alias {} was not found.  Nothing to do", name);
                    } else if (StringUtils.isEmpty(newName) && StringUtils.equalsIgnoreCase(currentWwn, newWwn)) {
                        // new alias was not specified, implies wants to change wwn. But new is the same as in system, ignore
                        _log.info("The existing alias {} already has the desired WWN {}. Nothing to do.", name, currentWwn);
                    } else if (aliasMap.containsValue(newWwn)) {
                        // if new wwn is already assigned to another alias, throw exception
                        throw NetworkDeviceControllerException.exceptions.wwnAssignedToAnotherAlias(newWwn, name,
                                getAliasForWwn(aliasMap, newWwn));
                    } else if (!StringUtils.isEmpty(oldWwn) && !StringUtils.equalsIgnoreCase(oldWwn, currentWwn)) {
                        // if old wwn of alias and the provided updating address do not match in system, throw exception
                        throw NetworkDeviceControllerException.exceptions.aliasWithDifferentWwnExists(name, currentWwn, oldWwn);
                    } else if (!StringUtils.isEmpty(newName) && aliasMap.containsKey(newName)) {
                        // updating alias is already in the system, error
                        throw NetworkDeviceControllerException.exceptions.aliasAlreadyInNetworkSystem(newName, "");
                    } else if (!StringUtils.isEmpty(newName) || !StringUtils.isEmpty(newWwn)) {

                        // update wwn
                        boolean wwnUpdated = false;
                        if (!StringUtils.isEmpty(newWwn) &&
                                (StringUtils.isEmpty(oldWwn) || StringUtils.equalsIgnoreCase(oldWwn, currentWwn))) {
                            // if oldWwn is not specified, or it is specified and the same as
                            // the current alias' wwn, update it to newWwn.

                            _log.info("The existing alias {} 's WWN is updated to {}", name, newWwn);

                            // update by remove the matching old, then re-add the new wwn
                            dialog.deviceAliasName(name, currentWwn, true);
                            dialog.deviceAliasName(name, newWwn, false);
                            aliasMap.put(name, newWwn); // update map with new value

                            wwnUpdated = true;
                        }

                        // update alias
                        if (!StringUtils.isEmpty(newName) && !StringUtils.equals(name, newName)) {
                            // if newName is specified, and it is different from the old one, rename it
                            _log.info("Renaming alias {} to {}", name, newName);

                            // update by remove the matching old, then re-add the new wwn
                            try {
                                dialog.deviceAliasRename(name, newName);
                            } catch (Exception ex) {
                                if (wwnUpdated) {
                                    // roll back
                                    _log.info("Failed to rename.  Rollback update wwn");
                                    dialog.deviceAliasName(name, newWwn, true);
                                    dialog.deviceAliasName(name, currentWwn, false);
                                    aliasMap.put(name, currentWwn); // update map with new value
                                }
                                throw ex;
                            }

                            aliasMap.put(newName, aliasMap.get(name)); // update map with new value
                            aliasMap.remove(name);
                        }

                        updateStatus = SUCCESS;
                    }
                } catch (Exception ex) {
                    _log.warn("Exception was encountered but will try the rest of the batch. " +
                            "Error message: " + ex.getMessage());
                    updateStatus = ERROR;
                }
                updatedAliasesName.put(name, updateStatus);
            }

            // if there was any alias added, commit them
            if (!updatedAliasesName.isEmpty()) {
                dialog.deviceAliasCommit();
                dialog.copyRunningConfigToStartupFabric();
            } else {
                dialog.exitToConfig();
            }

            time = System.currentTimeMillis() - time;
            _log.info("Aliases update time (msec): " + time.toString());

            return updatedAliasesName;

        } catch (Exception ex) {
            dialog.deviceAliasAbort();
            throw NetworkDeviceControllerException.exceptions.updateAliasesStrategyFailed(ex);
        } finally {
            dialog.endConfig();
        }
    }

    private String getAliasForWwn(Map<String, String> aliasMap, String wwn) {
        String foundAlias = null;
        for (Entry<String, String> aliasEntry : aliasMap.entrySet()) {
            if (StringUtils.equalsIgnoreCase(wwn, aliasEntry.getValue())) {
                foundAlias = aliasEntry.getKey();
            }
        }
        return foundAlias;
    }

    @Override
    public Map<String, List<Zone>> getEndpointsZones(
            NetworkSystem networkSystem, String fabricWwn,
            String nativeId, Collection<String> endpointsWwn)
            throws NetworkDeviceControllerException {
        MDSDialog dialog = null;
        Map<String, List<Zone>> zoneMap = new HashMap<String, List<Zone>>();
        try {
            Integer vsanId = Integer.valueOf(nativeId);

            dialog = setUpDialog(networkSystem);
            for (String endpointWwn : endpointsWwn) {
                Collection<String> zoneNames = dialog.showZoneNamesForPwwn(endpointWwn, vsanId, true);
                List<Zone> zones = dialog.showZones(zoneNames, true);
                zoneMap.put(endpointWwn, zones);
            }
            return zoneMap;
        } catch (Exception ex) {
            _log.error("Cannot read zones from device: " + networkSystem.getLabel() + ": " + ex.getLocalizedMessage());
            throw ex;
        } finally {
            disconnect(dialog);
        }
    }

	
    @Override
	public boolean isCapableOfRouting(NetworkSystem networkSystem) {
		return this.isIvrEnabled(networkSystem);
	}

    /*
     * (non-Javadoc)
     * @see com.emc.storageos.networkcontroller.impl.NetworkSystemDevice#determineRoutedNetworks(com.emc.storageos.db.client.model.NetworkSystem)
     */
	@Override
	public void determineRoutedNetworks(NetworkSystem networkSystem) throws NetworkDeviceControllerException {		
		MDSDialog dialog = null;
			
		/* Example output "show ivr vsan-topology"
		 * 	AFID  SWITCH WWN                 Active   Cfg. VSANS
		 * 	-----------------------------------------------------------
		 * 	1  20:00:00:0d:ec:dc:86:40 *   yes      no  1,3,11,99,200
		 * 	1  20:00:00:2a:6a:33:13:10     yes      no  1-3,10,78,99,200
		 */
		
		// 1. Build a map of switchWWN to NetworkSystem for all the discovered NetworkSystems
		
		Map<String, NetworkSystem> switchWWNToNetworkSystemMap = new HashMap<String, NetworkSystem>();
		for (URI discoveredNetworkSystemUri : NetworkUtil.getDiscoveredNetworkSystems(_dbClient)) {
			NetworkSystem discoveredNetworkSystem =_dbClient.queryObject(NetworkSystem.class, discoveredNetworkSystemUri);
			try {
			if (discoveredNetworkSystem.getSystemType().equalsIgnoreCase(NetworkSystem.Type.mds.toString())) {
				dialog = setUpDialog(discoveredNetworkSystem);			
				String switchWWN = dialog.showSwitchWwn();
				switchWWNToNetworkSystemMap.put(switchWWN, discoveredNetworkSystem);
				_log.info(String.format("NetworkSystem : %s - WWN : %s", switchWWNToNetworkSystemMap.get(switchWWN).getLabel(), switchWWN));			
				}	
			} catch (Exception e) {
				_log.info(String.format("Couldnt fetch the switch WWN information for %s, ignoring it", discoveredNetworkSystem.getLabel()));
			} finally {
				disconnect(dialog);
			}
		}
				
		//2. Get ouput from "show ivr vsan-topology" using the current network system. 
        //Build a map of Switch WWN to their VSANs from the topology map
        try {
            dialog = setUpDialog(networkSystem);
            String currentNetworkSytemWWN = dialog.showSwitchWwn();
         
            Map<String, Set<Integer>> switchWWNToVsans = new HashMap<>();            
             List<IvrVsanConfiguration> ivrVsansList = dialog.showIvrVsanTopology();
             for (IvrVsanConfiguration ivrVsan : ivrVsansList) {
                 Set<Integer> vsans = new HashSet<Integer>();
                 vsans.addAll(ivrVsan.getVsans());
                 for (IntRange ivrVsanRange : ivrVsan.getVsansRanges()) {                    	 
                	 for (int range = ivrVsanRange.getMinimumInteger(); range <= ivrVsanRange.getMaximumInteger(); range++) {
                        vsans.add(range);
                	 }
                 }
                 switchWWNToVsans.put(ivrVsan.getSwitchWwn(), vsans);
             }     
                                      
             //3. Check to make sure that the current Network system (that is being discovered) is in the ivr vsan-topology map.
             if (!switchWWNToVsans.containsKey(currentNetworkSytemWWN)) {            
            	 _log.info(String.format("Currently discovered NetworkSystem with WWN %s is not part of the ivr vsan-topology, returning.", currentNetworkSytemWWN));
            	 return;
             }             
             
             //4. Loop through all the switch WWNs from the topology map and check if they are discovered.
             //If yes, then all the networks of that network-system are routable to all the other networks from other discovered switches that are also in the vsan-topology map. 
             //Since this map is constructed from the output of "show ivr vsan-topology", the switch WWNs listed in that output are 
             //all routable to each others for the networks that belong to them. 
             //The assumption here is that there exists a transit VSAN between the switches that are on the IVR path. 
             List<Network> routedNetworks = new ArrayList<Network>();
             for (Entry<String, Set<Integer>> switchWWNToVsan : switchWWNToVsans.entrySet()) {
            	 String switchKey = switchWWNToVsan.getKey();
            	 Set<Integer> vsanValues = switchWWNToVsan.getValue();
            	            	
            	 if (switchWWNToNetworkSystemMap.containsKey(switchKey)) {     
            		 NetworkSystem ns = switchWWNToNetworkSystemMap.get(switchKey);
            		 URIQueryResultList networkSystemNetworkUriList = new URIQueryResultList();
            		//Fetch all the networks of this networkSystem
                     _dbClient.queryByConstraint(ContainmentConstraint.Factory.
                                     getNetworkSystemNetworkConstraint(ns.getId()), networkSystemNetworkUriList);
                     
                     for (URI networkSystemNetworkUri : networkSystemNetworkUriList) {
                         Network networkSystemNetwork = _dbClient.queryObject(Network.class, networkSystemNetworkUri);
                    	 if (vsanValues.contains(Integer.parseInt(networkSystemNetwork.getNativeId()))) {
                    		 _log.info("Routable Network : " +  networkSystemNetwork.getLabel());                    	
                    		 routedNetworks.add(networkSystemNetwork);
                    	 }                         
                     }            		
            	 }            	            	             
             }                   
             
             //5. update routed networks
             URIQueryResultList networkSystemNetworkUriList = new URIQueryResultList();
             _dbClient.queryByConstraint(ContainmentConstraint.Factory.
                             getNetworkSystemNetworkConstraint(networkSystem.getId()), networkSystemNetworkUriList);
             for (URI networkSystemNetworkUri : networkSystemNetworkUriList) {
            	 Network networkSystemNetwork = _dbClient.queryObject(Network.class, networkSystemNetworkUri);
            	 //clear and re-populate the routed networks for each network. 
            	 //This will ensure that any network changes are updated.
            	 networkSystemNetwork.setRoutedNetworks(new StringSet());
            	            	 
            	 for (Network routedNetwork : routedNetworks) {                 		
            		 _log.info(String.format("Network %s can route to Network %s", networkSystemNetwork.getLabel(), routedNetwork.getLabel()));
            		 networkSystemNetwork.getRoutedNetworks().add(routedNetwork.getId().toString());
            		 
            		 //Make the reverse association as well. 
            		 if (routedNetwork.getRoutedNetworks() == null) {
            			 routedNetwork.setRoutedNetworks(new StringSet());            			 
            		 }
            		 _log.info(String.format("Network %s can route to Network %s", routedNetwork.getLabel(), networkSystemNetwork.getLabel()));
            		 routedNetwork.getRoutedNetworks().add(networkSystemNetwork.getId().toString());
            	 }
            	 _dbClient.updateObject(networkSystemNetwork);
            	 _dbClient.updateObject(routedNetworks);
             }
        } catch (Exception ex) {
            _log.error("Cannot determine routable networks for networks on  " + networkSystem.getLabel() + " : " + ex.getLocalizedMessage());
            throw ex;
        } finally {
            disconnect(dialog);
        }			
	}
}
