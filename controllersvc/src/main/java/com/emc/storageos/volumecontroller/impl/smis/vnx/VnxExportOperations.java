/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.smis.vnx;

import java.net.URI;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.cim.CIMArgument;
import javax.cim.CIMDataType;
import javax.cim.CIMInstance;
import javax.cim.CIMObjectPath;
import javax.cim.CIMProperty;
import javax.cim.UnsignedInteger16;
import javax.wbem.CloseableIterator;
import javax.wbem.WBEMException;
import javax.wbem.client.WBEMClient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.storageos.cimadapter.connections.cim.CimConnection;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.AutoTieringPolicy.VnxFastPolicy;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.util.CommonTransformerFunctions;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.db.client.util.StringSetUtil;
import com.emc.storageos.db.client.util.WWNUtility;
import com.emc.storageos.exceptions.DeviceControllerErrors;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.networkcontroller.impl.NetworkDeviceController;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.recoverpoint.utils.WwnUtils;
import com.emc.storageos.recoverpoint.utils.WwnUtils.FORMAT;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.util.ExportUtils;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.ControllerUtils;
import com.emc.storageos.volumecontroller.impl.VolumeURIHLU;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.ExportMaskCreateCompleter;
import com.emc.storageos.volumecontroller.impl.smis.CIMObjectPathFactory;
import com.emc.storageos.volumecontroller.impl.smis.CIMPropertyFactory;
import com.emc.storageos.volumecontroller.impl.smis.ExportMaskOperations;
import com.emc.storageos.volumecontroller.impl.smis.ExportMaskOperationsHelper;
import com.emc.storageos.volumecontroller.impl.smis.SmisCommandHelper;
import com.emc.storageos.volumecontroller.impl.smis.SmisConstants;
import com.emc.storageos.volumecontroller.impl.smis.SmisException;
import com.emc.storageos.volumecontroller.impl.utils.ExportMaskUtils;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.Collections2;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.TreeMultimap;

public class VnxExportOperations implements ExportMaskOperations {

    private static Logger _log = LoggerFactory.getLogger(VnxExportOperations.class);
    private SmisCommandHelper _helper;
    private DbClient _dbClient;
    private CIMObjectPathFactory _cimPath;
    private static final int DEFAULT_STORAGE_TIER_METHODOLOGY = 4;

    @Autowired
    private NetworkDeviceController _networkDeviceController;

    public void setCimObjectPathFactory(CIMObjectPathFactory cimObjectPathFactory) {
        _cimPath = cimObjectPathFactory;
    }

    public void setSmisCommandHelper(SmisCommandHelper helper) {
        _helper = helper;
    }

    public void setDbClient(DbClient dbClient) {
        _dbClient = dbClient;
    }

    public void createExportMask(StorageSystem storage,
            URI exportMaskURI,
            VolumeURIHLU[] volumeURIHLUs,
            List<URI> targetURIList,
            List<Initiator> initiatorList,
            TaskCompleter taskCompleter) throws DeviceControllerException {
        _log.info("{} createExportMask START...", storage.getSerialNumber());
        try {
            // https://coprhd.atlassian.net/browse/COP-19019: Validation routine indicates that there
            // is some mask- other than the one that we are trying to create -containing the initiators.
            // This is an error because initiators can only belong to one VNX StorageGroup. If ViPR performs
            // the create, it will succeeded, but just end up moving all the initiators from the existing
            // StorageGroup into the new one. This would cause the initiators to lose access to the
            // volumes in the original StorageGroup, which is a DU situation. We need to prevent this by
            // performing this validation as a precaution.
            if (anyInitiatorsAreInAStorageGroup(storage, initiatorList)) {
                ServiceError error = SmisException.errors.anExistingSGAlreadyHasTheInitiators(exportMaskURI.toString(),
                        Joiner.on(',').join(initiatorList));
                taskCompleter.error(_dbClient, error);
                return;
            }
            CIMObjectPath[] protocolControllers = createOrGrowStorageGroup(storage,
                    exportMaskURI, volumeURIHLUs, null, null, taskCompleter);
            if (protocolControllers != null) {
                _log.debug("createExportMask succeeded.");
                for (CIMObjectPath protocolController : protocolControllers) {
                    _helper.setProtocolControllerNativeId(exportMaskURI, protocolController);
                }
                CimConnection cimConnection = _helper.getConnection(storage);
                createOrGrowStorageGroup(storage, exportMaskURI, null, initiatorList, targetURIList, taskCompleter);
                // Call populateDeviceNumberFromProtocolControllers only after initiators
                // have been added. HLU's will not be reported till the Device is Host
                // visible
                ExportMaskOperationsHelper.populateDeviceNumberFromProtocolControllers(_dbClient, cimConnection, exportMaskURI,
                        volumeURIHLUs, protocolControllers, taskCompleter);
                modifyClarPrivileges(storage, initiatorList);
                taskCompleter.ready(_dbClient);
            } else {
                _log.debug("createExportMask failed. No protocol controller created.");
                ServiceError error = DeviceControllerException.errors.smis.noProtocolControllerCreated();
                taskCompleter.error(_dbClient, error);
            }
        } catch (Exception e) {
            _log.error("Unexpected error: createExportMask failed.", e);
            ServiceError error = DeviceControllerErrors.smis.methodFailed("createExportMask", e.getMessage());
            taskCompleter.error(_dbClient, error);
        }
        _log.info("{} createExportMask END...", storage.getSerialNumber());
    }

    public void deleteExportMask(StorageSystem storage,
            URI exportMaskURI,
            List<URI> volumeURIList,
            List<URI> targetURIList,
            List<Initiator> initiatorList,
            TaskCompleter taskCompleter) throws DeviceControllerException {
        _log.info("{} deleteExportMask START...", storage.getSerialNumber());
        try {
            ExportMask exportMask = _dbClient.queryObject(ExportMask.class, exportMaskURI);
            String nativeId = exportMask.getNativeId();

            if (Strings.isNullOrEmpty(nativeId)) {
                _log.warn(String.format("ExportMask %s does not have a nativeID, " +
                        "indicating that this export may not have been created " +
                        "successfully. Marking the delete operation ready.",
                        exportMaskURI.toString()));
                // Perform post-mask-delete cleanup steps
                ExportUtils.cleanupAssociatedMaskResources(_dbClient, exportMask);
                taskCompleter.ready(_dbClient);
                return;
            }

            CIMObjectPath protocolController =
                    _cimPath.getClarProtocolControllers(storage, nativeId)[0];
            CIMInstance instance =
                    _helper.checkExists(storage, protocolController, true, true);
            if (instance != null) {
                deleteOrShrinkStorageGroup(storage, exportMaskURI, null, null);
                _helper.setProtocolControllerNativeId(exportMaskURI, null);
                ExportMask mask = _dbClient.queryObject(ExportMask.class, exportMaskURI);
                if (mask != null) {
                    List<URI> initiatorURIs = new ArrayList<URI>();
                    if (mask.getInitiators() != null) {
                        for (String initUriStr : mask.getInitiators()) {
                            initiatorURIs.add(URI.create(initUriStr));
                        }
                    }
                    List<Initiator> initiators =
                            _dbClient.queryObject(Initiator.class, initiatorURIs);
                    deleteStorageHWIDs(storage, initiators);
                }
            }
            // Perform post-mask-delete cleanup steps
            ExportUtils.cleanupAssociatedMaskResources(_dbClient, exportMask);

            taskCompleter.ready(_dbClient);
        } catch (Exception e) {
            _log.error("Unexpected error: deleteExportMask failed.", e);
            ServiceError error = DeviceControllerErrors.smis.methodFailed("deleteExportMask", e.getMessage());
            taskCompleter.error(_dbClient, error);
        }
        _log.info("{} deleteExportMask END...", storage.getSerialNumber());
    }

