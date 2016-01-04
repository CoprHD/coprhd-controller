/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.hds.discovery;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.db.client.model.HostInterface;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.StringSetMap;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.ZoneInfo;
import com.emc.storageos.db.client.model.ZoneInfoMap;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedExportMask;
import com.emc.storageos.db.client.util.CommonTransformerFunctions;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.db.client.util.WWNUtility;
import com.emc.storageos.db.client.util.iSCSIUtility;
import com.emc.storageos.hds.HDSConstants;
import com.emc.storageos.hds.api.HDSApiClient;
import com.emc.storageos.hds.api.HDSApiExportManager;
import com.emc.storageos.hds.model.HostStorageDomain;
import com.emc.storageos.hds.model.ISCSIName;
import com.emc.storageos.hds.model.Path;
import com.emc.storageos.hds.model.WorldWideName;
import com.emc.storageos.plugins.AccessProfile;
import com.emc.storageos.util.NetworkUtil;
import com.emc.storageos.volumecontroller.impl.NativeGUIDGenerator;
import com.emc.storageos.volumecontroller.impl.hds.prov.utils.HDSUtils;
import com.emc.storageos.volumecontroller.impl.utils.DiscoveryUtils;
import com.google.common.collect.Collections2;
import com.google.common.collect.Sets;

/**
 * Following are the steps followed:
 * 
 * 1. Fetch HostGroups from Hitachi HiCommand DM Server.
 * 2. Process the initiators of HostGroup and create a UnManagedExportMask.
 * 3. Process the storageports of HostGroup and populate UnManagedExportMask.
 * 4. Process the volumes of HostGroup and populate UnManagedExportMask.
 * 
 * It also does grouping of HostGroups of a known Host with initiators configured on different HostGroups.
 * 
 * @TODO Mark unmanaged Volumes based on VPLEX initiators.
 *
 */
public class HDSUnManagedExportMasksDiscoverer extends AbstractDiscoverer {

    private static final Logger log = LoggerFactory.getLogger(HDSUnManagedExportMasksDiscoverer.class);

    protected static final String UNMANAGED_EXPORT_MASK = "UnManagedExportMask";

    @Override
    public void discover(AccessProfile accessProfile) throws Exception {

        log.info("Discovering HostGroups on system {}", accessProfile.getSystemId());
        HDSApiClient hdsApiClient = hdsApiFactory.getClient(
                HDSUtils.getHDSServerManagementServerInfo(accessProfile),
                accessProfile.getUserName(), accessProfile.getPassword());
        HDSApiExportManager exportManager = hdsApiClient.getHDSApiExportManager();
        StorageSystem storageSystem = dbClient.queryObject(StorageSystem.class,
                accessProfile.getSystemId());
        URIQueryResultList systemPortList = new URIQueryResultList();
        dbClient.queryByConstraint(ContainmentConstraint.Factory.getStorageDeviceStoragePortConstraint(storageSystem.getId()),
                systemPortList);
        String systemObjectId = HDSUtils.getSystemObjectID(storageSystem);
        Map<String, StoragePort> portMap = new HashMap<String, StoragePort>();
        List<HostStorageDomain> hostGroupList = null;
        Set<URI> allMasks = new HashSet<URI>();
        hostGroupList = exportManager.getHostGroupInBatch(systemObjectId, null, null);
        // Process all HostGroups.
        if (null != hostGroupList && !hostGroupList.isEmpty()) {
            processHostGroupsInBatch(hostGroupList, storageSystem, portMap, allMasks);
        } else {
            log.info("No HostGroups found for {}", storageSystem.getSerialNumber());
        }

        // Mark the undiscovered masks to inactive.
        DiscoveryUtils.markInActiveUnManagedExportMask(storageSystem.getId(), allMasks, dbClient, partitionManager);
        log.info("Processed {} unmanaged exportmasks for system {}", allMasks.size(), storageSystem.getId());

    }

