/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.vplexcontroller;

import static com.google.common.collect.Collections2.transform;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;

import com.emc.storageos.cinder.CinderConstants;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StorageProvider;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.util.CommonTransformerFunctions;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.networkcontroller.impl.NetworkDeviceController;
import com.emc.storageos.recoverpoint.utils.WwnUtils;
import com.emc.storageos.util.ConnectivityUtil;
import com.emc.storageos.util.ExportUtils;
import com.emc.storageos.util.NetworkUtil;
import com.emc.storageos.util.VPlexUtil;
import com.emc.storageos.volumecontroller.ControllerException;
import com.emc.storageos.volumecontroller.impl.utils.ExportMaskUtils;
import com.emc.storageos.vplex.api.VPlexApiClient;
import com.emc.storageos.vplex.api.VPlexApiConstants;
import com.emc.storageos.vplex.api.VPlexApiException;
import com.emc.storageos.vplex.api.VPlexApiFactory;
import com.emc.storageos.vplex.api.VPlexResourceInfo;
import com.emc.storageos.vplex.api.VPlexStorageViewInfo;
import com.emc.storageos.vplex.api.VPlexStorageVolumeInfo;
import com.emc.storageos.vplex.api.VPlexTargetInfo;
import com.google.common.base.Joiner;
import com.google.common.collect.Collections2;

public class VPlexControllerUtils {
    // logger reference.
    private static final Logger log = LoggerFactory
            .getLogger(VPlexControllerUtils.class);

    private static final String VPLEX = "vplex";

    /**
     * Get a DataObject. Throw exception if not found or inactive.
     * 
     * @param clazz
     * @param id
     * @return
     * @throws ControllerException
     */
    protected static <T extends DataObject> T getDataObject(Class<T> clazz, URI id, DbClient dbClient)
            throws DeviceControllerException {
        try {
            T object = null;

            if (id != null) {
                object = dbClient.queryObject(clazz, id);
                if (object == null) {
                    throw VPlexApiException.exceptions.getDataObjectFailedNotFound(
                            clazz.getSimpleName(), id.toString());
                }
                if (object.getInactive()) {
                    log.info("database object is inactive: " + object.getId().toString());
                    throw VPlexApiException.exceptions.getDataObjectFailedInactive(id.toString());
                }
            }
            return object;
        } catch (DatabaseException ex) {
            throw VPlexApiException.exceptions.getDataObjectFailedExc(id.toString(), ex);
        }
    }

    /**
     * Get the HTTP client for making requests to the passed VPlex management server.
     * 
     * @param vplexApiFactory A reference to the VPlex API factory.
     * @param vplexMnmgtSvr A VPlex management server.
     * @param dbClient A reference to a DB client.
     * 
     * @return A reference to the VPlex API HTTP client.
     * @throws URISyntaxException
     */
    public static VPlexApiClient getVPlexAPIClient(VPlexApiFactory vplexApiFactory,
            StorageProvider vplexMnmgtSvr, DbClient dbClient) throws URISyntaxException {
        URI vplexEndpointURI = new URI("https", null, vplexMnmgtSvr.getIPAddress(),
                vplexMnmgtSvr.getPortNumber(), "/", null, null);

        VPlexApiClient client = vplexApiFactory.getClient(vplexEndpointURI,
                vplexMnmgtSvr.getUserName(), vplexMnmgtSvr.getPassword());
        return client;
    }

    /**
     * Get the HTTP client for making requests to the passed VPlex storage system.
     * 
     * @param vplexApiFactory A reference to the VPlex API factory.
     * @param vplexSystem The VPlex storage system.
     * @param dbClient A reference to a DB client.
     * 
     * @return A reference to the VPlex API HTTP client.
     * @throws URISyntaxException
     */
    public static VPlexApiClient getVPlexAPIClient(VPlexApiFactory vplexApiFactory,
            StorageSystem vplexSystem, DbClient dbClient) throws URISyntaxException {
        // Create the URI to access the VPlex Management Server based
        // on the IP and port for the active provider for the passed
        // VPlex system.
        StorageProvider activeMgmntSvr = null;
        URI activeMgmntSvrURI = vplexSystem.getActiveProviderURI();
        if (!NullColumnValueGetter.isNullURI(activeMgmntSvrURI)) {
            activeMgmntSvr = dbClient.queryObject(StorageProvider.class, activeMgmntSvrURI);
        }

        if (activeMgmntSvr == null) {
            log.error("No active management server for VPLEX system {}", vplexSystem.getId());
            throw VPlexApiException.exceptions.connectionFailure(vplexSystem.getId().toString());
        }

        return getVPlexAPIClient(vplexApiFactory, activeMgmntSvr, dbClient);
    }

