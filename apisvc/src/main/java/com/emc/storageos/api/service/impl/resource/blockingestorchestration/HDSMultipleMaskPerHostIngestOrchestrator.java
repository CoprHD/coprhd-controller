/*
 * Copyright (c) 2008-2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.blockingestorchestration;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.mutable.MutableInt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.service.impl.resource.blockingestorchestration.context.IngestionRequestContext;
import com.emc.storageos.api.service.impl.resource.utils.PropertySetterUtil;
import com.emc.storageos.api.service.impl.resource.utils.VolumeIngestionUtil;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.Cluster;
import com.emc.storageos.db.client.model.DataObject.Flag;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.ExportGroup.ExportGroupType;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.StringSetMap;
import com.emc.storageos.db.client.model.ZoneInfo;
import com.emc.storageos.db.client.model.ZoneInfoMap;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedExportMask;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume.SupportedVolumeInformation;
import com.emc.storageos.db.client.util.CommonTransformerFunctions;
import com.emc.storageos.db.client.util.WWNUtility;
import com.emc.storageos.volumecontroller.impl.utils.ExportMaskUtils;
import com.google.common.collect.Collections2;

/*
 * MULTIPLE_MASKS_PER_HOST :
 * Arrays whose existing masking containers can be modeled to export mask in ViPR DB
 * are candidates for this multiple mask per host behavior.
 * Here, during provisioning ViPR creates an export mask object for every masking container
 * found in the Array. There is no restriction of one export mask per host , as the export masks created in
 * ViPR DB are actually a replica of what's there in Array.
 * 
 * This is applicable to HDS Arrays.
 */
public class HDSMultipleMaskPerHostIngestOrchestrator extends BlockIngestExportOrchestrator {

    private static final Logger logger = LoggerFactory.getLogger(HDSMultipleMaskPerHostIngestOrchestrator.class);