    /**
     * Process each HostGroup and create an equivalent UnManagedExportMask in ViPR.
     * 
     * @param hostGroupList
     * @param storageSystem
     * @param storagePortsItr
     * @param partitionManager
     */
    private void processHostGroupsInBatch(List<HostStorageDomain> hostGroupList, StorageSystem storageSystem,
            Map<String, StoragePort> portMap, Set<URI> allMasks) {
        List<UnManagedExportMask> newMasks = new ArrayList<UnManagedExportMask>();
        Map<String, UnManagedExportMask> initiatorMasks = new HashMap<String, UnManagedExportMask>();
        List<UnManagedExportMask> updateMasks = new ArrayList<UnManagedExportMask>();
        for (HostStorageDomain hsd : hostGroupList) {
            List<StoragePort> matchedPorts = new ArrayList<StoragePort>();
            List<Initiator> matchedInitiators = new ArrayList<Initiator>();
            try {
                if (null != hsd.getWwnList() && !hsd.getWwnList().isEmpty()
                        && null != hsd.getIscsiList() && !hsd.getIscsiList().isEmpty()) {
                    log.info("Skipping the HSD {} as it has both FC & ISCSI ports", hsd.getObjectID());
                    continue;
                }
                if (hsd.getWwnList().isEmpty() && hsd.getIscsiList().isEmpty()) {
                    log.info("No initiators found in the Host Group: {}. Hence skipping.", hsd.getObjectID());
                    continue;
                }
                if (hsd.getPathList().isEmpty()) {
                    log.info("No volumes found in the HostGroup: {}. Hence skipping.", hsd.getObjectID());
                    continue;
                }

                UnManagedExportMask umExportMask = null;
                if (null != hsd.getWwnList() && !hsd.getWwnList().isEmpty()) {
                    // Process FC initiators configured on HostGroup.
                    umExportMask = processFCInitiators(storageSystem, hsd, matchedInitiators, newMasks,
                            updateMasks, allMasks, initiatorMasks);
                } else if (null != hsd.getIscsiList() && !hsd.getIscsiList().isEmpty()) {
                    // Process SCSI initiators configured on HostGroup.
                    umExportMask = processIscsiInitiators(storageSystem, hsd, matchedInitiators, newMasks, updateMasks,
                            allMasks, initiatorMasks);
                }
                if (null == umExportMask) {
                    log.warn("Not able to create/find unmanaged exportmask for host group {}", hsd.getObjectID());
                    continue;
                }
                // Process StoragePorts configured on HostGroup.
                processStoragePorts(storageSystem, hsd.getPortID(), portMap, umExportMask, matchedPorts);

                // Process all volumes configured on HostGroup.
                if (null != hsd.getPathList() && !hsd.getPathList().isEmpty()) {
                    processVolumes(storageSystem, umExportMask, hsd.getPathList());
                }
                umExportMask.setLabel(hsd.getNickname());
                umExportMask.setMaskName(hsd.getNickname());
                umExportMask.setMaskingViewPath(hsd.getNickname());
                umExportMask.setStorageSystemUri(storageSystem.getId());
                // Updates the zoning Map for the known initiators part of a Network.
                updateZoningMap(umExportMask, matchedInitiators, matchedPorts, hsd.getObjectID(), hsd.getPathList());
                if (!newMasks.isEmpty() && newMasks.size() >= BATCH_SIZE) {
                    partitionManager.insertInBatches(newMasks, BATCH_SIZE, dbClient, UNMANAGED_EXPORT_MASK);
                    newMasks.clear();
                }
                if (!updateMasks.isEmpty() && updateMasks.size() >= BATCH_SIZE) {
                    partitionManager.updateInBatches(updateMasks, BATCH_SIZE, dbClient, UNMANAGED_EXPORT_MASK);
                    updateMasks.clear();
                }
            } catch (Exception ex) {
                log.error("Exception occurred while processing the HostGroup: {}. continuing with next.", hsd.getObjectID(), ex);
            }
        }

        // persist the remaining masks.
        if (!newMasks.isEmpty() && newMasks.size() > 0) {
            partitionManager.insertInBatches(newMasks, BATCH_SIZE, dbClient, UNMANAGED_EXPORT_MASK);
            newMasks.clear();
        }
        if (!updateMasks.isEmpty() && updateMasks.size() > 0) {
            partitionManager.updateInBatches(updateMasks, BATCH_SIZE, dbClient, UNMANAGED_EXPORT_MASK);
            updateMasks.clear();
        }
    }

