/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.networkcontroller.impl;

import java.io.IOException;
import java.net.URI;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.DiscoveredDataObject.Type;
import com.emc.storageos.db.client.model.FCEndpoint;
import com.emc.storageos.db.client.model.Network;
import com.emc.storageos.db.client.model.NetworkSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.util.DataObjectUtils;
import com.emc.storageos.db.client.util.WWNUtility;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.networkcontroller.exceptions.NetworkDeviceControllerException;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.util.NetworkUtil;
import com.emc.storageos.util.VersionChecker;
import com.emc.storageos.volumecontroller.ControllerException;
import com.emc.storageos.volumecontroller.impl.ControllerServiceImpl;
import com.emc.storageos.volumecontroller.impl.ControllerUtils;
import com.emc.storageos.volumecontroller.impl.monitoring.RecordableBourneEvent;
import com.emc.storageos.volumecontroller.impl.monitoring.RecordableEventManager;
import com.emc.storageos.volumecontroller.impl.monitoring.cim.enums.RecordType;

/**
 * This class handles the discovery tasks for NetworkSystem. It uses an instances
 * of {@link NetworkSystemDevice} for device-specific communication.
 * 
 */
public class NetworkDiscoveryWorker {

    private NetworkSystemDevice _device;
    private DbClient dbClient;
    private CoordinatorClient _coordinator;
    private static final String BUNDLE_NAME = "networkdevice"; //$NON-NLS-1$
    private static final String EVENT_SERVICE_TYPE = "Network Discovery";
    private static final String EVENT_SERVICE_SOURCE = "NetworkDiscoveryWorker";

    // Properties controllering expiration of FCEndpoints
    // Here Awol means "Absent Without Leave", i.e. since we last saw it in a sample.
    private Integer _minAwolSamples = 3;
    private Long _minAwolTime = 60000L;

    private RecordableEventManager _evtMgr;

    private static final Logger _log = LoggerFactory
            .getLogger(NetworkDiscoveryWorker.class);

    public NetworkDiscoveryWorker() {
        try {
            ResourceBundle resourceBundle = ResourceBundle
                    .getBundle(BUNDLE_NAME);
            _minAwolSamples = Integer.valueOf(resourceBundle.getString("FCEndpoint.minAwolSamples"));
            _minAwolTime = Long.valueOf(resourceBundle.getString("FCEndpoint.minAwolTime"));
        } catch (Exception ex) {
            _log.error("Failed to get the values for _minAwolSamples and _minAwolTime from resource bundle "
                    + ex.getMessage());
        }
    }

    public NetworkDiscoveryWorker(NetworkSystemDevice device,
            DbClient dbClient) {
        this._device = device;
        this.dbClient = dbClient;
        RecordableEventManager evtMgr =
                new RecordableEventManager();
        evtMgr.setDbClient(dbClient);
        this._evtMgr = evtMgr;

    }

    /**
     * Verify the firmware version for the NetworkSystem
     * 
     * @param uri - Device URI
     * @throws ControllerException thrown if firmware version is not supported
     */
    public void verifyVersion(URI uri) throws ControllerException {
        // Retrieve the storage device info from the database.
        NetworkSystem networkDev = getDeviceObject(uri);
        NetworkSystemDevice networkDevice = getDevice();
        if (networkDevice == null) {
            throw NetworkDeviceControllerException.exceptions.verifyVersionFailedNull(uri.toString());
        }
        String version = null;
        try {
            version = networkDevice.getVersion(networkDev);
            networkDev.setVersion(version);
            String minimumSupportedVersion = VersionChecker.getMinimumSupportedVersion(Type.valueOf(networkDev.getSystemType()));
            _log.info("Verifying version details : Minimum Supported Version {} - Discovered Firmware Version {}",
                    minimumSupportedVersion, version);

            if (VersionChecker.verifyVersionDetails(minimumSupportedVersion, version) < 0) {
                networkDev.setCompatibilityStatus(DiscoveredDataObject.CompatibilityStatus.INCOMPATIBLE.name());
                throw NetworkDeviceControllerException.exceptions.versionNotSupported(version, minimumSupportedVersion);
            } else {
                networkDev.setCompatibilityStatus(DiscoveredDataObject.CompatibilityStatus.COMPATIBLE.name());
            }
        } catch (Exception ex) {
            Date date = new Date();
            networkDev.setLastDiscoveryStatusMessage(ex.getMessage());
            throw NetworkDeviceControllerException.exceptions.verifyVersionFailed(uri.toString(), date.toString(), ex);
        } finally {
            if (networkDev != null) {
                try {
                    dbClient.updateObject(networkDev);
                } catch (DatabaseException ex) {
                    _log.error("Error while persisting object to DB", ex);
                }
            }
        }
    }

