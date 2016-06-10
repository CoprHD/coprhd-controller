/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.xiv;

import java.net.URI;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.BlockMirror;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.Cluster;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.ScopedLabel;
import com.emc.storageos.db.client.model.ScopedLabelSet;
import com.emc.storageos.db.client.model.StorageProvider;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.networkcontroller.impl.NetworkDeviceController;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.util.ExportUtils;
import com.emc.storageos.db.client.util.CommonTransformerFunctions;
import com.emc.storageos.exceptions.DeviceControllerErrors;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.VolumeURIHLU;
import com.emc.storageos.volumecontroller.impl.utils.ExportMaskUtils;
import com.emc.storageos.xiv.api.XIVRestClient;
import com.emc.storageos.xiv.api.XIVRestClient.HOST_STATUS;
import com.emc.storageos.xiv.api.XIVRestClientFactory;
import com.emc.storageos.xiv.api.XIVRestException;
import com.google.common.base.Joiner;
import com.google.common.collect.Collections2;

/**
 * Helper class to support all REST operations performed on IBM XIV - Hyper Scale manager.
 * 
 * Current operations supported:
 * Find if a Host is part of cluster on XIV
 * Create Export Mask
 *
 */
public class XIVRestOperationsHelper {

    private static Logger _log = LoggerFactory.getLogger(XIVRestOperationsHelper.class);

    private DbClient _dbClient;
    private XIVRestClientFactory _restClientFactory;

    private static final int MAXIMUM_LUN = 511;
    private static final String INVALID_LUN_ERROR_MSG = "Logical unit number provided (%d) is larger than allowed (%d).";

    public void setDbClient(DbClient dbClient) {
        _dbClient = dbClient;
    }

    public void setXivRestClientFactory(XIVRestClientFactory factory) {
        _restClientFactory = factory;
    }

    /**
     * Gets REST Client instance for a StorageSystem
     * 
     * @param storage StorageSystem instance
     * @return XIVRESTExportOperations instance
     */
    private XIVRestClient getRestClient(StorageSystem storage) {
        XIVRestClient restClient = null;
        StorageProvider provider = _dbClient.queryObject(StorageProvider.class, storage.getActiveProviderURI());
        String providerUser = provider.getSecondaryUsername();
        String providerPassword = provider.getSecondaryPassword();
        String providerURL = provider.getElementManagerURL();

        if (StringUtils.isNotEmpty(providerURL) && StringUtils.isNotEmpty(providerPassword) && StringUtils.isNotEmpty(providerUser)) {
            restClient = (XIVRestClient) _restClientFactory.getRESTClient(URI.create(providerURL), providerUser, providerPassword);
        }
        return restClient;
    }

    /**
     * Validates if the Host is part of a Cluster on XIV system. Uses initiators to find out the Hosts
     * 
     * @param storage XIV Storage System instance
     * @param exportMaskURI ExportMask instance
     * @param initiatorList Host Initiators
     * @return True if the host is part of Cluster else false.
     */
    public boolean isClusteredHost(StorageSystem storage, URI exportMaskURI, List<Initiator> initiators) {
        boolean isClusteredHost = false;
        ExportMask exportMask = _dbClient.queryObject(ExportMask.class, exportMaskURI);

        List<ExportGroup> exportGroups = ExportMaskUtils.getExportGroups(_dbClient, exportMask);
        if (null != exportGroups && !exportGroups.isEmpty()) {
            for (ExportGroup exportGroup : exportGroups) {
                if (!isClusteredHost && exportGroup.forCluster()) {
                    String hostName = null;
                    if (null == initiators) {
                        Set<Initiator> exportMaskInits = ExportMaskUtils.getInitiatorsForExportMask(_dbClient, exportMask, null);
                        Iterator<Initiator> exportMaskInitsItr = exportMaskInits.iterator();
                        if (exportMaskInitsItr.hasNext()) {
                            hostName = exportMaskInitsItr.next().getHostName();
                        }
                    } else {
                        Host host = _dbClient.queryObject(Host.class, initiators.get(0).getHost());
                        hostName = host.getLabel();
                    }
                    isClusteredHost = isClusteredHostOnArray(storage, hostName);
                }
            }
        }
        return isClusteredHost;
    }
    
