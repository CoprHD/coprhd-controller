/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.networkcontroller.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.model.NetworkSystem;
import com.emc.storageos.db.client.util.WWNUtility;
import com.emc.storageos.networkcontroller.exceptions.NetworkDeviceControllerException;
import com.emc.storageos.networkcontroller.impl.mds.Zone;
import com.emc.storageos.networkcontroller.impl.mds.ZoneMember;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.volumecontroller.impl.BiosCommandResult;
import com.google.common.collect.Sets;

public abstract class NetworkSystemDeviceImpl implements NetworkSystemDevice {
    private static final Logger _log = LoggerFactory.getLogger(NetworkSystemDeviceImpl.class);

    /**
     * Given a list of zones requested to be added, and a list of existing zones on
     * the device, return a list of zones that should really be added.
     * Zones which already exist will not be added again (if they match by content and
     * name). Zones with match members but have a different name (and are therefore
     * assumed to be externally created) are ignored.
     * 
     * @param zones -- Zones requested to be created.
     * @param existingZones -- Existing zones on the device.
     * @return -- Zones to be added
     * @throws NetworkDeviceControllerException
     */
    protected List<Zone> getZonesToBeAdded(List<Zone> zones, List<Zone> existingZones)
            throws NetworkDeviceControllerException {
        // Now, we want to add only new zones.
        // Zones that have members completely in a zone in the active zoneset already are
        // ignored, if they match the zoneset name we have.
        // Make an array of sets containing the members of the active zones.
        // The next paragraph below checks each zone to be created against each existing zone and looks for matches.
        Map<String, Set<String>> memberSetMap = new HashMap<String, Set<String>>();  // key is an existing zone name
        for (Zone azone : existingZones) {
            Set<String> memberAddressSet = new HashSet<String>();
            for (ZoneMember amember : azone.getMembers()) {
                if (amember.getAddress() != null) {
                    memberAddressSet.add(amember.getAddress());
                }

                if (amember.getAlias() != null) {
                    memberAddressSet.add(amember.getAlias());
                }
            }
            memberSetMap.put(azone.getName(), memberAddressSet);
        }
        // Check each zone to be added to see if it's matched by an active set.
        List<Zone> zonesToBeAdded = new ArrayList<Zone>();
        for (Zone zone : zones) {
            boolean addIt = true;
            for (String azoneName : memberSetMap.keySet()) {
                Set<String> activeSet = memberSetMap.get(azoneName);
                boolean match = true;
                for (ZoneMember member : zone.getMembers()) {
                    if ((member.getAddress() != null && !WWNUtility.isValidWWN(member.getAddress())) ||
                            (member.getAlias() != null && !WWNUtility.isValidWWNAlias(member.getAlias()))) {
                        throw NetworkDeviceControllerException.exceptions.getZonesToBeAddedFailedIllegalAddress(
                                member.getAddress());
                    }

                    if ((member.getAddress() != null && !activeSet.contains(member.getAddress())) ||
                            (member.getAlias() != null && !activeSet.contains(member.getAlias()))) {
                        match = false;
                    }
                }

                if (match == true) {
                    // Check to see if it's the same zone name that we wanted. Otherwise, log a duplicate zone.
                    if (azoneName.equals(zone.getName())) {
                        addIt = false;
                    } else {
                        _log.info("Found duplicate zone: " + azoneName + " for: " + zone.getName() + " ... ignoring");
                    }
                }
            }
            if (addIt) {
                zonesToBeAdded.add(zone);
            }
        }
        return zonesToBeAdded;
    }

    /**
     * Given a list of zones requested to be deleted, and a list of existing zones on the device,
     * this returns a list of zones to actually be deleted, along with a count of the number
     * of remaining zones after they are deleted.
     * 
     * @param zones -- Zones requested to be deleted
     * @param existingZones -- on the device
     * @param remainingZones -- Integer[1] array that returns number of remaining zones
     * @return collection of zones to be deleted
     * @throws NetworkDeviceControllerException
     */
    protected List<Zone> getZonesToBeDeleted(List<Zone> zones, Collection<Zone> existingZones,
            Integer[] remainingZones, Map<String, String> removedZoneResults)
            throws NetworkDeviceControllerException {
        // Zones that have members completely in a zone in the active zoneset already are
        // to be deleted.
        // Make a map of zone names to zones found on the switch.
        Map<String, Zone> activeZonesMap = new HashMap<String, Zone>();
        Map<String, Object> cimObjectPaths = new HashMap<String, Object>();
        for (Zone azone : existingZones) {
            if (azone != null) {
                activeZonesMap.put(azone.getName(), azone);
                cimObjectPaths.put(azone.getName(), azone.getCimObjectPath());
            }
        }
        remainingZones[0] = new Integer(activeZonesMap.size());

        // Check each zone to be deleted to see if it's matched by a zone in the active set.
        List<Zone> zonesToBeDeleted = new ArrayList<Zone>();
        for (Zone zone : zones) {
            Zone zoneInFabric = activeZonesMap.get(zone.getName());
            if (zoneInFabric != null) {
                boolean match = true;
                if (zone.getMembers() != null && !zone.getMembers().isEmpty()) {
                    match = sameMembers(zoneInFabric, zone);
                }
                if (match) {
                    Object cimObjectPath = cimObjectPaths.get(zone.getName());
                    zone.setCimObjectPath(cimObjectPath);
                    zonesToBeDeleted.add(zone);
                    remainingZones[0]--;
                } else {
                    removedZoneResults.put(zone.getName(), ERROR + " : The existing zone members do not match what is in the request.");
                    _log.info("Zone " + zone.getName() + " was found but the members did not match");
                }
            } else {
                _log.info("Zone " + zone.getName() + " was not found in the active zone set. Nothing to do.");
                removedZoneResults.put(zone.getName(), NO_CHANGE);
            }
        }
        return zonesToBeDeleted;
    }