    /**
     * Update the network system physical inventory and creates/updates the discovered FC transport
     * zones as needed. The physical inventory is primarily the FCEndpoints (FCPortConnections),
     * which contains a record for each endpoint logged into the Fiber Channel Nameserver database.
     * The endpoints per fabric (vsan) constitute an FC transport zone which get created/updated
     * based on the FCEndpoints discovered.
     * 
     * @param uri - Device URI
     */
    public void updatePhysicalInventory(URI uri)
            throws ControllerException {
        // Retrieve the storage device info from the database.
        long start = System.currentTimeMillis();
        NetworkSystem networkDev = getDeviceObject(uri);
        String msg = "unknown status";
        NetworkSystemDevice networkDevice = getDevice();
        if (networkDevice == null) {
            throw NetworkDeviceControllerException.exceptions.updatePhysicalInventoryFailedNull(
                    uri.toString(), networkDev.getSystemType());
        }
        try {
            // === Reconcile the FCEndpoints of this device ===
            List<FCEndpoint> currentConnections = new ArrayList<FCEndpoint>();
            // IN/OUT parameter to get the routed endpoints map - Fabric-WWN-to-endpoints-WWN
            Map<String, Set<String>> routedEndpoints = new HashMap<String, Set<String>>();
            try {
                currentConnections = networkDevice.getPortConnections(networkDev, routedEndpoints);
                msg = MessageFormat.format("Retrieved {0} connections from device {1} at {2}",
                        new Integer(currentConnections.size()), uri, new Date());
                _log.info(msg);
            } catch (Exception e) {
                msg = MessageFormat.format("Discovery failed getting port connections for Network System : {0}",
                        uri.toString());
                throw (e);
            }

            try {
                reconcileFCEndpoints(networkDev, currentConnections);
            } catch (Exception e) {
                msg = MessageFormat.format("Discovery failed reconciling FC endpoints for Network System : {0}",
                        uri.toString());
                throw (e);
            }

            // ==== Reconcile the discovered transport zones ======
            try {
                reconcileTransportZones(networkDev, routedEndpoints);
            } catch (Exception e) {
                msg = MessageFormat.format("Discovery failed reconciling networks for Network System : {0}",
                        uri.toString());
                throw (e);
            }

            try {
                networkDev.setUptime(networkDevice.getUptime(networkDev));
            } catch (Exception e) {
                msg = MessageFormat.format("Discovery failed setting version/uptime for Network System : {0}",
                        uri.toString());
                throw (e);
            }

            // discovery succeeds
            msg = MessageFormat.format("Discovery completed successfully for Network System : {0}", uri.toString());
        } catch (Exception ex) {
            Date date = new Date();
            throw NetworkDeviceControllerException.exceptions.updatePhysicalInventoryFailedExc(
                    uri.toString(), date.toString(), ex);
        } finally {
            if (networkDev != null) {
                try {
                    // set detailed message
                    networkDev.setLastDiscoveryStatusMessage(msg);
                    dbClient.updateObject(networkDev);
                    _log.info("Discovery took {}", (System.currentTimeMillis() - start));
                } catch (DatabaseException ex) {
                    _log.error("Error while persisting object to DB", ex);
                }
            }
        }
    }