    public boolean isClusteredHost(StorageSystem storage, List<String> initiators) {
        boolean isClusteredHost = false;
        XIVRestClient restExportOpr = getRestClient(storage);
        if (null != restExportOpr) {
            for (String initiator : initiators) {
                if (!isClusteredHost) {
                    JSONArray iniDetails = null;
                    try {
                        iniDetails = restExportOpr.getPortDetails(storage.getSmisProviderIP(), initiator);
                    } catch (Exception e) {
                        _log.error("Unable to pull hostport details for port {} on array : {} ", initiator, storage.getLabel(), e);
                    }
                    if (null != iniDetails) {
                        for (int i = 0; i < iniDetails.length(); i++) {
                            JSONObject iniDetail = iniDetails.optJSONObject(i);
                            final String hostName = iniDetail.optString("host");
                            if (!isClusteredHost && null != hostName && !hostName.isEmpty()) {
                                isClusteredHost = isClusteredHostOnArray(storage, hostName);
                            }
                        }
                    }
                }
            }
        }
        return isClusteredHost;
    }

    /**
     * Validates if the given Host name is identified as Clustered host with respect to XIV
     * 
     * @param storage XIV storage system
     * @param hostName Host name to ve validated
     * @return true if the host is part of Cluster. Else false.
     */
    private boolean isClusteredHostOnArray(StorageSystem storage, String hostName) {
        boolean isClusteredHost = false;
        XIVRestClient restExportOpr = getRestClient(storage);
        if (null != restExportOpr && null != hostName) {
            HOST_STATUS hostStatus = null;

            try {
                hostStatus = restExportOpr.getHostStatus(storage.getSmisProviderIP(), hostName);
            } catch (Exception e) {
                _log.error("Unable to validate host {} information on array : {} ", hostName, storage.getLabel(), e);
            }

            if (null != hostStatus) {
                if (HOST_STATUS.HOST_NOT_PRESENT.equals(hostStatus)) {
                    _log.info("Host {} not present on Array {}. Creating a new instance!", hostName, storage.getLabel());
                    isClusteredHost = true;
                } else if (HOST_STATUS.CLUSTER_HOST.equals(hostStatus)) {
                    _log.info("Identified Host {} as a Clustered Host on Array {}.", hostName, storage.getLabel());
                    isClusteredHost = true;
                } else if (HOST_STATUS.STANDALONE_HOST.equals(hostStatus)) {
                    _log.info("Host {} identified as a Standalone host on Array {}. Using SMIS for provisioning!", hostName,
                            storage.getLabel());
                    isClusteredHost = false;
                }
            }
        }
        return isClusteredHost;
    }

    /**
     * Creates Export mask for a Cluster. Creates Cluster, Host and Inititators on XIV. Exports the volume to the Cluster.
     * 
     * @param storage XIV Storage System
     * @param exportMaskURI Export Mask URI
     * @param volumeURIHLUs Volume URIs to be exported
     * @param targetURIList Target ports (not used for XIV)
     * @param initiatorList Initiator ports
     * @param taskCompleter Task Completer instance
     */
    public void createRESTExportMask(StorageSystem storage, URI exportMaskURI,
            VolumeURIHLU[] volumeURIHLUs, List<URI> targetURIList,
            List<Initiator> initiatorList, TaskCompleter taskCompleter) {

        try {

            ExportMask exportMask = _dbClient.queryObject(ExportMask.class, exportMaskURI);
            XIVRestClient restExportOpr = getRestClient(storage);

            final String storageIP = storage.getSmisProviderIP();
            final Host host = _dbClient.queryObject(Host.class, initiatorList.get(0).getHost());

            String exportName = host.getLabel();
            String clusterName = null;
            final String exportType = ExportMaskUtils.getExportType(_dbClient, exportMask);
            if (ExportGroup.ExportGroupType.Cluster.name().equals(exportType)) {
                Cluster cluster = _dbClient.queryObject(Cluster.class, host.getCluster());
                clusterName = cluster.getLabel();
                exportName = clusterName;

                // Create Cluster if not exist
                restExportOpr.createCluster(storageIP, clusterName);
            }

            // Create Host if not exist
            restExportOpr.createHost(storageIP, clusterName, host.getLabel());

            // Add Initiators to Host.
            List<Initiator> existingInitiators = new ArrayList<Initiator>();
            if (initiatorList != null && !initiatorList.isEmpty()) {
                for (Initiator initiator : initiatorList) {
                    if (restExportOpr.createHostPort(storageIP, host.getLabel(), Initiator.normalizePort(initiator.getInitiatorPort()),
                            initiator.getProtocol().toLowerCase())) {
                        existingInitiators.add(initiator);
                    }
                }
            }

            // Export volume to Cluster
            if (volumeURIHLUs != null && volumeURIHLUs.length > 0) {
                for (VolumeURIHLU volumeURIHLU : volumeURIHLUs) {
                    final String lunName = getBlockObjectAlternateName(volumeURIHLU.getVolumeURI());
                    final String volumeHLU = volumeURIHLU.getHLU();
                    if (volumeHLU != null && !volumeHLU.equalsIgnoreCase(ExportGroup.LUN_UNASSIGNED_STR)) {
                        int hluDec = Integer.parseInt(volumeHLU, 16);
                        if (hluDec > MAXIMUM_LUN) {
                            String errMsg = String.format(INVALID_LUN_ERROR_MSG, hluDec, MAXIMUM_LUN);
                            _log.error(errMsg);
                            throw new Exception(errMsg);
                        } else {
                            restExportOpr.exportVolume(storageIP, exportType, exportName, lunName, String.valueOf(hluDec));
                        }
                    }
                }
            }

            // Update Masking information
            exportMask.setCreatedBySystem(false);
            exportMask.addToUserCreatedInitiators(existingInitiators);
            exportMask.setMaskName(host.getLabel());
            exportMask.setNativeId(exportName);
            exportMask.setLabel(host.getLabel());
            _dbClient.updateObject(exportMask);

            taskCompleter.ready(_dbClient);
        } catch (Exception e) {
            _log.error("Unexpected error: createRESTExportMask failed.", e);
            ServiceError error = XIVRestException.exceptions.methodFailed("createExportMask", e.getMessage());
            taskCompleter.error(_dbClient, error);
        }
    }