    /**
     * Get the HTTP client for making requests to the passed VPlex storage system URI.
     * Performs some helpful validation on the requested VPLEX system before returning
     * successfully.
     * 
     * @param vplexApiFactory A reference to the VPlex API factory.
     * @param vplexSystemUri The VPlex storage system URI.
     * @param dbClient A reference to a DB client.
     * 
     * @return A reference to the VPlex API HTTP client.
     * @throws URISyntaxException
     */
    public static VPlexApiClient getVPlexAPIClient(VPlexApiFactory vplexApiFactory,
            URI vplexUri, DbClient dbClient) throws URISyntaxException {

        if (vplexUri == null) {
            log.error("The provided VPLEX Storage System URI was null.");
            throw VPlexApiException.exceptions.vplexUriIsNull();
        }

        StorageSystem vplex = dbClient.queryObject(StorageSystem.class, vplexUri);

        if (vplex == null) {
            log.error("No VPLEX Storage System was found with URI {}.", vplexUri.toString());
            throw VPlexApiException.exceptions.vplexSystemNotFound(vplexUri.toString());
        }

        if (!vplex.getSystemType().equals(DiscoveredDataObject.Type.vplex.name())) {
            log.error("The Storage System (of type {}) with URI {} is not a VPLEX system.",
                    vplex.getSystemType(), vplexUri.toString());
            throw VPlexApiException.exceptions.invalidStorageSystemType(
                    vplex.getSystemType(), vplexUri.toString());
        }

        return getVPlexAPIClient(vplexApiFactory, vplex, dbClient);
    }

    /**
     * Determines the cluster name for a VPlex virtual volume based on the
     * volume's virtual array.
     * 
     * @param dbClient db client
     * @param vplexVolume The VPlex volume whose cluster we want to find.
     * @return The VPlex cluster name
     * @throws Exception
     * @throws URISyntaxException
     */
    public static String getVPlexClusterName(DbClient dbClient, Volume vplexVolume) throws Exception {
        // Get the virtual array from the vplex virtual volume. This will be used
        // to determine the volume's vplex cluster.
        URI vaURI = vplexVolume.getVirtualArray();
        URI vplexURI = vplexVolume.getStorageController();
        return getVPlexClusterName(dbClient, vaURI, vplexURI);
    }
    
    /**
     * Determines the cluster name based on the volume's virtual array.
     * 
     * @param dbClient db client
     * @param vplexURI The vplex system URI
     * @param vaURI The virtual array URI
     * @return The VPlex cluster name
     * @throws Exception
     * @throws URISyntaxException
     */
    public static String getVPlexClusterName(DbClient dbClient, URI vaURI, URI vplexURI) {
        String clusterName = null;
        
        // Get the vplex storage system so we can a handle on the vplex client
        StorageSystem vplexSystem = getDataObject(StorageSystem.class, vplexURI, dbClient);
        VPlexApiClient client = null;

        try {
            client = VPlexControllerUtils.getVPlexAPIClient(VPlexApiFactory.getInstance(), vplexSystem, dbClient);
        } catch (URISyntaxException e) {
            throw VPlexApiException.exceptions.connectionFailure(vplexURI.toString());
        }

        String vplexCluster = ConnectivityUtil.getVplexClusterForVarray(vaURI, vplexSystem.getId(), dbClient);
        if (vplexCluster.equals(ConnectivityUtil.CLUSTER_UNKNOWN)) {
            throw VPlexApiException.exceptions.couldNotFindCluster(vplexCluster);
        }

        clusterName = client.getClusterNameForId(vplexCluster);

        return clusterName;
    }

    /**
     * Returns the cluster name (free form, user-configurable)
     * for a given VPLEX cluster id (either "1" or "2").
     * 
     * @param clusterId the cluster id (either "1" or "2")
     * @param vplexUri URI of the VPLEX to look at
     * @param dbClient a database client instance
     * 
     * @return the cluster name as configured by the user, or null
     *         if it couldn't be determined
     */
    public static String getClusterNameForId(String clusterId, URI vplexUri, DbClient dbClient) {

        String clusterName = null;
        VPlexApiClient client = null;

        try {
            VPlexApiFactory vplexApiFactory = VPlexApiFactory.getInstance();
            client = VPlexControllerUtils.getVPlexAPIClient(vplexApiFactory, vplexUri, dbClient);
        } catch (URISyntaxException e) {
            log.error("cannot load vplex api client", e);
        }

        if (null != client) {
            clusterName = client.getClusterNameForId(clusterId);
        }

        log.info("VPLEX cluster name for cluster id {} is {}", clusterId, clusterName);
        return clusterName;
    }

    /**
     * Returns a VPlexResourceInfo object for the given device name based
     * on its virtual volume type (local or distributed).
     * 
     * @param deviceName the name of the device
     * @param virtualVolumeType the type of virtual volume (local or distributed)
     * @param vplexUri the URI of the VPLEX system
     * @param dbClient a reference to the database client
     * 
     * @return a VPlexResourceInfo object for the device name
     * @throws VPlexApiException
     */
    public static VPlexResourceInfo getDeviceInfo(String deviceName, String virtualVolumeType,
            URI vplexUri, DbClient dbClient) throws VPlexApiException {
        VPlexResourceInfo device = null;
        VPlexApiClient client = null;

        try {
            VPlexApiFactory vplexApiFactory = VPlexApiFactory.getInstance();
            client = VPlexControllerUtils.getVPlexAPIClient(vplexApiFactory, vplexUri, dbClient);
        } catch (URISyntaxException e) {
            log.error("cannot load vplex api client", e);
        }

        if (null != client) {
            device = client.getDeviceStructure(deviceName, virtualVolumeType);
        }

        return device;
    }