    /**
     * Reconciles the current set of a Device's endpoints with what is persisted.
     * Updates the database accordingly.
     * 
     * @param dev
     * @param currentConnections
     * @throws IOException
     */
    private void reconcileFCEndpoints(NetworkSystem dev, List<FCEndpoint> currentConnections)
            throws IOException {
        // First, read all the existing connections from the device, and put them into a map
        // keyed by remote wwpn.
        URIQueryResultList uriList = new URIQueryResultList();
        dbClient.queryByConstraint(ContainmentConstraint.Factory
                .getNetworkSystemFCPortConnectionConstraint(dev.getId()), uriList);
        Map<String, FCEndpoint> existingEndpoints = new HashMap<String, FCEndpoint>();
        for (URI uriold : uriList) {
            FCEndpoint connection = dbClient.queryObject(FCEndpoint.class, uriold);
            if (connection != null) {
                existingEndpoints.put(connection.getRemotePortName().toUpperCase(), connection);
            }
        }
        // Now, scan the new endpoints, looking for added or updated records by
        // comparing them with the existing endpoints. Keep track of what was processed
        // so can do deletions on anything not seen in the currentConnections.
        List<FCEndpoint> updated = new ArrayList<FCEndpoint>();
        List<FCEndpoint> created = new ArrayList<FCEndpoint>();
        Set<String> processedWwpns = new HashSet<String>();
        int conflictingEndpoints = 0;
        for (FCEndpoint current : currentConnections) {
            String key = current.getRemotePortName().toUpperCase();
            processedWwpns.add(key);
            FCEndpoint existing = existingEndpoints.get(key);
            if (existing == null) {
                current.setNetworkDevice(dev.getId());
                current.setId(URIUtil.createId(FCEndpoint.class));
                if (WWNUtility.isValidWWN(current.getRemotePortName())) {
                	created.add(current);
            	}
                conflictingEndpoints += removeConflictingEndpoints(key, current.getFabricWwn(), dev.getId());
            } else {
                boolean modified = checkUpdated(existing, current);
                if (existing.getAwolCount() > 0) {
                    modified = true;
                    existing.setAwolCount(0);
                    existing.setAwolTime(null);
                }
                if (modified) {
                    if (WWNUtility.isValidWWN(existing.getRemotePortName())) {
                   	 updated.add(existing);
                   }
                    conflictingEndpoints += removeConflictingEndpoints(key, current.getFabricWwn(), dev.getId());
                }
            }
        }

        // Determine those to be deleted. Remove all the processed records from the existing set.
        // What was left were not seen this time.
        for (String key : processedWwpns) {
            existingEndpoints.remove(key);
        }
        // The remaining existingEndpoints can be processed for removal.
        // They are removed after a minimum number of samples and minimum amount of time has transpired.
        Integer removedCount = 0;
        for (FCEndpoint entry : existingEndpoints.values()) {
            int count = entry.getAwolCount();
            if (count == 0) {
                entry.setAwolTime(System.currentTimeMillis());
            }
            entry.setAwolCount(++count);
            if (count >= _minAwolSamples
                    && (System.currentTimeMillis() - entry.getAwolTime()) > _minAwolTime) {
                removedCount++;
                dbClient.removeObject(entry);
            } else {
                updated.add(entry);        // update counters
            }
        }
        // Persist created, modified.
        dbClient.createObject(created);
        dbClient.updateObject(updated);
        _log.info(MessageFormat.format("{0} new connections persisted", created.size()).toString());
        _log.info(MessageFormat.format("{0} updated connections persisted", updated.size()).toString());
        _log.info(MessageFormat.format("{0} missing connections", existingEndpoints.values().size()).toString());
        _log.info(MessageFormat.format("{0} removed connections", removedCount.toString()));
        _log.info(MessageFormat.format("{0} conflicting connections (removed)", conflictingEndpoints));
    }

    /**
     * Check that a single pair of attributes changed.
     * 
     * @param existing
     * @param current
     * @return true if changed
     */
    private boolean checkAttributeChanged(String existing, String current) {
        if (existing == null && current == null)
        {
            return false; // Both null, no change
        }
        if ((existing == null) ^ (current == null))
        {
            return true; // One null, a change
        }
        return (!existing.equalsIgnoreCase(current));
    }