    /**
     * Updates the zoning information in the unManagedExportMask.
     * 
     * @param mask
     * @param initiators
     * @param storagePorts
     */
    private void updateZoningMap(UnManagedExportMask mask, List<Initiator> initiators, List<StoragePort> storagePorts, String hsdId,
            List<Path> pathList) {
        try {
            ZoneInfoMap zoningMap = networkController.getInitiatorsZoneInfoMap(initiators, storagePorts);
            if (null != zoningMap && !zoningMap.isEmpty()) {
                log.info("Found zones: {} ", zoningMap.values());
                StringSetMap deviceDataMap = mask.getDeviceDataMap();
                if (null == deviceDataMap) {
                    deviceDataMap = new StringSetMap();
                }
                for (Path path : pathList) {
                    updateITLMapForVolume(path.getDevNum(), zoningMap.values(), deviceDataMap);
                }
                mask.addZoningMap(zoningMap);
                mask.addDeviceDataMap(deviceDataMap);
            }
        } catch (Exception ex) {
            log.error("Failed to get the zoning map for mask {}", mask.getMaskName());
            mask.setZoningMap(null);
        }
    }

    /**
     * Update ITL Info in the DeviceDataMap.
     * 
     * @param devNum
     * @param values
     * @param deviceDataMap
     */
    private void updateITLMapForVolume(String devNum, Collection<ZoneInfo> values, StringSetMap deviceDataMap) {
        StringSet volumeITs = deviceDataMap.get(devNum);
        if (null == volumeITs) {
            volumeITs = new StringSet();
        }
        volumeITs.addAll(extractITInfoFromZoneMap(values));
        deviceDataMap.put(devNum, volumeITs);
    }

    /**
     * Extracts the Initiator Target information from the ZoningMap.
     * 
     * @param zoneInfoList
     * @return
     */
    private StringSet extractITInfoFromZoneMap(Collection<ZoneInfo> zoneInfoList) {
        StringSet itInfoSet = new StringSet();
        for (ZoneInfo zoneInfo : zoneInfoList) {
            String iniWwn = zoneInfo.getInitiatorWwn().replace(HDSConstants.COLON, HDSConstants.EMPTY_STR);
            String targetWwn = zoneInfo.getPortWwn().replace(HDSConstants.COLON, HDSConstants.EMPTY_STR);
            itInfoSet.add(iniWwn + HDSConstants.UNDERSCORE_OPERATOR + targetWwn);
        }
        return itInfoSet;
    }

    /**
     * Process the Host Group storage ports and update the details in UnManagedExportMask.
     * 
     * @param storageSystem - storageSystem
     * @param portID - Port In which HostGroup is configured.
     * @param storagePortMap - StoragePorts info [port NativeGuid => StoragePort]
     * @param umExportMask - UnmanagedExportMask to update.
     * @param matchedPorts - MatchedPorts to fetch zoning information.
     */
    private void processStoragePorts(StorageSystem storageSystem, String portID, Map<String, StoragePort> storagePortMap,
            UnManagedExportMask umExportMask, List<StoragePort> matchedPorts) {
        String portNativeGuid = HDSUtils.getPortNativeGuid(storageSystem, portID);
        StoragePort sport = null;
        if (storagePortMap.containsKey(portNativeGuid)) {
            sport = storagePortMap.get(portNativeGuid);
        } else {
            URIQueryResultList queryResult = new URIQueryResultList();
            this.dbClient
                    .queryByConstraint(AlternateIdConstraint.Factory.getStoragePortByNativeGuidConstraint(portNativeGuid), queryResult);
            while (queryResult.iterator().hasNext()) {
                sport = this.dbClient.queryObject(StoragePort.class, queryResult.iterator().next());
                storagePortMap.put(portNativeGuid, sport);
            }
        }

        StoragePort knownStoragePort = NetworkUtil.getStoragePort(sport.getPortNetworkId(), dbClient);

        if (null != knownStoragePort) {
            log.info("Found a matching storage port {} in ViPR ", knownStoragePort.getLabel());
            umExportMask.getKnownStoragePortUris().add(knownStoragePort.getId().toString());
            matchedPorts.add(knownStoragePort);
        } else {
            log.info("No storage port in ViPR found matching portNetworkId {}", sport.getPortNetworkId());
            umExportMask.getUnmanagedStoragePortNetworkIds().add(sport.getPortNetworkId());
        }
    }