    /**
     * Returns a Map of lowest-level storage-volume resource's WWN to its VPlexStorageVolumeInfo
     * object for a given device name, virtual volume type, and cluster name. If
     * hasMirror is true, this indicates the top-level device is composed of a
     * RAID-1 mirror, so there's an extra layers of components to traverse in finding
     * the lowest-level storage-volume resources.
     * 
     * @param deviceName the name of the top-level device to look at
     * @param virtualVolumeType the type of virtual volume (local or distributed)
     * @param clusterName the cluster name
     * @param hasMirror indicates if the top-level device is a RAID-1 mirror
     * @param vplexUri the URI of the VPLEX system
     * @param dbClient a reference to the database client
     * 
     * @return a map of WWNs to VPlexStorageVolumeInfo objects
     * @throws VPlexApiException
     */
    public static Map<String, VPlexStorageVolumeInfo> getStorageVolumeInfoForDevice(
            String deviceName, String virtualVolumeType, String clusterName,
            boolean hasMirror, URI vplexUri, DbClient dbClient) throws VPlexApiException {

        Map<String, VPlexStorageVolumeInfo> storageVolumeInfo = null;
        VPlexApiClient client = null;

        try {
            VPlexApiFactory vplexApiFactory = VPlexApiFactory.getInstance();
            client = VPlexControllerUtils.getVPlexAPIClient(vplexApiFactory, vplexUri, dbClient);
        } catch (URISyntaxException e) {
            log.error("cannot load vplex api client", e);
        }

        if (null != client) {
            storageVolumeInfo = client.getStorageVolumeInfoForDevice(
                    deviceName, virtualVolumeType, clusterName, hasMirror);
        }

        log.info("Backend storage volume wwns for {} are {}", deviceName, storageVolumeInfo);
        return storageVolumeInfo;
    }

    /**
     * Gets the list of ITLs from the volume extensions
     * 
     * @param volume
     * @return
     */
    public static List<String> getVolumeITLs(Volume volume) {
        List<String> itlList = new ArrayList<>();

        StringMap extensions = volume.getExtensions();
        if (null != extensions) {
            Set<String> keys = extensions.keySet();
            for (String key : keys)
            {
                if (key.startsWith(CinderConstants.PREFIX_ITL)) {
                    itlList.add(extensions.get(key));
                }
            }
        }

        return itlList;
    }

    /**
     * Returns true if the Initiator object represents a VPLEX StoragePort.
     * 
     * @param initiator the Initiator to test
     * @param dbClient a reference to the database client
     * 
     * @return true if the Initiator object represents a VPLEX StoragePort
     */
    public static boolean isVplexInitiator(Initiator initiator, DbClient dbClient) {
        StoragePort port = NetworkUtil.getStoragePort(initiator.getInitiatorPort(), dbClient);
        if (null != port) {
            StorageSystem vplex = dbClient.queryObject(StorageSystem.class, port.getStorageDevice());
            if (null != vplex && VPLEX.equals(vplex.getSystemType())) {
                return true;
            }
        }

        return false;
    }

    /**
     * Returns a Map of distributed device component context
     * paths from the VPLEX API to VPLEX cluster names.
     * 
     * @param vplexUri the VPLEX to query
     * @param dbClient a reference to the database client
     * @return a Map of distributed device component context
     *         paths from the VPLEX API to VPLEX cluster names
     * 
     * @throws VPlexApiException
     */
    public static Map<String, String> getDistributedDevicePathToClusterMap(
            URI vplexUri, DbClient dbClient) throws VPlexApiException {
        VPlexApiClient client = null;

        try {
            VPlexApiFactory vplexApiFactory = VPlexApiFactory.getInstance();
            client = VPlexControllerUtils.getVPlexAPIClient(vplexApiFactory, vplexUri, dbClient);
        } catch (URISyntaxException e) {
            log.error("cannot load vplex api client", e);
        }

        Map<String, String> distributedDevicePathToClusterMap = Collections.emptyMap();
        if (null != client) {
            distributedDevicePathToClusterMap =
                    client.getDistributedDevicePathToClusterMap();
        }

        return distributedDevicePathToClusterMap;
    }
    
    /**
     * Validates that the underlying structure of the given device name
     * satisfies the constraints for compatibility with ViPR.  Used for
     * validating unmanaged VPLEX volumes before ingestion.
     * 
     * @param deviceName the device to validate
     * @param vplexUri the VPLEX to query
     * @param dbClient a reference to the database client
     * @throws VPlexApiException if the device structure is incompatible with ViPR
     */
    public static void validateSupportingDeviceStructure(String deviceName, 
            URI vplexUri, DbClient dbClient) throws VPlexApiException {
        VPlexApiClient client = null;

        try {
            VPlexApiFactory vplexApiFactory = VPlexApiFactory.getInstance();
            client = VPlexControllerUtils.getVPlexAPIClient(vplexApiFactory, vplexUri, dbClient);
        } catch (URISyntaxException e) {
            log.error("cannot load vplex api client", e);
        }

        if (null != client) {
            String drillDownResponse = client.getDrillDownInfoForDevice(deviceName);
            if (!VPlexUtil.isDeviceStructureValid(deviceName, drillDownResponse)) {
                throw VPlexApiException.exceptions.deviceStructureIsIncompatibleForIngestion(drillDownResponse);
            }
        } else {
            throw VPlexApiException.exceptions.failedToExecuteDrillDownCommand(
                    deviceName, "cannot load vplex api client");
        }
    }