    /**
     * Returns true if any fields of significance has been modified.
     * 
     * @param existing
     * @param current
     * @return false if no updates, true if updated
     */
    private boolean checkUpdated(FCEndpoint existing, FCEndpoint current) {
        boolean updated = false;
        if (existing.getInactive() == true) {
            existing.setInactive(false);
            updated = true;
        }
        if (checkAttributeChanged(existing.getRemotePortName(), current.getRemotePortName())) {
            existing.setRemotePortName(current.getRemotePortName());
            updated = true;
        }
        if (checkAttributeChanged(existing.getRemoteNodeName(), current.getRemoteNodeName())) {
            existing.setRemoteNodeName(current.getRemoteNodeName());
            updated = true;
        }
        if (checkAttributeChanged(existing.getRemotePortAlias(), current.getRemotePortAlias())) {
            existing.setRemotePortAlias(current.getRemotePortAlias() == null ? "" : current.getRemotePortAlias());
            updated = true;
        }
        if (checkAttributeChanged(existing.getFabricId(), current.getFabricId())) {
            existing.setFabricId(current.getFabricId());
            updated = true;
        }
        if (checkAttributeChanged(existing.getFcid(), current.getFcid())) {
            existing.setFcid(current.getFcid());
            updated = true;
        }
        if (checkAttributeChanged(existing.getSwitchInterface(), current.getSwitchInterface())) {
            existing.setSwitchInterface(current.getSwitchInterface());
            updated = true;
        }
        if (checkAttributeChanged(existing.getSwitchName(), current.getSwitchName())) {
            existing.setSwitchName(current.getSwitchName());
            updated = true;
        }
        if (checkAttributeChanged(existing.getSwitchPortName(), current.getSwitchPortName())) {
            existing.setSwitchPortName(current.getSwitchPortName());
            updated = true;
        }
        if (checkAttributeChanged(existing.getFabricWwn(), current.getFabricWwn())) {
            existing.setFabricWwn(current.getFabricWwn());
            updated = true;
        }
        return updated;
    }

    /**
     * Search all the remote FCEndpoints, remove those having the same RemotePortName
     * that are in a different fabric from a different device.
     * 
     * @param remoteWwpn RemotePortName of new entry
     * @param fabricWwn FabricWwn of new entry
     * @param deviceId Device that found the updated connections
     * @return count of those removed
     * @throws IOException
     */
    private int removeConflictingEndpoints(String remoteWwpn, String fabricWwn, URI deviceId) throws IOException {
        int removedCount = 0;
        URIQueryResultList uriList = new URIQueryResultList();
        dbClient.queryByConstraint(AlternateIdConstraint.Factory.
                getFCEndpointRemotePortNameConstraint(remoteWwpn), uriList);
        for (URI uri : uriList) {
            FCEndpoint ep = dbClient.queryObject(FCEndpoint.class, uri);
            if (ep == null) {
                continue;
            }
            if (ep.getNetworkDevice().equals(deviceId)) {
                continue;
            }
            if (ep.getRemotePortName().equals(remoteWwpn) == false) {
                continue;
            }
            if (ep.getFabricWwn().equals(fabricWwn)) {
                continue;
            }
            dbClient.removeObject(ep);
            removedCount++;
        }
        return removedCount;
    }