    @Override
    protected <T extends BlockObject> void ingestExportMasks(IngestionRequestContext requestContext, UnManagedVolume unManagedVolume,
            T blockObject, List<UnManagedExportMask> unManagedMasks, MutableInt masksIngestedCount) throws IngestionException {
        logger.info("Ingestion exportMasks for volume: {}", unManagedVolume.getNativeGuid());
        Iterator<UnManagedExportMask> itr = unManagedMasks.iterator();
        List<UnManagedExportMask> uemsToPersist = new ArrayList<UnManagedExportMask>();
        List<String> errorMessages = requestContext.getErrorMessagesForVolume(unManagedVolume.getNativeGuid());

        ExportGroup exportGroup = requestContext.getExportGroup();
        StorageSystem system = requestContext.getStorageSystem();
        Host host = null;
        Cluster cluster = null;
        List<Host> hosts = new ArrayList<Host>();
        String exportGroupType = null;
        if (null != requestContext.getHost()) {
            host = _dbClient.queryObject(Host.class, requestContext.getHost());
            hosts.add(host);
            exportGroupType = ExportGroupType.Host.name();
        }

        if (null != requestContext.getCluster()) {
            cluster = _dbClient.queryObject(Cluster.class, requestContext.getCluster());
            hosts.addAll(getHostsOfCluster(requestContext.getCluster()));
            exportGroupType = ExportGroupType.Cluster.name();
        }

        // In cluster/Host , if we don't find at least 1 initiator in
        // registered state, then skip this volume from ingestion.
        for (Host hostObj : hosts) {
            Set<String> initiatorSet = getInitiatorsOfHost(hostObj.getId());
            Set<URI> initiatorUris = new HashSet<URI>(Collections2.transform(initiatorSet,
                    CommonTransformerFunctions.FCTN_STRING_TO_URI));
            List<Initiator> initiators = _dbClient.queryObject(Initiator.class, initiatorUris);

            if (!VolumeIngestionUtil.validateInitiatorPortsRegistered(initiators)) {
                // logs already inside the above method.
                logger.warn("Host skipped {} as we can't find at least 1 initiator in registered status", hostObj.getLabel());
                return;
            }
        }

        if (null != requestContext.getDeviceInitiators() && !requestContext.getDeviceInitiators().isEmpty()) {
            // note: ViPR-generated greenfield VPLEX export groups
            // actually have no export group type set
            exportGroupType = ExportGroupType.Host.name();

            if (!VolumeIngestionUtil.validateInitiatorPortsRegistered(requestContext.getDeviceInitiators())) {
                logger.warn("Device with initiators {} skipped as we can't "
                        + "find at least 1 initiator in registered status",
                        requestContext.getDeviceInitiators());
                return;
            }
        }

        // update the ExportGroupType in UnManagedVolume. This will be used to place the
        // volume in the right ExportGroup based on the ExportGroupType.
        updateExportTypeInUnManagedVolume(unManagedVolume, exportGroupType);

        // If we find an existing export mask in DB, with the expected set of initiators,
        // then add this unmanaged volume to the mask.
        while (itr.hasNext()) {
            UnManagedExportMask unManagedExportMask = itr.next();
            if (!VolumeIngestionUtil.validateStoragePortsInVarray(_dbClient, blockObject,
                    requestContext.getVarray().getId(), unManagedExportMask.getKnownStoragePortUris(),
                    unManagedExportMask, errorMessages)) {
                // logs already inside the above method.
                itr.remove();
                continue;
            }
            logger.info("looking for an existing export mask for " + unManagedExportMask.getMaskName());
            ExportMask exportMask = getExportsMaskAlreadyIngested(unManagedExportMask, _dbClient);
            if (null == exportMask) {
                logger.info("\tno mask found");
                continue;
            }
            logger.info("Export Mask {} already available", exportMask.getMaskName());
            masksIngestedCount.increment();
            List<URI> iniList = new ArrayList<URI>(Collections2.transform(exportMask.getInitiators(),
                    CommonTransformerFunctions.FCTN_STRING_TO_URI));
            List<Initiator> initiators = _dbClient.queryObject(Initiator.class, iniList);

            // if the block object is marked as internal then add it to existing volumes
            // of the mask, else add it to user created volumes
            if (blockObject.checkInternalFlags(Flag.NO_PUBLIC_ACCESS)) {
                logger.info("Block object {} is marked internal. Adding to existing volumes of the mask {}",
                        blockObject.getNativeGuid(), exportMask.getMaskName());
                exportMask.addToExistingVolumesIfAbsent(blockObject, ExportGroup.LUN_UNASSIGNED_STR);
            } else {
                exportMask.addToUserCreatedVolumes(blockObject);
                // remove this volume if already in existing
                exportMask.removeFromExistingVolumes(blockObject);
            }

            // Add new initiators found in ingest to list if absent.
            exportMask.addInitiators(initiators);
            // Add all unknown initiators to existing
            exportMask.addToExistingInitiatorsIfAbsent(new ArrayList(unManagedExportMask.getUnmanagedInitiatorNetworkIds()));
            // Always set this flag to true for ingested masks.
            exportMask.setCreatedBySystem(true);

            List<Initiator> userAddedInis = VolumeIngestionUtil.findUserAddedInisFromExistingIniListInMask(initiators,
                    unManagedExportMask.getId(), _dbClient);
            exportMask.addToUserCreatedInitiators(userAddedInis);

            // remove from existing if present - possible in ingestion after
            // coexistence
            exportMask.removeFromExistingInitiator(userAddedInis);

            // need to sync up all remaining existing volumes
            Map<String, Integer> wwnToHluMap =
                    VolumeIngestionUtil.extractWwnToHluMap(unManagedExportMask, _dbClient);
            exportMask.addToExistingVolumesIfAbsent(wwnToHluMap);

            // find the HLU and set it in the volumes
            Integer hlu = ExportGroup.LUN_UNASSIGNED;
            if (wwnToHluMap.containsKey(blockObject.getWWN())) {
                hlu = wwnToHluMap.get(blockObject.getWWN());
            }
            exportMask.addVolume(blockObject.getId(), hlu);
            // adding volume we need to add FCZoneReferences
            StringSetMap zoneMap = getZoneMapOfUnManagedVolume(unManagedVolume, unManagedExportMask, initiators);
            if (!zoneMap.isEmpty()) {
                exportMask.setZoningMap(zoneMap);
            }

            _dbClient.updateObject(exportMask);
            ExportMaskUtils.updateFCZoneReferences(exportGroup, blockObject, unManagedExportMask.getZoningMap(), initiators,
                    _dbClient);

            // remove the unmanaged mask from unmanaged volume only if the block object has not been marked as internal
            if (!blockObject.checkInternalFlags(Flag.NO_PUBLIC_ACCESS)) {
                logger.info("breaking relationship between UnManagedExportMask {} and UnManagedVolume {}",
                        unManagedExportMask.getMaskName(), unManagedVolume.forDisplay());
                unManagedVolume.getUnmanagedExportMasks().remove(unManagedExportMask.getId().toString());
                unManagedExportMask.getUnmanagedVolumeUris().remove(unManagedVolume.getId().toString());
                uemsToPersist.add(unManagedExportMask);
            }

            if (exportGroup.getExportMasks() == null || !exportGroup.getExportMasks().contains(exportMask.getId().toString())) {
                exportGroup.addExportMask(exportMask.getId().toString());
            }

            VolumeIngestionUtil.updateExportGroup(exportGroup, blockObject, _dbClient, initiators, hosts, cluster);

            logger.info("Removing unmanaged mask {} from the list of items to process, as block object is added already",
                    unManagedExportMask.getNativeGuid());
            itr.remove();
        }

    }

