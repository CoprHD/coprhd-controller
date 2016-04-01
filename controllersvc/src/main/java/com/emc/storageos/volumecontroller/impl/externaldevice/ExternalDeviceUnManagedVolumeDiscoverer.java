/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.externaldevice;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedConsistencyGroup;
import com.emc.storageos.storagedriver.BlockStorageDriver;
import com.emc.storageos.storagedriver.model.StorageObject;
import com.emc.storageos.storagedriver.model.VolumeConsistencyGroup;
import com.emc.storageos.storagedriver.model.VolumeSnapshot;
import com.emc.storageos.xtremio.restapi.XtremIOClient;
import com.emc.storageos.xtremio.restapi.XtremIOConstants;
import com.emc.storageos.xtremio.restapi.model.response.XtremIOConsistencyGroup;
import org.apache.commons.lang.mutable.MutableInt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.StringSetMap;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.plugins.common.PartitionManager;
import com.emc.storageos.storagedriver.DiscoveryDriver;
import com.emc.storageos.storagedriver.model.StorageSystem;
import com.emc.storageos.storagedriver.model.StorageVolume;
import com.emc.storageos.volumecontroller.impl.NativeGUIDGenerator;
import com.emc.storageos.volumecontroller.impl.plugins.ExternalDeviceCommunicationInterface;
import com.emc.storageos.volumecontroller.impl.utils.DiscoveryUtils;
import com.google.common.base.Joiner;

public class ExternalDeviceUnManagedVolumeDiscoverer {
    private static Logger log = LoggerFactory.getLogger(ExternalDeviceUnManagedVolumeDiscoverer.class);
    private static final String TRUE = "true";
    private static final String FALSE = "false";
    private static final String UNMANAGED_VOLUME = "UnManagedVolume";
    private static final String UNMANAGED_CONSISTENCY_GROUP = "UnManagedConsistencyGroup";