    /**
     * Creates a Map of target-port to port-wwn values for a VPLEX device
     * For example: target port - P0000000046E01E80-A0-FC02 
     *              port wwn    - 0x50001442601e8002
     * 
     * @param client a reference to the VPlexApiClient to query for port info
     * @param clusterName the cluster for this port name search; port names can potentially be different
     *                    across clusters.
     * @return a Map of target-port to port-wwn values for a VPLEX device
     */
    public static Map<String, String> getTargetPortToPwwnMap(VPlexApiClient client, String clusterName) {

        Map<String, String> targetPortToPwwnMap = new HashMap<String, String>();
        List<VPlexTargetInfo> targetInfos = client.getTargetInfoForCluster(clusterName);
        if (targetInfos != null) {
            for (VPlexTargetInfo vplexTargetPortInfo : targetInfos) {
                if (null != vplexTargetPortInfo.getPortWwn()) {
                    targetPortToPwwnMap.put(vplexTargetPortInfo.getName(), vplexTargetPortInfo.getPortWwn());
                }
            }
        }

        log.info("target port map for cluster {} is {}", clusterName, targetPortToPwwnMap);
        return targetPortToPwwnMap;
    }

    /**
     * Refreshes the given ExportMask with the latest export information from the VPLEX.
     * 
     * This method follows the same basic logic as VmaxExportOperations.refreshExportMask, 
     * but adjusted for VPLEX storage views and other concepts.
     * 
     * @param dbClient a reference to the database client
     * @param storageView a reference to the VPlexStorageViewInfo for the ExportMask's mask name
     * @param exportMask the ExportMask to refresh
     * @param vplexClusterName the VPLEX cluster name on which to find the ExportMask
     * @param targetPortToPwwnMap a Map of VPLEX target port names to WWNs
     * @param networkDeviceController the NetworkDeviceController, used for refreshing the zoning map
     */
    public static void refreshExportMask(DbClient dbClient, VPlexStorageViewInfo storageView, 
            ExportMask exportMask, Map<String, String> targetPortToPwwnMap, NetworkDeviceController networkDeviceController) {
        refreshExportMask(dbClient, storageView, exportMask, targetPortToPwwnMap, networkDeviceController, false);
    }