    /**
     * Given the updated list of end points for one network system, this function will update
     * the transport zones.
     * Require lock when reconciles vsan in fabrics that are linked through ISL. Without locking, multiple VSANs
     * could have same native gui id within the same fabric.
     * 
     * @param networkSystem the network system
     * @param routedEndpoints IN/OUT parameter to get the routed endpoints map of Fabric-WWN-to-endpoints-WWN
     * @throws ControllerException
     */
    private void reconcileTransportZones(NetworkSystem networkSystem, Map<String, Set<String>> routedEndpoints)
            throws ControllerException {

        _log.info("reconcileTransportZones for networkSystem {}", networkSystem.getId());
        ControllerServiceImpl.Lock lock = ControllerServiceImpl.Lock.getLock(ControllerServiceImpl.DISCOVERY_RECONCILE_TZ);
        try {
            _log.debug("Acquiring lock to reconcile transport zone for networkSystem {}", networkSystem.getId());
            lock.acquire();
            _log.info("Acquired lock to reconcile transport zone for networkSystem {}", networkSystem.getId());

            // get the network system's connections from the database
            Iterator<FCEndpoint> iNewEndPoints = getNetworkSystemEndPoints(networkSystem);
            // get all the transport zones we have in the DB
            List<Network> oldTransportZones = NetworkUtil.getDiscoveredNetworks(dbClient);
            _log.info("Found {} existing transport zones", oldTransportZones.size());
            // get the fabrics that exist on the network system
            Map<String, String> fabricIdsMap = getDevice().getFabricIdsMap(networkSystem);
            // get the list of fabrics added, removed, changed
            TransportZoneReconciler reconciler = new TransportZoneReconciler();
            TransportZoneReconciler.Results results = reconciler.reconcile(networkSystem, iNewEndPoints, fabricIdsMap, oldTransportZones);
            String networkSystemUri = networkSystem.getId().toString();
            for (Network tzone : results.getRemoved()) {
                List<String> removedEps = removeNetworkSystemTransportZone(tzone, networkSystemUri);
                _log.info("Removed network {} which removed discovered endpoints {}", tzone.getNativeGuid(), removedEps);
            }
            for (Network tzone : results.getAdded()) {
                handleEndpointsAdded(tzone, tzone.retrieveEndpoints());
                saveTransportZone(tzone, true);
            }
            for (Network tzone : results.getModified()) {
                if (results.getRemovedEndPoints().get(tzone) != null) {
                    NetworkAssociationHelper
                            .handleEndpointsRemoved(tzone, results.getRemovedEndPoints().get(tzone), dbClient, _coordinator);
                }
                if (results.getAddedEndPoints().get(tzone) != null) {
                    handleEndpointsAdded(tzone, results.getAddedEndPoints().get(tzone));
                }
                saveTransportZone(tzone, false);
            }
            // update routed networks for routed and modified networks
            updateRoutedNetworks(networkSystem, results.getAddedAndModified(), routedEndpoints);
        } catch (Exception ex) {
            throw NetworkDeviceControllerException.exceptions.reconcileTransportZonesFailedExc(
                    new Date().toString(), ex);
        } finally {
            try {
                _log.debug("Releasing reconcile transport zone lock for networkSystem {}", networkSystem.getId());
                lock.release();
                _log.info("Released reconcile transport zone lock for networkSystem {}", networkSystem.getId());
            } catch (Exception e) {
                _log.error("Failed to release  Lock while reconcile transport zone for network {} -->{}", networkSystem.getId(),
                        e.getMessage());
            }
        }
    }