    public void discoverUnManagedObjects(DiscoveryDriver driver, com.emc.storageos.db.client.model.StorageSystem storageSystem, DbClient dbClient,
                                         PartitionManager partitionManager) {
        log.info("Started discovery of UnManagedVolumes for system {}", storageSystem.getId());
        Set<URI> allCurrentUnManagedVolumeUris = new HashSet<>();
        Set<URI> allCurrentUnManagedCgURIs = new HashSet<>();
        MutableInt lastPage = new MutableInt(0);
        MutableInt nextPage = new MutableInt(0);
        List<UnManagedVolume> unManagedVolumesToCreate = new ArrayList<UnManagedVolume>();
        List<UnManagedVolume> unManagedVolumesToUpdate = new ArrayList<UnManagedVolume>();
        List<UnManagedConsistencyGroup> unManagedCGToUpdate;
        Map<String, UnManagedConsistencyGroup> unManagedCGToUpdateMap = new HashMap<>();

        // unManagedCGToUpdateMap = new HashMap<String, UnManagedConsistencyGroup>();
        // prepare storage system
        StorageSystem driverStorageSystem = ExternalDeviceCommunicationInterface.initStorageSystem(storageSystem);
        do {
            List<StorageVolume> driverVolumes = new ArrayList<>();
            log.info("Processing page {} ", nextPage);
            driver.getStorageVolumes(driverStorageSystem, driverVolumes, nextPage);
            log.info("Volume count on this page {} ", driverVolumes.size());
            ////////////////////////
            for (StorageVolume driverVolume : driverVolumes) {
                UnManagedVolume unManagedVolume = null;
                try {
                    com.emc.storageos.db.client.model.StoragePool storagePool = getStoragePoolOfUnmanagedVolume(storageSystem, driverVolume, dbClient);
                    if (null == storagePool) {
                        log.error("Skipping unmanaged volume discovery as the volume {} storage pool doesn't exist in ViPR", driverVolume.getNativeId());
                        continue;
                    }
                    String managedVolumeNativeGuid = NativeGUIDGenerator.generateNativeGuidForVolumeOrBlockSnapShot(
                            storageSystem.getNativeGuid(), driverVolume.getNativeId());
                    Volume viprVolume = DiscoveryUtils.checkStorageVolumeExistsInDB(dbClient, managedVolumeNativeGuid);
                    if (null != viprVolume) {
                        log.info("Skipping volume {} as it is already managed by ViPR", managedVolumeNativeGuid);
//                // Check if the xtremIO vol is exported. If yes, we need to store it to add to unmanaged
//                // export masks.
//                if (isExported) {
//                    populateKnownVolsMap(volume, viprVolume, igKnownVolumesMap);
//                }
//
//                // retrieve snap info to be processed later
//                if (hasSnaps) {
//                    StringSet vpoolUriSet = new StringSet();
//                    vpoolUriSet.add(viprVolume.getVirtualPool().toString());
//                    discoverVolumeSnaps(storageSystem, volume.getSnaps(), viprVolume.getNativeGuid(), vpoolUriSet,
//                            xtremIOClient, xioClusterName, dbClient, igUnmanagedVolumesMap, igKnownVolumesMap);
//                }

                        continue;
                    }

                    unManagedVolume = createUnManagedVolume(driverVolume, storageSystem, storagePool, unManagedVolumesToCreate,
                            unManagedVolumesToUpdate, dbClient);

                    // if the volume is associated with a CG, set up the unmanaged CG
                    if (driverVolume.getConsistencyGroup() != null && !driverVolume.getConsistencyGroup().isEmpty()) {
                        addObjectToUnManagedConsistencyGroup(storageSystem, driverVolume.getConsistencyGroup(), unManagedVolume,
                                allCurrentUnManagedCgURIs, unManagedCGToUpdateMap, driver, dbClient);
                    } else {
                        // Make sure the unManagedVolume object does not contain CG information from previous discovery
                        unManagedVolume.getVolumeCharacterstics().put(
                                UnManagedVolume.SupportedVolumeCharacterstics.IS_VOLUME_ADDED_TO_CONSISTENCYGROUP.toString(), Boolean.FALSE.toString());
                        // set the uri of the unmanaged CG in the unmanaged volume object to empty
                        unManagedVolume.getVolumeInformation().put(UnManagedVolume.SupportedVolumeInformation.UNMANAGED_CONSISTENCY_GROUP_URI.toString(),
                                "");
                    }

                    allCurrentUnManagedVolumeUris.add(unManagedVolume.getId());

                    Set<URI> unManagedSnaphotUris = processUnManagedSnapshots(driverVolume, unManagedVolume, storageSystem, storagePool,
                            unManagedVolumesToCreate,
                            unManagedVolumesToUpdate,
                            allCurrentUnManagedCgURIs, unManagedCGToUpdateMap,
                            (BlockStorageDriver)driver, dbClient);

                    allCurrentUnManagedVolumeUris.addAll(unManagedSnaphotUris);

                } catch (Exception ex) {
                    log.error("Error processing {} volume {}", storageSystem.getNativeId(), driverVolume.getNativeId(), ex);
                }
            }

            if (!unManagedVolumesToCreate.isEmpty()) {
                partitionManager.insertInBatches(unManagedVolumesToCreate,
                        Constants.DEFAULT_PARTITION_SIZE, dbClient, UNMANAGED_VOLUME);
                unManagedVolumesToCreate.clear();
            }
            if (!unManagedVolumesToUpdate.isEmpty()) {
                partitionManager.updateAndReIndexInBatches(unManagedVolumesToUpdate,
                        Constants.DEFAULT_PARTITION_SIZE, dbClient, UNMANAGED_VOLUME);
                unManagedVolumesToUpdate.clear();
            }
        } while (!nextPage.equals(lastPage));

        if (!unManagedCGToUpdateMap.isEmpty()) {
            unManagedCGToUpdate = new ArrayList<>(unManagedCGToUpdateMap.values());
            partitionManager.updateAndReIndexInBatches(unManagedCGToUpdate,
                    unManagedCGToUpdate.size(), dbClient, UNMANAGED_CONSISTENCY_GROUP);
            unManagedCGToUpdate.clear();
        }

        log.info("Processed {} unmanged objects.", allCurrentUnManagedVolumeUris.size());
        // Process those active unmanaged volume objects available in database but not in newly discovered items, to mark them inactive.
        if (allCurrentUnManagedVolumeUris.size() != 0) {
            DiscoveryUtils.markInActiveUnManagedVolumes(storageSystem, allCurrentUnManagedVolumeUris, dbClient, partitionManager);

        // Process those active unmanaged consistency group objects available in database but not in newly discovered items, to mark them
        // inactive.
        DiscoveryUtils.performUnManagedConsistencyGroupsBookKeeping(storageSystem, allCurrentUnManagedCgURIs, dbClient, partitionManager);

        }
        // Process those active unmanaged consistency group objects available in database but not in newly discovered items, to mark them
        // inactive.
        DiscoveryUtils.performUnManagedConsistencyGroupsBookKeeping(storageSystem, allCurrentUnManagedCgURIs, dbClient, partitionManager);

        // Next discover the unmanaged export masks
        //discoverUnmanagedExportMasks(storageSystem.getId(), igUnmanagedVolumesMap, igKnownVolumesMap, xtremIOClient, xioClusterName,
        //dbClient, partitionManager);

    }