    /**
     * This method will take a URI and return alternateName for the BlockObject object to which the
     * URI applies.
     *
     * @param uri
     *            - URI
     * @return Returns a nativeId String value
     * @throws XIVRestException.exceptions.notAVolumeOrBlocksnapshotUri
     *             if URI is not a Volume/BlockSnapshot URI
     */
    private String getBlockObjectAlternateName(URI uri) throws Exception {
        String label;
        if (URIUtil.isType(uri, Volume.class)) {
            Volume volume = _dbClient.queryObject(Volume.class, uri);
            label = volume.getLabel();
        } else if (URIUtil.isType(uri, BlockSnapshot.class)) {
            BlockSnapshot blockSnapshot = _dbClient.queryObject(BlockSnapshot.class, uri);
            label = blockSnapshot.getLabel();
        } else if (URIUtil.isType(uri, BlockMirror.class)) {
            BlockMirror blockMirror = _dbClient.queryObject(BlockMirror.class, uri);
            label = blockMirror.getLabel();
        } else {
            throw XIVRestException.exceptions.notAVolumeOrBlocksnapshotUri(uri);
        }
        return label;
    }

    /**
     * Refresh the export mask with the user added configuration
     * 
     * @param storage XIX sotrage system
     * @param mask Export Mask instance
     * @param _networkDeviceController Network configuration instance
     */
    public void refreshRESTExportMask(StorageSystem storage, ExportMask mask, NetworkDeviceController _networkDeviceController) {

        try {
            final String storageIP = storage.getSmisProviderIP();
            final String name = mask.getNativeId();

            XIVRestClient restExportOpr = getRestClient(storage);
            StringBuilder builder = new StringBuilder();

            boolean addInitiators = false;
            List<String> initiatorsToAdd = new ArrayList<String>();
            Set<String> discoveredPorts = restExportOpr.getHostPorts(storageIP, mask.getLabel());
            for (String port : discoveredPorts) {
                String normalizedPort = Initiator.normalizePort(port);
                if (!mask.hasExistingInitiator(normalizedPort) && !mask.hasUserInitiator(normalizedPort)) {
                    initiatorsToAdd.add(normalizedPort);
                    addInitiators = true;
                }
            }

            boolean removeInitiators = false;
            List<String> initiatorsToRemove = new ArrayList<String>();
            List<URI> initiatorIdsToRemove = new ArrayList<>();
            if (mask.getExistingInitiators() != null && !mask.getExistingInitiators().isEmpty()) {
                initiatorsToRemove.addAll(mask.getExistingInitiators());
                initiatorsToRemove.removeAll(discoveredPorts);
            }
            
            if (mask.getInitiators() != null &&
                    !mask.getInitiators().isEmpty()) {
                initiatorIdsToRemove.addAll(Collections2.transform(mask.getInitiators(), CommonTransformerFunctions.FCTN_STRING_TO_URI));
                for (String port : discoveredPorts) {
                    Initiator existingInitiator = ExportUtils.getInitiator(Initiator.toPortNetworkId(port), _dbClient);
                    if (existingInitiator != null) {
                        initiatorIdsToRemove.remove(existingInitiator.getId());
                    }
                }
            }
            
            removeInitiators = !initiatorsToRemove.isEmpty() || !initiatorIdsToRemove.isEmpty();

            // Get Volumes mapped to a Host on Array
            Map<String, Integer> discoveredVolumes = restExportOpr.getVolumesMappedToHost(storageIP, mask.getLabel());

            // Check the volumes and update the lists as necessary
            Map<String, Integer> volumesToAdd = ExportMaskUtils.diffAndFindNewVolumes(mask, discoveredVolumes);
            boolean addVolumes = !volumesToAdd.isEmpty();

            boolean removeVolumes = false;
            List<String> volumesToRemove = new ArrayList<String>();
            if (mask.getExistingVolumes() != null && !mask.getExistingVolumes().isEmpty()) {
                volumesToRemove.addAll(mask.getExistingVolumes().keySet());
                volumesToRemove.removeAll(discoveredVolumes.keySet());
                removeVolumes = !volumesToRemove.isEmpty();
            }
            builder.append(String.format(
                    "XM refresh: %s initiators; add:{%s} remove:{%s}%n",
                    name, Joiner.on(',').join(initiatorsToAdd),
                    Joiner.on(',').join(initiatorsToRemove)));
            builder.append(String.format(
                    "XM refresh: %s volumes; add:{%s} remove:{%s}%n", name,
                    Joiner.on(',').join(volumesToAdd.keySet()),
                    Joiner.on(',').join(volumesToRemove)));

            if (addInitiators || removeInitiators || addVolumes || removeVolumes) {
                builder.append("XM refresh: There are changes to mask updating it...\n");
                mask.removeFromExistingInitiators(initiatorsToRemove);
                if (initiatorIdsToRemove != null && !initiatorIdsToRemove.isEmpty()) {
                    mask.removeInitiators(_dbClient.queryObject(Initiator.class, initiatorIdsToRemove));
                }
                mask.addToExistingInitiatorsIfAbsent(initiatorsToAdd);
                mask.removeFromExistingVolumes(volumesToRemove);
                mask.addToExistingVolumesIfAbsent(volumesToAdd);
                ExportMaskUtils.sanitizeExportMaskContainers(_dbClient, mask);
                _dbClient.updateObject(mask);
            } else {
                builder.append("XM refresh: There are no changes to the mask\n");
            }
            _networkDeviceController.refreshZoningMap(mask, initiatorsToRemove, Collections.EMPTY_LIST, (addInitiators || removeInitiators),
                    true);
            _log.info(builder.toString());
        } catch (Exception e) {
            String msg = "Error when attempting to query LUN masking information: " + e.getMessage();
            _log.error(MessageFormat.format("Encountered an error when attempting to refresh existing exports: {0}", msg), e);
            throw XIVRestException.exceptions.refreshExistingMaskFailure(msg);
        }
    }

