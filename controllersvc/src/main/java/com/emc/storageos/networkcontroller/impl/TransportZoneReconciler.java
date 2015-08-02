/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.networkcontroller.impl;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.DiscoveredDataObject.RegistrationStatus;
import com.emc.storageos.db.client.model.FCEndpoint;
import com.emc.storageos.db.client.model.Network;
import com.emc.storageos.db.client.model.NetworkSystem;
import com.emc.storageos.db.client.model.StorageProtocol.Transport;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.util.NetworkUtil;
import com.emc.storageos.volumecontroller.impl.NativeGUIDGenerator;

/**
 * This is a utility class that is used to handle reconciliation of newly discovered
 * transport zones from a given network system with those stored in the database. It
 * is coded to provide the results back in the form of sets of added/removed/updated
 * transport zone as opposed to updating the DB to improve testability.
 * 
 * @author elalih
 * 
 */
public class TransportZoneReconciler {
    private static String PREFIX_VSAN = "VSAN_";
    private static String PREFIX_FABRIC = "FABRIC_";

    private static final Logger _log = LoggerFactory
            .getLogger(TransportZoneReconciler.class);

    /**
     * Given the old transport zones and the new fabrics and end points
     * returns the lists of what needs to be updated, deleted and added.
     * 
     * @param network - The network system being refreshed
     * @param iEndPoints - the iterator of new end points
     * @param fabricIdsMap - the list of all fabrics on the network system
     * @param oldTransportZones - the old transport zones
     * @throws Exception
     * @return A results object containing lists of added, removed and modified
     *         transport zone
     */
    public Results reconcile(NetworkSystem network, Iterator<FCEndpoint> iEndPoints,
            Map<String, String> fabricIdsMap,
            List<Network> oldTransportZones) throws Exception {
        // compute the new transport zones for the network system being
        // refreshed from the end points
        HashMap<String, Network> newTransportZones = getNewTransportZones(
                network, iEndPoints, fabricIdsMap);
        _log.info("Reconciling {} new networks with " +
                "the existing {} networks", newTransportZones.size(),
                oldTransportZones.size());
        // a list to keep track of used names - needed when generating new names
        // to avoid duplications
        List<String> existingTransportZoneNames = new ArrayList<String>();
        // This is the map used to track what has not been accounted for - i.e.
        // new transport zones
        HashMap<String, Network> mutableMap = new HashMap<String, Network>(
                newTransportZones);
        Results results = new Results();
        // reconcile
        for (Network transportZone : oldTransportZones) {
            existingTransportZoneNames.add(transportZone.getLabel());
            if (transportZone.getDiscovered() == true) {
                String fabricWwn = parseWWNFromGuid(transportZone
                        .getNativeGuid());
                if (newTransportZones.containsKey(fabricWwn)) {
                    _log.info("Checking if existing network {} " +
                            " needs updating.", transportZone.getLabel());
                    handleUpdatedTransportZone(network, transportZone,
                            newTransportZones.get(fabricWwn), results);
                    mutableMap.remove(fabricWwn);
                } else if (transportZone.getNetworkSystems().contains(
                        network.getId().toString())) {
                    _log.info("Existing network is not found." +
                            " Checking if it is removed or modified",
                            transportZone.getLabel());
                    Network newTZ = handleRemovedTransportZone(
                            transportZone, network.getId(), newTransportZones,
                            oldTransportZones, results);
                    if (newTZ != null) { // we figured the old transport zone has changed WWN
                        // so we're using one and throwing away one
                        mutableMap.remove(NetworkUtil.getNetworkWwn(newTZ));
                    }
                }
            }
        }
        // now I deal with anything that is left in the map - These are new
        // transport zones
        for (String wwn : mutableMap.keySet()) {
            Network newTZ = mutableMap.get(wwn);
            newTZ.setLabel(getUniqueTransportZoneName(newTZ.getLabel(),
                    existingTransportZoneNames));
            results.getAdded().add(newTZ);
        }
        return results;
    }

    private HashMap<String, Network> getNewTransportZones(
            NetworkSystem network, Iterator<FCEndpoint> iEndPoints,
            Map<String, String> fabricIdsMap) throws Exception {
        HashMap<String, Network> newTransportZones = new HashMap<String, Network>();
        // first create empty transport zones
        Network tz = null;
        for (String wwn : fabricIdsMap.keySet()) {
            tz = createTransportZone(network, wwn, fabricIdsMap.get(wwn));
            newTransportZones.put(wwn, tz);
        }
        while (iEndPoints.hasNext()) {
            FCEndpoint endpoint = iEndPoints.next();
            if (endpoint == null || endpoint.getInactive()
                    // this should never happen
                    || !newTransportZones.containsKey(endpoint.getFabricWwn())) {
                continue;
            }
            newTransportZones.get(endpoint.getFabricWwn()).addEndpoints(
                    Collections.singletonList(endpoint.getRemotePortName()
                            .toUpperCase()), true);
        }
        return newTransportZones;
    }