    private StringSetMap getZoneMapOfUnManagedVolume(UnManagedVolume unManagedVolume, UnManagedExportMask unManagedExportMask,
            List<Initiator> initiators) {
        StringSetMap deviceDataMap = unManagedExportMask.getDeviceDataMap();
        ZoneInfoMap zoningMap = unManagedExportMask.getZoningMap();
        StringSetMap volumeZoneInfo = new StringSetMap();
        String nativeId = PropertySetterUtil.extractValueFromStringSet(SupportedVolumeInformation.NATIVE_ID.toString(),
                unManagedVolume.getVolumeInformation());
        if (deviceDataMap.containsKey(nativeId)) {
            StringSet iTInfo = deviceDataMap.get(nativeId);
            if (null != iTInfo && !iTInfo.isEmpty()) {
                for (Initiator initiator : initiators) {
                    ZoneInfo zoneInfo = getInitiatorZone(iTInfo, initiator.getInitiatorPort(), zoningMap);
                    if (null != zoneInfo) {
                        volumeZoneInfo.put(zoneInfo.getInitiatorWwn(), zoneInfo.getPortWwn());
                    }
                }

            }
        }
        return volumeZoneInfo;
    }

    private ZoneInfo getInitiatorZone(StringSet iTInfo, String initiatorName, ZoneInfoMap zoningMap) {
        for (String iniTarget : iTInfo) {
            if (iniTarget.contains(WWNUtility.getUpperWWNWithNoColons(initiatorName))) {
                return zoningMap.get(iniTarget);
            }
        }
        return null;
    }

    private <T> void validateUmvVPool(IngestionRequestContext requestContext, UnManagedVolume unManagedVolume,
            List<UnManagedExportMask> unManagedMasks, T blockObject) {
        Iterator<UnManagedExportMask> itr = unManagedMasks.iterator();
        while (itr.hasNext()) {
            // Iterator through each UnManagedExportMask and validate the vpool path parameters.
            UnManagedExportMask umask = itr.next();
            if (null == umask.getDeviceDataMap()) {
                logger.info("No DeviceDataMap info found in mask {}. Hence skipping.", umask.getId());
                itr.remove();
                continue;
            }
            List<ZoneInfo> zoningInfo = getZoningInfo(umask, unManagedVolume);

        }
    }

    private List<ZoneInfo> getZoningInfo(UnManagedExportMask umask, UnManagedVolume unManagedVolume) {
        String nativeId = PropertySetterUtil.extractValueFromStringSet(SupportedVolumeInformation.NATIVE_ID.toString(),
                unManagedVolume.getVolumeInformation());
        List<ZoneInfo> zoneInfoList = new ArrayList<ZoneInfo>();
        StringSetMap deviceDataMap = umask.getDeviceDataMap();
        StringSet iTInfoSet = deviceDataMap.get(nativeId);
        if (null != iTInfoSet && !iTInfoSet.isEmpty()) {
            for (String iTInfo : iTInfoSet) {
                ZoneInfo zoneInfo = umask.getZoningMap().get(iTInfo);
                if (null != zoneInfo) {
                    zoneInfoList.add(zoneInfo);
                } else {
                    logger.info("No zoneInfo found for initiator_target {}", iTInfo);
                }
            }
        }
        return zoneInfoList;
    }

    @Override
    protected ExportMask getExportsMaskAlreadyIngested(UnManagedExportMask mask, DbClient dbClient) {
        ExportMask exportMask = null;
        URIQueryResultList maskList = new URIQueryResultList();
        dbClient.queryByConstraint(AlternateIdConstraint.Factory.getExportMaskByNameConstraint(mask
                .getMaskName()), maskList);
        if (null != maskList.iterator()) {
            for (URI maskUri : maskList) {
                exportMask = dbClient.queryObject(ExportMask.class, maskUri);
                if (null != exportMask) {
                    return exportMask;
                }
            }
        }

        return exportMask;
    }

}