    /**
     * Deletes the Export Mask and its attributes
     * 
     * @param storage XIV storage system
     * @param exportMaskURI Export mask URI
     * @param volumeURIList Volume URI as list
     * @param targetURIList target port URI as list [ not used for xiv]
     * @param initiatorList Initiator port URI as list
     * @param taskCompleter task completer instance
     */
    public void deleteRESTExportMask(StorageSystem storage, URI exportMaskURI, List<URI> volumeURIList, List<URI> targetURIList,
            List<Initiator> initiatorList, TaskCompleter taskCompleter) {
        try {
            ExportMask exportMask = _dbClient.queryObject(ExportMask.class, exportMaskURI);

            final String storageIP = storage.getSmisProviderIP();
            final String exportType = ExportMaskUtils.getExportType(_dbClient, exportMask);
            final String name = exportMask.getNativeId();
            final String hostName = exportMask.getLabel();

            final StringSet emInitiatorURIs = exportMask.getInitiators();
            final StringMap emVolumeURIs = exportMask.getVolumes();

            XIVRestClient restExportOpr = getRestClient(storage);
            URI hostURI = null;

            // Un export Volumes
            if (null != emVolumeURIs) {
                Iterator<Entry<String, String>> emVolumeURIItr = emVolumeURIs.entrySet().iterator();
                while (emVolumeURIItr.hasNext()) {
                    URI volUri = URI.create(emVolumeURIItr.next().getKey());
                    if (URIUtil.isType(volUri, Volume.class)) {
                        Volume volume = _dbClient.queryObject(Volume.class, volUri);
                        restExportOpr.unExportVolume(storageIP, exportType, name, volume.getLabel());
                    }
                }
            }

            // Delete initiators
            if (null != emInitiatorURIs) {
                for (String initiatorURI : emInitiatorURIs) {
                    Initiator initiator = _dbClient.queryObject(Initiator.class, URI.create(initiatorURI));
                    String normalizedPort = Initiator.normalizePort(initiator.getLabel());
                    restExportOpr.deleteHostPort(storageIP, hostName, normalizedPort, initiator.getProtocol().toLowerCase());
                    if (null == hostURI) {
                        hostURI = initiator.getHost();
                    }
                }
            }

            // Delete Host if there are no associated Initiators/Volume to it.
            boolean hostDeleted = restExportOpr.deleteHost(storageIP, hostName);

            // Delete Cluster if there is no associated hosts to it.
            if (ExportGroup.ExportGroupType.Cluster.name().equals(exportType)) {
                restExportOpr.deleteCluster(storageIP, name);
            }

            // Perform post-mask-delete cleanup steps
            if (hostDeleted && emVolumeURIs.size() > 0) {
                Host host = _dbClient.queryObject(Host.class, hostURI);
                unsetTag(host, storage.getSerialNumber());
            }

            ExportUtils.cleanupAssociatedMaskResources(_dbClient, exportMask);

            exportMask.setMaskName(NullColumnValueGetter.getNullURI().toString());
            exportMask.setLabel(NullColumnValueGetter.getNullURI().toString());
            exportMask.setNativeId(NullColumnValueGetter.getNullURI().toString());
            exportMask.setResource(NullColumnValueGetter.getNullURI().toString());

            _dbClient.updateObject(exportMask);
            taskCompleter.ready(_dbClient);
        } catch (Exception e) {
            _log.error("Unexpected error: deleteExportMask failed.", e);
            ServiceError error = XIVRestException.exceptions.methodFailed("createExportMask", e.getMessage());
            taskCompleter.error(_dbClient, error);
        }
    }
    