    private UnManagedVolume createUnManagedVolume(StorageVolume driverVolume, com.emc.storageos.db.client.model.StorageSystem storageSystem,
                                                  com.emc.storageos.db.client.model.StoragePool storagePool,
                                                  List<UnManagedVolume> unManagedVolumesToCreate, List<UnManagedVolume> unManagedVolumesToUpdate, DbClient dbClient) {

        boolean newVolume = false;
        StringSetMap unManagedVolumeInformation = null;
        Map<String, String> unManagedVolumeCharacteristics = null;

        String unManagedVolumeNatvieGuid = NativeGUIDGenerator.generateNativeGuidForPreExistingVolume(
                storageSystem.getNativeGuid(), driverVolume.getNativeId());

        UnManagedVolume unManagedVolume = DiscoveryUtils.checkUnManagedVolumeExistsInDB(dbClient, unManagedVolumeNatvieGuid);
        if (null == unManagedVolume) {
            unManagedVolume = new UnManagedVolume();
            unManagedVolume.setId(URIUtil.createId(UnManagedVolume.class));
            unManagedVolume.setNativeGuid(unManagedVolumeNatvieGuid);
            unManagedVolume.setStorageSystemUri(storageSystem.getId());

            if (driverVolume.getWwn() == null) {
                unManagedVolume.setWwn(String.format("%s%s", driverVolume.getStorageSystemId(), driverVolume.getNativeId()));
            } else {
                unManagedVolume.setWwn(driverVolume.getWwn());
            }
            newVolume = true;
            unManagedVolumeInformation = new StringSetMap();
            unManagedVolumeCharacteristics = new HashMap<String, String>();
        } else {
            unManagedVolumeInformation = unManagedVolume.getVolumeInformation();
            unManagedVolumeCharacteristics = unManagedVolume.getVolumeCharacterstics();
        }

        unManagedVolume.setLabel(driverVolume.getDeviceLabel());
        Boolean isVolumeExported = false;
        unManagedVolumeCharacteristics.put(UnManagedVolume.SupportedVolumeCharacterstics.IS_VOLUME_EXPORTED.toString(), isVolumeExported.toString());

        // Set these default to false. The individual storage discovery will change them if needed.
        unManagedVolumeCharacteristics.put(UnManagedVolume.SupportedVolumeCharacterstics.IS_NONRP_EXPORTED.toString(), FALSE);
        unManagedVolumeCharacteristics.put(UnManagedVolume.SupportedVolumeCharacterstics.IS_RECOVERPOINT_ENABLED.toString(), FALSE);

        StringSet deviceLabel = new StringSet();
        deviceLabel.add(driverVolume.getDeviceLabel());
        unManagedVolumeInformation.put(UnManagedVolume.SupportedVolumeInformation.DEVICE_LABEL.toString(),
                deviceLabel);



        StringSet accessState = new StringSet();
        accessState.add(driverVolume.getAccessStatus().toString());
        unManagedVolumeInformation.put(UnManagedVolume.SupportedVolumeInformation.ACCESS.toString(), accessState);

        StringSet provCapacity = new StringSet();
        provCapacity.add(String.valueOf(driverVolume.getProvisionedCapacity()));
        unManagedVolumeInformation.put(UnManagedVolume.SupportedVolumeInformation.PROVISIONED_CAPACITY.toString(),
                provCapacity);

        StringSet allocatedCapacity = new StringSet();
        allocatedCapacity.add(String.valueOf(driverVolume.getAllocatedCapacity()));
        unManagedVolumeInformation.put(UnManagedVolume.SupportedVolumeInformation.ALLOCATED_CAPACITY.toString(),
                allocatedCapacity);

        StringSet systemTypes = new StringSet();
        systemTypes.add(storageSystem.getSystemType());
        unManagedVolumeInformation.put(UnManagedVolume.SupportedVolumeInformation.SYSTEM_TYPE.toString(),
                systemTypes);

        StringSet nativeId = new StringSet();
        nativeId.add(driverVolume.getNativeId());
        unManagedVolumeInformation.put(UnManagedVolume.SupportedVolumeInformation.NATIVE_ID.toString(),
                nativeId);

        unManagedVolumeCharacteristics.put(
                UnManagedVolume.SupportedVolumeCharacterstics.IS_INGESTABLE.toString(), TRUE);

        unManagedVolumeCharacteristics.put(UnManagedVolume.SupportedVolumeCharacterstics.IS_THINLY_PROVISIONED.toString(),
                driverVolume.getThinlyProvisioned().toString());

//        // Get vipr pool
//        String driverPoolId = driverVolume.getStoragePoolId();
//        String poolNativeGuid = NativeGUIDGenerator.generateNativeGuid(
//                storageSystem, driverPoolId, NativeGUIDGenerator.POOL);
//        URIQueryResultList storagePoolURIs = new URIQueryResultList();
//        dbClient.queryByConstraint(AlternateIdConstraint.Factory
//                .getStoragePoolByNativeGuidConstraint(poolNativeGuid), storagePoolURIs);
//        if (!storagePoolURIs.isEmpty())
//        {
//            URI storagePoolUri = storagePoolURIs.get(0);
//            com.emc.storageos.db.client.model.StoragePool storagePool =
//                    (com.emc.storageos.db.client.model.StoragePool)dbClient.queryObject(
//                            com.emc.storageos.db.client.model.StoragePool.class, storagePoolURIs);
        unManagedVolume.setStoragePoolUri(storagePool.getId());
        StringSet pools = new StringSet();
        pools.add(storagePool.getId().toString());
        unManagedVolumeInformation.put(UnManagedVolume.SupportedVolumeInformation.STORAGE_POOL.toString(), pools);
        StringSet driveTypes = storagePool.getSupportedDriveTypes();
        if (null != driveTypes) {
            unManagedVolumeInformation.put(
                    UnManagedVolume.SupportedVolumeInformation.DISK_TECHNOLOGY.toString(),
                    driveTypes);
        }
        StringSet matchedVPools = DiscoveryUtils.getMatchedVirtualPoolsForPool(dbClient, storagePool.getId(),
                unManagedVolumeCharacteristics.get(UnManagedVolume.SupportedVolumeCharacterstics.IS_THINLY_PROVISIONED.toString()));
        log.debug("Matched Pools : {}", Joiner.on("\t").join(matchedVPools));
        if (matchedVPools.isEmpty()) {
            // clear all existing supported vpools.
            unManagedVolume.getSupportedVpoolUris().clear();
        } else {
            // replace with new StringSet
            unManagedVolume.getSupportedVpoolUris().replace(matchedVPools);
            log.info("Replaced Pools : {}", Joiner.on("\t").join(unManagedVolume.getSupportedVpoolUris()));
        }

        unManagedVolume.setVolumeInformation(unManagedVolumeInformation);

        if (unManagedVolume.getVolumeCharacterstics() == null) {
            unManagedVolume.setVolumeCharacterstics(new StringMap());
        }
        unManagedVolume.getVolumeCharacterstics().replace(unManagedVolumeCharacteristics);

        if (newVolume) {
            unManagedVolumesToCreate.add(unManagedVolume);
        } else {
            unManagedVolumesToUpdate.add(unManagedVolume);
        }

        return unManagedVolume;
    }