    public void addVolume(StorageSystem storage,
            URI exportMaskURI,
            VolumeURIHLU[] volumeURIHLUs,
            TaskCompleter taskCompleter) throws DeviceControllerException {
        _log.info("{} addVolume START...", storage.getSerialNumber());
        try {
            CIMObjectPath[] protocolControllers = createOrGrowStorageGroup(storage, exportMaskURI, volumeURIHLUs, null, null, taskCompleter);
            CimConnection cimConnection = _helper.getConnection(storage);
            ExportMaskOperationsHelper.populateDeviceNumberFromProtocolControllers(_dbClient, cimConnection, exportMaskURI,
                    volumeURIHLUs, protocolControllers, taskCompleter);
            taskCompleter.ready(_dbClient);
        } catch (Exception e) {
            _log.error("Unexpected error: addVolume failed.", e);
            ServiceError error = DeviceControllerErrors.smis.methodFailed("addVolume", e.getMessage());
            taskCompleter.error(_dbClient, error);
        }
        _log.info("{} addVolume END...", storage.getSerialNumber());
    }

    public void removeVolume(StorageSystem storage,
            URI exportMaskURI,
            List<URI> volumeURIList,
            TaskCompleter taskCompleter) throws DeviceControllerException {
        _log.info("{} removeVolume START...", storage.getSerialNumber());
        try {
        	if (null == volumeURIList || volumeURIList.isEmpty()) {
				taskCompleter.ready(_dbClient);
				_log.warn("{} removeVolume invoked with zero volumes, resulting in no-op....",
						storage.getSerialNumber());
				return;
			}
            deleteOrShrinkStorageGroup(storage, exportMaskURI, volumeURIList, null);
            taskCompleter.ready(_dbClient);
        } catch (Exception e) {
            _log.error("Unexpected error: removeVolume failed.", e);
            ServiceError error = DeviceControllerErrors.smis.methodFailed("removeVolume", e.getMessage());
            taskCompleter.error(_dbClient, error);
        }
        _log.info("{} removeVolume END...", storage.getSerialNumber());
    }

    public void addInitiator(StorageSystem storage,
            URI exportMaskURI,
            List<Initiator> initiatorList,
            List<URI> targets,
            TaskCompleter taskCompleter) throws DeviceControllerException {
        _log.info("{} addInitiator START...", storage.getSerialNumber());
        try {
            createOrGrowStorageGroup(storage, exportMaskURI, null, initiatorList, targets, taskCompleter);
            taskCompleter.ready(_dbClient);
        } catch (Exception e) {
            _log.error("Unexpected error: addInitiator failed.", e);
            ServiceError error = DeviceControllerErrors.smis.methodFailed("addInitiator", e.getMessage());
            taskCompleter.error(_dbClient, error);
        }
        _log.info("{} addInitiator END...", storage.getSerialNumber());
    }

    public void removeInitiator(StorageSystem storage,
            URI exportMaskURI,
            List<Initiator> initiatorList,
            List<URI> targets,
            TaskCompleter taskCompleter) throws DeviceControllerException {
        _log.info("{} removeInitiator START...", storage.getSerialNumber());
        try {
            deleteStorageHWIDs(storage, initiatorList);
            taskCompleter.ready(_dbClient);
        } catch (Exception e) {
            _log.error("Unexpected error: removeInitiator failed.", e);
            ServiceError error = DeviceControllerErrors.smis.methodFailed("removeInitiator", e.getMessage());
            taskCompleter.error(_dbClient, error);
        }
        _log.info("{} removeInitiator END...", storage.getSerialNumber());
    }