    private void setTag(DataObject object, String scope, String label) {
        if (label == null) { // shouldn't happen
            label = "";
        }

        ScopedLabel newScopedLabel = new ScopedLabel(scope, label);
        ScopedLabelSet tagSet = object.getTag();
        if (tagSet == null) {
            tagSet = new ScopedLabelSet();
            tagSet.add(newScopedLabel);
            object.setTag(tagSet);
        } else if (tagSet.contains(newScopedLabel)) {
            return;
        } else {
            removeLabel(tagSet, scope);
            tagSet.add(newScopedLabel);
        }

        _dbClient.persistObject(object);
    }

    private void unsetTag(DataObject object, String scope) {
        ScopedLabelSet tagSet = object.getTag();
        if (tagSet == null) {
            return;
        }

        removeLabel(tagSet, scope);
        _dbClient.updateObject(object);
    }

    private void removeLabel(ScopedLabelSet tagSet, String scope) {
        ScopedLabel oldScopedLabel = null;
        Iterator<ScopedLabel> itr = tagSet.iterator();
        while (itr.hasNext()) {
            ScopedLabel scopedLabel = itr.next();
            if (scope.equals(scopedLabel.getScope())) {
                oldScopedLabel = scopedLabel;
                break;
            }
        }

        if (oldScopedLabel != null) {
            tagSet.remove(oldScopedLabel);
        }
    }