    /**
     * Get storage pool of unmanaged object.
     *
     * @param storageSystem
     * @param driverVolume
     * @param dbClient
     * @return
     */
    private com.emc.storageos.db.client.model.StoragePool getStoragePoolOfUnmanagedVolume(com.emc.storageos.db.client.model.StorageSystem storageSystem,
                                                                                           StorageVolume driverVolume, DbClient dbClient)
    {
        com.emc.storageos.db.client.model.StoragePool storagePool = null;
        // Get vipr pool
        String driverPoolId = driverVolume.getStoragePoolId();
        String poolNativeGuid = NativeGUIDGenerator.generateNativeGuid(
                storageSystem, driverPoolId, NativeGUIDGenerator.POOL);
        URIQueryResultList storagePoolURIs = new URIQueryResultList();
        dbClient.queryByConstraint(AlternateIdConstraint.Factory
                .getStoragePoolByNativeGuidConstraint(poolNativeGuid), storagePoolURIs);
        Iterator<URI> poolsItr = storagePoolURIs.iterator();
        while (poolsItr.hasNext()) {
            URI storagePoolURI = poolsItr.next();
            storagePool = dbClient.queryObject(com.emc.storageos.db.client.model.StoragePool.class, storagePoolURI);
        }

        return storagePool;
    }