    /**
     * Refreshes the given ExportMask with the latest export information from the VPLEX, with
     * an option to indicate whether or not the caller is a remove volume or initiator operation.
     * If removing volumes or initiators, we will not remove anything from the existing collections
     * so that any logic for data unavailability prevention can be made on the state before ViPR
     * attempts to make any changes to the storage view.
     * 
     * This method follows the same basic logic as VmaxExportOperations.refreshExportMask, 
     * but adjusted for VPLEX storage views and other concepts.
     * 
     * @param dbClient a reference to the database client
     * @param storageView a reference to the VPlexStorageViewInfo for the ExportMask's mask name
     * @param exportMask the ExportMask to refresh
     * @param vplexClusterName the VPLEX cluster name on which to find the ExportMask
     * @param targetPortToPwwnMap a Map of VPLEX target port names to WWNs
     * @param networkDeviceController the NetworkDeviceController, used for refreshing the zoning map
     * @param isRemoveOperation flag to indicate whether the caller is a operation that removes inits or vols
     */
    private static void refreshExportMask(DbClient dbClient, VPlexStorageViewInfo storageView, 
            ExportMask exportMask, Map<String, String> targetPortToPwwnMap, 
            NetworkDeviceController networkDeviceController, boolean isRemoveOperation) {
        try {

            if (null == exportMask || null == storageView || null == targetPortToPwwnMap || targetPortToPwwnMap.isEmpty()) {
                int portNameMapEntryCount = targetPortToPwwnMap != null ? targetPortToPwwnMap.size() : 0;
                String message = String.format("export mask was %s, storage view was %s, and port name to wwn map had %d entries",
                        exportMask, storageView, portNameMapEntryCount);
                log.error(message);
                if (null == storageView) {
                    if (null != exportMask) {
                        log.warn(String.format("storage view %s could not be found on VPLEX device %s", 
                                exportMask.getMaskName(), exportMask.getStorageDevice()));
                        cleanStaleExportMasks(dbClient, exportMask.getStorageDevice());
                    }
                    return;
                } else {
                    throw new IllegalArgumentException("export mask refresh arguments are invalid: " + message);
                }
            }

            // Get volumes and initiators for the masking instance
            Map<String, Integer> discoveredVolumes = storageView.getWwnToHluMap();
            List<String> discoveredInitiators = storageView.getInitiatorPwwns();

            Set<String> existingVolumes = (exportMask.getExistingVolumes() != null) ?
                    exportMask.getExistingVolumes().keySet() : Collections.emptySet();
            Set<String> existingInitiators = (exportMask.getExistingInitiators() != null) ?
                    exportMask.getExistingInitiators() : Collections.emptySet();

            List<String> viprVolumes = new ArrayList<String>();
            if (exportMask.getVolumes() != null) {
                List<Volume> vols = dbClient.queryObject(Volume.class, URIUtil.toURIList(exportMask.getVolumes().keySet()));
                for (Volume volume : vols) {
                    viprVolumes.add(volume.getWWN());
                }
            }
            List<String> viprInits = new ArrayList<String>();
            if (exportMask.getInitiators() != null && !exportMask.getInitiators().isEmpty()) {
                List<Initiator> inits = dbClient.queryObject(Initiator.class, URIUtil.toURIList(exportMask.getInitiators()));
                for (Initiator init : inits) {
                    viprInits.add(Initiator.normalizePort(init.getInitiatorPort()));
                }
            }
            // Update user added volume's HLU information in ExportMask and ExportGroup
            ExportMaskUtils.updateHLUsInExportMask(exportMask, discoveredVolumes, dbClient);

            String name = exportMask.getMaskName();

            log.info(String.format("%nExportMask %s in the ViPR database: ViPR Vols:{%s} ViPR Inits:{%s} Existing Inits:{%s} Existing Vols:{%s}%n", name,
                    Joiner.on(',').join(viprVolumes),
                    Joiner.on(',').join(viprInits),
                    Joiner.on(',').join(existingInitiators),
                    Joiner.on(',').join(existingVolumes)));

            log.info(String.format("StorageView %s discovered on VPLEX: Inits:{%s} Vols:{%s}%n", name,
                    Joiner.on(',').join(discoveredInitiators),
                    Joiner.on(',').join(discoveredVolumes.keySet())));

            // Check the initiators and update the lists as necessary
            List<String> initiatorsToAddToExisting = new ArrayList<String>();
            List<Initiator> initiatorsToAddToUserAddedAndInitiatorList = new ArrayList<Initiator>();
            for (String port : discoveredInitiators) {
                String normalizedPort = Initiator.normalizePort(port);
                if (!exportMask.hasExistingInitiator(normalizedPort) &&
                        !exportMask.hasUserInitiator(normalizedPort) ) {
                    // If the initiator is in our DB, and it's in our compute resource, it gets added to to the initiator list.
                    // Otherwise it gets added to the existing list.
                    Initiator knownInitiator = ExportUtils.getInitiator(Initiator.toPortNetworkId(port), dbClient);
                    if (knownInitiator != null && !ExportMaskUtils.checkIfDifferentResource(exportMask, knownInitiator)) {
                        initiatorsToAddToUserAddedAndInitiatorList.add(knownInitiator);
                    } else {
                        initiatorsToAddToExisting.add(normalizedPort);
                    }
                    
                }
            }

            // Existing Initiators that are not part of the Storage View discovered initiators
            List<String> initiatorsToRemoveFromExistingList= new ArrayList<String>();
            if (exportMask.getExistingInitiators() != null &&
                    !exportMask.getExistingInitiators().isEmpty()) {
                for (String existingInitiatorStr : exportMask.getExistingInitiators()) {
                    if (!discoveredInitiators.contains(existingInitiatorStr)) {
                        initiatorsToRemoveFromExistingList.add(existingInitiatorStr);
                    } else {
                        Initiator existingInitiator = ExportUtils.getInitiator(Initiator.toPortNetworkId(existingInitiatorStr), dbClient);
                        if (existingInitiator != null && !ExportMaskUtils.checkIfDifferentResource(exportMask, existingInitiator)) {
                            log.info(
                                    "Initiator {}->{} belonging to same compute, removing from existing and adding to  userAdded and initiator list",
                                    existingInitiatorStr,
                                    existingInitiator.getId());
                            initiatorsToAddToUserAddedAndInitiatorList.add(existingInitiator);
                            initiatorsToRemoveFromExistingList.add(existingInitiatorStr);
                        }
                    }
                }
            }

            // Initiators that are not part of the Storage View discovered initiators
            List<URI> initiatorsToRemoveFromUserAddedAndInitiatorList = new ArrayList<URI>();
            if (exportMask.getInitiators() != null &&
                    !exportMask.getInitiators().isEmpty()) {
                initiatorsToRemoveFromUserAddedAndInitiatorList.addAll(Collections2.transform(exportMask.getInitiators(),
                        CommonTransformerFunctions.FCTN_STRING_TO_URI));
                for (String port : discoveredInitiators) {
                    String normalizedPort = Initiator.normalizePort(port);
                    Initiator initiatorDiscoveredInViPR = ExportUtils.getInitiator(Initiator.toPortNetworkId(port), dbClient);
                    if (initiatorDiscoveredInViPR != null) {
                        initiatorsToRemoveFromUserAddedAndInitiatorList.remove(initiatorDiscoveredInViPR.getId());
                    } else if (!exportMask.hasExistingInitiator(normalizedPort)) {
                        log.info("Initiator {} not found in database, removing from user Added and initiator list,"
                                + " and adding to existing list.", port);
                        initiatorsToAddToExisting.add(normalizedPort);
                    }
                }
            }

            boolean removeInitiators = !initiatorsToRemoveFromExistingList.isEmpty()
                    || !initiatorsToRemoveFromUserAddedAndInitiatorList.isEmpty();
            boolean addInitiators = !initiatorsToAddToUserAddedAndInitiatorList.isEmpty()
                    || !initiatorsToAddToExisting.isEmpty();

            // Check the volumes and update the lists as necessary
            Map<String, Integer> volumesToAdd = ExportMaskUtils.diffAndFindNewVolumes(exportMask, discoveredVolumes);
            boolean addVolumes = !volumesToAdd.isEmpty();

            boolean removeVolumes = false;
            List<String> volumesToRemoveFromExisting = new ArrayList<String>();
            if (exportMask.getExistingVolumes() != null &&
                    !exportMask.getExistingVolumes().isEmpty()) {
                volumesToRemoveFromExisting.addAll(exportMask.getExistingVolumes().keySet());
                volumesToRemoveFromExisting.removeAll(discoveredVolumes.keySet());
            }

            // if volume is in userAddedVolumes now, but also in existing volumes,
            // we should remove it from existing volumes
            if (!isRemoveOperation) {
                for (String wwn : discoveredVolumes.keySet()) {
                    if (exportMask.hasExistingVolume(wwn)) {
                        URIQueryResultList volumeList = new URIQueryResultList();
                        dbClient.queryByConstraint(AlternateIdConstraint.Factory.getVolumeWwnConstraint(wwn), volumeList);
                        if (volumeList.iterator().hasNext()) {
                            URI volumeURI = volumeList.iterator().next();
                            if (exportMask.hasUserCreatedVolume(volumeURI)) {
                                log.info("\texisting volumes contain wwn {}, but it is also in the "
                                        + "export mask's user added volumes, so removing from existing volumes", wwn);
                                volumesToRemoveFromExisting.add(wwn);
                            }
                        }
                    }
                }
            }
            removeVolumes = !volumesToRemoveFromExisting.isEmpty();

            // Grab the storage ports that have been allocated for this
            // existing mask and update them.
            List<String> storagePorts = storageView.getPorts();
            List<String> portWwns = new ArrayList<String>();
            for (String storagePort : storagePorts) {
                if (targetPortToPwwnMap.keySet().contains(storagePort)) {
                    portWwns.add(WwnUtils.convertWWN(targetPortToPwwnMap.get(storagePort), WwnUtils.FORMAT.COLON));
                }
            }
            List<String> storagePortURIs = ExportUtils.storagePortNamesToURIs(dbClient, portWwns);

            // Check the storagePorts and update the lists as necessary
            boolean addStoragePorts = false;
            List<String> storagePortsToAdd = new ArrayList<String>();
            if (exportMask.getStoragePorts() == null) {
                exportMask.setStoragePorts(new ArrayList<String>());
            }

            for (String portID : storagePortURIs) {
                if (!exportMask.getStoragePorts().contains(portID)) {
                    storagePortsToAdd.add(portID);
                    addStoragePorts = true;
                }
            }

            boolean removeStoragePorts = false;
            List<String> storagePortsToRemove = new ArrayList<String>();
            if (exportMask.getStoragePorts() != null &&
                    !exportMask.getStoragePorts().isEmpty()) {
                storagePortsToRemove.addAll(exportMask.getStoragePorts());
                storagePortsToRemove.removeAll(storagePortURIs);
                removeStoragePorts = !storagePortsToRemove.isEmpty();
            }

            log.info(
                    String.format(
                            "ExportMask %s refresh initiators; addToExisting:{%s} removeAndUpdateZoning:{%s} removeFromExistingOnly:{%s}%n",
                            name, Joiner.on(',').join(initiatorsToAddToExisting),
                            Joiner.on(',').join(initiatorsToRemoveFromUserAddedAndInitiatorList),
                            Joiner.on(',').join(initiatorsToRemoveFromExistingList)));
            log.info(
                    String.format(
                            "ExportMask %s refresh initiators; user Added and initiator List; add:{%s} remove:{%s}%n",
                            name, Joiner.on(',').join(initiatorsToAddToUserAddedAndInitiatorList),
                            Joiner.on(',').join(initiatorsToRemoveFromUserAddedAndInitiatorList)));
            log.info(
                    String.format("ExportMask %s refresh volumes; addToExisting:{%s} removeFromExistingOnly:{%s}%n",
                            name, Joiner.on(',').join(volumesToAdd.keySet()),
                            Joiner.on(',').join(volumesToRemoveFromExisting)));
            log.info(
                    String.format("ExportMask %s refresh ports; add:{%s} remove:{%s}%n",
                            name, Joiner.on(',').join(storagePortsToAdd),
                            Joiner.on(',').join(storagePortsToRemove)));

            // Any changes indicated, then update the mask and persist it
            if (addInitiators || removeInitiators || addVolumes ||
                    removeVolumes || addStoragePorts || removeStoragePorts) {
                log.info("ExportMask refresh: There are changes to mask, updating it...\n");
                exportMask.removeFromExistingInitiators(initiatorsToRemoveFromExistingList);
                if (initiatorsToRemoveFromUserAddedAndInitiatorList != null && !initiatorsToRemoveFromUserAddedAndInitiatorList.isEmpty()) {
                    exportMask.removeInitiators(dbClient.queryObject(Initiator.class, initiatorsToRemoveFromUserAddedAndInitiatorList));
                    exportMask.removeFromUserCreatedInitiators(dbClient.queryObject(Initiator.class,
                            initiatorsToRemoveFromUserAddedAndInitiatorList));
                }

                exportMask.addToUserCreatedInitiators(initiatorsToAddToUserAddedAndInitiatorList);
                exportMask.addInitiators(initiatorsToAddToUserAddedAndInitiatorList);
                exportMask.addToExistingInitiatorsIfAbsent(initiatorsToAddToExisting);

                exportMask.removeFromExistingVolumes(volumesToRemoveFromExisting);
                exportMask.addToExistingVolumesIfAbsent(volumesToAdd);
                exportMask.getStoragePorts().addAll(storagePortsToAdd);
                exportMask.getStoragePorts().removeAll(storagePortsToRemove);
                // update native id (this is the context path to the storage view on the vplex)
                exportMask.setNativeId(storageView.getPath());
                ExportMaskUtils.sanitizeExportMaskContainers(dbClient, exportMask);
                dbClient.updateObject(exportMask);
                log.info("ExportMask is now:\n" + exportMask.toString());
            } else {
                log.info("ExportMask refresh: There are no changes to the mask\n");
            }
            networkDeviceController.refreshZoningMap(exportMask,
                    transform(initiatorsToRemoveFromUserAddedAndInitiatorList, CommonTransformerFunctions.FCTN_URI_TO_STRING),
                    Collections.emptyList(),
                    (addInitiators || removeInitiators), true);
        } catch (Exception ex) {
            log.error("Failed to refresh VPLEX Storage View: " + ex.getLocalizedMessage(), ex);
            String storageViewName = exportMask != null ? exportMask.getMaskName() : "unknown";
            throw VPlexApiException.exceptions.failedToRefreshVplexStorageView(storageViewName, ex.getLocalizedMessage());
        }
    }