    /**
     * This call can be used to look up the passed in initiator/port names and find (if
     * any) to which export masks they belong on the 'storage' array.
     * 
     * 
     * @param storage [in] - StorageSystem object representing the array
     * @param initiatorNames [in] - Port identifiers (WWPN or iSCSI name)
     * @param mustHaveAllPorts [in] NOT APPLICABLE FOR VNX
     * @return Map of port name to Set of ExportMask URIs
     */
    public Map<String, Set<URI>> findExportMasks(StorageSystem storage,
            List<String> initiatorNames,
            boolean mustHaveAllPorts) {
        long startTime = System.currentTimeMillis();
        Map<String, Set<URI>> matchingMasks = new HashMap<String, Set<URI>>();
        CloseableIterator<CIMInstance> lunMaskingIter = null;
        try {
            StringBuilder builder = new StringBuilder();
            WBEMClient client = _helper.getConnection(storage).getCimClient();
            lunMaskingIter = _helper.getClarLunMaskingProtocolControllers(storage);
            while (lunMaskingIter.hasNext()) {
                CIMInstance instance = lunMaskingIter.next();
                String systemName = CIMPropertyFactory.getPropertyValue(instance,
                        SmisConstants.CP_SYSTEM_NAME);

                if (!systemName.contains(storage.getSerialNumber())) {
                    // We're interested in the specific StorageSystem's masks.
                    // The above getClarLunMaskingProtocolControllers call will get
                    // a listing of for all the protocol controllers seen by the
                    // SMISProvider pointed to by 'storage' system.
                    continue;
                }

                String name = CIMPropertyFactory.getPropertyValue(instance,
                        SmisConstants.CP_ELEMENT_NAME);
                CIMProperty<String> deviceIdProperty =
                        (CIMProperty<String>) instance.getObjectPath().
                                getKey(SmisConstants.CP_DEVICE_ID);
                // Get volumes and initiators for the masking instance
                Map<String, Integer> volumeWWNs =
                        _helper.getVolumesFromLunMaskingInstance(client, instance);
                List<String> initiatorPorts =
                        _helper.getInitiatorsFromLunMaskingInstance(client, instance);
                // Find out if the port is in this masking container
                List<String> matchingInitiators = new ArrayList<String>();
                for (String port : initiatorNames) {
                    String normalizedName = Initiator.normalizePort(port);
                    if (initiatorPorts.contains(normalizedName)) {
                        matchingInitiators.add(normalizedName);
                    }
                }
                builder.append(String.format("%nXM:%s I:{%s} V:{%s}%n", name,
                        Joiner.on(',').join(initiatorPorts),
                        Joiner.on(',').join(volumeWWNs.keySet())));
                if (!matchingInitiators.isEmpty()) {
                    // Look up ExportMask by deviceId/name and storage URI
                    boolean foundMaskInDb = false;
                    ExportMask exportMask = null;
                    URIQueryResultList uriQueryList = new URIQueryResultList();
                    _dbClient.queryByConstraint(AlternateIdConstraint.Factory
                            .getExportMaskByNameConstraint(name), uriQueryList);
                    while (uriQueryList.iterator().hasNext()) {
                        URI uri = uriQueryList.iterator().next();
                        exportMask = _dbClient.queryObject(ExportMask.class, uri);
                        if (exportMask != null && !exportMask.getInactive() &&
                                exportMask.getStorageDevice().equals(storage.getId())) {
                            foundMaskInDb = true;
                            // We're expecting there to be only one export mask of a
                            // given name for any storage array.
                            break;
                        }
                    }
                    // If there was no export mask found in the database,
                    // then create a new one
                    if (!foundMaskInDb) {
                        exportMask = new ExportMask();
                        exportMask.setMaskName(name);
                        exportMask.setNativeId(deviceIdProperty.getValue());
                        exportMask.setStorageDevice(storage.getId());
                        exportMask.setId(URIUtil.createId(ExportMask.class));
                        exportMask.setCreatedBySystem(false);
                        // Grab the storage ports that have been allocated for this
                        // existing mask and add them.
                        List<String> storagePorts =
                                _helper.getStoragePortsFromLunMaskingInstance(client,
                                        instance);
                        List<String> storagePortURIs =
                                ExportUtils.storagePortNamesToURIs(_dbClient, storagePorts);
                        exportMask.setStoragePorts(storagePortURIs);
                        builder.append(String.format("   ----> SP { %s }\n" +
                                "         URI{ %s }\n",
                                Joiner.on(',').join(storagePorts),
                                Joiner.on(',').join(storagePortURIs)));
                    } else {
                        // refresh the export mask
                        refreshExportMask(storage, exportMask);
                        builder.append('\n');
                    }
                    // Update the tracking containers
                    exportMask.addToExistingVolumesIfAbsent(volumeWWNs);
                    exportMask.addToExistingInitiatorsIfAbsent(matchingInitiators);

                    // Update the initiator list to include existing initiators if we know about them.
                    if (matchingInitiators != null) {
                        for (String port : matchingInitiators) {
                            Initiator existingInitiator = ExportUtils.getInitiator(Initiator.toPortNetworkId(port), _dbClient);
                            if (existingInitiator != null) {
                                exportMask.addInitiator(existingInitiator);
                            }
                        }
                    }

                    // Update the volume list to include existing volumes if know about them.
                    if (volumeWWNs != null) {
                        for (String wwn : volumeWWNs.keySet()) {
                            URIQueryResultList results = new URIQueryResultList();
                            _dbClient.queryByConstraint(AlternateIdConstraint.Factory
                                    .getVolumeWwnConstraint(wwn.toUpperCase()), results);
                            if (results != null) {
                                Iterator<URI> resultsIter = results.iterator();
                                if (resultsIter.hasNext()) {
                                    Volume volume = _dbClient.queryObject(Volume.class, resultsIter.next());
                                    if (volume != null) {
                                        Integer hlu = volumeWWNs.get(wwn);
                                        if (hlu == null) {
                                            _log.warn(String.format(
                                                    "The HLU for %s could not be found from the provider. Setting this to -1 (Unknown).",
                                                    wwn));
                                            hlu = -1;
                                        }
                                        exportMask.addVolume(volume.getId(), hlu);
                                    }
                                }
                            }
                        }
                    }

                    builder.append(String.format("XM:%s is matching. " +
                            "EI: { %s }, EV: { %s }",
                            name,
                            Joiner.on(',').join(exportMask.getExistingInitiators()),
                            Joiner.on(',').
                                    join(exportMask.getExistingVolumes().keySet())));
                    if (foundMaskInDb) {
                        ExportMaskUtils.sanitizeExportMaskContainers(_dbClient, exportMask);
                        _dbClient.updateAndReindexObject(exportMask);
                    } else {
                        _dbClient.createObject(exportMask);
                    }
                    for (String it : matchingInitiators) {
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

            throw SmisException.exceptions.queryExistingMasksFailure(msg, e);
        } finally {
            if (lunMaskingIter != null) {
                lunMaskingIter.close();
            }
            long totalTime = System.currentTimeMillis() - startTime;
            _log.info(String.format("findExportMasks took %f seconds", (double) totalTime / (double) 1000));
        }
        return matchingMasks;
    }

    @Override
    public ExportMask refreshExportMask(StorageSystem storage, ExportMask mask) {
        try {
            CIMInstance instance =
                    _helper.getLunMaskingProtocolController(storage, mask);
            if (instance != null) {
                StringBuilder builder = new StringBuilder();
                WBEMClient client = _helper.getConnection(storage).getCimClient();
                String name = CIMPropertyFactory.getPropertyValue(instance,
                        SmisConstants.CP_ELEMENT_NAME);
                // Get volumes and initiators for the masking instance
                Map<String, Integer> discoveredVolumes =
                        _helper.getVolumesFromLunMaskingInstance(client, instance);
                List<String> discoveredPorts =
                        _helper.getInitiatorsFromLunMaskingInstance(client, instance);

                Set existingInitiators = (mask.getExistingInitiators() != null) ?
                        mask.getExistingInitiators() : Collections.emptySet();
                Set existingVolumes = (mask.getExistingVolumes() != null) ?
                        mask.getExistingVolumes().keySet() : Collections.emptySet();

                builder.append(String.format("%nXM object: %s I{%s} V:{%s}%n", name,
                        Joiner.on(',').join(existingInitiators),
                        Joiner.on(',').join(existingVolumes)));

                builder.append(String.format("XM discovered: %s I:{%s} V:{%s}%n", name,
                        Joiner.on(',').join(discoveredPorts),
                        Joiner.on(',').join(discoveredVolumes.keySet())));

                // Check the initiators and update the lists as necessary
                boolean addInitiators = false;
                List<String> initiatorsToAdd = new ArrayList<String>();
                for (String port : discoveredPorts) {
                    String normalizedPort = Initiator.normalizePort(port);
                    if (!mask.hasExistingInitiator(normalizedPort) &&
                            !mask.hasUserInitiator(normalizedPort)) {
                        initiatorsToAdd.add(normalizedPort);
                        addInitiators = true;
                    }
                }

                boolean removeInitiators = false;
                List<String> initiatorsToRemove = new ArrayList<String>();
                if (mask.getExistingInitiators() != null &&
                        !mask.getExistingInitiators().isEmpty()) {
                    initiatorsToRemove.addAll(mask.getExistingInitiators());
                    initiatorsToRemove.removeAll(discoveredPorts);
                    removeInitiators = !initiatorsToRemove.isEmpty();
                }

                // Check the volumes and update the lists as necessary
                boolean addVolumes = false;
                Map<String, Integer> volumesToAdd = new HashMap<String, Integer>();
                for (Map.Entry<String, Integer> entry : discoveredVolumes.entrySet()) {
                    String normalizedWWN = BlockObject.normalizeWWN(entry.getKey());
                    if (!mask.hasExistingVolume(normalizedWWN) &&
                            !mask.hasUserCreatedVolume(normalizedWWN)) {
                        volumesToAdd.put(normalizedWWN, entry.getValue());
                        addVolumes = true;
                    }
                }

                boolean removeVolumes = false;
                List<String> volumesToRemove = new ArrayList<String>();
                if (mask.getExistingVolumes() != null &&
                        !mask.getExistingVolumes().isEmpty()) {
                    volumesToRemove.addAll(mask.getExistingVolumes().keySet());
                    volumesToRemove.removeAll(discoveredVolumes.keySet());
                    removeVolumes = !volumesToRemove.isEmpty();
                }

                // NOTE/TODO: We are not modifying the storage ports upon refresh like we do for VMAX.
                // Refer to CTRL-6982.

                builder.append(
                        String.format("XM refresh: %s initiators; add:{%s} remove:{%s}%n",
                                name, Joiner.on(',').join(initiatorsToAdd),
                                Joiner.on(',').join(initiatorsToRemove)));
                builder.append(
                        String.format("XM refresh: %s volumes; add:{%s} remove:{%s}%n",
                                name, Joiner.on(',').join(volumesToAdd.keySet()),
                                Joiner.on(',').join(volumesToRemove)));

                // Any changes indicated, then update the mask and persist it
                if (addInitiators || removeInitiators || addVolumes ||
                        removeVolumes) {
                    builder.append("XM refresh: There are changes to mask, " +
                            "updating it...\n");
                    mask.removeFromExistingInitiators(initiatorsToRemove);
                    mask.addToExistingInitiatorsIfAbsent(initiatorsToAdd);
                    mask.removeFromExistingVolumes(volumesToRemove);
                    mask.addToExistingVolumesIfAbsent(volumesToAdd);

                    // Update the initiator list to include existing initiators if we know about them.
                    if (addInitiators) {
                        for (String port : initiatorsToAdd) {
                            Initiator existingInitiator = ExportUtils.getInitiator(Initiator.toPortNetworkId(port), _dbClient);
                            if (existingInitiator != null) {
                                mask.addInitiator(existingInitiator);
                            }
                        }
                    }

                    // Update the volume list to include existing volumes if know about them.
                    if (addVolumes) {
                        for (String wwn : volumesToAdd.keySet()) {
                            URIQueryResultList results = new URIQueryResultList();
                            _dbClient.queryByConstraint(AlternateIdConstraint.Factory
                                    .getVolumeWwnConstraint(wwn.toUpperCase()), results);
                            if (results != null) {
                                Iterator<URI> resultsIter = results.iterator();
                                if (resultsIter.hasNext()) {
                                    Volume volume = _dbClient.queryObject(Volume.class, resultsIter.next());
                                    if (null != volume) {
                                        mask.addVolume(volume.getId(), volumesToAdd.get(wwn));
                                    }
                                }
                            }
                        }
                    }
                    ExportMaskUtils.sanitizeExportMaskContainers(_dbClient, mask);
                    _dbClient.updateAndReindexObject(mask);
                } else {
                    builder.append("XM refresh: There are no changes to the mask\n");
                }
                _networkDeviceController.refreshZoningMap(mask,
                        initiatorsToRemove, Collections.EMPTY_LIST,
                        (addInitiators || removeInitiators), true);
                _log.info(builder.toString());
            }
        } catch (Exception e) {
            boolean throwException = true;
            if (e instanceof WBEMException) {
                WBEMException we = (WBEMException) e;
                // Only throw exception if code is not CIM_ERROR_NOT_FOUND
                throwException = (we.getID() != WBEMException.CIM_ERR_NOT_FOUND);
            }
            if (throwException) {
                String msg = "Error when attempting to query LUN masking information: " + e.getMessage();
                _log.error(MessageFormat.format("Encountered an SMIS error when attempting to refresh existing exports: {0}", msg), e);

                throw SmisException.exceptions.refreshExistingMaskFailure(msg, e);
            }
        }
        return mask;
    }

    /**
     * This call will attempt to call CreateStorageHardwareID for the given list of
     * Initiators. CSHID is called only if the SE_StorageHardwareID (Initiator) is
     * not already known to the array. If the Initiator is known to the array, function
     * will attempt to extract any known targets end points and return those.
     * 
     * @param storage [in] - StorageSystem object representing the array
     * @param initiators [in] - List Initiator objects
     * @return Multimap of Initiator.normalizedPort(initiatorPort) to target endpoint names
     * @throws Exception
     */
    private Multimap<String, String> createStorageHWIDs(StorageSystem storage, Map<String, CIMObjectPath> existingHwStorageIds,
            List<Initiator> initiators)
            throws Exception {
        _log.info("{} createStorageHWID START...", storage.getSerialNumber());
        Multimap<String, String> existingTargets = TreeMultimap.create();
        if (initiators == null || initiators.isEmpty()) {
            _log.info("No initiators ...");
            return existingTargets;
        }
        try {
            CIMObjectPath hwIdManagementSvc = _cimPath.getStorageHardwareIDManagementService(storage);
            for (Initiator initiator : initiators) {
                String normalizedPortName = Initiator.normalizePort(initiator.getInitiatorPort());
                // Skip any initiators that already exist on the system
                if (existingHwStorageIds.containsKey(normalizedPortName)) {
                    List<String> endpoints = getEMCTargetEndpoints(hwIdManagementSvc, storage,
                            existingHwStorageIds.get(normalizedPortName));
                    _log.info("Endpoint found for {} EndPoints {}", normalizedPortName, endpoints);
                    for (String endpoint : endpoints) {
                        existingTargets.put(normalizedPortName, endpoint);
                        _log.info("Endpoint found for {} EndPoint {}", normalizedPortName, endpoint);
                    }
                    _log.info("WWNs found on the array already: {}", Joiner.on(',').join(existingHwStorageIds.keySet()));
                    _log.info(String.format("Initiator %s already exists, skip creation", initiator.getInitiatorPort()));
                    continue;
                }

                CIMArgument[] createHwIdIn = _helper.getCreateStorageHardwareIDArgs(initiator);
                CIMArgument[] createHwIdOut = new CIMArgument[5];
                _helper.invokeMethod(storage, hwIdManagementSvc,
                        SmisConstants.CREATE_STORAGE_HARDWARE_ID, createHwIdIn, createHwIdOut);
            }
        } catch (WBEMException e) {
            _log.error("Problem making SMI-S call: ", e);
            throw e;
        } catch (Exception e) {
            _log.error("Unexpected error: createStorageHWIDs failed.", e);
            throw e;
        }
        return existingTargets;
    }

    /**
     * Find any initiator on the storage array that belongs to the same host and grab the "hostname"
     * of that initiator. Update our existing transient initiator object so it will use the same name.
     * 
     * @param initiator
     * @return
     * @throws Exception
     */
    private Initiator updateInitiatorBasedOnPeers(StorageSystem storage, Map<String, CIMObjectPath> existingHwStorageIds,
            Initiator initiator) throws Exception {
        // First, find all initiators for this host
        List<Initiator> initiatorsWithHostName = CustomQueryUtility
                .queryActiveResourcesByAltId(_dbClient, Initiator.class, "hostname", initiator.getHostName());

        CloseableIterator<CIMInstance> seHwIter = null;
        try {
            if (initiatorsWithHostName != null) {
                for (Initiator hostInitiator : initiatorsWithHostName) {
                    // Exclude any initiator that's not pointing to the same host
                    // Exclude any initiator that's not pointing to a host at all, such as VPLEX
                    // Exclude the current initiator because we know we're adding that already.
                    if (hostInitiator.getId().equals(initiator.getId()) ||
                            hostInitiator.getHost() == null || initiator.getHost() == null ||
                            !hostInitiator.getHost().equals(initiator.getHost())) {
                        continue;
                    }

                    // Find this WWN on the storage array
                    String portName = Initiator.normalizePort(hostInitiator.getInitiatorPort());
                    if (existingHwStorageIds.containsKey(portName)) {
                        // Find if this initiator is in any storage group
                        seHwIter = _helper.getAssociatorInstances(storage, existingHwStorageIds.get(portName), null,
                                SmisConstants.EMC_CLAR_PRIVILEGE, null, null, SmisConstants.PS_EMC_HOST_NAME);
                        if (seHwIter != null) {
                            if (seHwIter.hasNext()) {
                                CIMInstance priv = seHwIter.next();
                                String hostName = CIMPropertyFactory.getPropertyValue(priv, SmisConstants.CP_EMC_HOST_NAME);
                                if (hostName == null || hostName.isEmpty()) {
                                    _log.info("updateInitiatorsBasedOnPeers: could not retrieve hostname of initiator: "
                                            + hostInitiator.getInitiatorPort());
                                } else {
                                    // Update the initiator object with this hostname and return.
                                    _log.info("updateInitiatorsBasedOnPeers: retrieved hostname of initiator: "
                                            + hostInitiator.getInitiatorPort() + " and found hostname " + hostName);
                                    initiator.setHostName(hostName);
                                    return initiator;
                                }
                            }
                        }
                    }
                }
            }
        } finally {
            if (seHwIter != null) {
                seHwIter.close();
            }
        }

        // No changes to initiator (default behavior)
        return initiator;
    }

    /**
     * Method invokes the SMI-S operation to remove the initiator hardware ID from the
     * array. This should be called whenever the initiator is removed from an export or
     * when the export is deleted.
     * 
     * @param storage [in] - StorageSystem representing the array
     * @param initiators [in] - An array Initiator objects, whose representation will
     *            be removed from the array.
     */
    private void deleteStorageHWIDs(StorageSystem storage,
            List<Initiator> initiators) {
        if (initiators == null || initiators.isEmpty()) {
            _log.debug("No initiators ...");
            return;
        }
        CIMObjectPath hwIdManagementSvc = _cimPath
                .getStorageHardwareIDManagementService(storage);
        for (Initiator initiator : initiators) {
            try {
                CIMArgument[] createHwIdIn =
                        _helper.getDeleteStorageHardwareIDArgs(storage, initiator);
                CIMArgument[] createHwIdOut = new CIMArgument[5];
                _helper.invokeMethod(storage, hwIdManagementSvc,
                        SmisConstants.DELETE_STORAGE_HARDWARE_ID, createHwIdIn,
                        createHwIdOut);
            } catch (WBEMException e) {
                _log.error("deleteStorageHWIDs -- WBEMException: " + e.getMessage());
            } catch (Exception e) {
                _log.error("deleteStorageHWIDs -- Exception: " + e.getMessage());
            }
        }
    }

    /**
     * Method invokes the SMI-S operation to modify the initiator parameters such as type and failovermode.
     * 
     * @param storage [in] - StorageSystem representing the array
     * @param initiators [in] - An array Initiator objects, whose representation will
     *            be removed from the array.
     * @throws Exception
     */
    @SuppressWarnings("rawtypes")
    public void modifyClarPrivileges(StorageSystem storage,
            List<Initiator> initiators) throws Exception {
        if (initiators == null || initiators.isEmpty()) {
            _log.debug("No initiators ...");
            return;
        }

        _log.info("Start -- modifyClarPrivileges");
        List<String> initiatorStrings = new ArrayList<String>();
        final String RP_INITIATOR_PREFIX = "500124";
        final int RP_INITIATOR_TYPE = 31;
        final int RP_INITIATOR_FAILOVERMODE = 4;
        final CIMProperty[] RP_CLAR_PRIVILIEGE_CIM_PROPERTY = new CIMProperty[] {
                new CIMProperty<UnsignedInteger16>(SmisConstants.CP_EMC_INITIATOR_TYPE, CIMDataType.UINT16_T,
                        new UnsignedInteger16(RP_INITIATOR_TYPE)),

                new CIMProperty<UnsignedInteger16>(SmisConstants.CP_EMC_FAILOVER_MODE, CIMDataType.UINT16_T,
                        new UnsignedInteger16(RP_INITIATOR_FAILOVERMODE))
        };

        CloseableIterator<CIMInstance> privilegeInstances = null;

        for (Initiator initiator : initiators) {
            if (initiator.getProtocol().equalsIgnoreCase(Initiator.Protocol.FC.name())) {
                initiatorStrings.add(WwnUtils.convertWWN(initiator.getInitiatorNode(), FORMAT.NOMARKERS).toString()
                        .concat(WwnUtils.convertWWN(initiator.getInitiatorPort(), FORMAT.NOMARKERS).toString()));
            }
        }

        if (initiatorStrings.isEmpty()) {
            _log.info("There are no initiators in the list whose privileges need to be changed.");
            return;
        }

        try {
            privilegeInstances = _helper.getClarPrivileges(storage);

            while (privilegeInstances.hasNext()) {
                CIMInstance existingInstance = privilegeInstances.next();
                String initiatorType = CIMPropertyFactory.getPropertyValue(existingInstance, SmisConstants.CP_EMC_INITIATOR_TYPE);

                // Clar_Privilege consists of instances of all initiators from all the storagesystems that the SMIS is connected to. Filter
                // for only the ones you need based on the storage system.
                // We are only interested in the RP initiators, so check if the initiators are RP initiators
                if (existingInstance.toString().contains(storage.getSerialNumber())
                        && existingInstance.toString().contains(RP_INITIATOR_PREFIX)) {

                    for (String initiatorString : initiatorStrings) {
                        if (existingInstance.toString().contains(initiatorString)
                                && (initiatorType != null && Integer.parseInt(initiatorType) != RP_INITIATOR_TYPE)) {
                            CIMInstance toUpdate = new CIMInstance(existingInstance.getObjectPath(), RP_CLAR_PRIVILIEGE_CIM_PROPERTY);

                            _log.info("Modifying -- " + existingInstance.toString());
                            _helper.modifyInstance(storage, toUpdate, SmisConstants.PS_EMC_CLAR_PRIVILEGE);
                            break;
                        }
                    }
                }
            }
            _log.info("end -- modifyClarPrivileges");
        } catch (Exception e1) {
            _log.error("Unexpected error: modifyClarPrivileges failed");
            throw e1;
        } finally {
            if (null != privilegeInstances) {
                privilegeInstances.close();
            }
        }
    }

    private CIMObjectPath[] createOrGrowStorageGroup(StorageSystem storage,
            URI exportMaskURI,
            VolumeURIHLU[] volumeURIHLUs,
            List<Initiator> initiatorList,
            List<URI> targetURIList, TaskCompleter completer) throws Exception {
        // TODO - Refactor createOrGrowStorageGroup by moving code for creating an empty storage group
        // to it's own createStorageGroup method which calls exposePaths with null for initiators
        // and targets
        _log.info("{} createOrGrowStorageGroup START...", storage.getSerialNumber());
        try {
            List<CIMObjectPath> paths = new ArrayList<CIMObjectPath>();
            Map<String, CIMObjectPath> existingHwStorageIds = getStorageHardwareIds(storage);

            // Determine if the initiator belongs to a host that already has other initiators registered to it.
            // If so, we need to register that initiator as the same name as the existing initiators. (CTRL-8407)
            if (initiatorList != null) {
                for (Initiator initiator : initiatorList) {
                    updateInitiatorBasedOnPeers(storage, existingHwStorageIds, initiator);
                    if(initiator != null) {
                        _log.info("After updateIntiatorBasedOnPeers : {} {}", initiator.getHostName(), initiator.toString());
                    }
                }
            }

            Multimap<String, String> existingTargets = createStorageHWIDs(storage, existingHwStorageIds, initiatorList);
            if (initiatorList != null && existingTargets.keySet().size() == initiatorList.size()) {
                _log.info(String.format("All the initiators are known to the array and have target endpoints: %s\n." +
                        "These are the targets %s",
                        Joiner.on(',').join(existingTargets.entries()),
                        Joiner.on(',').join(targetURIList)));
            }

            Multimap<URI, Initiator> targetPortsToInitiators = HashMultimap.create();

            //Some of the Initiators are already registered partially on the array based on pre existing zoning
            //COP-16954 We need to  manually register them, the Initiators will have HardwareId created but,
            //The registration is not complete..  createHardwareIDs method above will include those Initiators

            _log.info("Preregistered Target and Initiator ports processing .. Start");
            //Map to hash translations
            HashMap<String, URI> targetPortMap = new HashMap<>();
            for (String initPort : existingTargets.keySet()) {
                _log.info("InitiatorPort {} and TargetStoragePort {}", initPort, existingTargets.get(initPort));
                // IntiatorPort 50012481006B7807 and TargetStoragePort
                // [CLARIION+CKM00115001014+PORT+50:06:01:60:3E:A0:45:79,
                // CLARIION+CKM00115001014+PORT+50:06:01:61:3E:A0:45:79]
                if (!WWNUtility.isValidNoColonWWN(initPort)) {
                    _log.info("InitiatorPort {} is not a valid FC WWN so ignore it", initPort);
                    continue;
                }
                Collection<String> targetPorts = existingTargets.get(initPort);
                for (String targetPortGuid : targetPorts) {
                    URI targetPortURI = targetPortMap.get(targetPortGuid);
                    if (targetPortURI == null) {
                        targetPortURI = getStoragePortURI(targetPortGuid);
                        targetPortMap.put(targetPortGuid, targetPortURI);
                    }
                    Initiator translatedInitiator = getInitiatorForWWN(initPort);
                    _log.info("Calculating Initiator {} and Target {}", translatedInitiator, targetPortURI);
                    if (targetPortURI != null && translatedInitiator != null) {
                        targetPortsToInitiators.put(targetPortURI, translatedInitiator);
                    } else {
                        _log.info("Initiator WWN {} translation was null or targetPort is null {}",
                                initPort, targetPortURI);
                    }
                }
            }
            _log.info("Preregistered Target and Initiator ports processing .. End");

            if (initiatorList == null || initiatorList.isEmpty()) {
                _log.info("InitiatorList is null or Empty so call exposePathsWithVolumesOnly");
                paths.addAll(Arrays.asList(exposePathsWithVolumesOnly(storage, exportMaskURI, volumeURIHLUs)));
            } else {
                ExportMask mask = _dbClient.queryObject(ExportMask.class, exportMaskURI);
                for (Initiator initiator : initiatorList) {
                    // TODO - Ask Tom is there is a reason why we should not do this instead of old code
                    List<URI> tzTargets = ExportUtils.getInitiatorPortsInMask(mask, initiator, _dbClient);
                    _log.info("Calculating Intiator {} and Targets {}", initiator, tzTargets);
                    if (!tzTargets.isEmpty()) {
                        for (URI targetURI : tzTargets) {
                            targetPortsToInitiators.put(targetURI, initiator);
                        }
                    }
                }
                _log.info("Call manuallyRegisterHostInitiators with {} ", targetPortsToInitiators.toString());
                // Register the initiator to target port mappings
                manuallyRegisterHostInitiators(storage, targetPortsToInitiators);

                // CTRL-9086
                // Modify the list of initiators list to match what is being mapped. If there are any initiators
                // that are passed to the ExposePaths call that weren't manuallyRegistered (above), then those
                // initiators will automatically get mapped all the array's StoragePorts.
                //
                // If the targetPortsToInitiators MultiMap is empty, then we will send all the initiators.
                // Presumably, in this situation there are already some existing mappings for the initiators,
                // so would just need to call ExposePaths with those initiators, so that they get added to the
                // StorageGroup
                List<Initiator> initiatorsToExpose = initiatorList;
                if (!targetPortsToInitiators.isEmpty()) {
                    Map<URI, Initiator> uniqueInitiatorMap = new HashMap<>();
                    for (Collection<Initiator> initiatorCollection : targetPortsToInitiators.asMap().values()) {
                        for (Initiator initiator : initiatorCollection) {
                            uniqueInitiatorMap.put(initiator.getId(), initiator);
                        }
                    }
                    initiatorsToExpose = new ArrayList<>(uniqueInitiatorMap.values());
                    // CTRL-10022
                    // If the exportMask needs to use only a subset of initiators, then we should
                    // adjust its initiator list to match. Otherwise, the masking orchestrator will get
                    // confused about the initiators.
                    if (completer instanceof ExportMaskCreateCompleter) {
                        ExportMaskCreateCompleter createCompleter = ((ExportMaskCreateCompleter) completer);
                        List<URI> removedInitiators = new ArrayList<>();
                        List<URI> maskInitiators = StringSetUtil.stringSetToUriList(mask.getInitiators());
                        for (URI maskInitiator : maskInitiators) {
                            if (!uniqueInitiatorMap.containsKey(maskInitiator)) {
                                mask.removeInitiator(maskInitiator);
                                removedInitiators.add(maskInitiator);
                            }
                        }
                        _dbClient.updateAndReindexObject(mask);
                        if (!removedInitiators.isEmpty()) {
                            _log.info(String.format("The following initiators will not be mapped, hence they will be " +
                                    "removed from the initiator list of ExportMask %s (%s): %s",
                                    mask.getMaskName(), mask.getId(), Joiner.on(',').join(removedInitiators)));
                        }
                        // Adjust the completer's initiator list
                        createCompleter.removeInitiators(removedInitiators);
                    }
                }
                _log.info(String.format("ExposePaths will be called with these initiators: %s",
                        Joiner.on(',').join(Collections2.transform(initiatorsToExpose,
                                CommonTransformerFunctions.fctnInitiatorToPortName()))));

                // Add all the initiators to the StorageGroup
                paths.addAll(Arrays.asList(exposePathsWithVolumesAndInitiatorsOnly(storage, exportMaskURI, volumeURIHLUs,
                        initiatorsToExpose)));
            }
            _log.info("{} createOrGrowStorageGroup END...", storage.getSerialNumber());
            return paths.toArray(new CIMObjectPath[paths.size()]);
        } catch (WBEMException e) {
            _log.error("Problem making SMI-S call: ", e);
            throw e;
        } catch (Exception e) {
            _log.error("Unexpected error: createOrGrowStorageGroup failed.", e);
            throw e;
        }
    }

    private CIMObjectPath[] deleteOrShrinkStorageGroup(StorageSystem storage,
            URI exportMaskURI,
            List<URI> volumeURIList,
            List<Initiator> initiatorList) throws Exception {
        _log.debug("{} deleteOrShrinkStorageGroup START...", storage.getSerialNumber());
        try {
            boolean bDeleteStorageGroup = (volumeURIList == null && initiatorList == null);
            CIMArgument[] inArgs = _helper.getDeleteOrShrinkStorageGroupInputArguments(storage,
                    exportMaskURI, volumeURIList, initiatorList, bDeleteStorageGroup);
            CIMArgument[] outArgs = new CIMArgument[5];
            if (bDeleteStorageGroup) {
                _helper.invokeMethod(storage, _cimPath.getControllerConfigSvcPath(storage),
                        "DeleteProtocolController", inArgs, outArgs);
            } else {
                _helper.invokeMethod(storage, _cimPath.getControllerConfigSvcPath(storage),
                        "HidePaths", inArgs, outArgs);
            }
            _log.debug("{} deleteOrShrinkStorageGroup END...", storage.getSerialNumber());
            return _cimPath.getProtocolControllersFromOutputArgs(outArgs);
        } catch (WBEMException e) {
            _log.error("Problem making SMI-S call: ", e);
            throw e;
        } catch (Exception e) {
            _log.error("Unexpected error: deleteOrShrinkStorageGroup failed.", e);
            throw e;
        }
    }

    /**
     * Routine will return a list of SE_StorageHardwareID.ElementName values from the
     * provider managing the specified 'storage' array.
     * 
     * The values will be normalized. That is, in the case of WWN,
     * it will be all uppercase with colons (if any) removed.
     * 
     * @param storage [in] - StorageSystem object
     * @return Map of String(initiator port name) to CIMObjectPath representing the Initiator in SMI-S
     * @throws Exception
     */
    private Map<String, CIMObjectPath> getStorageHardwareIds(StorageSystem storage) throws Exception {
        Map<String, CIMObjectPath> idsMap = new HashMap<>();
        CloseableIterator<CIMInstance> seHwIter = null;
        try {
            // Multiple arrays can be managed by a single SMI-S instance. The SE_StorageHardwareID is
            // global to the provider, so we need to get the SE_StorageHardware_ID object that are
            // associated with a specific array.
            CIMObjectPath hwManagementIDSvcPath = _cimPath.getStorageHardwareIDManagementService(storage);
            seHwIter = _helper.getAssociatorInstances(storage, hwManagementIDSvcPath, null,
                    SmisConstants.CP_SE_STORAGE_HARDWARE_ID, null, null, SmisConstants.PS_ELEMENT_NAME);
            while (seHwIter.hasNext()) {
                CIMInstance instance = seHwIter.next();
                String port = CIMPropertyFactory.getPropertyValue(instance,
                        SmisConstants.CP_ELEMENT_NAME);
                String elementName = Initiator.normalizePort(port);
                idsMap.put(elementName, instance.getObjectPath());
            }
        } finally {
            if (seHwIter != null) {
                seHwIter.close();
            }
        }
        return idsMap;
    }

    /**
     * Looks up the targets that are associated with the initiator (if any).
     * 
     * @param idMgmtSvcPath [in] - Clar_StorageHardwareIDManagementService CIMObjectPath
     * @param storage [in] - StorageSystem object representing the array
     * @param initiator [in] - CIMObjectPath representing initiator to lookup target endpoints (StoragePorts) for
     * @return List or StoragePort URIs that were found to be end points for the initiator
     * @throws Exception
     */
    private List<String> getEMCTargetEndpoints(CIMObjectPath idMgmtSvcPath, StorageSystem storage,
            CIMObjectPath initiator) throws Exception {
        List<String> endpoints = new ArrayList<>();
        try {
            CIMArgument[] input = _helper.getEMCGetConnectedTargetEndpoints(initiator);
            CIMArgument[] output = new CIMArgument[5];
            _helper.invokeMethod(storage, idMgmtSvcPath, SmisConstants.EMC_GET_TARGET_ENDPOINTS, input, output);
            CIMObjectPath[] tePaths = (CIMObjectPath[]) _cimPath.getFromOutputArgs(output, SmisConstants.CP_TARGET_ENDPOINTS);
            if (tePaths != null) {
                for (CIMObjectPath tePath : tePaths) {
                    CIMInstance teInstance = _helper.getInstance(storage, tePath, false, false, SmisConstants.PS_NAME);
                    String tePortNetworkId = CIMPropertyFactory.getPropertyValue(teInstance, SmisConstants.CP_NAME);
                    List<StoragePort> storagePorts =
                            CustomQueryUtility.queryActiveResourcesByAltId(_dbClient, StoragePort.class, "portNetworkId",
                                    WWNUtility.getWWNWithColons(tePortNetworkId));
                    for (StoragePort port : storagePorts) {
                        endpoints.add(port.getNativeGuid());
                    }
                }
            }
            _log.info(String.format("Initiator %s has these target endpoints: [ %s ]", initiator.toString(),
                    Joiner.on(',').join(endpoints)));
        } catch (WBEMException e) {
            // The initiator CIMObjectPath passed into this function was determined by getting
            // the associators to the StorageHardwareIDManagementService. When we call
            // getEMCTargetEndpoints, it is done based on seeing that the initiator is in this
            // associator list. Sometimes, the provider is returing initiator CIMObjectPaths
            // that actually do not exist on the array. In this case, there will be WBEMException
            // thrown when we try to get the targets storage ports using this CIMObject reference.
            // So, here we're trying to protect against this possibility.
            _log.info(String.format("Could not get TargetEndPoints for %s - %s", initiator, e.getMessage()));
        }
        return endpoints;
    }

    /**
     * Method to call EMCManuallyRegisterHostInitiators. This call will bind initiators and target ports
     * on the VNX array.
     * 
     * @param storage [in] - StorageSystem object
     * @param targetPortsToInitiators [in] - Multimap that holds a reference of storage port URI to a list
     *            of Initiator objects. These will be used for the SMI-S calls.
     * @throws Exception
     */
    private void manuallyRegisterHostInitiators(StorageSystem storage,
            Multimap<URI, Initiator> targetPortsToInitiators)
            throws Exception {
        _log.info("manuallyRegisterHostInitiators Start : {}", targetPortsToInitiators);
        for (Map.Entry<URI, Collection<Initiator>> t2is : targetPortsToInitiators.asMap().entrySet()) {
            URI storagePortURI = t2is.getKey();
            Collection<Initiator> initiators = t2is.getValue();
            _log.info("Manually register : Initiators {}. StoragePort {}", initiators, storagePortURI);
            CIMArgument[] inArgs = _helper.getEMCManuallyRegisterHostInitiators(storage, initiators, storagePortURI);
            CIMArgument[] outArgs = new CIMArgument[5];
            _helper.invokeMethod(storage, _cimPath.getStorageHardwareIDManagementService(storage),
                    SmisConstants.EMC_MANUALLY_REGISTER_HOST_INITIATORS, inArgs, outArgs);
        }
        _log.info("manuallyRegisterHostInitiators End : ");
    }

    /**
     * Wrapper function of exposePaths. This one only using the volumes to call exposePaths.
     * 
     * @param storage [in] - StorageSystem object representing the array
     * @param exportMaskURI [in] - ExportMask URI reference
     * @param volumeURIHLUs [in] - Array representing VolumeURIs to HLUs
     * @return An array CIMObjectPaths representing the ProtocolController that was created/updated.
     * @throws Exception
     */
    private CIMObjectPath[] exposePathsWithVolumesOnly(StorageSystem storage,
            URI exportMaskURI,
            VolumeURIHLU[] volumeURIHLUs) throws Exception {
        CIMArgument[] inArgs = _helper.getCreateOrGrowStorageGroupInputArguments(storage,
                exportMaskURI, volumeURIHLUs, null, null);
        CIMArgument[] outArgs = new CIMArgument[5];
        _helper.invokeMethod(storage, _cimPath.getControllerConfigSvcPath(storage),
                "ExposePaths", inArgs, outArgs);
        return _cimPath.getProtocolControllersFromOutputArgs(outArgs);
    }

    /**
     * Wrapper function of exposePaths. This one only using the volumes and initiators to call exposePaths.
     * 
     * @param storage [in] - StorageSystem object representing the array
     * @param exportMaskURI [in] - ExportMask URI reference
     * @param volumeURIHLUs [in] - Array representing VolumeURIs to HLUs
     * @param initiatorList [in] - List of Initiator objects
     * @return An array CIMObjectPaths representing the ProtocolController that was created/updated.
     * @throws Exception
     */
    private CIMObjectPath[] exposePathsWithVolumesAndInitiatorsOnly(StorageSystem storage,
            URI exportMaskURI,
            VolumeURIHLU[] volumeURIHLUs,
            List<Initiator> initiatorList) throws Exception {
        CIMArgument[] inArgs = _helper.getCreateOrGrowStorageGroupInputArguments(storage,
                exportMaskURI, volumeURIHLUs, initiatorList, null);
        CIMArgument[] outArgs = new CIMArgument[5];
        _helper.invokeMethod(storage, _cimPath.getControllerConfigSvcPath(storage),
                "ExposePaths", inArgs, outArgs);
        return _cimPath.getProtocolControllersFromOutputArgs(outArgs);
    }

    /**
     * Updates Auto-tiering policy for the given volumes.
     * 
     * @param storage the storage
     * @param exportMask the export mask
     * @param volumeURIs the volume uris
     * @param newVirtualPool the new virtual pool where policy name can be obtained
     * @param rollback boolean to know if it is called as a roll back step from workflow.
     * @param taskCompleter
     * @throws Exception the exception
     */
    @Override
    public void updateStorageGroupPolicyAndLimits(StorageSystem storage, ExportMask exportMask,
            List<URI> volumeURIs, VirtualPool newVirtualPool, boolean rollback,
            TaskCompleter taskCompleter) throws Exception {

        String message = rollback ? ("updateAutoTieringPolicy" + "(rollback)") : ("updateAutoTieringPolicy");
        _log.info("{} {} START...", storage.getSerialNumber(), message);
        _log.info("{} : volumeURIs: {}", message, volumeURIs);
        try {
            String newPolicyName = ControllerUtils.getFastPolicyNameFromVirtualPool(_dbClient, storage, newVirtualPool);
            _log.info("{} : AutoTieringPolicy: {}", message, newPolicyName);

            List<Volume> volumes = _dbClient.queryObject(Volume.class, volumeURIs);
            /**
             * get tier methodology for policy name
             * volume has tier methodology as '4' when no policy set (START_HIGH_THEN_AUTO_TIER).
             * 
             * For VNX, Policy is set on Volumes during creation.
             */
            int storageTierMethodologyId = DEFAULT_STORAGE_TIER_METHODOLOGY;
            if (!Constants.NONE.equalsIgnoreCase(newPolicyName)) {
                storageTierMethodologyId = getStorageTierMethodologyFromPolicyName(newPolicyName);
            }

            // Build list of native ids
            Set<String> nativeIds = new HashSet<String>();
            for (Volume volume : volumes) {
                nativeIds.add(volume.getNativeId());
            }
            _log.info("Native Ids of Volumes: {}", nativeIds);
            CimConnection connection = _helper.getConnection(storage);
            WBEMClient client = connection.getCimClient();

            // CIMObjectPath replicationSvc = _cimPath.getControllerReplicationSvcPath(storage);
            String[] memberNames = nativeIds.toArray(new String[nativeIds.size()]);
            CIMObjectPath[] volumePaths = _cimPath.getVolumePaths(storage,
                    memberNames);
            CIMProperty[] inArgs = _helper
                    .getModifyStorageTierMethodologyIdInputArguments(storageTierMethodologyId);
            for (CIMObjectPath volumeObject : volumePaths) {
                if (_helper.getVolumeStorageTierMethodologyId(storage, volumeObject) == storageTierMethodologyId) {
                    _log.info(
                            "Current and new Storage Tier Methodology Ids are same '{}'." +
                                    " No need to update it on Volume Object Path {}.",
                            storageTierMethodologyId, volumeObject);
                } else {
                    CIMInstance modifiedSettingInstance = new CIMInstance(volumeObject,
                            inArgs);
                    _log.info(
                            "Updating Storage Tier Methodology ({}) on Volume Object Path {}.",
                            storageTierMethodologyId, volumeObject);
                    client.modifyInstance(modifiedSettingInstance,
                            SmisConstants.PS_EMC_STORAGE_TIER_METHODOLOGY);
                }
            }

            taskCompleter.ready(_dbClient);
        } catch (Exception e) {
            String errMsg = String
                    .format("An error occurred while updating Auto-tiering policy for Volumes %s",
                            volumeURIs);
            _log.error(errMsg, e);
            ServiceError serviceError = DeviceControllerException.errors
                    .jobFailedMsg(errMsg, e);
            taskCompleter.error(_dbClient, serviceError);
        }

        _log.info("{} {} END...", storage.getSerialNumber(), message);
    }

    /**
     * Gets the storage tier methodology from policy name.
     * 
     * @param policyName the policy name
     * @return the storage tier methodology from policy name
     */
    private int getStorageTierMethodologyFromPolicyName(String policyName) {
        int storageTierMethodologyId = 0;
        switch (VnxFastPolicy.valueOf(policyName)) {
            case DEFAULT_NO_MOVEMENT:
                storageTierMethodologyId = Constants.NO_DATA_MOVEMENT;
                break;
            case DEFAULT_AUTOTIER:
                storageTierMethodologyId = Constants.AUTO_TIER;
                break;
            case DEFAULT_HIGHEST_AVAILABLE:
                storageTierMethodologyId = Constants.HIGH_AVAILABLE_TIER;
                break;
            case DEFAULT_LOWEST_AVAILABLE:
                storageTierMethodologyId = Constants.LOW_AVAILABLE_TIER;
                break;
            case DEFAULT_START_HIGH_THEN_AUTOTIER:
                storageTierMethodologyId = Constants.START_HIGH_THEN_AUTO_TIER;
                break;
            default:
                // volume has tier methodology as '4' when no policy set.
                // START_HIGH_THEN_AUTO_TIER is the default and recommended policy
                storageTierMethodologyId = Constants.START_HIGH_THEN_AUTO_TIER;
                break;
        }
        return storageTierMethodologyId;
    }

    @Override
    public Map<URI, Integer> getExportMaskHLUs(StorageSystem storage, ExportMask exportMask) {
        Map<URI, Integer> hlus = Collections.emptyMap();
        try {
            CIMInstance instance = _helper.getLunMaskingProtocolController(storage, exportMask);
            // There's a StorageGroup on the array for the ExportMask and it has userAddedVolumes.
            if (instance != null && exportMask.getUserAddedVolumes() != null) {
                hlus = new HashMap<>();
                WBEMClient client = _helper.getConnection(storage).getCimClient();
                // Get the volume WWN to HLU mapping from the StorageGroup
                Map<String, Integer> discoveredVolumes = _helper.getVolumesFromLunMaskingInstance(client, instance);
                for (String wwn : discoveredVolumes.keySet()) {
                    Integer hlu = discoveredVolumes.get(wwn);
                    if (hlu != null && exportMask.getUserAddedVolumes().containsKey(wwn)) {
                        // Look up the volume URI given the WWN
                        String uriString = exportMask.getUserAddedVolumes().get(wwn);
                        // We have a proper HLU
                        hlus.put(URI.create(uriString), hlu);
                    }
                }
            }
            _log.info(String.format("Retrieved these volumes from ExportMask %s (%s): %s", exportMask.getMaskName(), exportMask.getId(),
                    CommonTransformerFunctions.collectionString(hlus.entrySet())));
        } catch (Exception e) {
            // Log an error, but return an empty list
            _log.error(String.format("Encountered an exception when attempting to get volume to HLU mapping from ExportMask %s",
                    exportMask.getMaskName()), e);
            // We encountered an exception, so let's not return partial data ...
            if (!hlus.isEmpty()) {
                hlus.clear();
            }
        }
        return hlus;
    }


    /**
     * Gets the Storage Port(s) associated with the GUID passed
     * Returns empty list if no storage ports found
     */
    private URI getStoragePortURI(String storagePortGuid) {
        URIQueryResultList uriQueryList = new URIQueryResultList();
        _dbClient.queryByConstraint(AlternateIdConstraint.Factory
                .getStoragePortByNativeGuidConstraint(storagePortGuid), uriQueryList);
        while (uriQueryList.iterator().hasNext()) {
            URI uri = uriQueryList.iterator().next();
            StoragePort storagePort = _dbClient.queryObject(StoragePort.class, uri);
            if (storagePort != null && !storagePort.getInactive()) {
                _log.info("getStoagePortURI called with {} and result {}", storagePortGuid, uri);
                return uri;
            }
        }
        return null;
    }

    /**
     * Gets the Initiator Port associated with the WWN passed
     * Returns null if no Initiators are found
     */
    private Initiator getInitiatorForWWN(String WWN) {
        String formatedWWN = WWNUtility.getWWNWithColons(WWN);
        Initiator init = ExportUtils.getInitiator(formatedWWN, _dbClient);
        _log.info("getInitiatorForWWN called with {} and result {}", WWN + ":" + formatedWWN, init);
        return init;
    }

    /**
     * Returns true if one or all of the 'initiators' are associated to an active VNX StorageGroup on 'storage'.
     *
     * @param storage [IN] - StorageSystem representing the VNX array to check
     * @param initiators [IN] - Initiators to check for association to existing ExportMask(s)
     * @return true iff any of the initiators were found to be associated with some ExportMask on the array.
     */
    private boolean anyInitiatorsAreInAStorageGroup(StorageSystem storage, List<Initiator> initiators) {
        List<String> portNames = new ArrayList<>(Collections2.transform(initiators, CommonTransformerFunctions.fctnInitiatorToPortName()));
        Map<String, Set<URI>> foundMasks = findExportMasks(storage, portNames, false);
        // Return true when there was a match found (i.e., when foundMasks is not empty)
        return !foundMasks.isEmpty();
    }
}