    /**
     * Looks in the topology view for endpoints that accessible by routing
     * 
     * @param networkSystem the network system being refreshed
     * @param updatedNetworks the networks that require updating
     * @param routedEndpoints the routed endpoints map of Fabric-WWN-to-endpoints-WWN
     * @throws Exception
     */
    private void updateRoutedNetworks(NetworkSystem networkSystem,
            List<Network> updatedNetworks, Map<String, Set<String>> routedEndpoints) throws Exception {
        // for each network, get the list of routed ports and locate them in other networks
        StringSet routedNetworks = null;
        Network routedNetwork = null;

        if (!this.getDevice().isCapableOfRouting(networkSystem)) {
        	_log.info("NetworkSystem {} does not support routing across VSANs, skipping routed networks update/discovery", networkSystem.getLabel());
        	
        	//Clear out the routedNetworks entries, if filled, for non-IVR Cisco switches. This can happen in upgrade scenarios.
           URIQueryResultList networkSystemNetworkUriList = new URIQueryResultList();
            dbClient.queryByConstraint(ContainmentConstraint.Factory.
                            getNetworkSystemNetworkConstraint(networkSystem.getId()), networkSystemNetworkUriList);

           for (URI networkSystemNetworkUri : networkSystemNetworkUriList) {
                   Network networkSystemNetwork = dbClient.queryObject(Network.class, networkSystemNetworkUri);
                   boolean isNetworkConnectedToRoutableSwitch = false;
                   for (String ns : networkSystemNetwork.getNetworkSystems()) {
                	   NetworkSystem connectedNetworkSystem = dbClient.queryObject(NetworkSystem.class, URI.create(ns));
                	   if (this.getDevice().isCapableOfRouting(connectedNetworkSystem)) {
                		   isNetworkConnectedToRoutableSwitch = true;;
                	   }                	   
                   }
                   
                   //It is not uncommon to have a VSAN/network that spans two switches and those switches can be both IVR or both non-IVR or a mix of IVR and non-IVR.
                   //In the case of one IVR and one non-IVR switch for a network, do not zero/null the routedNetwork. It is possible to route from that VSAN on the IVR switch
                   //to another switch that supports routing. 
                   if (!isNetworkConnectedToRoutableSwitch) {
                	   _log.info("Updating routedNetwork to null for {}", networkSystemNetwork.getLabel());
                	   networkSystemNetwork.setRoutedNetworks(null);
                	   dbClient.updateObject(networkSystemNetwork);
                   }
           }
           
           //Update the connected varray assignments
           _log.info("Updating connected network and varray assignments");
           for (Network network : DataObjectUtils.toMap(NetworkUtil.getDiscoveredNetworks(dbClient)).values()) {
               NetworkAssociationHelper.setNetworkConnectedVirtualArrays(network, false, dbClient);
           }
           return;
        }
        
        
        // get the current networks from the database
        Map<URI, Network> allNetworks = DataObjectUtils.toMap(NetworkUtil.getDiscoveredNetworks(dbClient));
        for (Network network : updatedNetworks) {
            // if this network has any routed endpoints
            Set<String> netRoutedEndpoints = routedEndpoints.get(NetworkUtil.getNetworkWwn(network));
            if (netRoutedEndpoints == null || netRoutedEndpoints.isEmpty()) {
                _log.debug("No routed endpoint in network {}", network.getNativeGuid());
                network.setRoutedNetworks(null);
            } else {
                _log.info("Found {} routed endpoint in network {}", netRoutedEndpoints, network.getNativeGuid());
                routedNetworks = new StringSet();
                for (String endpoint : netRoutedEndpoints) {
                    // find the source network of the routed endpoint
                    routedNetwork = findNetworkForDiscoveredEndPoint(allNetworks.values(), endpoint, network);
                    if (routedNetwork != null) { // it is possible we did not discover the source
                        routedNetworks.add(routedNetwork.getId().toString());
                    }
                }
                network.setRoutedNetworks(routedNetworks);
            }
            dbClient.updateObject(network);
            _log.info("Updated routed networks for {} to {}", network.getNativeGuid(), routedNetworks);
        }
        // clean up transit networks from any one-way associations.
        // Transit networks will show any endpoints routed thru them
        // which may cause one-way associations in the routedNetworks.
        // For example if network A has ep1 and B has ep2 and there is
        // a routed zone between A and B, the transit network C will
        // reports ep1 and ep2 but there is not actual routing between
        // C and A or C and B, so we want to remove these associations.
        for (URI id : allNetworks.keySet()) {
            Network net = allNetworks.get(id);
            boolean updated = false;
            if (net.getRoutedNetworks() != null) {
                routedNetworks = new StringSet(net.getRoutedNetworks());
                // for each network this network is pointing to
                for (String strUri : net.getRoutedNetworks()) {
                    // get the opposite network
                    Network opNet = allNetworks.get(URI.create(strUri));
                    if (opNet != null // it is possible this network is getting removed - the next discovery cleans up
                            && opNet.getRoutedNetworks() != null // check for null in case the other network routed eps are not yet visible
                            && !opNet.getRoutedNetworks().contains(net.getId().toString())) { // if the opposite network is not seeing this
                                                                                              // one
                        // remove this association because the opposite network is does not have the matching association
                        routedNetworks.remove(opNet.getId().toString());
                        updated = true;
                    }
                }
                if (updated) {
                    _log.info("Reconciled routed networks for {} to {}", net.getNativeGuid(), routedNetworks);
                    net.setRoutedNetworks(routedNetworks);
                    dbClient.updateObject(net);
                }
            }
        }
            
        //Determine routed networks. We get here only for switches that have IVR feature enabled. 
        getDevice().determineRoutedNetworks(networkSystem);
        
        for (Network network : allNetworks.values()) {
            NetworkAssociationHelper.setNetworkConnectedVirtualArrays(network, false, dbClient);
        }
    }