    public Map<String, Set<URI>> findRESTExportMasks(StorageSystem storage, List<String> initiatorNames, boolean mustHaveAllPorts) {
        long startTime = System.currentTimeMillis();
        Map<String, Set<URI>> matchingMasks = new HashMap<String, Set<URI>>();

        try {
            final String storageIP = storage.getSmisProviderIP();
            XIVRestClient restExportOpr = getRestClient(storage);
            Set<String> hostSet = new HashSet<String>();
            List<String> hostPortSet = new ArrayList<String>();

            for (String initiatorName : initiatorNames) {
                JSONArray portResult = restExportOpr.getPortDetails(storageIP, initiatorName);
                for (int i = 0; i < portResult.length(); i++) {
                    JSONObject resultObj = portResult.getJSONObject(i);
                    hostSet.add(resultObj.getString("host"));
                    hostPortSet.add(initiatorName);
                }
            }

            StringBuilder builder = new StringBuilder();

            for (String host : hostSet) {
                Map<String, Integer> volMapResult = restExportOpr.getVolumesMappedToHost(storageIP, host);
                if (null != volMapResult && volMapResult.size() > 0) {
                    URIQueryResultList uriQueryList = new URIQueryResultList();
                    _dbClient.queryByConstraint(AlternateIdConstraint.Factory.getExportMaskByNameConstraint(host), uriQueryList);
                    ExportMask exportMask = null;
                    boolean foundMaskInDb = false;
                    while (uriQueryList.iterator().hasNext()) {
                        URI uri = uriQueryList.iterator().next();
                        exportMask = _dbClient.queryObject(ExportMask.class, uri);
                        if (exportMask != null && !exportMask.getInactive() && exportMask.getStorageDevice().equals(storage.getId())) {
                            foundMaskInDb = true;
                            break;
                        }
                    }

                    // If there was no export mask found in the database, then create a new one
                    if (!foundMaskInDb) {
                        exportMask = new ExportMask();
                        exportMask.setLabel(host);
                        exportMask.setMaskName(host);
                        exportMask.setNativeId(host);
                        exportMask.setStorageDevice(storage.getId());
                        exportMask.setId(URIUtil.createId(ExportMask.class));
                        exportMask.setCreatedBySystem(false);
                    }

                    exportMask.addToExistingVolumesIfAbsent(volMapResult);
                    exportMask.addToExistingInitiatorsIfAbsent(hostPortSet);

                    builder.append(String.format("XM %s is matching. " + "EI: { %s }, EV: { %s }%n", host,
                            Joiner.on(',').join(exportMask.getExistingInitiators()),
                            Joiner.on(',').join(exportMask.getExistingVolumes().keySet())));

                    if (foundMaskInDb) {
                        ExportMaskUtils.sanitizeExportMaskContainers(_dbClient, exportMask);
                        _dbClient.updateAndReindexObject(exportMask);
                    } else {
                        _dbClient.createObject(exportMask);
                    }

                    // update hosts
                    Initiator initiator = ExportUtils.getInitiator(Initiator.toPortNetworkId(hostPortSet.get(0)), _dbClient);
                    if(null!=initiator && null!=initiator.getHost()){
                    	Host hostIns = _dbClient.queryObject(Host.class, initiator.getHost());
                        String label = hostIns.getLabel();
                        if (label.equals(host)) {
                            unsetTag(hostIns, storage.getSerialNumber());
                        } else {
                            setTag(hostIns, storage.getSerialNumber(), host);
                        }
                    }

                    for (String it : hostPortSet) {
                        Set<URI> maskURIs = matchingMasks.get(it);
                        if (maskURIs == null) {
                            maskURIs = new HashSet<URI>();
                            matchingMasks.put(it, maskURIs);
                        }
                        maskURIs.add(exportMask.getId());
                    }
                }
            }
            _log.info(builder.toString());
        } catch (Exception e) {
            String msg = "Error when attempting to query LUN masking information: " + e.getMessage();
            _log.error(MessageFormat.format("Encountered an SMIS error when attempting to query existing exports: {0}", msg), e);
            throw XIVRestException.exceptions.queryExistingMasksFailure(msg, e);
        } finally {
            long totalTime = System.currentTimeMillis() - startTime;
            _log.info(String.format("findExportMasks took %f seconds", (double) totalTime / (double) 1000));
        }
        return matchingMasks;
    }