    /**
     * Process all Volumes configured on HostGroup & populate in UnManagedExportMask.
     *
     * @param storageSystem - StorageSystem
     * @param umExportMask - UnManagedExportMask to update.
     * @param pathList - Volumes List.
     */
    private void processVolumes(StorageSystem storageSystem, UnManagedExportMask umExportMask,
            List<Path> pathList) {
        Set<String> knownVolumeSet = new HashSet<String>();
        for (Path path : pathList) {
            String volumeNativeGuid = NativeGUIDGenerator.generateNativeGuidForVolumeOrBlockSnapShot(
                    storageSystem.getNativeGuid(), String.valueOf(path.getDevNum()));
            URIQueryResultList result = new URIQueryResultList();
            dbClient.queryByConstraint(AlternateIdConstraint.Factory.getVolumeNativeGuidConstraint(volumeNativeGuid), result);

            Volume volume = null;
            Iterator<URI> volumes = result.iterator();
            if (volumes.hasNext()) {
                volume = dbClient.queryObject(Volume.class, volumes.next());
                if (null != volume) {
                    knownVolumeSet.add(volume.getId().toString());
                }
            }

            String umvNativeGuid = NativeGUIDGenerator.generateNativeGuidForPreExistingVolume(storageSystem.getNativeGuid(),
                    path.getDevNum());
            Set<UnManagedExportMask> umvMasks = volumeMasks.get(umvNativeGuid);
            if (null == umvMasks) {
                umvMasks = new HashSet<UnManagedExportMask>();
            }
            umvMasks.add(umExportMask);
            volumeMasks.put(umvNativeGuid, umvMasks);
        }

        if (!knownVolumeSet.isEmpty()) {
            umExportMask.getKnownVolumeUris().addAll(knownVolumeSet);
        }
    }

    /**
     * Process the iSCSI initiators.
     * 
     * @param system - storage system
     * @param hsd - HostGroup details to process
     * @param matchedInitiators - MatchedInitiators to get zoning information.
     * @param newMasks - New Masks to create.
     * @param updateMasks - Masks to update.
     * @param allMasks - allMasks for bookkeeping.
     * @param currentProcessinginiMasks - current processing masks.
     * @return
     */
    private UnManagedExportMask processIscsiInitiators(StorageSystem system,
            HostStorageDomain hsd, List<Initiator> matchedInitiators,
            List<UnManagedExportMask> newMasks, List<UnManagedExportMask> updateMasks, Set<URI> allMasks,
            Map<String, UnManagedExportMask> currentProcessinginiMasks) {
        UnManagedExportMask umExportMask = null;
        StringSet unknownIniSet = new StringSet();
        List<ISCSIName> iscsiList = hsd.getIscsiList();
        Map<URI, Set<String>> hostInitiatorsMap = new HashMap<URI, Set<String>>();
        for (ISCSIName scsiName : iscsiList) {
            String scsiInitiatorName = scsiName.getiSCSIName();
            if (iSCSIUtility.isValidIQNPortName(scsiInitiatorName) || iSCSIUtility
                    .isValidEUIPortName(scsiInitiatorName)) {
                log.info("Found a valid scsi initiator: {}", scsiInitiatorName);
            } else {
                log.warn("Found an invalid scsi initiator: {}", scsiInitiatorName);
                continue;
            }
            Initiator knownInitiator = NetworkUtil.getInitiator(scsiInitiatorName, dbClient);
            if (null != knownInitiator) {

                URI hostURI = knownInitiator.getHost();
                if (!NullColumnValueGetter.isNullURI(hostURI)) {
                    Set<String> hostInitiators = hostInitiatorsMap.get(hostURI);
                    if (null == hostInitiators) {
                        hostInitiators = new HashSet<String>();
                    }
                    if (HostInterface.Protocol.iSCSI.toString().equals(knownInitiator.getProtocol())) {
                        hostInitiators.add(knownInitiator.getInitiatorPort());
                        hostInitiatorsMap.put(hostURI, hostInitiators);
                        matchedInitiators.add(knownInitiator);
                    }
                } else {
                    log.info("Unknown Host found for initiator {} ", scsiInitiatorName);
                    unknownIniSet.add(scsiInitiatorName);
                }

            } else {
                log.info("no hosts in ViPR found configured for initiator {} ", scsiInitiatorName);
                unknownIniSet.add(scsiInitiatorName);
            }
            currentProcessinginiMasks.put(scsiInitiatorName, umExportMask);
        }
        if (!hostInitiatorsMap.isEmpty() && !unknownIniSet.isEmpty()) {
            log.warn("Skipping the HostGroup {} as it has both known & unknown initiators.", hsd.getObjectID());
            return null;
        }

        return processComputeElementInitiators(system, hostInitiatorsMap, unknownIniSet, currentProcessinginiMasks, matchedInitiators,
                newMasks,
                updateMasks, allMasks);
    }