    /**
     * Finds the network in the list that has this endpoint.
     * 
     * @param networks a list of networks
     * @param endpoint the endpoint
     * @parame excludeNetwork - exclude this network from result if provided
     * @return the network that contains the endpoint if found, otherwise null.
     */
    private Network findNetworkForDiscoveredEndPoint(Collection<Network> networks, String endpoint, Network excludeNetwork) {
        for (Network network : networks) {
            /*
             * if excludeNetwork not provided, look for first one.
             * Otherwise, ignore the provided network
             */
            if (excludeNetwork == null || !network.getId().equals(excludeNetwork.getId())) {
                if (network.endpointIsDiscovered(endpoint)) {
                    return network;
                }
            }
        }
        return null;
    }

    private void handleEndpointsAdded(Network tzone, Collection<String> endpoints) throws IOException {
        // find if the endpoints exit in some old transport zone
        Map<String, Network> transportZoneMap =
                NetworkAssociationHelper.getNetworksMap(endpoints, dbClient);
        if (!transportZoneMap.isEmpty()) {
            _log.info("Added endpoints {} to transport zone {}", endpoints.toArray(), tzone.getLabel());
            // before we add the endpoints, they need to be removed from their old transport zones
            NetworkAssociationHelper.handleRemoveFromOldNetworks(transportZoneMap, tzone, dbClient, _coordinator);
        }
        // now, add the the endpoints
        NetworkAssociationHelper.handleEndpointsAdded(tzone, endpoints, dbClient, _coordinator);
    }

    private Iterator<FCEndpoint> getNetworkSystemEndPoints(NetworkSystem networkSystem) throws IOException {
        URIQueryResultList uriList = new URIQueryResultList();
        dbClient.queryByConstraint(ContainmentConstraint.Factory
                .getNetworkSystemFCPortConnectionConstraint(networkSystem.getId()), uriList);
        List<URI> uris = new ArrayList<URI>();
        while (uriList.iterator().hasNext()) {
            uris.add(uriList.iterator().next());
        }
        return dbClient.queryIterativeObjects(FCEndpoint.class, uris);
    }

    