	public void addVolumeUsingREST(StorageSystem storage, URI exportMaskURI, VolumeURIHLU[] volumeURIHLUs, TaskCompleter taskCompleter) {
		
		_log.info("{} addVolume START...", storage.getLabel());
        try {
        
			// Export volume to Cluster
	        if (volumeURIHLUs != null && volumeURIHLUs.length > 0) {
	        	ExportMask exportMask = _dbClient.queryObject(ExportMask.class, exportMaskURI);
	        	final String storageIP = storage.getSmisProviderIP();
	            XIVRestClient restExportOpr = getRestClient(storage);
	            
	            //Find HOST from Export Mask
	            URI hostName = null;
	            Set<Initiator> exportMaskInits = ExportMaskUtils.getInitiatorsForExportMask(_dbClient, exportMask, null);
	            Iterator<Initiator> exportMaskInitsItr = exportMaskInits.iterator();
	            if (exportMaskInitsItr.hasNext()) {
	                hostName = exportMaskInitsItr.next().getHost();
	            }
	            final Host host = _dbClient.queryObject(Host.class, hostName);
	            
	            //Validate if it is a cluster
	            String exportName = host.getLabel();
	            String clusterName = null;
	            final String exportType = ExportMaskUtils.getExportType(_dbClient, exportMask);
	            if (ExportGroup.ExportGroupType.Cluster.name().equals(exportType)) {
	                Cluster cluster = _dbClient.queryObject(Cluster.class, host.getCluster());
	                clusterName = cluster.getLabel();
	                exportName = clusterName;
	            }
	  
	            //Export volume
	            for (VolumeURIHLU volumeURIHLU : volumeURIHLUs) {
	                final String lunName = getBlockObjectAlternateName(volumeURIHLU.getVolumeURI());
	                final String volumeHLU = volumeURIHLU.getHLU();
	                if (volumeHLU != null && !volumeHLU.equalsIgnoreCase(ExportGroup.LUN_UNASSIGNED_STR)) {
	                	int hluDec = Integer.parseInt(volumeHLU, 16);
	                    if (hluDec > MAXIMUM_LUN) {
	                        String errMsg = String.format(INVALID_LUN_ERROR_MSG, hluDec, MAXIMUM_LUN);
	                        _log.error(errMsg);
	                        throw new Exception(errMsg);
	                    } else {
	                        restExportOpr.exportVolume(storageIP, exportType, exportName, lunName, String.valueOf(hluDec));
	                    }
	                }
	            }
	            
	            taskCompleter.ready(_dbClient);
	        }
        } catch (Exception e) {
            _log.error("Unexpected error: addVolume failed.", e);
            ServiceError error = XIVRestException.exceptions.methodFailed("addVolume", e.getMessage());
            taskCompleter.error(_dbClient, error);
        }

        _log.info("{} addVolume END...", storage.getLabel());
	}

	public void removeVolumeUsingREST(StorageSystem storage, URI exportMaskURI, List<URI> volumeURIList, TaskCompleter taskCompleter) {
		try {
	        
			// Export volume to Cluster
	        if (volumeURIList != null && volumeURIList.size() > 0) {
	        	ExportMask exportMask = _dbClient.queryObject(ExportMask.class, exportMaskURI);
	        	final String storageIP = storage.getSmisProviderIP();
	            XIVRestClient restExportOpr = getRestClient(storage);
	            
	            //Find HOST from Export Mask
	            URI hostName = null;
	            Set<Initiator> exportMaskInits = ExportMaskUtils.getInitiatorsForExportMask(_dbClient, exportMask, null);
	            Iterator<Initiator> exportMaskInitsItr = exportMaskInits.iterator();
	            if (exportMaskInitsItr.hasNext()) {
	                hostName = exportMaskInitsItr.next().getHost();
	            }
	            final Host host = _dbClient.queryObject(Host.class, hostName);
	            
	            //Validate if it is a cluster
	            String exportName = host.getLabel();
	            final String exportType = ExportMaskUtils.getExportType(_dbClient, exportMask);
	            if (ExportGroup.ExportGroupType.Cluster.name().equals(exportType)) {
	                Cluster cluster = _dbClient.queryObject(Cluster.class, host.getCluster());
	                exportName = cluster.getLabel();
	            }
	  
	            //Export volume
	            for (URI volumeURI : volumeURIList) {
	            	final Volume volume = _dbClient.queryObject(Volume.class, volumeURI);
	                if (volume != null) {
	                        restExportOpr.unExportVolume(storageIP, exportType, exportName, volume.getLabel());
	                    }
	                }
	            }
	        taskCompleter.ready(_dbClient);
        } catch (Exception e) {
            _log.error("Unexpected error: removeVolume failed.", e);
            ServiceError error = XIVRestException.exceptions.methodFailed("removeVolume", e.getMessage());
            taskCompleter.error(_dbClient, error);
        }
		
	}
}