    /**
     * Process FC initiators information.
     * 
     * @param system - storagesystem details
     * @param hsd - HostGroup to process.
     * @param matchedInitiators - Matched initiators to get zoning information.
     * @param newMasks - New masks to create.
     * @param updateMasks - masks to update.
     * @param allMasks - all masks for bookkeeping purpose.
     * @param currentProcessinginiMasks - Current Processing masks.
     * @return
     */
    private UnManagedExportMask processFCInitiators(StorageSystem system, HostStorageDomain hsd,
            List<Initiator> matchedInitiators, List<UnManagedExportMask> newMasks,
            List<UnManagedExportMask> updateMasks, Set<URI> allMasks,
            Map<String, UnManagedExportMask> currentProcessinginiMasks) {
        StringSet unknownIniWwnSet = new StringSet();
        Map<URI, Set<String>> computeElementInisMap = new HashMap<URI, Set<String>>();
        List<WorldWideName> wwnList = hsd.getWwnList();
        for (WorldWideName wwn : wwnList) {
            String initiatorWwn = wwn.getWwn().replace(HDSConstants.DOT_OPERATOR, HDSConstants.EMPTY_STR);
            if (WWNUtility.isValidNoColonWWN(initiatorWwn)) {
                initiatorWwn = WWNUtility.getWWNWithColons(initiatorWwn.toUpperCase());
            } else {
                log.warn("Found an invalid initiator wwn: {}", wwn.getWwn());
                continue;
            }
            Initiator knownInitiator = NetworkUtil.getInitiator(initiatorWwn, dbClient);
            if (null != knownInitiator) {
                log.info("Found an initiator in ViPR on host {}", knownInitiator.getHostName());

                URI hostURI = knownInitiator.getHost();
                if (!NullColumnValueGetter.isNullURI(hostURI)) {
                    Set<String> hostInitiators = computeElementInisMap.get(hostURI);
                    if (null == hostInitiators) {
                        hostInitiators = new HashSet<String>();
                    }
                    if (HostInterface.Protocol.FC.toString().equals(knownInitiator.getProtocol())) {
                        hostInitiators.add(knownInitiator.getInitiatorPort());
                        computeElementInisMap.put(hostURI, hostInitiators);
                        matchedInitiators.add(knownInitiator);
                    }

                } else {
                    log.info("Unknown Host found for initiator {} ", initiatorWwn);
                    unknownIniWwnSet.add(initiatorWwn);
                }

            } else {
                log.info("No host in ViPR found configured for initiator {} ", initiatorWwn);
                unknownIniWwnSet.add(initiatorWwn);
            }
        }

        if (!computeElementInisMap.isEmpty() && !unknownIniWwnSet.isEmpty()) {
            log.warn("Skipping the HostGroup {} as it has both known & unknown initiators.", hsd.getObjectID());
            return null;
        }

        return processComputeElementInitiators(system, computeElementInisMap, unknownIniWwnSet, currentProcessinginiMasks,
                matchedInitiators,
                newMasks,
                updateMasks, allMasks);
    }