    private void addObjectToUnManagedConsistencyGroup(com.emc.storageos.db.client.model.StorageSystem storageSystem,
                                                      String cgNativeId, UnManagedVolume unManagedVolume,
                                                      Set<URI> allCurrentUnManagedCgURIs,
                                                      Map<String, UnManagedConsistencyGroup> unManagedCGToUpdateMap,
                                                      DiscoveryDriver driver, DbClient dbClient) throws Exception {
        log.info("Unmanaged storage object {} belongs to consistency group {} on the array", unManagedVolume.getLabel(),
                cgNativeId);
        // determine the native guid for the unmanaged CG
        String unManagedCGNativeGuid = NativeGUIDGenerator.generateNativeGuidForCG(storageSystem.getNativeGuid(),
                cgNativeId);
        log.info("Unmanaged consistency group has nativeGuid {} ", unManagedCGNativeGuid);
        // determine if the unmanaged CG already exists in the unManagedCGToUpdateMap or in the database
        // if the the unmanaged CG is not in either create a new one
        UnManagedConsistencyGroup unManagedCG = null;
        if (unManagedCGToUpdateMap.containsKey(unManagedCGNativeGuid)) {
            unManagedCG = unManagedCGToUpdateMap.get(unManagedCGNativeGuid);
            log.info("Unmanaged consistency group {} was previously added to the unManagedCGToUpdateMap", unManagedCG.getNativeGuid());
        } else {
            unManagedCG = DiscoveryUtils.checkUnManagedCGExistsInDB(dbClient, unManagedCGNativeGuid);
            if (null == unManagedCG) {
                // unmanaged CG does not exist in the database, create it
                VolumeConsistencyGroup driverCG = driver.getStorageObject(storageSystem.getNativeId(), cgNativeId,
                        VolumeConsistencyGroup.class);
                if (driverCG != null) {
                    unManagedCG = createUnManagedCG(driverCG, storageSystem, dbClient);
                    log.info("Created unmanaged consistency group: {} with nativeGuid {}",
                            unManagedCG.getId().toString(), unManagedCG.getNativeGuid());
                } else {
                    // todo this is a failure
                    String msg = String.format("Driver VolumeConsistencyGroup with native id %s does not exist on storage system %s",
                            cgNativeId, storageSystem.getNativeId());
                    log.error(msg);
                    throw new Exception(msg);
                }

            } else {
                log.info("Unmanaged consistency group {} was previously added to the database", unManagedCG.getNativeGuid());
                // clean out the list of unmanaged objects if this unmanaged cg was already
                // in the database and its first time being used in this discovery operation
                // the list should be re-populated by the current discovery operation
                log.info("Cleaning out unmanaged object map from unmanaged consistency group: {}", unManagedCG.getNativeGuid());
                unManagedCG.getUnManagedVolumesMap().clear();
            }
        }
        log.info("Adding unmanaged storage object {} to unmanaged consistency group {}", unManagedVolume.getLabel(), unManagedCG.getNativeGuid());
        // Update the unManagedVolume object with CG information
        unManagedVolume.getVolumeCharacterstics().put(UnManagedVolume.SupportedVolumeCharacterstics.IS_VOLUME_ADDED_TO_CONSISTENCYGROUP.toString(),
                Boolean.TRUE.toString());
        // set the uri of the unmanaged CG in the unmanaged volume object
        unManagedVolume.getVolumeInformation().put(UnManagedVolume.SupportedVolumeInformation.UNMANAGED_CONSISTENCY_GROUP_URI.toString(),
                unManagedCG.getId().toString());
        // add the unmanaged volume object to the unmanaged CG
        unManagedCG.getUnManagedVolumesMap().put(unManagedVolume.getNativeGuid(), unManagedVolume.getId().toString());
        // add the unmanaged CG to the map of unmanaged CGs to be updated in the database once all volumes have been processed
        unManagedCGToUpdateMap.put(unManagedCGNativeGuid, unManagedCG);
        // add the unmanaged CG to the current set of CGs being discovered on the array. This is for book keeping later.
        allCurrentUnManagedCgURIs.add(unManagedCG.getId());
    }

    private UnManagedConsistencyGroup createUnManagedCG(VolumeConsistencyGroup driverCG,
                                                        com.emc.storageos.db.client.model.StorageSystem storageSystem,
                                                        DbClient dbClient) {
        UnManagedConsistencyGroup unManagedCG = new UnManagedConsistencyGroup();
        unManagedCG.setId(URIUtil.createId(UnManagedConsistencyGroup.class));
        unManagedCG.setLabel(driverCG.getDeviceLabel());
        unManagedCG.setName(driverCG.getDeviceLabel());
        String unManagedCGNativeGuid = NativeGUIDGenerator.generateNativeGuidForCG(storageSystem.getNativeGuid(),
                driverCG.getNativeId());
        unManagedCG.setNativeGuid(unManagedCGNativeGuid);
        unManagedCG.setNativeId(driverCG.getNativeId());
        unManagedCG.setStorageSystemUri(storageSystem.getId());
        //unManagedCG.setNumberOfVols(consistencyGroup.getNumOfVols());
        dbClient.createObject(unManagedCG);

        return unManagedCG;
    }

