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
import com.emc.storageos.db.client.model.BlockObject;
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
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.VolumeURIHLU;
import com.emc.storageos.volumecontroller.impl.utils.ExportMaskUtils;
import com.emc.storageos.xiv.api.XIVRestClient;
import com.emc.storageos.xiv.api.XIVRestClient.HOST_STATUS;
import com.emc.storageos.xiv.api.XIVRestClientFactory;
import com.emc.storageos.xiv.api.XIVRestException;
import com.google.common.base.Joiner;

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
        String providerURL = provider.getSecondaryURL();

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
    public boolean isClusteredHost(StorageSystem storage, List<Initiator> initiators) {
        Set<Boolean> result = new HashSet<Boolean>();
        if (null != initiators) {
            for (Initiator initiator : initiators) {
            	URI hostURI = initiator.getHost();
            	if(null != hostURI) {
            		Host host = _dbClient.queryObject(Host.class, hostURI);
                    result.add(isClusteredHostOnArray(storage, host.getLabel()));	
            	}
            }
        }
        return result.size() == 1 ? result.iterator().next() : false;
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
    public void createRESTExportMask(StorageSystem storage, URI exportMaskURI, VolumeURIHLU[] volumeURIHLUs, List<URI> targetURIList,
            List<Initiator> initiatorList, TaskCompleter taskCompleter) {

        try {

            ExportMask exportMask = _dbClient.queryObject(ExportMask.class, exportMaskURI);
            XIVRestClient restExportOpr = getRestClient(storage);

            final String storageIP = storage.getSmisProviderIP();
            String exportName = null;
            String clusterName = null;

            URI clusterURI = null;
            Set<String> hosts = new HashSet<String>();
            for (Initiator initiator : initiatorList) {
                final Host host = _dbClient.queryObject(Host.class, initiator.getHost());
                exportName = host.getLabel();
                hosts.add(exportName);
                clusterURI = host.getCluster();
            }

            final String exportType = ExportMaskUtils.getExportType(_dbClient, exportMask);
            if (ExportGroup.ExportGroupType.Cluster.name().equals(exportType) && null != clusterURI) {
                Cluster cluster = _dbClient.queryObject(Cluster.class, clusterURI);
                clusterName = cluster.getLabel();
                exportName = clusterName;

                // Create Cluster if not exist
                restExportOpr.createCluster(storageIP, clusterName);
            }

            // Create Host if not exist
            for (String hostName : hosts) {
                restExportOpr.createHost(storageIP, clusterName, hostName);
            }

            List<Initiator> userAddedInitiator = new ArrayList<Initiator>();
            List<BlockObject> userAddedVolumes = new ArrayList<BlockObject>();
            for (Initiator initiator : initiatorList) {
                final Host host = _dbClient.queryObject(Host.class, initiator.getHost());

                // Add Initiators to Host.
                if (!restExportOpr.createHostPort(storageIP, host.getLabel(), Initiator.normalizePort(initiator.getInitiatorPort()),
                        initiator.getProtocol().toLowerCase())) {
                    userAddedInitiator.add(initiator);
                }
            }

            // Export volume to Cluster
            if (volumeURIHLUs != null && volumeURIHLUs.length > 0) {
                for (VolumeURIHLU volumeURIHLU : volumeURIHLUs) {
                    final BlockObject blockObject = getBlockObject(volumeURIHLU.getVolumeURI());
                    final String volumeHLU = volumeURIHLU.getHLU();
                    if (volumeHLU != null && !volumeHLU.equalsIgnoreCase(ExportGroup.LUN_UNASSIGNED_STR)) {
                        int hluDec = Integer.parseInt(volumeHLU, 16);
                        if (hluDec > MAXIMUM_LUN) {
                            String errMsg = String.format(INVALID_LUN_ERROR_MSG, hluDec, MAXIMUM_LUN);
                            _log.error(errMsg);
                            throw new Exception(errMsg);
                        } else {
                            if (!restExportOpr.exportVolume(storageIP, exportType, exportName, blockObject.getLabel(),
                                    String.valueOf(hluDec))) {
                                userAddedVolumes.add(blockObject);
                            }
                        }
                    }
                }
            }

            // Update Masking information
            exportMask.setCreatedBySystem(true);
            exportMask.addToUserCreatedInitiators(userAddedInitiator);
            exportMask.addToUserCreatedVolumes(userAddedVolumes);
            exportMask.setMaskName(exportName);
            exportMask.setNativeId(exportName);
            exportMask.setLabel(exportName);
            _dbClient.updateObject(exportMask);

            taskCompleter.ready(_dbClient);
        } catch (Exception e) {
            _log.error("Unexpected error: createRESTExportMask failed.", e);
            ServiceError error = XIVRestException.exceptions.methodFailed("createExportMask", e);
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
    private BlockObject getBlockObject(URI uri) throws Exception {
        BlockObject object;
        if (URIUtil.isType(uri, Volume.class)) {
            object = _dbClient.queryObject(Volume.class, uri);
        } else if (URIUtil.isType(uri, BlockSnapshot.class)) {
            object = _dbClient.queryObject(BlockSnapshot.class, uri);
        } else if (URIUtil.isType(uri, BlockMirror.class)) {
            object = _dbClient.queryObject(BlockMirror.class, uri);
        } else {
            throw XIVRestException.exceptions.notAVolumeOrBlocksnapshotUri(uri);
        }
        return object;
    }

    /**
     * Refresh the export mask with the user added configuration
     * 
     * @param storage XIV sotrage system
     * @param mask Export Mask instance
     * @param _networkDeviceController Network configuration instance
     */
    public void refreshRESTExportMask(StorageSystem storage, ExportMask mask, NetworkDeviceController _networkDeviceController) {

        try {
            final String storageIP = storage.getSmisProviderIP();
            final String name = mask.getNativeId();

            XIVRestClient restExportOpr = getRestClient(storage);
            StringBuilder builder = new StringBuilder();

            Set<String> discoveredPorts = new HashSet<String>();
            Set<URI> hostURIs = new HashSet<URI>();
            Set<Initiator> exportMaskInits = ExportMaskUtils.getInitiatorsForExportMask(_dbClient, mask, null);
            Iterator<Initiator> exportMaskInitsItr = exportMaskInits.iterator();
            while (exportMaskInitsItr.hasNext()) {
                hostURIs.add(exportMaskInitsItr.next().getHost());
            }

            // Check the initiators and update the lists as necessary
            List<Host> hostList = _dbClient.queryObject(Host.class, hostURIs);
            for (Host host : hostList) {
                discoveredPorts.addAll(restExportOpr.getHostPorts(storageIP, host.getLabel()));
            }
            boolean addInitiators = false;
            List<String> initiatorsToAdd = new ArrayList<String>();
            List<Initiator> initiatorIdsToAdd = new ArrayList<>();
            for (String port : discoveredPorts) {
                String normalizedPort = Initiator.normalizePort(port);
                if (!mask.hasExistingInitiator(normalizedPort) && !mask.hasUserInitiator(normalizedPort)) {
                    initiatorsToAdd.add(normalizedPort);
                    Initiator existingInitiator = ExportUtils.getInitiator(Initiator.toPortNetworkId(port), _dbClient);
                    if (existingInitiator != null) {
                        initiatorIdsToAdd.add(existingInitiator);
                    }
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

            removeInitiators = !initiatorsToRemove.isEmpty();

            // Get Volumes mapped to a Host on Array
            Map<String, Integer> discoveredVolumes = new HashMap<String, Integer>();
            final String exportType = ExportMaskUtils.getExportType(_dbClient, mask);
            if (ExportGroup.ExportGroupType.Cluster.name().equals(exportType)) {
                discoveredVolumes.putAll(restExportOpr.getVolumesMappedToHost(storageIP, mask.getLabel(), null));
            } else {
                for (Host host : hostList) {
                    discoveredVolumes.putAll(restExportOpr.getVolumesMappedToHost(storageIP, null, host.getLabel()));
                }
            }

            // Check the volumes and update the lists as necessary
            Map<String, Integer> volumesToAdd = ExportMaskUtils.diffAndFindNewVolumes(mask, discoveredVolumes);
            boolean addVolumes = !volumesToAdd.isEmpty();

            boolean removeVolumes = false;
            List<String> volumesToRemove = new ArrayList<String>();
            if (mask.getExistingVolumes() != null &&
                    !mask.getExistingVolumes().isEmpty()) {
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
                builder.append("XM refresh: There are changes to mask, " +
                        "updating it...\n");
                mask.removeFromExistingInitiators(initiatorsToRemove);
                if (initiatorIdsToRemove != null && !initiatorIdsToRemove.isEmpty()) {
                    mask.removeInitiators(_dbClient.queryObject(Initiator.class, initiatorIdsToRemove));
                }
                List<Initiator> userAddedInitiators = ExportMaskUtils.findIfInitiatorsAreUserAddedInAnotherMask(mask, initiatorIdsToAdd,
                        _dbClient);
                mask.addToUserCreatedInitiators(userAddedInitiators);
                mask.addToExistingInitiatorsIfAbsent(initiatorsToAdd);
                mask.addInitiators(initiatorIdsToAdd);
                mask.removeFromExistingVolumes(volumesToRemove);
                mask.setExistingVolumes(new StringMap());
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
            final StringSet emInitiatorURIs = exportMask.getInitiators();
            final StringMap emVolumeURIs = exportMask.getVolumes();

            XIVRestClient restExportOpr = getRestClient(storage);
            Set<URI> hostURIs = new HashSet<URI>();

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
                    Host host = _dbClient.queryObject(Host.class, initiator.getHost());
                    hostURIs.add(host.getId());
                    String normalizedPort = Initiator.normalizePort(initiator.getInitiatorPort());
                    restExportOpr.deleteHostPort(storageIP, host.getLabel(), normalizedPort, initiator.getProtocol().toLowerCase(), false);
                }
            }

            // Delete Host if there are no associated Initiators/Volume to it.
            for (URI hostURI : hostURIs) {
                Host host = _dbClient.queryObject(Host.class, hostURI);
                boolean hostDeleted = restExportOpr.deleteHost(storageIP, host.getLabel());
                // Perform post-mask-delete cleanup steps
                if (hostDeleted && emVolumeURIs.size() > 0) {
                    unsetTag(host, storage.getSerialNumber());
                }
            }

            // Delete Cluster if there is no associated hosts to it.
            if (ExportGroup.ExportGroupType.Cluster.name().equals(exportType)) {
                restExportOpr.deleteCluster(storageIP, name);
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
            ServiceError error = XIVRestException.exceptions.methodFailed("createExportMask", e);
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

    /**
     * Find Export mask
     * 
     * @param storage Storage System instance
     * @param initiatorNames Initiator names
     * @param mustHaveAllPorts Must have all the ports boolean
     * @return Initiator to Export mask map.
     */
    public Map<String, Set<URI>> findRESTExportMasks(StorageSystem storage, List<String> initiatorNames, boolean mustHaveAllPorts) {
        long startTime = System.currentTimeMillis();
        Map<String, Set<URI>> matchingMasks = new HashMap<String, Set<URI>>();

        try {
            final String storageIP = storage.getSmisProviderIP();
            XIVRestClient restExportOpr = getRestClient(storage);
            StringBuilder builder = new StringBuilder();
            
            for (String initiatorName : initiatorNames) {
                final String hostName = restExportOpr.getHostPortContainer(storageIP, initiatorName);
                Set<String> exportMaskNames = new HashSet<String>();
                if(null != hostName){
                	exportMaskNames.add(hostName);
                	final String clusterNames = restExportOpr.getHostContainer(storageIP, hostName);
                	if(null != clusterNames){
                		exportMaskNames.add(clusterNames);	
                	}
                }
                
                // Find the existing masks
                for(String exportMaskName : exportMaskNames) {
                    URIQueryResultList uriQueryList = new URIQueryResultList();
                    _dbClient.queryByConstraint(AlternateIdConstraint.Factory.getExportMaskByNameConstraint(exportMaskName), uriQueryList);
                    ExportMask exportMask = null;
                    while (uriQueryList.iterator().hasNext()) {
                        URI uri = uriQueryList.iterator().next();
                        exportMask = _dbClient.queryObject(ExportMask.class, uri);
                        if (exportMask != null && !exportMask.getInactive() && exportMask.getStorageDevice().equals(storage.getId())) {
                            ExportMaskUtils.sanitizeExportMaskContainers(_dbClient, exportMask);
                            _dbClient.updateAndReindexObject(exportMask);
                            Set<URI> maskURIs = matchingMasks.get(initiatorName);
                            if (maskURIs == null) {
                                maskURIs = new HashSet<URI>();
                                matchingMasks.put(initiatorName, maskURIs);
                            }
                            maskURIs.add(exportMask.getId());
                            break;
                        }
                    }

                    // update hosts
                    Initiator initiator = ExportUtils.getInitiator(Initiator.toPortNetworkId(initiatorName), _dbClient);
                    if (null != initiator && null != initiator.getHost()) {
                        Host hostIns = _dbClient.queryObject(Host.class, initiator.getHost());
                        String label = hostIns.getLabel();
                        if (label.equals(exportMaskName)) {
                            unsetTag(hostIns, storage.getSerialNumber());
                        } else {
                            setTag(hostIns, storage.getSerialNumber(), exportMaskName);
                        }
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

    /**
     * Adds a volume to a Export Group
     * 
     * @param storage Storage system instance
     * @param exportMaskURI Export mask URI
     * @param volumeURIHLUs Volume to be added URI
     * @param taskCompleter task completer instance
     */
    public void addVolumeUsingREST(StorageSystem storage, URI exportMaskURI, VolumeURIHLU[] volumeURIHLUs, TaskCompleter taskCompleter) {

        _log.info("{} addVolume START...", storage.getLabel());
        try {

            // Export volume to Cluster
            if (volumeURIHLUs != null && volumeURIHLUs.length > 0) {
                ExportMask exportMask = _dbClient.queryObject(ExportMask.class, exportMaskURI);
                final String storageIP = storage.getSmisProviderIP();
                XIVRestClient restExportOpr = getRestClient(storage);

                // Find HOST from Export Mask
                URI hostName = null;
                Set<Initiator> exportMaskInits = ExportMaskUtils.getInitiatorsForExportMask(_dbClient, exportMask, null);
                Iterator<Initiator> exportMaskInitsItr = exportMaskInits.iterator();
                if (exportMaskInitsItr.hasNext()) {
                    hostName = exportMaskInitsItr.next().getHost();
                }
                final Host host = _dbClient.queryObject(Host.class, hostName);

                // Validate if it is a cluster
                String exportName = host.getLabel();
                String clusterName = null;
                final String exportType = ExportMaskUtils.getExportType(_dbClient, exportMask);
                if (ExportGroup.ExportGroupType.Cluster.name().equals(exportType)) {
                    Cluster cluster = _dbClient.queryObject(Cluster.class, host.getCluster());
                    clusterName = cluster.getLabel();
                    exportName = clusterName;
                }

                // Export volume
                List<BlockObject> userAddedVolumes = new ArrayList<BlockObject>();
                for (VolumeURIHLU volumeURIHLU : volumeURIHLUs) {
                    final BlockObject blockObject = getBlockObject(volumeURIHLU.getVolumeURI());
                    final String volumeHLU = volumeURIHLU.getHLU();
                    if (volumeHLU != null && !volumeHLU.equalsIgnoreCase(ExportGroup.LUN_UNASSIGNED_STR)) {
                        int hluDec = Integer.parseInt(volumeHLU, 16);
                        if (hluDec > MAXIMUM_LUN) {
                            String errMsg = String.format(INVALID_LUN_ERROR_MSG, hluDec, MAXIMUM_LUN);
                            _log.error(errMsg);
                            throw new Exception(errMsg);
                        } else {
                            restExportOpr.exportVolume(storageIP, exportType, exportName, blockObject.getLabel(), String.valueOf(hluDec));
                            userAddedVolumes.add(blockObject);
                        }
                    }
                }
                exportMask.addToUserCreatedVolumes(userAddedVolumes);
                _dbClient.updateObject(exportMask);

                taskCompleter.ready(_dbClient);
            }
        } catch (Exception e) {
            _log.error("Unexpected error: addVolume failed.", e);
            ServiceError error = XIVRestException.exceptions.methodFailed("addVolume", e);
            taskCompleter.error(_dbClient, error);
        }

        _log.info("{} addVolume END...", storage.getLabel());
    }

    /**
     * Removes a volume from a Export group
     * 
     * @param storage Storage system instance
     * @param exportMaskURI Export mask URI
     * @param volumeURIList Volume to be removed URI
     * @param taskCompleter task completer instance
     */
    public void removeVolumeUsingREST(StorageSystem storage, URI exportMaskURI, List<URI> volumeURIList, TaskCompleter taskCompleter) {
        try {

            // Export volume to Cluster
            if (volumeURIList != null && volumeURIList.size() > 0) {
                ExportMask exportMask = _dbClient.queryObject(ExportMask.class, exportMaskURI);
                final String storageIP = storage.getSmisProviderIP();
                XIVRestClient restExportOpr = getRestClient(storage);

                // Find HOST from Export Mask
                URI hostName = null;
                Set<Initiator> exportMaskInits = ExportMaskUtils.getInitiatorsForExportMask(_dbClient, exportMask, null);
                Iterator<Initiator> exportMaskInitsItr = exportMaskInits.iterator();
                if (exportMaskInitsItr.hasNext()) {
                    hostName = exportMaskInitsItr.next().getHost();
                }
                final Host host = _dbClient.queryObject(Host.class, hostName);

                // Validate if it is a cluster
                String exportName = host.getLabel();
                final String exportType = ExportMaskUtils.getExportType(_dbClient, exportMask);
                if (ExportGroup.ExportGroupType.Cluster.name().equals(exportType)) {
                    Cluster cluster = _dbClient.queryObject(Cluster.class, host.getCluster());
                    exportName = cluster.getLabel();
                }

                // Export volume
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
            ServiceError error = XIVRestException.exceptions.methodFailed("removeVolume", e);
            taskCompleter.error(_dbClient, error);
        }

    }

    /**
     * Add Initiator to a existing Export Mask.
     * @param storage StorageSystem instance
     * @param exportMaskURI Export mask URI where the initiator needs to be added
     * @param initiatorList List of initiators need to be added
     * @param taskCompleter Task Completer instance
     */
	public void addInitiatorUsingREST(StorageSystem storage, URI exportMaskURI, List<Initiator> initiatorList, TaskCompleter taskCompleter) {
		
		try {

            ExportMask mask = _dbClient.queryObject(ExportMask.class, exportMaskURI);
            XIVRestClient restExportOpr = getRestClient(storage);

            final String storageIP = storage.getSmisProviderIP();
            
	        List<Initiator> userAddedInitiators = new ArrayList<Initiator>();
	        for (Initiator initiator : initiatorList) {
	            final Host host = _dbClient.queryObject(Host.class, initiator.getHost());
	
	            // Add Initiators to Host.
	            if (!restExportOpr.createHostPort(storageIP, host.getLabel(), Initiator.normalizePort(initiator.getInitiatorPort()),
	                    initiator.getProtocol().toLowerCase())) {
	            	userAddedInitiators.add(initiator);
	            }
	        }
	        mask.addToUserCreatedInitiators(userAddedInitiators);
            ExportMaskUtils.sanitizeExportMaskContainers(_dbClient, mask);
            _dbClient.updateObject(mask);
	        taskCompleter.ready(_dbClient);
		} catch (Exception e) {
			_log.error("Unexpected error: addInitiator failed.", e);
            ServiceError error = XIVRestException.exceptions.methodFailed("addInitiator", e);
            taskCompleter.error(_dbClient, error);
		}
	}
	
	/**
	 * Removes a initiator from ExportMask
	 * @param storage StorageSystem instance
	 * @param exportMaskURI ExportMask URI where Initiator needs to be removed
	 * @param initiatorList List of Initiators to be removed
	 * @param taskCompleter Task Completer instance
	 */
	public void removeInitiatorUsingREST(StorageSystem storage, URI exportMaskURI, List<Initiator> initiatorList, TaskCompleter taskCompleter) {
		
		try {

            ExportMask mask = _dbClient.queryObject(ExportMask.class, exportMaskURI);
            XIVRestClient restExportOpr = getRestClient(storage);

            final String storageIP = storage.getSmisProviderIP();
            
            List<URI> userRemovedInitiators = new ArrayList<URI>();
            if (null != initiatorList) {
                for (Initiator initiator : initiatorList) {
                    final Host host = _dbClient.queryObject(Host.class, initiator.getHost());
                    final String normalizedPort = Initiator.normalizePort(initiator.getInitiatorPort());
                    if(restExportOpr.deleteHostPort(storageIP, host.getLabel(), normalizedPort, initiator.getProtocol().toLowerCase(), true)) {
                    	userRemovedInitiators.add(initiator.getId());
                    }
                }
            }
	        mask.removeFromUserAddedInitiatorsByURI(userRemovedInitiators);
            ExportMaskUtils.sanitizeExportMaskContainers(_dbClient, mask);
            _dbClient.updateObject(mask);
	        taskCompleter.ready(_dbClient);
		} catch (Exception e) {
			_log.error("Unexpected error: addInitiator failed.", e);
            ServiceError error = XIVRestException.exceptions.methodFailed("addInitiator", e);
            taskCompleter.error(_dbClient, error);
		}
	}
}