    /**
     * Process the ComputerElement initiators to find a matching UnManagedExportMask.
     * 
     * @param system
     * @param computeElementInitiatorsMap
     * @param unknownIniSet
     * @param initiatorMasks
     * @param matchedInitiators
     * @param newMasks
     * @param updateMasks
     * @param allMasks
     * @return
     */
    private UnManagedExportMask processComputeElementInitiators(StorageSystem system, Map<URI, Set<String>> computeElementInitiatorsMap,
            StringSet unknownIniSet, Map<String, UnManagedExportMask> initiatorMasks, List<Initiator> matchedInitiators,
            List<UnManagedExportMask> newMasks, List<UnManagedExportMask> updateMasks, Set<URI> allMasks) {
        UnManagedExportMask umExportMask = null;
        String exportType = ExportGroup.ExportGroupType.Host.toString();
        if (computeElementInitiatorsMap.keySet().size() > 1) {
            // process Cluster initiators
            exportType = ExportGroup.ExportGroupType.Cluster.toString();
        }

        umExportMask = processHostKnownInitiators(computeElementInitiatorsMap, system, initiatorMasks, exportType);
        if (null != umExportMask) {
            // If the mask is processing for first time then cleanup the mask.
            // This is required during multiple rediscoveries to update the latest information.
            if (!allMasks.contains(umExportMask.getId())) {
                cleanupUnManagedExportMaskInfo(umExportMask);
            }
            updateCurrentInitiatorMasks(umExportMask, matchedInitiators, initiatorMasks);
            umExportMask.setExportType(exportType);
            Set<String> initiatorWwns = new HashSet<String>(Collections2.transform(matchedInitiators,
                    CommonTransformerFunctions.fctnInitiatorToPortNameWONormalize()));
            log.debug("initiator names to add: {}", initiatorWwns);
            umExportMask.addKnownInitiatorNetworkIds(initiatorWwns);

            Set<String> initiatorIds = new HashSet<String>(Collections2.transform(matchedInitiators,
                    CommonTransformerFunctions.fctnDataObjectToURIString()));
            log.debug("initiators to add: {}", initiatorIds);
            umExportMask.addKnownInitiatorUris(initiatorIds);
        }
        if (!unknownIniSet.isEmpty()) {
            // process the unknown initiators. We cann't determine the exportType as there could be initiators from different hosts.
            // So treat them as one Host & group into one UEM. When the hosts are discovered set the right exportType.
            umExportMask = processUnknownInitiators(system, unknownIniSet, initiatorMasks, exportType);
            // If the mask is processing for first time then cleanup the mask.
            // This is required during multiple rediscoveries to update the latest information.
            if (!allMasks.contains(umExportMask.getId())) {
                cleanupUnManagedExportMaskInfo(umExportMask);
            }
            updateCurrentInitiatorMasksForUnknownInits(umExportMask, unknownIniSet, initiatorMasks);
            umExportMask.setUnmanagedInitiatorNetworkIds(unknownIniSet);
        }

        if (null == umExportMask.getCreationTime()) {
            newMasks.add(umExportMask);
        } else {
            updateMasks.add(umExportMask);
        }

        allMasks.add(umExportMask.getId());
        return umExportMask;
    }

    /**
     * Updates the current ProcessMasks for unknown initiators to reuse the masks in the current request.
     * 
     * @param umExportMask - UnManagedExportMask.
     * @param unknownIniWwnSet -
     * @param currentProcessingMasks
     */
    private void updateCurrentInitiatorMasksForUnknownInits(UnManagedExportMask umExportMask, StringSet unknownIniWwnSet,
            Map<String, UnManagedExportMask> currentProcessingMasks) {
        for (String unknownInitiator : unknownIniWwnSet) {
            currentProcessingMasks.put(unknownInitiator, umExportMask);
        }
    }

    /**
     * Update the current processMasks
     * 
     * @param uem
     * @param initiators
     * @param initiatorMasks
     */
    private void updateCurrentInitiatorMasks(UnManagedExportMask uem, List<Initiator> initiators,
            Map<String, UnManagedExportMask> currentProcessingMasks) {
        if (null != initiators && !initiators.isEmpty()) {
            for (Initiator initiator : initiators) {
                currentProcessingMasks.put(initiator.getInitiatorPort(), uem);
            }
        }
    }

    /**
     * Process the known initiators to find the UnManagedExportMask.
     * 
     * @param hostInitiatorsMap - Process for each Host
     * @param system
     * @param initiatorMasks
     * @param exportType
     * @return
     */
    private UnManagedExportMask processHostKnownInitiators(Map<URI, Set<String>> hostInitiatorsMap, StorageSystem system,
            Map<String, UnManagedExportMask> initiatorMasks, String exportType) {
        UnManagedExportMask umExportMask = null;
        for (Entry<URI, Set<String>> hostEntrySet : hostInitiatorsMap.entrySet()) {
            Set<String> hostInitiatorWwns = getHostInitiators(hostEntrySet.getKey());
            if (null != hostInitiatorWwns && !hostInitiatorWwns.isEmpty()) {
                umExportMask = findSuitableUemForInitiators(system, hostInitiatorWwns, initiatorMasks, exportType);
                if (null != umExportMask) {
                    log.info("Found a mask for the host: {}", hostEntrySet.getKey());
                    break;
                }
            }
        }
        return umExportMask;
    }