    private Set<URI> processUnManagedSnapshots(StorageVolume driverVolume, UnManagedVolume unManagedParentVolume,
                                               com.emc.storageos.db.client.model.StorageSystem storageSystem,
                                               com.emc.storageos.db.client.model.StoragePool storagePool,
                                               List<UnManagedVolume> unManagedVolumesToCreate, List<UnManagedVolume> unManagedVolumesToUpdate,
                                               Set<URI> allCurrentUnManagedCgURIs,
                                               Map<String, UnManagedConsistencyGroup> unManagedCGToUpdateMap,
                                               BlockStorageDriver driver, DbClient dbClient) throws Exception {

        log.info("Processing snapshots for volume {} ", unManagedParentVolume.getNativeGuid());
        Set<URI> snapshotUris = new HashSet<>();
        List<VolumeSnapshot> driverSnapshots = driver.getVolumeSnapshots(driverVolume);
        if (driverSnapshots == null || driverSnapshots.isEmpty()) {
            log.info("There are no snapshots for volume {} ", unManagedParentVolume.getNativeGuid());
            // clean unmanaged snaps from previous discovery
            unManagedParentVolume.getVolumeCharacterstics().put(UnManagedVolume.SupportedVolumeCharacterstics.HAS_REPLICAS.toString(), FALSE);
            if (unManagedParentVolume.getVolumeInformation().containsKey(UnManagedVolume.SupportedVolumeInformation.SNAPSHOTS.toString())) {
                unManagedParentVolume.getVolumeInformation().get(UnManagedVolume.SupportedVolumeInformation.SNAPSHOTS.toString()).clear();
            }
        } else {
            log.info("Snapshots for unmanaged volume {}:" + Joiner.on("\t").join(driverSnapshots), unManagedParentVolume.getNativeGuid());
            StringSet unManagedSnaps = new StringSet();
            for (VolumeSnapshot driverSnapshot : driverSnapshots) {
                String managedSnapNativeGuid = NativeGUIDGenerator.generateNativeGuidForVolumeOrBlockSnapShot(
                        storageSystem.getNativeGuid(), driverSnapshot.getNativeId());
                BlockSnapshot viprSnap = DiscoveryUtils.checkBlockSnapshotExistsInDB(dbClient, managedSnapNativeGuid);
                if (null != viprSnap) {
                    log.info("Skipping snapshot {} as it is already managed by ViPR", managedSnapNativeGuid);
                    continue;
                }

                String unManagedSnapNatvieGuid = NativeGUIDGenerator.generateNativeGuidForPreExistingVolume(
                        storageSystem.getNativeGuid(), driverSnapshot.getNativeId());
                UnManagedVolume unManagedSnap = createUnManagedSnapshot(driverSnapshot, unManagedParentVolume, storageSystem, storagePool,
                        unManagedVolumesToCreate,
                        unManagedVolumesToUpdate, dbClient);
                snapshotUris.add(unManagedSnap.getId());
                unManagedSnaps.add(unManagedSnapNatvieGuid);

                // Check if this snap is for a volume in consistency group on device.
                // todo....
//                if (driverSnapshot.getSnapSetId() != null && !driverSnapshot.getSnapSetId().isEmpty()) {
//                    addObjectToUnManagedConsistencyGroup(storageSystem, driverSnapshot.getSnapSetId(), unManagedSnap,
//                            allCurrentUnManagedCgURIs, unManagedCGToUpdateMap, (DiscoveryDriver)driver, dbClient);
//                } else {
//                    // Make sure the unManagedVolume object does not contain CG information from previous discovery
//                    unManagedSnap.getVolumeCharacterstics().put(
//                            UnManagedVolume.SupportedVolumeCharacterstics.IS_VOLUME_ADDED_TO_CONSISTENCYGROUP.toString(), Boolean.FALSE.toString());
//                    // set the uri of the unmanaged CG in the unmanaged volume object to empty
//                    unManagedSnap.getVolumeInformation().put(UnManagedVolume.SupportedVolumeInformation.UNMANAGED_CONSISTENCY_GROUP_URI.toString(),
//                            "");
//                }
            }
            if (!unManagedSnaps.isEmpty()) {
                // set the HAS_REPLICAS property
                unManagedParentVolume.getVolumeCharacterstics().put(UnManagedVolume.SupportedVolumeCharacterstics.HAS_REPLICAS.toString(), TRUE);
                StringSetMap unManagedVolumeInformation = unManagedParentVolume.getVolumeInformation();
                log.info("New unmanaged snaps for unmanaged volume {}:" + Joiner.on("\t").join(unManagedSnaps), unManagedParentVolume.getNativeGuid());
                if (unManagedVolumeInformation.containsKey(UnManagedVolume.SupportedVolumeInformation.SNAPSHOTS.toString())) {
                    log.info("Old unmanaged snaps for unmanaged volume {}:" + Joiner.on("\t").join(unManagedVolumeInformation.get(
                            UnManagedVolume.SupportedVolumeInformation.SNAPSHOTS.toString())), unManagedParentVolume.getNativeGuid());
                    // replace with new StringSet
                    unManagedVolumeInformation.get(
                            UnManagedVolume.SupportedVolumeInformation.SNAPSHOTS.toString()).replace(unManagedSnaps);
                    log.info("Replaced snaps :" + Joiner.on("\t").join(unManagedVolumeInformation.get(
                            UnManagedVolume.SupportedVolumeInformation.SNAPSHOTS.toString())));
                }
                else {
                    unManagedVolumeInformation.put(
                            UnManagedVolume.SupportedVolumeInformation.SNAPSHOTS.toString(), unManagedSnaps);
                }
            } else {
                log.info("All snapshots for volume {} are already managed.", unManagedParentVolume.getNativeGuid());
                // todo: should we set HAS_REPLICAS to true in this case?
                unManagedParentVolume.getVolumeCharacterstics().put(UnManagedVolume.SupportedVolumeCharacterstics.HAS_REPLICAS.toString(), FALSE);
                unManagedParentVolume.getVolumeInformation().get(UnManagedVolume.SupportedVolumeInformation.SNAPSHOTS.toString()).clear();
            }
        }
        return snapshotUris;
    }


