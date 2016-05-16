/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.xiv;

import java.net.URI;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
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
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.VolumeURIHLU;
import com.emc.storageos.volumecontroller.impl.utils.ExportMaskUtils;
import com.emc.storageos.xiv.api.XIVApiFactory;
import com.emc.storageos.xiv.api.XIVRESTExportOperations;
import com.emc.storageos.xiv.api.XIVRESTExportOperations.HOST_STATUS;
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
public class XIVRESTOperationHelper {

    private static Logger _log = LoggerFactory.getLogger(XIVRESTOperationHelper.class);

    private DbClient _dbClient;
    private XIVApiFactory _restAPIFactory;

    private static final int MAXIMUM_LUN = 511;
    private static final String INVALID_LUN_ERROR_MSG = "Logical unit number provided (%d) is larger than allowed (%d).";

    public void setDbClient(DbClient dbClient) {
        _dbClient = dbClient;
    }

    public void setRestAPIFactory(XIVApiFactory factory) {
        _restAPIFactory = factory;
    }

    /**
     * Gets REST Client instance for a StorageSystem
     * 
     * @param storage StorageSystem instance
     * @return XIVRESTExportOperations instance
     */
    private XIVRESTExportOperations getRestClient(StorageSystem storage) {
        XIVRESTExportOperations restExportOpr = null;
        StorageProvider provider = _dbClient.queryObject(StorageProvider.class, storage.getActiveProviderURI());
        String providerUser = provider.getSecondaryUsername();
        String providerPassword = provider.getSecondaryPassword();
        String providerURL = provider.getElementManagerURL();

        if (StringUtils.isNotEmpty(providerURL) && StringUtils.isNotEmpty(providerPassword) && StringUtils.isNotEmpty(providerUser)) {
            restExportOpr = _restAPIFactory.getRESTClient(URI.create(providerURL), providerUser, providerPassword);
        }
        return restExportOpr;
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

        List<ExportGroup> exportGroup = ExportMaskUtils.getExportGroups(_dbClient, exportMask);
        if (null != exportGroup && !exportGroup.isEmpty() && exportGroup.get(0).forCluster()) {
            XIVRESTExportOperations restExportOpr = getRestClient(storage);
            if (null != restExportOpr) {
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
        return isClusteredHost;
    }

    /**
     * Validates if the given Host name is identified as Clustered host with respect to XIV
     * 
     * @param storage XIV storage system
     * @param hostName Host name to ve validated
     * @return true if the host is part of Cluster. Else false.
     */
    public boolean isClusteredHostOnArray(StorageSystem storage, String hostName) {
        boolean isClusteredHost = false;
        XIVRESTExportOperations restExportOpr = getRestClient(storage);
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
                    _log.info("Identified Host {} as a Clustered Host Array {}.", hostName, storage.getLabel());
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
            XIVRESTExportOperations restExportOpr = getRestClient(storage);

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
                            restExportOpr.exportVolume(storageIP, exportType, exportName, lunName, volumeHLU);
                        }
                    }
                }
            }

            // Update Masking information
            exportMask.setCreatedBySystem(false);
            exportMask.addToUserCreatedInitiators(existingInitiators);
            exportMask.setMaskName(exportName);
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
        String nativeId;
        if (URIUtil.isType(uri, Volume.class)) {
            Volume volume = _dbClient.queryObject(Volume.class, uri);
            nativeId = volume.getLabel();
        } else if (URIUtil.isType(uri, BlockSnapshot.class)) {
            BlockSnapshot blockSnapshot = _dbClient.queryObject(BlockSnapshot.class, uri);
            nativeId = blockSnapshot.getLabel();
        } else if (URIUtil.isType(uri, BlockMirror.class)) {
            BlockMirror blockMirror = _dbClient.queryObject(BlockMirror.class, uri);
            nativeId = blockMirror.getLabel();
        } else {
            throw XIVRestException.exceptions.notAVolumeOrBlocksnapshotUri(uri);
        }
        return nativeId;
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
            final String name = mask.getMaskName();

            XIVRESTExportOperations restExportOpr = getRestClient(storage);
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
            if (mask.getExistingInitiators() != null && !mask.getExistingInitiators().isEmpty()) {
                initiatorsToRemove.addAll(mask.getExistingInitiators());
                initiatorsToRemove.removeAll(discoveredPorts);
                removeInitiators = !initiatorsToRemove.isEmpty();
            }

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
            _log.error(MessageFormat.format("Encountered an SMIS error when attempting to refresh existing exports: {0}", msg), e);
            // throw XIVRestException.exceptions.refreshExistingMaskFailure(msg);
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
            final String name = exportMask.getMaskName();
            final String hostName = exportMask.getLabel();

            final StringSet emInitiatorURIs = exportMask.getInitiators();
            final StringMap emVolumeURIs = exportMask.getVolumes();

            XIVRESTExportOperations restExportOpr = getRestClient(storage);
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
}