    /**
     * Get all initiators of the given Host.
     * 
     * @param hostURI
     * @return
     */
    private Set<String> getHostInitiators(URI hostURI) {
        Set<String> hostInitiators = new HashSet<String>();
        if (!NullColumnValueGetter.isNullURI(hostURI)) {
            Host host = dbClient.queryObject(Host.class, hostURI);
            List<Initiator> allHostInitiators =
                    CustomQueryUtility.queryActiveResourcesByConstraint(dbClient, Initiator.class,
                            ContainmentConstraint.Factory.getContainedObjectsConstraint(host.getId(), Initiator.class, "host"));
            if (null != allHostInitiators && !allHostInitiators.isEmpty()) {
                for (Initiator initiator : allHostInitiators) {
                    hostInitiators.add(initiator.getInitiatorPort());
                }
            }

        }
        return hostInitiators;
    }

    /**
     * Process the unknown initiators to find the UnManagedExportMask details.
     * 
     * @param system
     * @param hostInitiatorWwns
     * @param initiatorMasks
     * @param exportType
     * @return
     */
    private UnManagedExportMask processUnknownInitiators(StorageSystem system, Set<String> hostInitiatorWwns,
            Map<String, UnManagedExportMask> initiatorMasks, String exportType) {
        // check if the initiators has any exportmask in the current processed list.
        UnManagedExportMask umExportMask = checkCurrentProcessedMasks(hostInitiatorWwns, initiatorMasks, exportType);
        // Now check from database.
        if (null == umExportMask) {
            umExportMask = fetchUmexportMaskFromDBForUnknownInis(system, hostInitiatorWwns, exportType);
        }
        if (null == umExportMask) {
            // If we still didn't find unmanagedExportMask from DB, create a new mask.
            umExportMask = getNewUnManagedExportMask(system);
        }
        return umExportMask;

    }

    /**
     * Fetch the UnManagedExportMask for the unknown initiators from the Database.
     * 
     * @param system
     * @param hostInitiatorWwns
     * @param exportType
     * @return
     */
    private UnManagedExportMask
            fetchUmexportMaskFromDBForUnknownInis(StorageSystem system, Set<String> hostInitiatorWwns, String exportType) {
        URIQueryResultList unmanagedMasksResult = new URIQueryResultList();
        dbClient.queryByConstraint(ContainmentConstraint.Factory.getStorageSystemUnManagedExportMaskConstraint(system.getId()),
                unmanagedMasksResult);
        UnManagedExportMask unManagedExportMask = null;
        while (unmanagedMasksResult.iterator().hasNext()) {
            unManagedExportMask = dbClient.queryObject(UnManagedExportMask.class, unmanagedMasksResult.iterator().next());
            if (null != unManagedExportMask && !unManagedExportMask.getInactive()) {
                StringSet uemInitiators = unManagedExportMask.getUnmanagedInitiatorNetworkIds();
                Set<String> commonSet = Sets.intersection(uemInitiators, hostInitiatorWwns);
                if (!commonSet.isEmpty()) {
                    return unManagedExportMask;
                }
            }
        }
        return null;
    }

    /**
     * Find the UnManagedExportMask for the given initiators from the current processing masks or from database.
     * 
     * @param system
     * @param hostInitiatorWwns
     * @param initiatorMasks
     * @param exportType
     * @return
     */
    private UnManagedExportMask findSuitableUemForInitiators(StorageSystem system, Set<String> hostInitiatorWwns,
            Map<String, UnManagedExportMask> initiatorMasks, String exportType) {
        // check if the initiators has any exportmask in the current processed list.
        UnManagedExportMask umExportMask = checkCurrentProcessedMasks(hostInitiatorWwns, initiatorMasks, exportType);
        // Now check from database.
        if (null == umExportMask) {
            umExportMask = fetchUmexportMaskFromDB(hostInitiatorWwns, exportType);
        }
        if (null == umExportMask) {
            // If we still didn't find unmanagedExportMask from DB, create a new mask.
            umExportMask = getNewUnManagedExportMask(system);
        }
        return umExportMask;

    }