    /**
     * Returns all VPLEX storage systems in ViPR.
     * 
     * @param dbClient a database client reference
     * @return a List of StorageSystems that are "vplex" type
     */
    public static List<StorageSystem> getAllVplexStorageSystems(DbClient dbClient) {
        List<StorageSystem> vplexStorageSystems = new ArrayList<StorageSystem>();
        List<URI> allStorageSystemUris = dbClient.queryByType(StorageSystem.class, true);
        Iterator<StorageSystem> allStorageSystems = dbClient.queryIterativeObjects(StorageSystem.class, allStorageSystemUris);

        while(allStorageSystems.hasNext()){
            StorageSystem storageSystem = allStorageSystems.next();
            if ((storageSystem != null)
                    && (DiscoveredDataObject.Type.vplex.name().equals(storageSystem.getSystemType()))) {
                vplexStorageSystems.add(storageSystem);
            }
        }

        return vplexStorageSystems;
    }

    /**
     * Returns all VPLEX Storage Systems in ViPR that have the given assembly id count. The
     * VPLEX assembly id is another term for the cluster serial number.
     * 
     * @param dbClient a database client reference
     * @param assemblyIdCount the VPLEX assembly id count
     * @return a List of StorageSystems with a matching assembly id count
     */
    private static List<StorageSystem> getVplexesByAssemblyIdCount(DbClient dbClient, Integer assemblyIdCount) {
        List<StorageSystem> vplexStorageSystems = getAllVplexStorageSystems(dbClient);
        Iterator<StorageSystem> it = vplexStorageSystems.iterator();
        while (it.hasNext()) {
            StorageSystem vplex = it.next();
            if (null != vplex.getVplexAssemblyIdtoClusterId()
                    && (assemblyIdCount != vplex.getVplexAssemblyIdtoClusterId().size())) {
                it.remove();
            }
        }

        return vplexStorageSystems;
    }