    /**
     * Remove the transport zone for a given network system. This typically means
     * to dis-associated it unless this is the last network system associated with the
     * transport zone. In this case, the transport zone will be deleted if:
     * <ul>
     * <li>It was discovered</li>
     * <li>It does not have any user-created ports</li>
     * <li>It does not have any registered ports</li>
     * </ul>
     * 
     * @param tzone
     * @param uri
     * @throws IOException
     */
    public List<String> removeNetworkSystemTransportZone(Network tzone, String uri) throws IOException {
        tzone.removeNetworkSystems(Collections.singletonList(uri)); // dis-associate
        // list of end points getting deleted
        ArrayList<String> toRemove = new ArrayList<String>();
        if (tzone.getNetworkSystems().isEmpty()) {  // if this is the last network system
            List<String> userCreatedEndPoints = TransportZoneReconciler.getUserCreatedEndPoints(tzone);
            if (userCreatedEndPoints.isEmpty() && !tzone.assignedToVarray()) { // delete only if not modified by a user
                _log.info("Removing network {}", tzone.getLabel());
                toRemove.addAll(tzone.retrieveEndpoints());
                NetworkAssociationHelper.handleEndpointsRemoved(tzone, toRemove, dbClient, _coordinator);
                dbClient.markForDeletion(tzone);
                recordTransportZoneEvent(tzone, OperationTypeEnum.DELETE_NETWORK.getEvType(true),
                        OperationTypeEnum.DELETE_NETWORK.getDescription());
            } else {
                _log.info("Network {} is changed by the user and will " +
                        "not be removed. Discovered end points will be removed.",
                        tzone.getLabel());
                for (String pt : tzone.retrieveEndpoints()) {
                    if (!userCreatedEndPoints.contains(pt)) {
                        toRemove.add(pt);
                    }
                }
                tzone.removeEndpoints(toRemove);
                NetworkAssociationHelper.handleEndpointsRemoved(tzone, toRemove, dbClient, _coordinator);
                _log.info("Discovered endpoints removed {}", toRemove.toArray());
                dbClient.updateObject(tzone);
                recordTransportZoneEvent(tzone, OperationTypeEnum.UPDATE_NETWORK.getEvType(true),
                        OperationTypeEnum.UPDATE_NETWORK.getDescription());
            }
        } else {
            _log.info("Removing network {} from network system {}",
                    tzone.getLabel(), uri);
            dbClient.updateObject(tzone);
            recordTransportZoneEvent(tzone, OperationTypeEnum.UPDATE_NETWORK.getEvType(true),
                    OperationTypeEnum.UPDATE_NETWORK.getDescription());
        }
        return toRemove;
    }

    private void saveTransportZone(Network network, boolean newTransportZone) throws IOException {
        if (newTransportZone) {
            dbClient.createObject(network);
            _log.info("Added networks {}", network.getLabel());
            recordTransportZoneEvent(network, OperationTypeEnum.CREATE_NETWORK.getEvType(true),
                    OperationTypeEnum.CREATE_NETWORK.getDescription());
        } else {
            dbClient.updateObject(network);
            _log.info("Updated transport zone {}", network.getLabel());
            recordTransportZoneEvent(network, OperationTypeEnum.UPDATE_NETWORK.getEvType(true),
                    OperationTypeEnum.UPDATE_NETWORK.getDescription());
        }
    }

    /**
     * Returns the NetworkDevice from the db
     * 
     * @param network device URI
     * @return NetworkDevice
     * @throws ControllerException
     */
    private NetworkSystem getDeviceObject(URI network) throws ControllerException {
        NetworkSystem networkDev = null;
        try {
            networkDev = dbClient.queryObject(NetworkSystem.class, network);
        } catch (Exception e) {
            throw NetworkDeviceControllerException.exceptions.getDeviceObjectFailed(
                    network.toString(), e);
        }
        // Verify non-null network device returned from the database client.
        if (networkDev == null) {
            throw NetworkDeviceControllerException.exceptions.getDeviceObjectFailedNull(
                    network.toString());
        }
        return networkDev;
    }

    private NetworkSystemDevice getDevice() {
        return _device;
    }

    public void setDevice(NetworkSystemDevice _device) {
        this._device = _device;
    }

    public void setDbClient(DbClient dbClient) {
        this.dbClient = dbClient;
    }

    public void setCoordinator(CoordinatorClient coordinator) {
        _coordinator = coordinator;
    }

    /**
     * Create a nice event based on the TransportZone
     * 
     * @param tz Network for which the event is about
     * @param type Type of event such as updated, created, removed
     * @param description Description for the event if needed
     */
    private void recordTransportZoneEvent(Network tz, String type, String description) {
        if (tz == null) {
            _log.error("Invalid Network event");
            return;
        }
        // TODO fix the bogus user ID once we have AuthZ working
        RecordableBourneEvent event = ControllerUtils.convertToRecordableBourneEvent(tz, type, description,
                null, dbClient, EVENT_SERVICE_TYPE, RecordType.Event.name(), EVENT_SERVICE_SOURCE);

        try {
            _evtMgr.recordEvents(event);
        } catch (Exception ex) {
            _log.error("Failed to record event. Event description: {}. Error: {}.", description, ex);
        }
    }

}