    @Override
    public Set<String> getRoutedEndpoints(NetworkSystem networkSystem,
            String fabricId, String fabricWwn) throws Exception {
        return Collections.EMPTY_SET;
    }

    /**
     * Create default zoneset for a given fabricId
     * 
     * @param fabricId
     * @return default zoneset name for given fabric
     */
    abstract protected String getDefaultZonesetName(String fabricId);

    /**
     * Convenient method to handle exception while zoning
     * 
     * @param ex
     * @param activateZones
     */
    protected void handleZonesStrategyException(Exception ex, boolean activateZones) throws Exception {
        if (activateZones) { // immediate activation means we expect all zones to be successful
            throw ex;        // we should fail if any zone fails
        } else {
            _log.info("Exception was encountered but will try the rest of the batch. " +
                    "Error message: " + ex.getMessage());
        }
    }

    /**
     * Get zone member addresses that are not mapped from device alias database.
     * 
     * @param zone
     * @return list of zone members
     */
    protected Collection<String> getWwnsInZone(Zone zone) {
        Set<String> col = Sets.newHashSet();
        if (zone.getMembers() != null) {
            for (ZoneMember member : zone.getMembers()) {
                if (!StringUtils.isEmpty(member.getAddress())) {
                    col.add(member.getAddress());
                }
            }
        }
        return col;
    }

    /**
     * Get zone members alias
     * 
     * @param zone
     * @return list of zone members alias
     */
    protected Collection<String> getAliasesInZone(Zone zone) {
        Set<String> col = Sets.newHashSet();
        if (zone.getMembers() != null) {
            for (ZoneMember member : zone.getMembers()) {
                if (!StringUtils.isEmpty(member.getAlias()) && member.isAliasType()) {
                    col.add(member.getAlias());
                }
            }
        }
        return col;
    }

    /**
     * Returns true if at least item in the results matches the desired result.
     * 
     * @param results
     * @return true if at least one item has the desired result
     */
    protected boolean hasResult(Map<String, String> results, String result) {
        if (results != null) {
            for (String str : results.values()) {
                if (str.startsWith(result)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Returns a formatted string of the results.
     * 
     * @param results
     * @return
     */
    protected String toMessage(Map<String, String> results) {
        StringBuilder builder = new StringBuilder();
        if (results != null) {
            for (Map.Entry<String, String> entry : results.entrySet()) {
                builder.append(entry.getKey() + ": " + entry.getValue() + ";\n");
            }
        }
        return builder.toString();
    }

    protected BiosCommandResult getBiosCommandResult(Map<String, String> results) {
        BiosCommandResult result = null;
        if (hasResult(results, ERROR)) {
            ServiceError serviceError = NetworkDeviceControllerException.errors.batchOperationFailed(toMessage(results));
            result = BiosCommandResult.createErrorResult(serviceError);
        } else {
            result = BiosCommandResult.createSuccessfulResult();
        }
        result.setObjectList(Collections.singletonList((Object) results));
        return result;
    }

    /**
     * Check if 2 zones has the same members
     * 
     * @param zoneInFabric
     * @param zone
     * @return true if both zone has the same wwn and alias members
     */
    protected boolean sameMembers(Zone zoneInFabric, Zone zone) {
        boolean same = true;
        if (zoneInFabric.getMembers().size() == zone.getMembers().size()) {
            Collection<String> wwnsInFabric = getWwnsInZone(zoneInFabric);

            for (ZoneMember member : zone.getMembers()) {
                if (!StringUtils.isEmpty(member.getAddress()) && !wwnsInFabric.contains(member.getAddress())) {
                    _log.info("Zone member WWN {} not found in active zone {}", member.getAddress(), zone.getName());
                    same = false;
                    break;
                }  
            }
        } else {
            same = false;
        }

        // if zones do not have same info, log their info for debugging.
        if (!same) {
            _log.info("Zones {} and {} do not have the same info", zoneInFabric.getName(), zone.getName());
            _log.info("Zone {} has {} members in fabric.", zoneInFabric.getName(), zoneInFabric.getMembers().size());
            _log.info("and intended zone {} has {} members.", zone.getName(), zone.getMembers().size());
            zoneInFabric.print();
            zone.print();
        }
        return same;
    }
}