    /**
     * Returns all VPLEX local storage systems in ViPR.
     * 
     * @param dbClient a database client reference
     * @return a List of StorageSystems that are in a VPLEX local configuration
     */
    public static List<StorageSystem> getAllVplexLocalStorageSystems(DbClient dbClient) {
        return getVplexesByAssemblyIdCount(dbClient, VPlexApiConstants.VPLEX_LOCAL_ASSEMBLY_COUNT);
    }

    /**
     * Returns all VPLEX metro storage systems in ViPR.
     * 
     * @param dbClient a database client reference
     * @return a List of StorageSystems that are in a VPLEX metro configuration
     */
    public static List<StorageSystem> getAllVplexMetroStorageSystems(DbClient dbClient) {
        return getVplexesByAssemblyIdCount(dbClient, VPlexApiConstants.VPLEX_METRO_ASSEMBLY_COUNT);
    }

    /**
     * Cleans stale instances of ExportMasks from the database.  A stale instance is
     * one which no longer exists as a storage view on the VPLEX and also contains
     * no more user added volumes.
     *
     * @param dbClient a reference to the database client
     * @param vplexUri the VPLEX system URI
     */
    public static void cleanStaleExportMasks(DbClient dbClient, URI vplexUri) {

        log.info("starting clean up of stale export masks for vplex {}", vplexUri);
        List<ExportMask> exportMasks = ExportMaskUtils.getExportMasksForStorageSystem(dbClient, vplexUri);

        // get a VPLEX API client for this VPLEX URI
        VPlexApiClient client = null;
        try {
            client = VPlexControllerUtils.getVPlexAPIClient(VPlexApiFactory.getInstance(), vplexUri, dbClient);
        } catch (URISyntaxException ex) {
            log.error("URISyntaxException encountered: ", ex);
        }
        if (null == client) {
            log.error("Couldn't load vplex api client, skipping stale export mask cleanup.");
            return;
        }

        // assemble collections of storage view native ids (VPLEX API context paths)
        // and export mask names (VPLEX API storage view names) for comparison with ViPR 
        List<VPlexStorageViewInfo> storageViewsOnDevice = client.getStorageViewsLite();
        Set<String> svNativeIds = new HashSet<String>();
        Set<String> svNames = new HashSet<String>();
        for (VPlexStorageViewInfo sv : storageViewsOnDevice) {
            svNativeIds.add(sv.getPath());
            svNames.add(sv.getName());
        }

        // create collections to hold any stale data we find, for clean up all at once at the very end
        Set<ExportMask> staleExportMasks = new HashSet<ExportMask>();
        Map<ExportGroup, Set<ExportMask>> exportGroupToStaleMaskMap = new HashMap<ExportGroup, Set<ExportMask>>();
        Map<URI, ExportGroup> exportGroupUriMap = new HashMap<URI, ExportGroup>();

        // check all export masks in the database to make sure they still exist on the VPLEX.
        // a null or empty ExportMask.nativeId would indicate an ExportMask that has been created
        // by ViPR in the database, but not yet created on the VPLEX device itself. skip those of course.
        for (ExportMask exportMask : exportMasks) {
            if (null != exportMask && !exportMask.getInactive() 
                    && (exportMask.getNativeId() != null && !exportMask.getNativeId().isEmpty())) {
                // we need to check both native id and export mask name to make sure we are NOT finding the storage view.
                // native id is most accurate, but greenfield ExportMasks for VPLEX don't have this property set.
                // native id will be set on ingested export masks, however, and we should check it, in case the same
                // storage view name is used on both vplex clusters.  
                // greenfield VPLEX ExportMasks will always have unique mask names on both clusters (prefixed by V1_ or V2_),
                // so for greenfield export masks, we can check mask names if the native id property is not set.
                boolean noNativeIdMatch = (null != exportMask.getNativeId()) && !svNativeIds.contains(exportMask.getNativeId());
                boolean noMaskNameMatch = (null != exportMask.getMaskName()) && !svNames.contains(exportMask.getMaskName());
                if (noNativeIdMatch || noMaskNameMatch) {
                    log.info("ExportMask {} is not found on VPLEX", exportMask.getMaskName());
                    // if any user added volumes are still present, we will not do anything with this export mask
                    boolean hasActiveVolumes = false;
                    if (exportMask.hasAnyUserAddedVolumes()) {
                        List<URI> userAddedVolUris = URIUtil.toURIList(exportMask.getUserAddedVolumes().values());
                        List<Volume> userAddedVols = dbClient.queryObject(Volume.class, userAddedVolUris);
                        for (Volume vol : userAddedVols) {
                            if (null != vol && !vol.getInactive()) {
                                hasActiveVolumes = true;
                                break;
                            }
                        }
                    }
                    if (hasActiveVolumes) {
                        log.warn("ExportMask {} has active user added volumes, so will not remove from database.", 
                                exportMask.forDisplay());
                        continue;
                    }

                    // this is a stale export mask because it doesn't exist on the VPLEX and doesn't have user-added volumes
                    staleExportMasks.add(exportMask);

                    // we need to remove this stale ExportMask from any ExportGroups that contain it.
                    // we use the exportGroupUriMap so that at the end of this process we will only
                    // be updating a single ExportGroup instance from the database in case
                    // multiple ExportMasks from the same ExportGroup need to be removed.
                    List<ExportGroup> egList = ExportUtils.getExportGroupsForMask(exportMask.getId(), dbClient);
                    if (!CollectionUtils.isEmpty(egList)) {
                        for (ExportGroup exportGroup : egList) {
                            // skip this one if the export group is no longer existent or active
                            if (null == exportGroup || exportGroup.getInactive()) {
                                continue;
                            }
                            // update or reuse from the cache of already-loaded ExportGroups
                            if (!exportGroupUriMap.containsKey(exportGroup.getId())) {
                                // add this one to the cache
                                exportGroupUriMap.put(exportGroup.getId(), exportGroup);
                            } else {
                                // just reuse the one already loaded from the database
                                exportGroup = exportGroupUriMap.get(exportGroup.getId());
                            }
                            // map the current ExportGroup and ExportMask for breaking
                            // of associations at the end of this whole process
                            if (!exportGroupToStaleMaskMap.containsKey(exportGroup)) {
                                exportGroupToStaleMaskMap.put(exportGroup, new HashSet<ExportMask>());
                            }
                            exportGroupToStaleMaskMap.get(exportGroup).add(exportMask);
                            log.info("Stale ExportMask {} will be removed from ExportGroup {}", 
                                    exportMask.getMaskName(), exportGroup.getLabel());
                        }
                    }
                }
            }
        }

        if (!CollectionUtils.isEmpty(staleExportMasks)) {
            dbClient.markForDeletion(staleExportMasks);
            log.info("Deleted {} stale ExportMasks from database.", staleExportMasks.size());
            if (!CollectionUtils.isEmpty(exportGroupToStaleMaskMap.keySet())) {
                for (Entry<ExportGroup, Set<ExportMask>> entry : exportGroupToStaleMaskMap.entrySet()) {
                    ExportGroup exportGroup = entry.getKey();
                    for (ExportMask exportMask : entry.getValue()) {
                        log.info("Removing ExportMask {} from ExportGroup {}",
                                exportMask.getMaskName(), exportGroup.getLabel());
                        exportGroup.removeExportMask(exportMask.getId());
                    }
                }
                dbClient.updateObject(exportGroupToStaleMaskMap.keySet());
            }
        }

        log.info("Stale Export Mask cleanup complete.");
    }
}
