/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.hds.discovery;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
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

/**
 * 
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
        List<UnManagedExportMask> updateMasks = new ArrayList<UnManagedExportMask>();
        for (HostStorageDomain hsd : hostGroupList) {
            Set<String> knownPortSet = new HashSet<String>();
            Set<String> knownVolumeSet = new HashSet<String>();
            List<StoragePort> matchedPorts = new ArrayList<StoragePort>();
            Set<String> knownIniSet = new HashSet<String>();
            Set<String> knownIniWwnSet = new HashSet<String>();
            List<Initiator> matchedInitiators = new ArrayList<Initiator>();
            try {
                List<WorldWideName> wwnList = hsd.getWwnList();
                List<ISCSIName> iscsiList = hsd.getIscsiList();
                List<Path> pathList = hsd.getPathList();
                if (null != wwnList && !wwnList.isEmpty() && null != iscsiList && !iscsiList.isEmpty()) {
                    log.info("Skipping the HSD {} as it has both FC & ISCSI ports", hsd.getObjectID());
                    continue;
                }
                if (wwnList.isEmpty() && iscsiList.isEmpty()) {
                    log.info("No initiators found in the Host Group: {}. Hence skipping.", hsd.getObjectID());
                    continue;
                }
                if (pathList.isEmpty()) {
                    log.info("No volumes found in the HostGroup: {}. Hence skipping.", hsd.getObjectID());
                    continue;
                }

                UnManagedExportMask umExportMask = null;
                if (null != wwnList && !wwnList.isEmpty()) {
                    umExportMask = processFCInitiators(storageSystem, hsd, wwnList, matchedInitiators, knownIniSet, knownIniWwnSet,
                            newMasks,
                            updateMasks, allMasks);
                } else if (null != iscsiList && !iscsiList.isEmpty()) {
                    umExportMask = processIscsiInitiators(storageSystem, hsd, iscsiList, matchedInitiators, knownIniSet, knownIniWwnSet,
                            newMasks, updateMasks, allMasks);
                }
                processStoragePorts(storageSystem, hsd.getPortID(), portMap, umExportMask, matchedPorts, knownPortSet);

                if (null != pathList && !pathList.isEmpty()) {
                    processVolumes(storageSystem, umExportMask, knownVolumeSet, pathList);
                }
                umExportMask.setLabel(hsd.getNickname());
                umExportMask.setMaskName(hsd.getNickname());
                umExportMask.setMaskingViewPath(hsd.getNickname());
                umExportMask.setStorageSystemUri(storageSystem.getId());
                updateZoningMap(umExportMask, matchedInitiators, matchedPorts, hsd.getObjectID(), pathList);
                updateMaskInfo(umExportMask, knownIniSet, knownIniWwnSet, knownVolumeSet, knownPortSet);
                if (!newMasks.isEmpty() && newMasks.size() > BATCH_SIZE) {
                    partitionManager.insertInBatches(newMasks, BATCH_SIZE, dbClient, UNMANAGED_EXPORT_MASK);
                    newMasks.clear();
                }
                if (!updateMasks.isEmpty() && updateMasks.size() > BATCH_SIZE) {
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
     * 
     * @param umExportMask
     * @param knownIniSet
     * @param knownIniWwnSet
     * @param knownVolumeSet
     * @param knownPortSet
     */
    private void updateMaskInfo(UnManagedExportMask umExportMask, Set<String> knownIniSet, Set<String> knownIniWwnSet,
            Set<String> knownVolumeSet, Set<String> knownPortSet) {
        if (null != knownIniSet && !knownIniSet.isEmpty()) {
            log.debug("Known Initiator uris to add: {}", knownIniSet);
            umExportMask.getKnownInitiatorUris().replace(knownIniSet);
        } else {
            umExportMask.getKnownInitiatorUris().clear();
        }
        if (null != knownIniWwnSet && !knownIniWwnSet.isEmpty()) {
            log.debug("Known Initiator wwn to add: {}", knownIniWwnSet);
            umExportMask.getKnownInitiatorNetworkIds().replace(knownIniWwnSet);
        } else {
            umExportMask.getKnownInitiatorNetworkIds().clear();
        }
        if (null != knownVolumeSet && !knownVolumeSet.isEmpty()) {
            log.debug("Known volumes to add: {}", knownVolumeSet);
            umExportMask.getKnownVolumeUris().replace(knownVolumeSet);
        } else {
            umExportMask.getKnownVolumeUris().clear();
        }
        if (null != knownPortSet && !knownPortSet.isEmpty()) {
            log.debug("Known ports to add: {}", knownPortSet);
            umExportMask.getKnownStoragePortUris().replace(knownPortSet);
        } else {
            umExportMask.getKnownStoragePortUris().clear();
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
            StringSetMap itlMap = new StringSetMap();
            StringSet itlSet = new StringSet();
            for (ZoneInfo zoneInfo : zoningMap.values()) {
                log.info("Found zone: {} for initiator {} and port {}", new Object[] { zoneInfo.getZoneName(),
                        zoneInfo.getInitiatorWwn(), zoneInfo.getPortWwn() });
                for (Path path : pathList) {
                    StringBuffer itl = new StringBuffer(zoneInfo.getInitiatorWwn());
                    itl.append(HDSConstants.UNDERSCORE_OPERATOR).append(zoneInfo.getPortWwn()).append(HDSConstants.UNDERSCORE_OPERATOR)
                            .append(path.getDevNum());
                    itlSet.add(itl.toString());
                }
            }
            if (null != zoningMap || !zoningMap.isEmpty()) {
                mask.addZoningMap(zoningMap);
                itlMap.put(hsdId, itlSet);
                mask.addDeviceDataMap(itlMap);
            }
        } catch (Exception ex) {
            log.error("Failed to get the zoning map for mask {}", mask.getMaskName());
            mask.setZoningMap(null);
        }
    }

    /**
     * Process the Host Group port and update the details in UnManagedExportMask.
     * 
     * @param storageSystem
     * @param portID
     * @param storagePortsItr
     * @param umExportMask
     * @param matchedPorts
     * @param knownPortSet
     */
    private void processStoragePorts(StorageSystem storageSystem, String portID, Map<String, StoragePort> storagePortMap,
            UnManagedExportMask umExportMask, List<StoragePort> matchedPorts, Set<String> knownPortSet) {
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
            knownPortSet.add(knownStoragePort.getId().toString());
            matchedPorts.add(knownStoragePort);
        } else {
            log.info("No storage port in ViPR found matching portNetworkId {}", sport.getPortNetworkId());
            umExportMask.getUnmanagedStoragePortNetworkIds().add(sport.getPortNetworkId());
        }
    }

    private void processVolumes(StorageSystem storageSystem, UnManagedExportMask umExportMask, Set<String> knownVolumeSet,
            List<Path> pathList) {
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
            Set<String> umvMasks = volumeMasks.get(umvNativeGuid);
            if (null == umvMasks) {
                umvMasks = new HashSet<String>();
            }
            umvMasks.add(umExportMask.getId().toString());
            volumeMasks.put(umvNativeGuid, umvMasks);
        }

    }

    private UnManagedExportMask processIscsiInitiators(StorageSystem storageSystem,
            HostStorageDomain hsd, List<ISCSIName> iscsiList, List<Initiator> matchedInitiators, Set<String> knownIniSet,
            Set<String> knownIniWwnSet, List<UnManagedExportMask> newMasks, List<UnManagedExportMask> updateMasks, Set<URI> allMasks) {
        UnManagedExportMask umExportMask = null;
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
            // Initialize for the first initiator and use the same for rest of the initiators in HSD.
            if (null == umExportMask) {
                umExportMask = findAnyExistingUnManagedExportMask(storageSystem, scsiInitiatorName, knownInitiator, newMasks, updateMasks);
            }
            if (null != knownInitiator) {
                log.info("   found an initiator in ViPR on host " + knownInitiator.getHostName());
                knownIniSet.add(knownInitiator.getId().toString());

                knownIniWwnSet.add(knownInitiator.getInitiatorPort());
                if (HostInterface.Protocol.iSCSI.toString().equals(knownInitiator.getProtocol())) {
                    matchedInitiators.add(knownInitiator);
                }
            } else {
                log.info("no hosts in ViPR found configured for initiator {} ", scsiInitiatorName);
                umExportMask.getUnmanagedInitiatorNetworkIds().add(scsiInitiatorName);
            }
        }
        allMasks.add(umExportMask.getId());
        return umExportMask;
    }

    private UnManagedExportMask processFCInitiators(StorageSystem storageSystem, HostStorageDomain hsd,
            List<WorldWideName> wwnList, List<Initiator> matchedInitiators, Set<String> knownIniSet, Set<String> knownIniWwnSet,
            List<UnManagedExportMask> newMasks, List<UnManagedExportMask> updateMasks, Set<URI> allMasks) {
        StringSet unknownIniWwnSet = new StringSet();
        UnManagedExportMask umExportMask = null;
        for (WorldWideName wwn : wwnList) {
            String initiatorWwn = wwn.getWwn().replace(HDSConstants.DOT_OPERATOR, HDSConstants.EMPTY_STR);
            if (WWNUtility.isValidNoColonWWN(initiatorWwn)) {
                initiatorWwn = WWNUtility.getWWNWithColons(initiatorWwn.toUpperCase());
            } else {
                log.warn("Found an invalid initiator wwn: {}", wwn.getWwn());
                continue;
            }
            Initiator knownInitiator = NetworkUtil.getInitiator(initiatorWwn, dbClient);
            // Initialize for the first initiator and use the same for rest of the initiators in HSD.
            if (null == umExportMask) {
                umExportMask = findAnyExistingUnManagedExportMask(storageSystem, initiatorWwn, knownInitiator, newMasks, updateMasks);
            }
            if (null != knownInitiator) {
                log.info("Found an initiator in ViPR on host {}", knownInitiator.getHostName());
                knownIniSet.add(knownInitiator.getId().toString());

                knownIniWwnSet.add(knownInitiator.getInitiatorPort());
                if (HostInterface.Protocol.FC.toString().equals(knownInitiator.getProtocol())) {
                    matchedInitiators.add(knownInitiator);
                }
            } else {
                log.info("No host in ViPR found configured for initiator {} ", initiatorWwn);
                unknownIniWwnSet.add(initiatorWwn);
            }
        }
        // If the mask is processing first time then cleanup the mask.
        // This is required during multiple rediscoveries to update the latest information.
        if (!allMasks.contains(umExportMask.getId())) {
            cleanupUnManagedExportMaskInfo(umExportMask);
        }
        umExportMask.getUnmanagedInitiatorNetworkIds().addAll(unknownIniWwnSet);
        allMasks.add(umExportMask.getId());
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

    private UnManagedExportMask findAnyExistingUnManagedExportMask(StorageSystem storageSystem, String initiatorWwn,
            Initiator knownInitiator, List<UnManagedExportMask> newMasks, List<UnManagedExportMask> updateMasks) {

        // Verify whether there is any UnManagedExportMask for the given InitiatorWWN.
        // If not exists check by its other Host initiators.
        UnManagedExportMask initiatorMask = checkExistingMaskByInitiator(initiatorWwn);
        // Return the ExportMaks if there is exists.
        if (null != initiatorMask) {
            updateMasks.add(initiatorMask);
            return initiatorMask;
        }
        // If there is no mask by initiatorWwn but Host is registered with ViPR, then check by its other initiators.
        if (null != knownInitiator) {
            URI hostURI = knownInitiator.getHost();
            if (!NullColumnValueGetter.isNullURI(knownInitiator.getHost())) {
                Host host = dbClient.queryObject(Host.class, hostURI);
                List<Initiator> allHostInitiators =
                        CustomQueryUtility.queryActiveResourcesByConstraint(dbClient, Initiator.class,
                                ContainmentConstraint.Factory.getContainedObjectsConstraint(host.getId(), Initiator.class, "host"));
                if (null != allHostInitiators && !allHostInitiators.isEmpty()) {
                    for (Initiator initiator : allHostInitiators) {
                        UnManagedExportMask otherInitiatorMask = checkExistingMaskByInitiator(initiator.getInitiatorPort());
                        if (null != otherInitiatorMask) {
                            updateMasks.add(otherInitiatorMask);
                            return otherInitiatorMask;
                        }
                    }
                }

            }
        }

        // There is no unmanaged exportmask for the entire Host for the given known initiator. Now check in the unmanaged initiators.
        // Verify in all masks created on system.
        URIQueryResultList unmanagedMasksResult = new URIQueryResultList();
        dbClient.queryByConstraint(ContainmentConstraint.Factory.getStorageSystemUnManagedExportMaskConstraint(storageSystem.getId()),
                unmanagedMasksResult);
        UnManagedExportMask unManagedExportMask = null;
        while (unmanagedMasksResult.iterator().hasNext()) {
            unManagedExportMask = dbClient.queryObject(UnManagedExportMask.class, unmanagedMasksResult.iterator().next());
            if (unManagedExportMask.getUnmanagedInitiatorNetworkIds().contains(initiatorWwn)) {
                updateMasks.add(unManagedExportMask);
                return unManagedExportMask;
            }
        }
        UnManagedExportMask newMask = getNewUnManagedExportMask(storageSystem);
        newMasks.add(newMask);
        // Still we don't find any unmanaged exportmask, create a new one.
        return newMask;

    }

    /**
     * Verifies whether there is any existing UnManagedExportMask for the given known initiatorWWN.
     * 
     * @param initiatorWwn
     * @return
     */
    private UnManagedExportMask checkExistingMaskByInitiator(String initiatorWwn) {
        URIQueryResultList uemInitiatorMaskList = new URIQueryResultList();
        dbClient.queryByConstraint(AlternateIdConstraint.Factory.getUnManagedExportMaskKnownInitiatorConstraint(initiatorWwn),
                uemInitiatorMaskList);
        if (uemInitiatorMaskList.iterator().hasNext()) {
            return dbClient.queryObject(UnManagedExportMask.class, uemInitiatorMaskList.iterator().next());
        }
        return null;
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