    private String getUniqueTransportZoneName(String label,
            List<String> existingTransportZoneNames) {
        if (!existingTransportZoneNames.contains(label)) {
            return label; // it is already a unique name
        } else {
            for (int i = 1; i < 100; i++) {
                String newLabel = label + "(" + i + ")";
                if (!existingTransportZoneNames.contains(newLabel)) {
                    return newLabel;
                }
            }
        }
        return null;
    }

    private void handleUpdatedTransportZone(NetworkSystem networkSystem,
            Network oldTransportZone, Network newTransportZone, Results results)
            throws IOException {
        List<String> removedEndPoints = new ArrayList<String>();
        List<String> addedEndPoints = new ArrayList<String>(
                newTransportZone.retrieveEndpoints());
        StringSet newEndPoints = newTransportZone.retrieveEndpoints();
        for (String endpoint : oldTransportZone.retrieveEndpoints()) {
            if (newEndPoints.contains(endpoint)) {
                addedEndPoints.remove(endpoint); // The endpoint still present, account for that
            } else if (oldTransportZone.endpointIsDiscovered(endpoint)) {
                removedEndPoints.add(endpoint);
            }
        }
        if (!addedEndPoints.isEmpty()) {
            oldTransportZone.addEndpoints(addedEndPoints, true);
            // update the results object
            results.getAddedEndPoints().put(oldTransportZone, addedEndPoints);
            _log.info("Endpoints {} were added to fabric {} on network {}",
                    new Object[] { addedEndPoints.toArray(), oldTransportZone.getNativeId(), oldTransportZone.getId().toString() });
        }
        if (!removedEndPoints.isEmpty()) {
            oldTransportZone.removeEndpoints(removedEndPoints);
            // update the results object
            results.getRemovedEndPoints().put(oldTransportZone, removedEndPoints);
            _log.info("Endpoints {} were removed from fabric {} on network {}",
                    new Object[] { removedEndPoints.toArray(), oldTransportZone.getNativeId(), oldTransportZone.getId().toString() });
        }
        if (!oldTransportZone.getNetworkSystems().contains(
                networkSystem.getId().toString())) {
            oldTransportZone.addNetworkSystems(Collections
                    .singletonList(networkSystem.getId().toString()));
            if (RegistrationStatus.REGISTERED.name().equalsIgnoreCase(networkSystem.getRegistrationStatus())) {
                oldTransportZone.setRegistrationStatus(RegistrationStatus.REGISTERED.name());
            }
        }
        if (!oldTransportZone.getNativeId().equals(
                newTransportZone.getNativeId())) {
            oldTransportZone.setNativeId(newTransportZone.getNativeId());
        }
        // update the results object
        results.getModified().add(oldTransportZone);
    }

    /**
     * This function handles transport zones that were seen on the network
     * system before but cannot be found by fabric WWN search in the new list of
     * transport zone. The possible scenario are :
     * <ol>
     * <li>The fabric was indeed removed from the network system</li>
     * <li>The fabric still exists but under a new WWN (case when principal switch changes or fragmentation)</li>
     * <li>The fabric was discovered while fragmented and assumed to be an isolated fabric but now it is merging again and so, for all
     * practical purposes, the old WWN should be removed</li>
     * </ol>
     * 
     * @param tzone
     *            - the zone that cannot be matched by WWN to a new zone
     * @param networkUri
     *            - the URI of the network system
     * @param newTransportZones
     *            - The collection of transport zones discovered on the network system
     * @param oldTransportZones
     *            - The collection of all transport zones discovered and active
     * @return The new transport zone that has been determined to be the match of tzone.
     * @throws IOException
     */
    private Network handleRemovedTransportZone(Network tzone,
            URI networkUri, HashMap<String, Network> newTransportZones,
            Collection<Network> oldTransportZones, Results results) throws IOException {
        Network newTransportZone = null;
        String uri = networkUri.toString();
        if (tzone.getNetworkSystems().contains(uri)) {
            // first we check if it is removed, fragmented or has a new WWN
            // If we find a network with the same fabricId, the network either merging and has new WWN
            newTransportZone = findByFabricId(tzone, newTransportZones);
            if (newTransportZone == null) { // this transport zone no longer exists
                _log.info("Existing network {} did not match any in the " +
                        " new networks by name or by WWN. It must have been removed.",
                        tzone.getLabel());
                results.getRemoved().add(tzone);
            } else { // new principal switch? merging?
                _log.info("Existing network {} matches {} in the " +
                        " new networks by name. Reconciling the two.",
                        tzone.getLabel(), newTransportZone.getLabel());
                // find if an existing network matched by fabricId and WWN
                Network oldTransportZone = findMatchByFabricIdAndWwn(newTransportZone, oldTransportZones);
                if (oldTransportZone != null) {
                    _log.info("The fabric must have fragmented and is now merging." +
                            "User changes will be merged also any endpoints found will be added.");
                    // merge user changes to the other fragment and remove from this one so it can be deleted
                    oldTransportZone.addEndpoints(getUserCreatedEndPoints(tzone), false);
                    tzone.removeEndpoints(getUserCreatedEndPoints(tzone));
                    if (tzone.getAssignedVirtualArrays() != null) {
                        oldTransportZone.addAssignedVirtualArrays(tzone.getAssignedVirtualArrays());
                        tzone.removeAssignedVirtualArrays(tzone.getAssignedVirtualArrays());
                    }
                    // remove the fragment
                    results.getRemoved().add(tzone);
                    // we are removing the old - the new one is NOT a match
                    newTransportZone = null;
                } else { // either fragmenting or principal switch change -
                         // Update the old transportZone
                    _log.info("Either the fabric is fragmenting or the principal " +
                            "switch has changed. Adding new endpoints and keeping old ones.");
                    mergeNetworks(tzone, newTransportZone);
                    results.getModified().add(tzone);
                }
            }
        }
        return newTransportZone;
    }