    /**
     * Fetches the UnManagedExportMask for the given initiators from the db.
     * 
     * @param hostInitiatorWwns
     * @param exportType
     * @return
     */
    private UnManagedExportMask fetchUmexportMaskFromDB(Set<String> hostInitiatorWwns, String exportType) {
        UnManagedExportMask umExportMask = null;
        for (String initiatorWwn : hostInitiatorWwns) {
            URIQueryResultList uemInitiatorMaskList = new URIQueryResultList();
            dbClient.queryByConstraint(AlternateIdConstraint.Factory.getUnManagedExportMaskKnownInitiatorConstraint(initiatorWwn),
                    uemInitiatorMaskList);
            while (uemInitiatorMaskList.iterator().hasNext()) {
                UnManagedExportMask tmpUmExportMask = dbClient.queryObject(UnManagedExportMask.class, uemInitiatorMaskList.iterator()
                        .next());
                if (null != tmpUmExportMask && !tmpUmExportMask.getInactive()) {
                    // For unknown hosts, we don't set the exportType. Hence only matching initiators match.
                    if (null == tmpUmExportMask.getExportType() || exportType.equalsIgnoreCase(tmpUmExportMask.getExportType())) {
                        umExportMask = tmpUmExportMask;
                        break;
                    }
                }
            }
        }
        return umExportMask;
    }

    /**
     * Process the current processing masks to find if there is any UnManagedExportMask to resuse.
     * 
     * @param hostInitiatorWwns
     * @param initiatorMasks
     * @param exportType
     * @return
     */
    private UnManagedExportMask checkCurrentProcessedMasks(Set<String> hostInitiatorWwns, Map<String, UnManagedExportMask> initiatorMasks,
            String exportType) {
        UnManagedExportMask umExportMask = null;
        if (null == initiatorMasks || initiatorMasks.isEmpty()) {
            return umExportMask;
        }
        for (String initiatorWwn : hostInitiatorWwns) {
            if (initiatorMasks.containsKey(initiatorWwn)) {
                UnManagedExportMask tmpUmExportMask = initiatorMasks.get(initiatorWwn);
                // For unknown hosts, we don't set the exportType. Hence only matching initiators match.
                if (null == tmpUmExportMask.getExportType() || exportType.equalsIgnoreCase(tmpUmExportMask.getExportType())) {
                    umExportMask = tmpUmExportMask;
                    break;
                }
            }
        }
        return umExportMask;
    }

    /**
     * Clear the unmanaged ExportMask information during rediscoveries.
     * 
     * @param umExportMask
     */
    private void cleanupUnManagedExportMaskInfo(UnManagedExportMask umExportMask) {
        if (null != umExportMask && !umExportMask.getInactive()) {
            if (null != umExportMask.getKnownInitiatorUris()) {
                umExportMask.getKnownInitiatorUris().clear();
            }
            if (null != umExportMask.getKnownInitiatorNetworkIds()) {
                umExportMask.getKnownInitiatorNetworkIds().clear();
            }
            if (null != umExportMask.getKnownStoragePortUris()) {
                umExportMask.getKnownStoragePortUris().clear();
            }
            if (null != umExportMask.getKnownVolumeUris()) {
                umExportMask.getKnownVolumeUris().clear();
            }
            if (null != umExportMask.getUnmanagedInitiatorNetworkIds()) {
                umExportMask.getUnmanagedInitiatorNetworkIds().clear();
            }
            if (null != umExportMask.getUnmanagedStoragePortNetworkIds()) {
                umExportMask.getUnmanagedStoragePortNetworkIds().clear();
            }
            if (null != umExportMask.getUnmanagedVolumeUris()) {
                umExportMask.getUnmanagedVolumeUris().clear();
            }
            if (null != umExportMask.getZoningMap()) {
                umExportMask.getZoningMap().clear();
            }
            if (null != umExportMask.getDeviceDataMap()) {
                umExportMask.getDeviceDataMap().clear();
            }
        }

    }

    /**
     * Return new initialized UnManagedExportMask.
     * 
     * @return
     */
    private UnManagedExportMask getNewUnManagedExportMask(StorageSystem storageSystem) {
        UnManagedExportMask uem = new UnManagedExportMask();
        uem.setId(URIUtil.createId(UnManagedExportMask.class));
        uem.setStorageSystemUri(storageSystem.getId());
        return uem;
    }
}