    private UnManagedVolume createUnManagedSnapshot(VolumeSnapshot driverSnapshot, UnManagedVolume parentUnManagedVolume,
                                                    com.emc.storageos.db.client.model.StorageSystem storageSystem,
                                                    com.emc.storageos.db.client.model.StoragePool storagePool,
                                                    List<UnManagedVolume> unManagedVolumesToCreate, List<UnManagedVolume> unManagedVolumesToUpdate,
                                                    DbClient dbClient) {

        // We process unmanaged snapshot as unmanaged volume
        boolean newVolume = false;
        StringSetMap unManagedVolumeInformation = null;
        Map<String, String> unManagedVolumeCharacteristics = null;

        String unManagedVolumeNatvieGuid = NativeGUIDGenerator.generateNativeGuidForPreExistingVolume(
                storageSystem.getNativeGuid(), driverSnapshot.getNativeId());

        UnManagedVolume unManagedVolume = DiscoveryUtils.checkUnManagedVolumeExistsInDB(dbClient, unManagedVolumeNatvieGuid);
        if (null == unManagedVolume) {
            unManagedVolume = new UnManagedVolume();
            unManagedVolume.setId(URIUtil.createId(UnManagedVolume.class));
            unManagedVolume.setNativeGuid(unManagedVolumeNatvieGuid);
            unManagedVolume.setStorageSystemUri(storageSystem.getId());

            if (driverSnapshot.getWwn() == null) {
                unManagedVolume.setWwn(String.format("%s%s", driverSnapshot.getStorageSystemId(), driverSnapshot.getNativeId()));
            } else {
                unManagedVolume.setWwn(driverSnapshot.getWwn());
            }
            newVolume = true;
            unManagedVolumeInformation = new StringSetMap();
            unManagedVolumeCharacteristics = new HashMap<String, String>();
        } else {
            unManagedVolumeInformation = unManagedVolume.getVolumeInformation();
            unManagedVolumeCharacteristics = unManagedVolume.getVolumeCharacterstics();
        }

        unManagedVolume.setLabel(driverSnapshot.getDeviceLabel());
        Boolean isVolumeExported = false;
        unManagedVolumeCharacteristics.put(UnManagedVolume.SupportedVolumeCharacterstics.IS_VOLUME_EXPORTED.toString(), isVolumeExported.toString());

        // Set these default to false. The individual storage discovery will change them if needed.
        unManagedVolumeCharacteristics.put(UnManagedVolume.SupportedVolumeCharacterstics.IS_NONRP_EXPORTED.toString(), FALSE);
        unManagedVolumeCharacteristics.put(UnManagedVolume.SupportedVolumeCharacterstics.IS_RECOVERPOINT_ENABLED.toString(), FALSE);

        StringSet deviceLabel = new StringSet();
        deviceLabel.add(driverSnapshot.getDeviceLabel());
        unManagedVolumeInformation.put(UnManagedVolume.SupportedVolumeInformation.DEVICE_LABEL.toString(),
                deviceLabel);


        if (driverSnapshot.getAccessStatus() != null) {
            StringSet accessState = new StringSet();
            accessState.add(driverSnapshot.getAccessStatus().toString());
            unManagedVolumeInformation.put(UnManagedVolume.SupportedVolumeInformation.ACCESS.toString(), accessState);
        }

        StringSet systemTypes = new StringSet();
        systemTypes.add(storageSystem.getSystemType());
        unManagedVolumeInformation.put(UnManagedVolume.SupportedVolumeInformation.SYSTEM_TYPE.toString(),
                systemTypes);

        StringSet nativeId = new StringSet();
        nativeId.add(driverSnapshot.getNativeId());
        unManagedVolumeInformation.put(UnManagedVolume.SupportedVolumeInformation.NATIVE_ID.toString(),
                nativeId);

        unManagedVolumeCharacteristics.put(
                UnManagedVolume.SupportedVolumeCharacterstics.IS_INGESTABLE.toString(), TRUE);

        unManagedVolume.getVolumeCharacterstics().put(UnManagedVolume.SupportedVolumeCharacterstics.IS_SNAP_SHOT.toString(), TRUE);

        StringSet parentVol = new StringSet();
        parentVol.add(parentUnManagedVolume.getNativeGuid());
        unManagedVolume.getVolumeInformation().put(UnManagedVolume.SupportedVolumeInformation.LOCAL_REPLICA_SOURCE_VOLUME.toString(), parentVol);

        StringSet isSyncActive = new StringSet();
        isSyncActive.add(TRUE);
        unManagedVolume.getVolumeInformation().put(UnManagedVolume.SupportedVolumeInformation.IS_SYNC_ACTIVE.toString(), isSyncActive);

        StringSet isReadOnly = new StringSet();
        Boolean readOnly = Boolean.FALSE;
        if (driverSnapshot.getAccessStatus() != null) {
            readOnly = (driverSnapshot.getAccessStatus().toString()).equals(StorageObject.AccessStatus.READ_ONLY.toString()) ?
                              Boolean.TRUE : Boolean.FALSE;
        }
        isReadOnly.add(readOnly.toString());
        unManagedVolume.getVolumeInformation().put(UnManagedVolume.SupportedVolumeInformation.IS_READ_ONLY.toString(), isReadOnly);

        StringSet techType = new StringSet();
        techType.add(BlockSnapshot.TechnologyType.NATIVE.toString());
        unManagedVolume.getVolumeInformation().put(UnManagedVolume.SupportedVolumeInformation.TECHNOLOGY_TYPE.toString(), techType);

        if (driverSnapshot.getSnapSetId() != null && !driverSnapshot.getSnapSetId().isEmpty()) {
            StringSet snapCgName = new StringSet();
            snapCgName.add(driverSnapshot.getSnapSetId());
            unManagedVolume.getVolumeInformation().put(UnManagedVolume.SupportedVolumeInformation.SNAPSHOT_CONSISTENCY_GROUP_NAME.toString(),
                    snapCgName);
        }

        unManagedVolume.setStoragePoolUri(storagePool.getId());
        StringSet pools = new StringSet();
        pools.add(storagePool.getId().toString());
        unManagedVolumeInformation.put(UnManagedVolume.SupportedVolumeInformation.STORAGE_POOL.toString(), pools);

        StringSet driveTypes = storagePool.getSupportedDriveTypes();
        if (null != driveTypes) {
            unManagedVolumeInformation.put(
                    UnManagedVolume.SupportedVolumeInformation.DISK_TECHNOLOGY.toString(),
                    driveTypes);
        }

        // Set matched vpools the same as parent.
        StringSet parentMatchedVPools = unManagedVolume.getSupportedVpoolUris();
        if (null != parentMatchedVPools) {
            log.debug("Matched Pools : {}", Joiner.on("\t").join(parentMatchedVPools));
        }
        if (null == parentMatchedVPools || parentMatchedVPools.isEmpty()) {
            // Clearn all vpools as no matching vpools found.
            unManagedVolume.getSupportedVpoolUris().clear();
        } else {
            // replace with new StringSet
            unManagedVolume.getSupportedVpoolUris().replace(parentMatchedVPools);
            log.info("Replaced Pools :{}", Joiner.on("\t").join(unManagedVolume.getSupportedVpoolUris()));
        }

        unManagedVolume.setVolumeInformation(unManagedVolumeInformation);

        if (unManagedVolume.getVolumeCharacterstics() == null) {
            unManagedVolume.setVolumeCharacterstics(new StringMap());
        }
        unManagedVolume.getVolumeCharacterstics().replace(unManagedVolumeCharacteristics);

        if (newVolume) {
            unManagedVolumesToCreate.add(unManagedVolume);
        } else {
            unManagedVolumesToUpdate.add(unManagedVolume);
        }

        return unManagedVolume;
    }



}