    /**
     * Update one network with information from another
     * 
     * @param tzone the target network to be updates
     * @param sZone the source network
     */
    private void mergeNetworks(Network tzone, Network sZone) {
        List<String> addedEndPoints = new ArrayList<String>(
                sZone.retrieveEndpoints());
        StringSet newEndPoints = sZone.retrieveEndpoints();
        for (String endpoint : tzone.retrieveEndpoints()) {
            if (newEndPoints.contains(endpoint)) {
                // the end point still present - account for that
                addedEndPoints.remove(endpoint);
            }
        }
        if (!addedEndPoints.isEmpty()) {
            // only add, do not remove because things are not stable
            tzone.addEndpoints(addedEndPoints, true);
        }
        tzone.setNativeGuid(sZone.getNativeGuid());
    }

    /**
     * We have a network discovered on the network system and we want
     * to find the existing matching network
     * 
     * @param newTransportZone
     * @param oldTransportZones
     * @return
     */
    private Network findMatchByFabricIdAndWwn(Network newTransportZone,
            Collection<Network> oldTransportZones) {
        for (Network zone : oldTransportZones) {
            if (newTransportZone.getNativeId().equals(zone.getNativeId())
                    && newTransportZone.getNativeGuid().equals(
                            zone.getNativeGuid())) {
                return zone;
            }
        }
        return null;
    }

    private String parseWWNFromGuid(String guid) {
        String[] splitGuid = guid.split("\\+");
        if (splitGuid.length == 3) {
            return splitGuid[2];
        }
        return "";
    }

    private Network createTransportZone(NetworkSystem network,
            String fabricWwn, String fabricId) {
        Network newTZ = new Network();
        String nativeGuid = NativeGUIDGenerator
                .generateTransportZoneNativeGuid(Transport.FC.toString(),
                        network.getSystemType(), fabricWwn);
        String prefix = network.getSystemType().equals(NetworkSystem.Type.mds.toString()) ? PREFIX_VSAN
                : PREFIX_FABRIC;

        newTZ.setId(URIUtil.createId(Network.class));
        newTZ.setLabel(prefix + fabricId);
        newTZ.setNativeId(fabricId);
        newTZ.setTransportType(Transport.FC.toString());
        newTZ.setDiscovered(true);
        newTZ.setNativeGuid(nativeGuid);
        newTZ.setNetworkSystems(new StringSet());
        newTZ.getNetworkSystems().add(network.getId().toString());
        newTZ.setRegistrationStatus(network.getRegistrationStatus());
        return newTZ;
    }

    private Network findByFabricId(Network tzone,
            HashMap<String, Network> newTransportZones) {
        for (Network newTz : newTransportZones.values()) {
            if (newTz.getNativeId().equals(tzone.getNativeId())) {
                return newTz;
            }
        }
        return null;
    }

    /**
     * Finds and returns the user-added endpoints in a network
     * 
     * @param tzone the network
     * @return a list of user-added endpoints in the network. An empty list
     *         if no user-added endpoints were found.
     */
    public static List<String> getUserCreatedEndPoints(Network tzone) {
        List<String> endPoints = new ArrayList<String>();
        for (String endPoint : tzone.retrieveEndpoints()) {
            if (tzone.getEndpointsMap().get(endPoint).equals(Boolean.FALSE.toString())) {
                endPoints.add(endPoint);
            }
        }
        return endPoints;
    }

    class Results {
        private List<Network> added = new ArrayList<Network>();
        private List<Network> removed = new ArrayList<Network>();
        private List<Network> modified = new ArrayList<Network>();
        private Map<Network, List<String>> addedEndPoints = new HashMap<Network, List<String>>();
        private Map<Network, List<String>> removedEndPoints = new HashMap<Network, List<String>>();

        public List<Network> getAdded() {
            return added;
        }

        public List<Network> getRemoved() {
            return removed;
        }

        public List<Network> getModified() {
            return modified;
        }

        public Map<Network, List<String>> getAddedEndPoints() {
            return addedEndPoints;
        }

        public Map<Network, List<String>> getRemovedEndPoints() {
            return removedEndPoints;
        }

        public List<Network> getAddedAndModified() {
            List<Network> changedNetworks = new ArrayList<Network>(added);
            changedNetworks.addAll(modified);
            return changedNetworks;
        }
    }
}
