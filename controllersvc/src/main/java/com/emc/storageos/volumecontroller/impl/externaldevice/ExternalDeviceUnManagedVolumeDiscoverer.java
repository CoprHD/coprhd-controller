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
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang.mutable.MutableInt;
import org.apache.curator.framework.recipes.locks.InterProcessLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.coordinator.service.Coordinator;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.HostInterface;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.StringSetMap;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedConsistencyGroup;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedExportMask;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.ZoneInfo;
import com.emc.storageos.db.client.model.ZoneInfoMap;
import com.emc.storageos.networkcontroller.impl.NetworkDeviceController;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.plugins.common.PartitionManager;
import com.emc.storageos.storagedriver.BlockStorageDriver;
import com.emc.storageos.storagedriver.HostExportInfo;
import com.emc.storageos.storagedriver.model.Initiator;
import com.emc.storageos.storagedriver.model.StorageObject;
import com.emc.storageos.storagedriver.model.StoragePort;
import com.emc.storageos.storagedriver.model.StorageSystem;
import com.emc.storageos.storagedriver.model.StorageVolume;
import com.emc.storageos.storagedriver.model.VolumeClone;
import com.emc.storageos.storagedriver.model.VolumeConsistencyGroup;
import com.emc.storageos.storagedriver.model.VolumeSnapshot;
import com.emc.storageos.util.NetworkUtil;
import com.emc.storageos.volumecontroller.impl.NativeGUIDGenerator;
import com.emc.storageos.volumecontroller.impl.plugins.ExternalDeviceCommunicationInterface;
import com.emc.storageos.volumecontroller.impl.utils.DiscoveryUtils;
import com.emc.storageos.volumecontroller.impl.utils.ExportMaskUtils;
import com.google.common.base.Joiner;

public class ExternalDeviceUnManagedVolumeDiscoverer {
    private static Logger log = LoggerFactory.getLogger(ExternalDeviceUnManagedVolumeDiscoverer.class);
    private static final String TRUE = "true";
    private static final String FALSE = "false";
    private static final String UNMANAGED_VOLUME = "UnManagedVolume";
    private static final String UNMANAGED_CONSISTENCY_GROUP = "UnManagedConsistencyGroup";
    private static final String UNMANAGED_EXPORT_MASK = "UnManagedExportMask";
    private static final String UNMANAGED_DISCOVERY_LOCK = "UnManagedObjectsDiscoveryLock-";
    public static final long UNMANAGED_DISCOVERY_LOCK_TIMEOUT= 3 * 60; // set to 3 minutes

    private NetworkDeviceController networkDeviceController;
    private CoordinatorClient coordinator;

    public void setNetworkDeviceController(
            NetworkDeviceController networkDeviceController) {
        this.networkDeviceController = networkDeviceController;
    }

    public void setCoordinator(CoordinatorClient coordinator) {
        this.coordinator = coordinator;
    }

    public void discoverUnManagedBlockObjects(BlockStorageDriver driver, com.emc.storageos.db.client.model.StorageSystem storageSystem,
                                              DbClient dbClient, PartitionManager partitionManager) {

        Set<URI> allCurrentUnManagedVolumeUris = new HashSet<>();
        Set<URI> allCurrentUnManagedCgURIs = new HashSet<>();
        MutableInt lastPage = new MutableInt(0);
        MutableInt nextPage = new MutableInt(0);
        List<UnManagedVolume> unManagedVolumesToCreate = new ArrayList<UnManagedVolume>();
        List<UnManagedVolume> unManagedVolumesToUpdate = new ArrayList<UnManagedVolume>();
        List<UnManagedConsistencyGroup> unManagedCGToUpdate;
        Map<String, UnManagedConsistencyGroup> unManagedCGToUpdateMap = new HashMap<>();

        // We support only single export mask for host-array combination.
        // If we find that storage system has volumes which are exported to the same host through
        // different initiators or different array ports (we cannot create single UnManaged export
        // mask for the host and the array in this case), we won't discover exports to this
        // host on the array; we discover only volumes.
        // The result of this limitation is that it could happen that for some volumes we are able to
        // discover all their host exports;
        // for some volumes we will be able to discover their exports to subset of hosts;
        // for some volumes we may not be able to discover their exports to hosts.
        Set<String> invalidExportHosts = new HashSet<>(); // set of hosts for which we cannot build single export mask
                                                          // for exported array volumes

        // get inter-process lock for exclusive discovery of unmanaged objects for a given system
        // lock is backed by curator's InterProcessMutex.
        InterProcessLock lock;
        String lockName = UNMANAGED_DISCOVERY_LOCK + storageSystem.getSystemType() + "-" + storageSystem.getNativeId();
        try {
            lock = coordinator.getLock(lockName);
            boolean lockAcquired = lock.acquire(UNMANAGED_DISCOVERY_LOCK_TIMEOUT, TimeUnit.SECONDS);
            if (lockAcquired) {
                log.info("Acquired lock {} for storage system {} .", lockName, storageSystem.getNativeId());
            } else {
                log.info("Failed to acquire lock {} for storage system {} .", lockName, storageSystem.getNativeId());
                return;
            }
        } catch (Exception ex) {
            log.error("Error processing unmanaged discovery for storage system: {}. Failed to get lock {} for this operation.",
                       storageSystem.getNativeId(), lockName, ex);
            return;
        }
        log.info("Started discovery of UnManagedVolumes for system {}", storageSystem.getId());

        try {
            // We need to deactivate all old unManaged export masks for this array. Each export discovery starts a new.
            // Otherwise, we cannot distinguish between stale host masks and host mask discovered for volumes on the previous pages.
            DiscoveryUtils.markInActiveUnManagedExportMask(storageSystem.getId(), new HashSet<URI>(),
                    dbClient, partitionManager);
            // prepare storage system
            StorageSystem driverStorageSystem = ExternalDeviceCommunicationInterface.initStorageSystem(storageSystem);
            do {
                // Map of host FQDN to list of export info objects for unManaged volumes exported to this host
                Map<String, List<HostExportInfo>> hostToUnManagedVolumeExportInfoMap = new HashMap<>();
                // Map of host FQDN to list of export info objects for managed volumes exported to this host
                Map<String, List<HostExportInfo>> hostToManagedVolumeExportInfoMap = new HashMap<>();
                Map<String, List<HostExportInfo>> hostToVolumeExportInfoMap = new HashMap<>();

                List<StorageVolume> driverVolumes = new ArrayList<>();
                Map<String, URI> unManagedVolumeNativeIdToUriMap = new HashMap<>();
                Map<String, URI> managedVolumeNativeIdToUriMap = new HashMap<>();

                log.info("Processing page {} ", nextPage);
                driver.getStorageVolumes(driverStorageSystem, driverVolumes, nextPage);
                log.info("Volume count on this page {} ", driverVolumes.size());

                for (StorageVolume driverVolume : driverVolumes) {
                    UnManagedVolume unManagedVolume = null;
                    try {
                        com.emc.storageos.db.client.model.StoragePool storagePool = getStoragePoolOfUnManagedVolume(storageSystem, driverVolume, dbClient);
                        if (null == storagePool) {
                            log.error("Skipping unManaged volume discovery as the volume {} storage pool doesn't exist in ViPR", driverVolume.getNativeId());
                            continue;
                        }
                        String managedVolumeNativeGuid = NativeGUIDGenerator.generateNativeGuidForVolumeOrBlockSnapShot(
                                storageSystem.getNativeGuid(), driverVolume.getNativeId());
                        Volume viprVolume = DiscoveryUtils.checkStorageVolumeExistsInDB(dbClient, managedVolumeNativeGuid);
                        if (null != viprVolume) {
                            log.info("Skipping volume {} as it is already managed by ViPR", managedVolumeNativeGuid);

                            // get export data for managed volume to process later --- we need to collect export data for
                            // managed volume
                            managedVolumeNativeIdToUriMap.put(driverVolume.getNativeId(), viprVolume.getId());
                            getVolumeExportInfo(driver, driverVolume, hostToVolumeExportInfoMap);
                            getExportInfoForManagedVolumeReplicas(managedVolumeNativeIdToUriMap, hostToVolumeExportInfoMap,
                                    dbClient, storageSystem, viprVolume, driverVolume, driver);
                            continue;
                        }

                        unManagedVolume = createUnManagedVolume(driverVolume, storageSystem, storagePool, unManagedVolumesToCreate,
                                unManagedVolumesToUpdate, dbClient);
                        unManagedVolumeNativeIdToUriMap.put(driverVolume.getNativeId(), unManagedVolume.getId());

                        // if the volume is associated with a CG, set up the unManaged CG
                        if (driverVolume.getConsistencyGroup() != null && !driverVolume.getConsistencyGroup().isEmpty()) {
                            addObjectToUnManagedConsistencyGroup(storageSystem, driverVolume.getConsistencyGroup(), unManagedVolume,
                                    allCurrentUnManagedCgURIs, unManagedCGToUpdateMap, driver, dbClient);
                        } else {
                            // Make sure the unManagedVolume object does not contain CG information from previous discovery
                            unManagedVolume.getVolumeCharacterstics().put(
                                    UnManagedVolume.SupportedVolumeCharacterstics.IS_VOLUME_ADDED_TO_CONSISTENCYGROUP.toString(), Boolean.FALSE.toString());
                            // remove uri of the unManaged CG in the unManaged volume object
                            unManagedVolume.getVolumeInformation().remove(UnManagedVolume.SupportedVolumeInformation.UNMANAGED_CONSISTENCY_GROUP_URI.toString());
                        }

                        allCurrentUnManagedVolumeUris.add(unManagedVolume.getId());
                        getVolumeExportInfo(driver, driverVolume, hostToVolumeExportInfoMap);

                        Set<URI> unManagedSnaphotUris = processUnManagedSnapshots(driverVolume, unManagedVolume, storageSystem, storagePool,
                                unManagedVolumesToCreate,
                                unManagedVolumesToUpdate,
                                allCurrentUnManagedCgURIs, unManagedCGToUpdateMap,
                                unManagedVolumeNativeIdToUriMap, hostToVolumeExportInfoMap,
                                driver, dbClient);

                        allCurrentUnManagedVolumeUris.addAll(unManagedSnaphotUris);

                        Set<URI> unManagedCloneUris = processUnManagedClones(driverVolume, unManagedVolume, storageSystem, storagePool,
                                unManagedVolumesToCreate,
                                unManagedVolumesToUpdate,
                                allCurrentUnManagedCgURIs, unManagedCGToUpdateMap,
                                unManagedVolumeNativeIdToUriMap, hostToVolumeExportInfoMap,
                                driver, dbClient);

                        allCurrentUnManagedVolumeUris.addAll(unManagedCloneUris);

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

                // Process export data for volumes
                processExportData(driver, storageSystem, unManagedVolumeNativeIdToUriMap,
                        managedVolumeNativeIdToUriMap,
                        hostToVolumeExportInfoMap, hostToManagedVolumeExportInfoMap,
                        invalidExportHosts, dbClient, partitionManager);
            } while (!nextPage.equals(lastPage));

            if (!unManagedCGToUpdateMap.isEmpty()) {
                unManagedCGToUpdate = new ArrayList<>(unManagedCGToUpdateMap.values());
                partitionManager.updateAndReIndexInBatches(unManagedCGToUpdate,
                        unManagedCGToUpdate.size(), dbClient, UNMANAGED_CONSISTENCY_GROUP);
                unManagedCGToUpdate.clear();
            }

            log.info("Processed {} unmanged objects.", allCurrentUnManagedVolumeUris.size());
            // Process those active unManaged volume objects available in database but not in newly discovered items, to mark them inactive.
            DiscoveryUtils.markInActiveUnManagedVolumes(storageSystem, allCurrentUnManagedVolumeUris, dbClient, partitionManager);

            // Process those active unManaged consistency group objects available in database but not in newly discovered items, to mark them
            // inactive.
            DiscoveryUtils.performUnManagedConsistencyGroupsBookKeeping(storageSystem, allCurrentUnManagedCgURIs, dbClient, partitionManager);
        } catch (Exception ex) {
            log.error("Error processing unmanaged discovery for storage system: {}. Error on page: {}.",
                    storageSystem.getNativeId(), nextPage.toString(), ex);
        } finally {
            // release lock
            try {
                lock.release();
                log.info("Released lock for storage system {}", storageSystem.getNativeId());
            } catch (Exception e) {
                log.error("Failed to release  Lock {} : {}", lockName, e.getMessage());
            }
        }

    }

    /**
     * Create or update unManaged volume for a given driver volume.
     *
     * @param driverVolume storage system volume
     * @param storageSystem storage system for unManaged volume
     * @param storagePool  storage pool for unManaged volume
     * @param unManagedVolumesToCreate list of new unManaged volumes
     * @param unManagedVolumesToUpdate list of unManaged volumes to update
     * @param dbClient
     * @return
     */
    private UnManagedVolume createUnManagedVolume(StorageVolume driverVolume, com.emc.storageos.db.client.model.StorageSystem storageSystem,
                                                  com.emc.storageos.db.client.model.StoragePool storagePool,
                                                  List<UnManagedVolume> unManagedVolumesToCreate, List<UnManagedVolume> unManagedVolumesToUpdate,
                                                  DbClient dbClient) {

        boolean newVolume = false;
        StringSetMap unManagedVolumeInformation = null;
        StringMap unManagedVolumeCharacteristics = null;

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
            unManagedVolumeCharacteristics = new StringMap();

            unManagedVolume.setVolumeInformation(unManagedVolumeInformation);
            unManagedVolume.setVolumeCharacterstics(unManagedVolumeCharacteristics);
        } else {
            unManagedVolumeInformation = unManagedVolume.getVolumeInformation();
            unManagedVolumeCharacteristics = unManagedVolume.getVolumeCharacterstics();

            // cleanup relationships from previous discoveries, will set them according to this discovery
            unManagedVolumeCharacteristics.put(UnManagedVolume.SupportedVolumeCharacterstics.HAS_REPLICAS.toString(), FALSE);
            unManagedVolumeInformation.remove(UnManagedVolume.SupportedVolumeInformation.SNAPSHOTS.toString());
            unManagedVolumeInformation.remove(UnManagedVolume.SupportedVolumeInformation.FULL_COPIES.toString());
            // Clear old export mask information
            unManagedVolume.getUnmanagedExportMasks().clear();
        }

        unManagedVolume.setLabel(driverVolume.getDeviceLabel());
        Boolean isVolumeExported = false;
        unManagedVolumeCharacteristics.put(UnManagedVolume.SupportedVolumeCharacterstics.IS_VOLUME_EXPORTED.toString(), isVolumeExported.toString());

        // Set these default to false.
        unManagedVolumeCharacteristics.put(UnManagedVolume.SupportedVolumeCharacterstics.IS_NONRP_EXPORTED.toString(), FALSE);
        unManagedVolumeCharacteristics.put(UnManagedVolume.SupportedVolumeCharacterstics.IS_RECOVERPOINT_ENABLED.toString(), FALSE);

        StringSet deviceLabel = new StringSet();
        deviceLabel.add(driverVolume.getDeviceLabel());
        unManagedVolumeInformation.remove(UnManagedVolume.SupportedVolumeInformation.DEVICE_LABEL.toString());
        unManagedVolumeInformation.put(UnManagedVolume.SupportedVolumeInformation.DEVICE_LABEL.toString(),
                deviceLabel);

        StringSet accessState = new StringSet();
        accessState.add(driverVolume.getAccessStatus().toString());
        unManagedVolumeInformation.remove(UnManagedVolume.SupportedVolumeInformation.ACCESS.toString());
        unManagedVolumeInformation.put(UnManagedVolume.SupportedVolumeInformation.ACCESS.toString(), accessState);

        StringSet provCapacity = new StringSet();
        provCapacity.add(String.valueOf(driverVolume.getProvisionedCapacity()));
        unManagedVolumeInformation.remove(UnManagedVolume.SupportedVolumeInformation.PROVISIONED_CAPACITY.toString());
        unManagedVolumeInformation.put(UnManagedVolume.SupportedVolumeInformation.PROVISIONED_CAPACITY.toString(),
                provCapacity);

        StringSet allocatedCapacity = new StringSet();
        allocatedCapacity.add(String.valueOf(driverVolume.getAllocatedCapacity()));
        unManagedVolumeInformation.remove(UnManagedVolume.SupportedVolumeInformation.ALLOCATED_CAPACITY.toString());
        unManagedVolumeInformation.put(UnManagedVolume.SupportedVolumeInformation.ALLOCATED_CAPACITY.toString(),
                allocatedCapacity);

        StringSet systemTypes = new StringSet();
        systemTypes.add(storageSystem.getSystemType());
        unManagedVolumeInformation.remove(UnManagedVolume.SupportedVolumeInformation.SYSTEM_TYPE.toString());
        unManagedVolumeInformation.put(UnManagedVolume.SupportedVolumeInformation.SYSTEM_TYPE.toString(),
                systemTypes);

        StringSet nativeId = new StringSet();
        nativeId.add(driverVolume.getNativeId());
        unManagedVolumeInformation.remove(UnManagedVolume.SupportedVolumeInformation.NATIVE_ID.toString());
        unManagedVolumeInformation.put(UnManagedVolume.SupportedVolumeInformation.NATIVE_ID.toString(),
                nativeId);

        unManagedVolumeCharacteristics.put(
                UnManagedVolume.SupportedVolumeCharacterstics.IS_INGESTABLE.toString(), TRUE);

        unManagedVolumeCharacteristics.put(UnManagedVolume.SupportedVolumeCharacterstics.IS_THINLY_PROVISIONED.toString(),
                driverVolume.getThinlyProvisioned().toString());

        unManagedVolume.setStoragePoolUri(storagePool.getId());
        StringSet pools = new StringSet();
        pools.add(storagePool.getId().toString());
        unManagedVolumeInformation.remove(UnManagedVolume.SupportedVolumeInformation.STORAGE_POOL.toString());
        unManagedVolumeInformation.put(UnManagedVolume.SupportedVolumeInformation.STORAGE_POOL.toString(), pools);
        StringSet driveTypes = storagePool.getSupportedDriveTypes();
        if (null != driveTypes) {
            unManagedVolumeInformation.remove(UnManagedVolume.SupportedVolumeInformation.DISK_TECHNOLOGY.toString());
            unManagedVolumeInformation.put(UnManagedVolume.SupportedVolumeInformation.DISK_TECHNOLOGY.toString(), driveTypes);
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

        if (newVolume) {
            unManagedVolumesToCreate.add(unManagedVolume);
        } else {
            unManagedVolumesToUpdate.add(unManagedVolume);
        }

        return unManagedVolume;
    }

    /**
     * Get storage pool of unManaged object.
     *
     * @param storageSystem
     * @param driverVolume
     * @param dbClient
     * @return
     */
    private com.emc.storageos.db.client.model.StoragePool getStoragePoolOfUnManagedVolume(com.emc.storageos.db.client.model.StorageSystem storageSystem,
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


    /**
     * Add storage object to unManaged consistency group.
     * Sets consistency group related attributes in the object and adds object to the list of unManaged
     * objects in the unManaged consistency group instance.
     *
     * @param storageSystem storage system of the object
     * @param cgNativeId  native id of umanaged consistency group
     * @param unManagedVolume unManaged object
     * @param allCurrentUnManagedCgURIs set of unManaged CG uris found in the current discovery
     * @param unManagedCGToUpdateMap map of unManaged CG GUID to unManaged CG instance
     * @param driver storage driver
     * @param dbClient
     * @throws Exception
     */
    private void addObjectToUnManagedConsistencyGroup(com.emc.storageos.db.client.model.StorageSystem storageSystem,
                                                      String cgNativeId, UnManagedVolume unManagedVolume,
                                                      Set<URI> allCurrentUnManagedCgURIs,
                                                      Map<String, UnManagedConsistencyGroup> unManagedCGToUpdateMap,
                                                      BlockStorageDriver driver, DbClient dbClient) throws Exception {
        log.info("UnManaged storage object {} belongs to consistency group {} on the array", unManagedVolume.getLabel(),
                cgNativeId);
        // determine the native guid for the unManaged CG
        String unManagedCGNativeGuid = NativeGUIDGenerator.generateNativeGuidForCG(storageSystem.getNativeGuid(),
                cgNativeId);
        log.info("UnManaged consistency group has nativeGuid {} ", unManagedCGNativeGuid);
        // determine if the unManaged CG already exists in the unManagedCGToUpdateMap or in the database
        // if the the unManaged CG is not in either create a new one
        UnManagedConsistencyGroup unManagedCG = null;
        if (unManagedCGToUpdateMap.containsKey(unManagedCGNativeGuid)) {
            unManagedCG = unManagedCGToUpdateMap.get(unManagedCGNativeGuid);
            log.info("UnManaged consistency group {} was previously added to the unManagedCGToUpdateMap", unManagedCG.getNativeGuid());
        } else {
            unManagedCG = DiscoveryUtils.checkUnManagedCGExistsInDB(dbClient, unManagedCGNativeGuid);
            if (null == unManagedCG) {
                // unManaged CG does not exist in the database, create it
                VolumeConsistencyGroup driverCG = driver.getStorageObject(storageSystem.getNativeId(), cgNativeId,
                        VolumeConsistencyGroup.class);
                if (driverCG != null) {
                    unManagedCG = createUnManagedCG(driverCG, storageSystem, dbClient);
                    log.info("Created unManaged consistency group: {} with nativeGuid {}",
                            unManagedCG.getId().toString(), unManagedCG.getNativeGuid());
                } else {
                    String msg = String.format("Driver VolumeConsistencyGroup with native id %s does not exist on storage system %s",
                            cgNativeId, storageSystem.getNativeId());
                    log.error(msg);
                    throw new Exception(msg);
                }

            } else {
                log.info("UnManaged consistency group {} was previously added to the database (by previous unManaged discovery).", unManagedCG.getNativeGuid());
                // clean out the list of unManaged objects if this unManaged cg was already
                // in the database and its first time being used in this discovery operation
                // the list should be re-populated by the current discovery operation
                log.info("Cleaning out unManaged object map from unManaged consistency group: {}", unManagedCG.getNativeGuid());
                unManagedCG.getUnManagedVolumesMap().clear();
            }
        }
        log.info("Adding unManaged storage object {} to unManaged consistency group {}", unManagedVolume.getLabel(), unManagedCG.getNativeGuid());
        // Update the unManagedVolume object with CG information
        unManagedVolume.getVolumeCharacterstics().put(UnManagedVolume.SupportedVolumeCharacterstics.IS_VOLUME_ADDED_TO_CONSISTENCYGROUP.toString(),
                Boolean.TRUE.toString());
        // set the uri of the unManaged CG in the unManaged volume object
        unManagedVolume.getVolumeInformation().remove(UnManagedVolume.SupportedVolumeInformation.UNMANAGED_CONSISTENCY_GROUP_URI.toString());
        unManagedVolume.getVolumeInformation().put(UnManagedVolume.SupportedVolumeInformation.UNMANAGED_CONSISTENCY_GROUP_URI.toString(),
                unManagedCG.getId().toString());
        // add the unManaged volume object to the unManaged CG
        unManagedCG.getUnManagedVolumesMap().put(unManagedVolume.getNativeGuid(), unManagedVolume.getId().toString());
        // add the unManaged CG to the map of unManaged CGs to be updated in the database once all volumes have been processed
        unManagedCGToUpdateMap.put(unManagedCGNativeGuid, unManagedCG);
        // add the unManaged CG to the current set of CGs being discovered on the array. This is for book keeping later.
        allCurrentUnManagedCgURIs.add(unManagedCG.getId());
    }

    /**
     * Create unManaged CG for a given driver CG.
     *
     * @param driverCG
     * @param storageSystem
     * @param dbClient
     * @return
     */
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
        dbClient.createObject(unManagedCG);

        return unManagedCG;
    }

    /**
     * Process snapshots of unManaged volume.
     * Check if unManaged snapshot should be created and create unManaged volume indtance for a snpa in such a case.
     * Add unManaged snapshot to parent volume CG if needed and update the snap with parent volume CG information.
     *
     * @param driverVolume driver volume for snap parent volume.
     * @param unManagedParentVolume unManaged parent volume
     * @param storageSystem
     * @param storagePool
     * @param unManagedVolumesToCreate
     * @param unManagedVolumesToUpdate
     * @param allCurrentUnManagedCgURIs
     * @param unManagedCGToUpdateMap book keeping map of CG GUID to CG  instance
     * @param driver storage driver for the storage array
     * @param dbClient
     * @return
     * @throws Exception
     */
    private Set<URI> processUnManagedSnapshots(StorageVolume driverVolume, UnManagedVolume unManagedParentVolume,
                                               com.emc.storageos.db.client.model.StorageSystem storageSystem,
                                               com.emc.storageos.db.client.model.StoragePool storagePool,
                                               List<UnManagedVolume> unManagedVolumesToCreate, List<UnManagedVolume> unManagedVolumesToUpdate,
                                               Set<URI> allCurrentUnManagedCgURIs,
                                               Map<String, UnManagedConsistencyGroup> unManagedCGToUpdateMap,
                                               Map<String, URI> unManagedVolumeNativeIdToUriMap,
                                               Map<String, List<HostExportInfo>> hostToUnManagedVolumeExportInfoMap,
                                               BlockStorageDriver driver, DbClient dbClient) throws Exception {

        log.info("Processing snapshots for volume {} ", unManagedParentVolume.getNativeGuid());
        Set<URI> snapshotUris = new HashSet<>();
        List<VolumeSnapshot> driverSnapshots = driver.getVolumeSnapshots(driverVolume);
        if (driverSnapshots == null || driverSnapshots.isEmpty()) {
            log.info("There are no snapshots for volume {} ", unManagedParentVolume.getNativeGuid());
        } else {
            log.info("Snapshots for unManaged volume {}:" + Joiner.on("\t").join(driverSnapshots), unManagedParentVolume.getNativeGuid());
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
                String isParentVolumeInCG =
                        unManagedParentVolume.getVolumeCharacterstics().get(UnManagedVolume.SupportedVolumeCharacterstics.IS_VOLUME_ADDED_TO_CONSISTENCYGROUP.toString());
                if (isParentVolumeInCG.equals(Boolean.TRUE.toString())) {
                    // add snapshot to parent volume unManaged consistency group, update snapshot with parent volume CG information.
                    addObjectToUnManagedConsistencyGroup(storageSystem, driverVolume.getConsistencyGroup(), unManagedSnap,
                            allCurrentUnManagedCgURIs, unManagedCGToUpdateMap, driver, dbClient);
                }

                // get export data for the snapshot
                unManagedVolumeNativeIdToUriMap.put(driverSnapshot.getNativeId(), unManagedSnap.getId());
                getSnapshotExportInfo(driver, driverSnapshot, hostToUnManagedVolumeExportInfoMap);
            }
            if (!unManagedSnaps.isEmpty()) {
                // set the HAS_REPLICAS property
                unManagedParentVolume.getVolumeCharacterstics().put(UnManagedVolume.SupportedVolumeCharacterstics.HAS_REPLICAS.toString(), TRUE);
                StringSetMap unManagedVolumeInformation = unManagedParentVolume.getVolumeInformation();
                log.info("New unManaged snaps for unManaged volume {}:" + Joiner.on("\t").join(unManagedSnaps), unManagedParentVolume.getNativeGuid());
                if (unManagedVolumeInformation.containsKey(UnManagedVolume.SupportedVolumeInformation.SNAPSHOTS.toString())) {
                    log.info("Old unManaged snaps for unManaged volume {}:" + Joiner.on("\t").join(unManagedVolumeInformation.get(
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
            }
        }
        return snapshotUris;
    }

    private Set<URI> processUnManagedClones(StorageVolume driverVolume, UnManagedVolume unManagedParentVolume,
                                               com.emc.storageos.db.client.model.StorageSystem storageSystem,
                                               com.emc.storageos.db.client.model.StoragePool storagePool,
                                               List<UnManagedVolume> unManagedVolumesToCreate, List<UnManagedVolume> unManagedVolumesToUpdate,
                                               Set<URI> allCurrentUnManagedCgURIs,
                                               Map<String, UnManagedConsistencyGroup> unManagedCGToUpdateMap,
                                               Map<String, URI> unManagedVolumeNativeIdToUriMap,
                                               Map<String, List<HostExportInfo>> hostToUnManagedVolumeExportInfoMap,
                                               BlockStorageDriver driver, DbClient dbClient) throws Exception {

        log.info("Processing clones for volume {} ", unManagedParentVolume.getNativeGuid());
        Set<URI> cloneUris = new HashSet<>();
        List<VolumeClone> driverClones = driver.getVolumeClones(driverVolume);
        if (driverClones == null || driverClones.isEmpty()) {
            log.info("There are no clones for volume {} ", unManagedParentVolume.getNativeGuid());
        } else {
            log.info("Clones for unManaged volume {}:" + Joiner.on("\t").join(driverClones), unManagedParentVolume.getNativeGuid());
            StringSet unManagedClones = new StringSet();
            for (VolumeClone driverClone : driverClones) {
                String managedCloneNativeGuid = NativeGUIDGenerator.generateNativeGuidForVolumeOrBlockSnapShot(
                        storageSystem.getNativeGuid(), driverClone.getNativeId());
                Volume viprClone = DiscoveryUtils.checkStorageVolumeExistsInDB(dbClient, managedCloneNativeGuid);
                if (null != viprClone) {
                    log.info("Skipping clone {} as it is already managed by ViPR", managedCloneNativeGuid);
                    continue;
                }

                String unManagedCloneNatvieGuid = NativeGUIDGenerator.generateNativeGuidForPreExistingVolume(
                        storageSystem.getNativeGuid(), driverClone.getNativeId());
                UnManagedVolume unManagedClone = createUnManagedClone(driverClone, unManagedParentVolume, storageSystem, storagePool,
                        unManagedVolumesToCreate,
                        unManagedVolumesToUpdate, dbClient);
                cloneUris.add(unManagedClone.getId());
                unManagedClones.add(unManagedCloneNatvieGuid);

                // Check if this clone is for a volume in consistency group on device.
                String isParentVolumeInCG =
                        unManagedParentVolume.getVolumeCharacterstics().get(UnManagedVolume.SupportedVolumeCharacterstics.IS_VOLUME_ADDED_TO_CONSISTENCYGROUP.toString());
                if (isParentVolumeInCG.equals(Boolean.TRUE.toString())) {
                    // We do not add clones to parent volumes CG (the same as in the green field: verified with VMAX/VNX clones)
                    log.info("Clone {} is for volume in CG. ", managedCloneNativeGuid);
                }

                // get export data for the clone
                unManagedVolumeNativeIdToUriMap.put(driverClone.getNativeId(), unManagedClone.getId());
                getCloneExportInfo(driver, driverClone, hostToUnManagedVolumeExportInfoMap);
            }
            if (!unManagedClones.isEmpty()) {
                // set the HAS_REPLICAS property
                unManagedParentVolume.getVolumeCharacterstics().put(UnManagedVolume.SupportedVolumeCharacterstics.HAS_REPLICAS.toString(), TRUE);
                StringSetMap unManagedVolumeInformation = unManagedParentVolume.getVolumeInformation();
                log.info("New unManaged clones for unManaged volume {}:" + Joiner.on("\t").join(unManagedClones), unManagedParentVolume.getNativeGuid());
                if (unManagedVolumeInformation.containsKey(UnManagedVolume.SupportedVolumeInformation.FULL_COPIES.toString())) {
                    log.info("Old unManaged clones for unManaged volume {}:" + Joiner.on("\t").join(unManagedVolumeInformation.get(
                            UnManagedVolume.SupportedVolumeInformation.FULL_COPIES.toString())), unManagedParentVolume.getNativeGuid());
                    // replace with new StringSet
                    unManagedVolumeInformation.get(
                            UnManagedVolume.SupportedVolumeInformation.FULL_COPIES.toString()).replace(unManagedClones);
                    log.info("Replaced snaps :" + Joiner.on("\t").join(unManagedVolumeInformation.get(
                            UnManagedVolume.SupportedVolumeInformation.FULL_COPIES.toString())));
                }
                else {
                    unManagedVolumeInformation.put(
                            UnManagedVolume.SupportedVolumeInformation.FULL_COPIES.toString(), unManagedClones);
                }
            } else {
                log.info("All clones for volume {} are already managed.", unManagedParentVolume.getNativeGuid());
            }
        }
        return cloneUris;
    }



    /**
     * Create new or update existing unManaged snapshot with unManaged snapshot discovery data.
     *
     * @param driverSnapshot
     * @param parentUnManagedVolume
     * @param storageSystem
     * @param storagePool
     * @param unManagedVolumesToCreate
     * @param unManagedVolumesToUpdate
     * @param dbClient
     * @return
     */
    private UnManagedVolume createUnManagedSnapshot(VolumeSnapshot driverSnapshot, UnManagedVolume parentUnManagedVolume,
                                                    com.emc.storageos.db.client.model.StorageSystem storageSystem,
                                                    com.emc.storageos.db.client.model.StoragePool storagePool,
                                                    List<UnManagedVolume> unManagedVolumesToCreate, List<UnManagedVolume> unManagedVolumesToUpdate,
                                                    DbClient dbClient) {
        // We process unManaged snapshot as unManaged volume
        boolean newVolume = false;
        StringSetMap unManagedVolumeInformation = null;
        StringMap unManagedVolumeCharacteristics = null;

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
            unManagedVolumeCharacteristics = new StringMap();

            unManagedVolume.setVolumeInformation(unManagedVolumeInformation);
            unManagedVolume.setVolumeCharacterstics(unManagedVolumeCharacteristics);
        } else {
            unManagedVolumeInformation = unManagedVolume.getVolumeInformation();
            unManagedVolumeCharacteristics = unManagedVolume.getVolumeCharacterstics();

            // cleanup relationships from previous discoveries, we will set them according to this discovery
            // Make sure the unManagedVolume snapshot object does not contain parent CG information from previous discovery
            unManagedVolumeCharacteristics.put(
                    UnManagedVolume.SupportedVolumeCharacterstics.IS_VOLUME_ADDED_TO_CONSISTENCYGROUP.toString(), Boolean.FALSE.toString());
            // remove uri of the unManaged CG in the unManaged volume object
            unManagedVolumeInformation.remove(UnManagedVolume.SupportedVolumeInformation.UNMANAGED_CONSISTENCY_GROUP_URI.toString());
            // Clean old data for replication group name
            unManagedVolumeInformation.remove(UnManagedVolume.SupportedVolumeInformation.SNAPSHOT_CONSISTENCY_GROUP_NAME.toString());
            // Clear old export mask information
            unManagedVolume.getUnmanagedExportMasks().clear();
        }

        unManagedVolume.setLabel(driverSnapshot.getDeviceLabel());
        Boolean isVolumeExported = false;
        unManagedVolumeCharacteristics.put(UnManagedVolume.SupportedVolumeCharacterstics.IS_VOLUME_EXPORTED.toString(), isVolumeExported.toString());

        // Set these default to false. The individual storage discovery will change them if needed.
        unManagedVolumeCharacteristics.put(UnManagedVolume.SupportedVolumeCharacterstics.IS_NONRP_EXPORTED.toString(), FALSE);
        unManagedVolumeCharacteristics.put(UnManagedVolume.SupportedVolumeCharacterstics.IS_RECOVERPOINT_ENABLED.toString(), FALSE);

        StringSet deviceLabel = new StringSet();
        deviceLabel.add(driverSnapshot.getDeviceLabel());
        unManagedVolumeInformation.remove(UnManagedVolume.SupportedVolumeInformation.DEVICE_LABEL.toString());
        unManagedVolumeInformation.put(UnManagedVolume.SupportedVolumeInformation.DEVICE_LABEL.toString(),
                deviceLabel);


        if (driverSnapshot.getAccessStatus() != null) {
            StringSet accessState = new StringSet();
            accessState.add(driverSnapshot.getAccessStatus().toString());
            unManagedVolumeInformation.remove(UnManagedVolume.SupportedVolumeInformation.ACCESS.toString());
            unManagedVolumeInformation.put(UnManagedVolume.SupportedVolumeInformation.ACCESS.toString(), accessState);
        }

        StringSet systemTypes = new StringSet();
        systemTypes.add(storageSystem.getSystemType());
        unManagedVolumeInformation.remove(UnManagedVolume.SupportedVolumeInformation.SYSTEM_TYPE.toString());
        unManagedVolumeInformation.put(UnManagedVolume.SupportedVolumeInformation.SYSTEM_TYPE.toString(),
                systemTypes);

        StringSet nativeId = new StringSet();
        nativeId.add(driverSnapshot.getNativeId());
        unManagedVolumeInformation.remove(UnManagedVolume.SupportedVolumeInformation.NATIVE_ID.toString());
        unManagedVolumeInformation.put(UnManagedVolume.SupportedVolumeInformation.NATIVE_ID.toString(),
                nativeId);

        unManagedVolumeCharacteristics.put(
                UnManagedVolume.SupportedVolumeCharacterstics.IS_INGESTABLE.toString(), TRUE);

        unManagedVolumeCharacteristics.put(UnManagedVolume.SupportedVolumeCharacterstics.IS_SNAP_SHOT.toString(), TRUE);

        StringSet parentVol = new StringSet();
        parentVol.add(parentUnManagedVolume.getNativeGuid());
        unManagedVolumeInformation.remove(UnManagedVolume.SupportedVolumeInformation.LOCAL_REPLICA_SOURCE_VOLUME.toString());
        unManagedVolumeInformation.put(UnManagedVolume.SupportedVolumeInformation.LOCAL_REPLICA_SOURCE_VOLUME.toString(), parentVol);

        StringSet isSyncActive = new StringSet();
        isSyncActive.add(TRUE);
        unManagedVolumeInformation.remove(UnManagedVolume.SupportedVolumeInformation.IS_SYNC_ACTIVE.toString());
        unManagedVolumeInformation.put(UnManagedVolume.SupportedVolumeInformation.IS_SYNC_ACTIVE.toString(), isSyncActive);

        StringSet isReadOnly = new StringSet();
        Boolean readOnly = Boolean.FALSE;
        if (driverSnapshot.getAccessStatus() != null) {
            readOnly = (driverSnapshot.getAccessStatus().toString()).equals(StorageObject.AccessStatus.READ_ONLY.toString()) ?
                              Boolean.TRUE : Boolean.FALSE;
        }
        isReadOnly.add(readOnly.toString());
        unManagedVolumeInformation.remove(UnManagedVolume.SupportedVolumeInformation.IS_READ_ONLY.toString());
        unManagedVolumeInformation.put(UnManagedVolume.SupportedVolumeInformation.IS_READ_ONLY.toString(), isReadOnly);

        StringSet techType = new StringSet();
        techType.add(BlockSnapshot.TechnologyType.NATIVE.toString());
        unManagedVolumeInformation.remove(UnManagedVolume.SupportedVolumeInformation.TECHNOLOGY_TYPE.toString());
        unManagedVolumeInformation.put(UnManagedVolume.SupportedVolumeInformation.TECHNOLOGY_TYPE.toString(), techType);

        // set snapshot consistency group information in the unmanged snapshot object
        String isParentVolumeInCG =
                parentUnManagedVolume.getVolumeCharacterstics().get(UnManagedVolume.SupportedVolumeCharacterstics.IS_VOLUME_ADDED_TO_CONSISTENCYGROUP.toString());
        if (isParentVolumeInCG.equals(Boolean.TRUE.toString())) {
            // set snapshot consistency group name
            if (driverSnapshot.getConsistencyGroup() != null && !driverSnapshot.getConsistencyGroup().isEmpty()) {
                StringSet snapCgName = new StringSet();
                snapCgName.add(driverSnapshot.getConsistencyGroup());
                unManagedVolumeInformation.put(UnManagedVolume.SupportedVolumeInformation.SNAPSHOT_CONSISTENCY_GROUP_NAME.toString(),
                        snapCgName);
            }
        }

        // set from parent volume (required for snaps by ingest framework)
        unManagedVolumeCharacteristics.put(UnManagedVolume.SupportedVolumeCharacterstics.IS_THINLY_PROVISIONED.toString(),
                parentUnManagedVolume.getVolumeCharacterstics().get(UnManagedVolume.SupportedVolumeCharacterstics.IS_THINLY_PROVISIONED.toString()));

        unManagedVolume.setStoragePoolUri(storagePool.getId());
        StringSet pools = new StringSet();
        pools.add(storagePool.getId().toString());
        unManagedVolumeInformation.remove(UnManagedVolume.SupportedVolumeInformation.STORAGE_POOL.toString());
        unManagedVolumeInformation.put(UnManagedVolume.SupportedVolumeInformation.STORAGE_POOL.toString(), pools);

        StringSet driveTypes = storagePool.getSupportedDriveTypes();
        if (null != driveTypes) {
            unManagedVolumeInformation.remove(UnManagedVolume.SupportedVolumeInformation.DISK_TECHNOLOGY.toString());
            unManagedVolumeInformation.put(UnManagedVolume.SupportedVolumeInformation.DISK_TECHNOLOGY.toString(), driveTypes);
        }

        StringSet provCapacity = new StringSet();
        provCapacity.add(String.valueOf(driverSnapshot.getProvisionedCapacity()));
        unManagedVolumeInformation.remove(UnManagedVolume.SupportedVolumeInformation.PROVISIONED_CAPACITY.toString());
        unManagedVolumeInformation.put(UnManagedVolume.SupportedVolumeInformation.PROVISIONED_CAPACITY.toString(),
                provCapacity);

        StringSet allocatedCapacity = new StringSet();
        allocatedCapacity.add(String.valueOf(driverSnapshot.getAllocatedCapacity()));
        unManagedVolumeInformation.remove(UnManagedVolume.SupportedVolumeInformation.ALLOCATED_CAPACITY.toString());
        unManagedVolumeInformation.put(UnManagedVolume.SupportedVolumeInformation.ALLOCATED_CAPACITY.toString(),
                allocatedCapacity);

        // Set matched vpools the same as parent.
        StringSet parentMatchedVPools = parentUnManagedVolume.getSupportedVpoolUris();
        if (null != parentMatchedVPools) {
            log.info("Parent Matched Virtual Pools : {}", Joiner.on("\t").join(parentMatchedVPools));
        }
        if (null == parentMatchedVPools || parentMatchedVPools.isEmpty()) {
            // Clean all vpools as no matching vpools found.
            unManagedVolume.getSupportedVpoolUris().clear();
        } else {
            // replace with new StringSet
            unManagedVolume.getSupportedVpoolUris().replace(parentMatchedVPools);
            log.info("Replaced Virtual Pools :{}", Joiner.on("\t").join(unManagedVolume.getSupportedVpoolUris()));
        }

        if (newVolume) {
            unManagedVolumesToCreate.add(unManagedVolume);
        } else {
            unManagedVolumesToUpdate.add(unManagedVolume);
        }

        return unManagedVolume;
    }

    private UnManagedVolume createUnManagedClone(VolumeClone driverClone, UnManagedVolume parentUnManagedVolume,
                                                    com.emc.storageos.db.client.model.StorageSystem storageSystem,
                                                    com.emc.storageos.db.client.model.StoragePool storagePool,
                                                    List<UnManagedVolume> unManagedVolumesToCreate, List<UnManagedVolume> unManagedVolumesToUpdate,
                                                    DbClient dbClient) {
        // We process unManaged clone as unManaged volume
        boolean newVolume = false;
        StringSetMap unManagedVolumeInformation = null;
        StringMap unManagedVolumeCharacteristics = null;

        String unManagedVolumeNatvieGuid = NativeGUIDGenerator.generateNativeGuidForPreExistingVolume(
                storageSystem.getNativeGuid(), driverClone.getNativeId());

        UnManagedVolume unManagedVolume = DiscoveryUtils.checkUnManagedVolumeExistsInDB(dbClient, unManagedVolumeNatvieGuid);
        if (null == unManagedVolume) {
            unManagedVolume = new UnManagedVolume();
            unManagedVolume.setId(URIUtil.createId(UnManagedVolume.class));
            unManagedVolume.setNativeGuid(unManagedVolumeNatvieGuid);
            unManagedVolume.setStorageSystemUri(storageSystem.getId());

            if (driverClone.getWwn() == null) {
                unManagedVolume.setWwn(String.format("%s%s", driverClone.getStorageSystemId(), driverClone.getNativeId()));
            } else {
                unManagedVolume.setWwn(driverClone.getWwn());
            }
            newVolume = true;
            unManagedVolumeInformation = new StringSetMap();
            unManagedVolumeCharacteristics = new StringMap();

            unManagedVolume.setVolumeInformation(unManagedVolumeInformation);
            unManagedVolume.setVolumeCharacterstics(unManagedVolumeCharacteristics);
        } else {
            unManagedVolumeInformation = unManagedVolume.getVolumeInformation();
            unManagedVolumeCharacteristics = unManagedVolume.getVolumeCharacterstics();

            // cleanup relationships from previous discoveries, we will set them according to this discovery
            // Make sure the unManagedVolume clone object does not contain parent CG information from previous discovery
            unManagedVolumeCharacteristics.put(
                    UnManagedVolume.SupportedVolumeCharacterstics.IS_VOLUME_ADDED_TO_CONSISTENCYGROUP.toString(), Boolean.FALSE.toString());
            // remove uri of the unManaged CG in the unManaged volume object
            unManagedVolumeInformation.remove(UnManagedVolume.SupportedVolumeInformation.UNMANAGED_CONSISTENCY_GROUP_URI.toString());
            // Clean old data for replication group name
            unManagedVolumeInformation.remove(UnManagedVolume.SupportedVolumeInformation.FULL_COPY_CONSISTENCY_GROUP_NAME.toString());
            // Clear old export mask information
            unManagedVolume.getUnmanagedExportMasks().clear();
        }

        unManagedVolume.setLabel(driverClone.getDeviceLabel());
        unManagedVolumeCharacteristics.put(UnManagedVolume.SupportedVolumeCharacterstics.IS_VOLUME_EXPORTED.toString(), FALSE);

        // Set these default to false. The individual storage discovery will change them if needed.
        unManagedVolumeCharacteristics.put(UnManagedVolume.SupportedVolumeCharacterstics.IS_NONRP_EXPORTED.toString(), FALSE);
        unManagedVolumeCharacteristics.put(UnManagedVolume.SupportedVolumeCharacterstics.IS_RECOVERPOINT_ENABLED.toString(), FALSE);

        StringSet deviceLabel = new StringSet();
        deviceLabel.add(driverClone.getDeviceLabel());
        unManagedVolumeInformation.remove(UnManagedVolume.SupportedVolumeInformation.DEVICE_LABEL.toString());
        unManagedVolumeInformation.put(UnManagedVolume.SupportedVolumeInformation.DEVICE_LABEL.toString(),
                deviceLabel);


        if (driverClone.getAccessStatus() != null) {
            StringSet accessState = new StringSet();
            accessState.add(driverClone.getAccessStatus().toString());
            unManagedVolumeInformation.remove(UnManagedVolume.SupportedVolumeInformation.ACCESS.toString());
            unManagedVolumeInformation.put(UnManagedVolume.SupportedVolumeInformation.ACCESS.toString(), accessState);
        }

        StringSet systemTypes = new StringSet();
        systemTypes.add(storageSystem.getSystemType());
        unManagedVolumeInformation.remove(UnManagedVolume.SupportedVolumeInformation.SYSTEM_TYPE.toString());
        unManagedVolumeInformation.put(UnManagedVolume.SupportedVolumeInformation.SYSTEM_TYPE.toString(),
                systemTypes);

        StringSet nativeId = new StringSet();
        nativeId.add(driverClone.getNativeId());
        unManagedVolumeInformation.remove(UnManagedVolume.SupportedVolumeInformation.NATIVE_ID.toString());
        unManagedVolumeInformation.put(UnManagedVolume.SupportedVolumeInformation.NATIVE_ID.toString(),
                nativeId);

        unManagedVolumeCharacteristics.put(
                UnManagedVolume.SupportedVolumeCharacterstics.IS_INGESTABLE.toString(), TRUE);

        unManagedVolumeCharacteristics.put(UnManagedVolume.SupportedVolumeCharacterstics.IS_THINLY_PROVISIONED.toString(),
                driverClone.getThinlyProvisioned().toString());

        unManagedVolumeCharacteristics.put(UnManagedVolume.SupportedVolumeCharacterstics.IS_FULL_COPY.toString(), TRUE);

        StringSet parentVol = new StringSet();
        parentVol.add(parentUnManagedVolume.getNativeGuid());
        unManagedVolumeInformation.remove(UnManagedVolume.SupportedVolumeInformation.LOCAL_REPLICA_SOURCE_VOLUME.toString());
        unManagedVolumeInformation.put(UnManagedVolume.SupportedVolumeInformation.LOCAL_REPLICA_SOURCE_VOLUME.toString(), parentVol);

        StringSet isSyncActive = new StringSet();
        isSyncActive.add(TRUE);
        unManagedVolumeInformation.remove(UnManagedVolume.SupportedVolumeInformation.IS_SYNC_ACTIVE.toString());
        unManagedVolumeInformation.put(UnManagedVolume.SupportedVolumeInformation.IS_SYNC_ACTIVE.toString(), isSyncActive);

        StringSet isReadOnly = new StringSet();
        Boolean readOnly = Boolean.FALSE;
        if (driverClone.getAccessStatus() != null) {
            readOnly = (driverClone.getAccessStatus().toString()).equals(StorageObject.AccessStatus.READ_ONLY.toString()) ?
                    Boolean.TRUE : Boolean.FALSE;
        }
        isReadOnly.add(readOnly.toString());
        unManagedVolumeInformation.remove(UnManagedVolume.SupportedVolumeInformation.IS_READ_ONLY.toString());
        unManagedVolumeInformation.put(UnManagedVolume.SupportedVolumeInformation.IS_READ_ONLY.toString(), isReadOnly);

        StringSet techType = new StringSet();
        techType.add(BlockSnapshot.TechnologyType.NATIVE.toString());
        unManagedVolumeInformation.remove(UnManagedVolume.SupportedVolumeInformation.TECHNOLOGY_TYPE.toString());
        unManagedVolumeInformation.put(UnManagedVolume.SupportedVolumeInformation.TECHNOLOGY_TYPE.toString(), techType);

        // set clone consistency group information in the unmanged clone object
        String isParentVolumeInCG =
                parentUnManagedVolume.getVolumeCharacterstics().get(UnManagedVolume.SupportedVolumeCharacterstics.IS_VOLUME_ADDED_TO_CONSISTENCYGROUP.toString());
        if (isParentVolumeInCG.equals(Boolean.TRUE.toString())) {
            // set clone consistency group name
            if (driverClone.getConsistencyGroup() != null && !driverClone.getConsistencyGroup().isEmpty()) {
                StringSet snapCgName = new StringSet();
                snapCgName.add(driverClone.getConsistencyGroup());
                unManagedVolumeInformation.put(UnManagedVolume.SupportedVolumeInformation.FULL_COPY_CONSISTENCY_GROUP_NAME.toString(),
                        snapCgName);
            }
        }

        unManagedVolume.setStoragePoolUri(storagePool.getId());
        StringSet pools = new StringSet();
        pools.add(storagePool.getId().toString());
        unManagedVolumeInformation.remove(UnManagedVolume.SupportedVolumeInformation.STORAGE_POOL.toString());
        unManagedVolumeInformation.put(UnManagedVolume.SupportedVolumeInformation.STORAGE_POOL.toString(), pools);

        StringSet driveTypes = storagePool.getSupportedDriveTypes();
        if (null != driveTypes) {
            unManagedVolumeInformation.remove(UnManagedVolume.SupportedVolumeInformation.DISK_TECHNOLOGY.toString());
            unManagedVolumeInformation.put(UnManagedVolume.SupportedVolumeInformation.DISK_TECHNOLOGY.toString(), driveTypes);
        }

        StringSet provCapacity = new StringSet();
        provCapacity.add(String.valueOf(driverClone.getProvisionedCapacity()));
        unManagedVolumeInformation.remove(UnManagedVolume.SupportedVolumeInformation.PROVISIONED_CAPACITY.toString());
        unManagedVolumeInformation.put(UnManagedVolume.SupportedVolumeInformation.PROVISIONED_CAPACITY.toString(),
                provCapacity);

        StringSet allocatedCapacity = new StringSet();
        allocatedCapacity.add(String.valueOf(driverClone.getAllocatedCapacity()));
        unManagedVolumeInformation.remove(UnManagedVolume.SupportedVolumeInformation.ALLOCATED_CAPACITY.toString());
        unManagedVolumeInformation.put(UnManagedVolume.SupportedVolumeInformation.ALLOCATED_CAPACITY.toString(),
                allocatedCapacity);

        // Set matched vpools the same as parent.
        StringSet parentMatchedVPools = parentUnManagedVolume.getSupportedVpoolUris();
        if (null != parentMatchedVPools) {
            log.info("Parent Matched Virtual Pools : {}", Joiner.on("\t").join(parentMatchedVPools));
        }
        if (null == parentMatchedVPools || parentMatchedVPools.isEmpty()) {
            // Clean all vpools as no matching vpools found.
            unManagedVolume.getSupportedVpoolUris().clear();
        } else {
            // replace with new StringSet
            unManagedVolume.getSupportedVpoolUris().replace(parentMatchedVPools);
            log.info("Replaced Virtual Pools :{}", Joiner.on("\t").join(unManagedVolume.getSupportedVpoolUris()));
        }

        if (newVolume) {
            unManagedVolumesToCreate.add(unManagedVolume);
        } else {
            unManagedVolumesToUpdate.add(unManagedVolume);
        }

        return unManagedVolume;
    }

    /**
     * Gets export information for a given volume from the driver.
     * Add this information to the provided hostToVolumeExportInfoMap parameter.
     *
     * @param driver driver to call (input)
     * @param driverVolume volume for which we need to get export info (input)
     * @param hostToVolumeExportInfoMap map, key --- host FQDN, value: list with export info for volumes exported to this host (input/output)
     */
    private void getVolumeExportInfo(BlockStorageDriver driver, StorageVolume driverVolume, Map<String, List<HostExportInfo>> hostToVolumeExportInfoMap) {
        // get VolumeToHostExportInfo data for this volume from driver
        Map<String, HostExportInfo> volumeToHostExportInfo = driver.getVolumeExportInfoForHosts(driverVolume);
        log.info("Export info for volume {} is {}:", driverVolume, volumeToHostExportInfo);
        if (volumeToHostExportInfo == null || volumeToHostExportInfo.isEmpty()) {
            return;
        }

        //add HostExportInfo data to hostToVolumeExportInfoMap
        for(Map.Entry<String, HostExportInfo> entry : volumeToHostExportInfo.entrySet()) {
            String hostFqdn = entry.getKey();
            List<HostExportInfo> volumeToHostExportInfoList = hostToVolumeExportInfoMap.get(hostFqdn);
            if (volumeToHostExportInfoList == null) {
                volumeToHostExportInfoList = new ArrayList<>();
                hostToVolumeExportInfoMap.put(hostFqdn, volumeToHostExportInfoList);
            }
            volumeToHostExportInfoList.add(entry.getValue());
        }
    }

    /**
     * Gets export information for a given snapshot from the driver.
     * Add this information to the provided hostToVolumeExportInfoMap parameter.
     *
     * @param driver driver to call (input)
     * @param driverSnapshot snapshot for which we need to get export info (input)
     * @param hostToVolumeExportInfoMap map, key --- host FQDN, value: list with export info for block objects exported
     *                            to this host (input/output)
     */
    private void getSnapshotExportInfo(BlockStorageDriver driver, VolumeSnapshot driverSnapshot, Map<String, List<HostExportInfo>> hostToVolumeExportInfoMap) {
        // get HostExportInfo data for this snap from driver
        Map<String, HostExportInfo> volumeToHostExportInfo = driver.getSnapshotExportInfoForHosts(driverSnapshot);
        log.info("Export info for snapshot {} is {}:", driverSnapshot, volumeToHostExportInfo);
        if (volumeToHostExportInfo == null || volumeToHostExportInfo.isEmpty()) {
            return;
        }

        //add volumeToHostExportInfo data to hostToVolumeExportInfoMap
        for(Map.Entry<String, HostExportInfo> entry : volumeToHostExportInfo.entrySet()) {
            String hostFqdn = entry.getKey();
            List<HostExportInfo> volumeToHostExportInfoList = hostToVolumeExportInfoMap.get(hostFqdn);
            if (volumeToHostExportInfoList == null) {
                volumeToHostExportInfoList = new ArrayList<>();
                hostToVolumeExportInfoMap.put(hostFqdn, volumeToHostExportInfoList);
            }
            volumeToHostExportInfoList.add(entry.getValue());
        }
    }

    /**
     * Gets export information for a given clone from the driver.
     * Add this information to the provided hostToVolumeExportInfoMap parameter.
     *
     * @param driver driver to call (input)
     * @param driverClone clone for which we need to get export info (input)
     * @param hostToVolumeExportInfoMap map, key --- host FQDN, value: list with export info for block objects exported
     *                            to this host (input/output)
     */
    private void getCloneExportInfo(BlockStorageDriver driver, VolumeClone driverClone, Map<String, List<HostExportInfo>> hostToVolumeExportInfoMap) {
        // get HostExportInfo data for this clone from driver
        Map<String, HostExportInfo> volumeToHostExportInfo = driver.getCloneExportInfoForHosts(driverClone);
        log.info("Export info for clone {} is {}:", driverClone, volumeToHostExportInfo);
        if (volumeToHostExportInfo == null || volumeToHostExportInfo.isEmpty()) {
            return;
        }

        //add volumeToHostExportInfo data to hostToVolumeExportInfoMap
        for(Map.Entry<String, HostExportInfo> entry : volumeToHostExportInfo.entrySet()) {
            String hostFqdn = entry.getKey();
            List<HostExportInfo> volumeToHostExportInfoList = hostToVolumeExportInfoMap.get(hostFqdn);
            if (volumeToHostExportInfoList == null) {
                volumeToHostExportInfoList = new ArrayList<>();
                hostToVolumeExportInfoMap.put(hostFqdn, volumeToHostExportInfoList);
            }
            volumeToHostExportInfoList.add(entry.getValue());
        }
    }


    /**
     * Get export info for replicas (snaps and clones) of managed volume.
     * We expect that all replicas of managed volume should be managed (known to vipr) ---
     * enforced by  ingest framework, plus we do not support coexistence .
     * Warning log message is generated for each replica which is unmanaged.
     *
     * @param managedVolumeNativeIdToUriMap [OUT]
     * @param hostToManagedVolumeExportInfoMap [OUT], map: key --- host name, value: list of export infos for volumes exported
     *                                         to this host.
     * @param dbClient [IN]
     * @param storageSystem [IN]
     * @param viprVolume [IN]
     * @param driverVolume [IN]
     * @param driver [IN]
     * @throws Exception
     */
    private void getExportInfoForManagedVolumeReplicas(Map<String, URI> managedVolumeNativeIdToUriMap,
                                                       Map<String, List<HostExportInfo>> hostToManagedVolumeExportInfoMap,
                                                       DbClient dbClient, com.emc.storageos.db.client.model.StorageSystem storageSystem,
                                                       Volume viprVolume, StorageVolume driverVolume,
                                                       BlockStorageDriver driver) throws Exception {

        // get export info for managed volume  snapshots
        log.info("Processing snapshots for managed volume {} ", viprVolume.getNativeGuid());
        List<VolumeSnapshot> driverSnapshots = driver.getVolumeSnapshots(driverVolume);
        if (driverSnapshots == null || driverSnapshots.isEmpty()) {
            log.info("There are no snapshots for volume {} ", viprVolume.getNativeGuid());
        } else {
            log.info("Snapshots for managed volume {}:" + Joiner.on("\t").join(driverSnapshots), viprVolume.getNativeGuid());
            for (VolumeSnapshot driverSnapshot : driverSnapshots) {
                String managedSnapNativeGuid = NativeGUIDGenerator.generateNativeGuidForVolumeOrBlockSnapShot(
                        storageSystem.getNativeGuid(), driverSnapshot.getNativeId());
                BlockSnapshot viprSnap = DiscoveryUtils.checkBlockSnapshotExistsInDB(dbClient, managedSnapNativeGuid);
                if (viprSnap == null) {
                    log.warn("Found unmanaged snapshot of managed volume --- this is unexpected! Skipping this snapshot {}.",
                            driverSnapshot.getNativeId());
                    continue;
                } else {
                    log.info("Processing managed {} snapshot of managed volume ().",
                            viprSnap.getNativeId(), viprVolume.getNativeGuid());
                }

                // get export data for the snapshot
                managedVolumeNativeIdToUriMap.put(driverSnapshot.getNativeId(), viprSnap.getId());
                getSnapshotExportInfo(driver, driverSnapshot, hostToManagedVolumeExportInfoMap);
            }
        }
        // get export info for managed volume  clones
        log.info("Processing clones for managed volume {} ", viprVolume.getNativeGuid());
        List<VolumeClone> driverClones = driver.getVolumeClones(driverVolume);
        if (driverClones == null || driverClones.isEmpty()) {
            log.info("There are no clones for volume {} ", viprVolume.getNativeGuid());
        } else {
            log.info("Clones for managed volume {}:" + Joiner.on("\t").join(driverClones), viprVolume.getNativeGuid());
            for (VolumeClone driverClone : driverClones) {
                String managedCloneNativeGuid = NativeGUIDGenerator.generateNativeGuidForVolumeOrBlockSnapShot(
                        storageSystem.getNativeGuid(), driverClone.getNativeId());
                Volume viprClone = DiscoveryUtils.checkStorageVolumeExistsInDB(dbClient, managedCloneNativeGuid);
                if (viprClone == null) {
                    log.warn("Found unmanaged clone of managed volume --- this is unexpected! Skipping this clone {}.",
                            driverClone.getNativeId());
                    continue;
                } else {
                    log.info("Processing managed {} clone of managed volume ().",
                            viprClone.getNativeId(), viprVolume.getNativeGuid());
                }

                // get export data for the clone
                managedVolumeNativeIdToUriMap.put(driverClone.getNativeId(), viprClone.getId());
                getCloneExportInfo(driver, driverClone, hostToManagedVolumeExportInfoMap);
            }
        }
    }

    /**
     * Processes export info for unmanaged and managed volumes found on storage array during volume discovery.
     * Analyses export info and builds/updates UnManagedExport masks for the exports.
     *
     * @param driver driver reference [IN]
     * @param storageSystem storage system [IN]
     * @param unManagedVolumeNativeIdToUriMap helper map of unmanaged volumes native ids to persistent Uris [IN]
     * @param managedVolumeNativeIdToUriMap  helper map of managed volumes native ids to persistent Uris [IN]
     * @param hostToVolumeExportInfoMap map with export info for unmanaged volumes found on storage array.
     *                                           key: host FQDN, value: list of volume export info for this host.[IN]
     * @param hostToManagedVolumeExportInfoMap  map with export info for managed volumes found on storage array.
     *                                           key: host FQDN, value: list of volume export info for this host.[IN]
     * @param invalidExportHosts Set of host FQDN for which we found invalid exports to the storage system. [IN/OUT]
     *                           We will exclude exports for these hosts from processing.
     * @param dbClient
     * @param partitionManager
     */
    private void processExportData(BlockStorageDriver driver, com.emc.storageos.db.client.model.StorageSystem storageSystem,
                                   Map<String, URI> unManagedVolumeNativeIdToUriMap,
                                   Map<String, URI> managedVolumeNativeIdToUriMap,
                                   Map<String, List<HostExportInfo>> hostToVolumeExportInfoMap,
                                   Map<String, List<HostExportInfo>> hostToManagedVolumeExportInfoMap,
                                   Set<String> invalidExportHosts,
                                   DbClient dbClient, PartitionManager partitionManager) {
        /*
        Processing of hostToUnManagedVolumeExportInfoMap:
          - for each host, which is not in invalid hosts set:
            Verify that all export info for this host in the map have valid export data (same initiators and same ports).
            If valid, return export info with all volumes.
            If invalid, we do not discover exports for this host; add this host to invalid hosts.
            Next, verify initiators and ports against existing unManaged export mask for the host and array.
            a. If unManaged export mask exist and valid, return this mask --- we will update it with new volumes.
            b. If unManaged export mask exist and invalid, we invalidate this mask, add the host to invalid hosts
               and do not discover exports for this host.

            If unManaged export mask does not exist, we will create a new one for host/array.
            Next, check if there is existing managed export mask for the host and array.
            If managed export mask for host/array exists, we do not process new export info for the host/array ---
            no support for co-existence.
         */

        List<UnManagedExportMask> unManagedExportMasksToCreate = new ArrayList<>();
        List<UnManagedExportMask> unManagedExportMasksToUpdate = new ArrayList<>();

        // Map, key: uri of existing unmanaged volume mask, value: new export info to add to the mask
        Map<URI, HostExportInfo> masksToUpdateForUnManagedVolumes = new HashMap<>();
        List<HostExportInfo>  masksToCreateForUnManagedVolumes = new ArrayList<>();

        // Map, key: uri of existing unmanaged volume mask, value: new export info to add to the mask
        Map<URI, HostExportInfo> masksToUpdateForManagedVolumes = new HashMap<>();
        List<HostExportInfo>  masksToCreateForManagedVolumes = new ArrayList<>();

        // process export info for unManaged volumes to get masksToUpdateForUnManagedVolumes and masksToCreateForUnManagedVolumes
        determineUnManagedExportMasksForExportInfo(storageSystem,
                hostToVolumeExportInfoMap,
                invalidExportHosts,
                dbClient, masksToUpdateForUnManagedVolumes, masksToCreateForUnManagedVolumes);

        log.info("Export info for masks to create for unmanaged volumes: {}", masksToCreateForUnManagedVolumes);
        log.info("Export info for masks to update for unmanaged volumes: {}", masksToUpdateForUnManagedVolumes);
//        // process export info for managed volumes to get masksToUpdateForManagedVolumes and masksToCreateForManagedVolumes
//        determineUnManagedExportMasksForExportInfo(storageSystem,
//                hostToManagedVolumeExportInfoMap,
//                invalidExportHosts,
//                dbClient, masksToUpdateForManagedVolumes, masksToCreateForManagedVolumes);
//        log.info("Export info for masks to create for managed volumes: {}", masksToCreateForManagedVolumes);
//        log.info("Export info for masks to update for managed volumes: {}", masksToUpdateForManagedVolumes);

        // process unmanaged export masks for unmanaged volumes
        if (!(masksToCreateForUnManagedVolumes.isEmpty() && masksToUpdateForUnManagedVolumes.isEmpty()) ) {
            processUnManagedMasksForUnManagedVolumes(storageSystem, masksToUpdateForUnManagedVolumes,
                    masksToCreateForUnManagedVolumes, unManagedVolumeNativeIdToUriMap, managedVolumeNativeIdToUriMap,
                    unManagedExportMasksToUpdate, unManagedExportMasksToCreate, dbClient);
        }
        log.info("Masks to create for unmanaged volumes: {}", unManagedExportMasksToCreate);
        log.info("Masks to update for unmanaged volumes: {}", unManagedExportMasksToUpdate);
        // update db with results, so we do not create duplicate masks when we process masks for managed volumes
        if (!unManagedExportMasksToCreate.isEmpty()) {
            partitionManager.insertInBatches(unManagedExportMasksToCreate,
                    Constants.DEFAULT_PARTITION_SIZE, dbClient, UNMANAGED_EXPORT_MASK);
        }
        if (!unManagedExportMasksToUpdate.isEmpty()) {
            partitionManager.updateInBatches(unManagedExportMasksToUpdate,
                    Constants.DEFAULT_PARTITION_SIZE, dbClient, UNMANAGED_EXPORT_MASK);
        }

        updateUnManagedVolumesWithExportData(unManagedVolumeNativeIdToUriMap, unManagedExportMasksToCreate, unManagedExportMasksToUpdate, dbClient, partitionManager);
        unManagedExportMasksToCreate.clear();
        unManagedExportMasksToUpdate.clear();

//        // process unmanaged export masks for managed volumes
//        if (!(masksToCreateForManagedVolumes.isEmpty() && masksToUpdateForManagedVolumes.isEmpty()) ) {
//            processUnManagedMasksForManagedVolumes(storageSystem, masksToUpdateForManagedVolumes,
//                    masksToCreateForManagedVolumes, managedVolumeNativeIdToUriMap,
//                    unManagedExportMasksToUpdate, unManagedExportMasksToCreate, dbClient);
//        }
//
//        log.info("Masks to create for managed volumes: {}", unManagedExportMasksToCreate);
//        log.info("Masks to update for managed volumes: {}", unManagedExportMasksToUpdate);
//        if (!unManagedExportMasksToCreate.isEmpty()) {
//            partitionManager.insertInBatches(unManagedExportMasksToCreate,
//                    Constants.DEFAULT_PARTITION_SIZE, dbClient, UNMANAGED_EXPORT_MASK);
//            unManagedExportMasksToCreate.clear();
//        }
//        if (!unManagedExportMasksToUpdate.isEmpty()) {
//            partitionManager.updateInBatches(unManagedExportMasksToUpdate,
//                    Constants.DEFAULT_PARTITION_SIZE, dbClient, UNMANAGED_EXPORT_MASK);
//            unManagedExportMasksToUpdate.clear();
//        }
    }


    /**
     * This method processes hostToVolumeExportInfoMap to find out which existing unmanaged masks has to be updated,
     * and which unmanaged masks has to be created new for this export info. It also identifies hosts with unsupported
     * export info data (exported host volumes are not seen through the same set of initiators and the same set of storage
     * ports) and adds these hosts to invalidExportHosts set.
     *
     * @param storageSystem
     * @param hostToVolumeExportInfoMap [IN] map: key --- host FQDN, value --- list of volume export info instances
     * @param invalidExportHosts [IN, OUT] set of invalid hosts, for which we skip export processing for a given array
     * @param dbClient
     * @param masksToUpdateForVolumes [OUT] map: key --- URI of existing unmanaged export mask, value --- export info to use
     *                                to update the mask.
     * @param masksToCreateForVolumes [OUT] list of export info instances for which we need to create new unmanaged masks.
     */
    private void determineUnManagedExportMasksForExportInfo(com.emc.storageos.db.client.model.StorageSystem storageSystem,
                                               Map<String, List<HostExportInfo>> hostToVolumeExportInfoMap,
                                               Set<String> invalidExportHosts,
                                               DbClient dbClient, Map<URI, HostExportInfo> masksToUpdateForVolumes,
                                               List<HostExportInfo>  masksToCreateForVolumes) {

        for (Map.Entry<String, List<HostExportInfo>> entry : hostToVolumeExportInfoMap.entrySet()) {
            String hostName = entry.getKey();
            log.info("Processing export info for host {} .", hostName);

            if (invalidExportHosts.contains(hostName)) {
                // skip and continue to the next host.
                log.info("Found host {} in invalid hosts list. We will not process this host export data.", hostName);
                continue;
            }
            List<HostExportInfo> volumeToHostExportInfoList = entry.getValue();
            log.info("Processing export info list {} .", volumeToHostExportInfoList);
            HostExportInfo hostExportInfo = verifyHostExports(volumeToHostExportInfoList);
            if (hostExportInfo == null) {
                // invalid, continue to the next host
                invalidExportHosts.add(hostName);
                log.info("Found export info for host {} invalid. We will not process this host export data.", hostName);
                continue;
            }
            log.info("The result export info for host {} : {} .", hostName, hostExportInfo);

            // check existing UnManaged export mask for host/array: the mask could be discovered for volumes on previous
            // pages (all unmanaged masks from previous discovery have been deactivated at the begging).
            UnManagedExportMask unManagedMask = getUnManagedExportMask(hostName, dbClient, storageSystem.getId());
            boolean isValid = true;
            if (unManagedMask != null) {
                log.info("Found existing unmanaged export mask for host {} and array {} --- {} .", hostName, storageSystem.getNativeId(), unManagedMask);
                // check that existing host/array unManaged export mask has the same set of initiators and the same
                // set of ports as new discovered hostExportInfo
                StringSet storagePortsUris = unManagedMask.getKnownStoragePortUris();
                Set<String> storagePortsNativeIds = new HashSet<>();
                Set<String> initiatorsNativeIds = new HashSet<>();
                for (String portUriString : storagePortsUris) {
                    URI portUri = URI.create(portUriString);
                    com.emc.storageos.db.client.model.StoragePort port = dbClient.queryObject(com.emc.storageos.db.client.model.StoragePort.class, portUri);
                    storagePortsNativeIds.add(port.getNativeId());
                }
                storagePortsNativeIds.addAll(unManagedMask.getUnmanagedStoragePortNetworkIds());

                initiatorsNativeIds.addAll(unManagedMask.getKnownInitiatorNetworkIds());
                initiatorsNativeIds.addAll(unManagedMask.getUnmanagedInitiatorNetworkIds());
                isValid = verifyHostExports(initiatorsNativeIds, storagePortsNativeIds, hostExportInfo);
                if (!isValid) {
                    // invalid, we deactivate existing unmanaged mask --- make sure we do not discover invalid export
                    // masks.
                    log.info("The result export info for host {} and storage array {} does not comply with existing mask.",
                            hostName, storageSystem.getNativeId());
                    unManagedMask.setInactive(true);
                    dbClient.updateObject(unManagedMask);
                }
            } else {
                // Check if export mask for host/array is already managed. In such a case, skip export info for this
                // host/array. We do not discover export info for hosts which already have managed export mask volume's
                // storage array.
                List<String> initiatorPorts = new ArrayList<>();
                for (Initiator initiator : hostExportInfo.getInitiators()) {
                    initiatorPorts.add(initiator.getPort());
                }
                Map<URI, ExportMask> uriToExportMask = ExportMaskUtils.getExportMasksWithInitiatorPorts(dbClient, initiatorPorts);
                // Look for export mask for the storage system under processing.
                for (ExportMask mask : uriToExportMask.values()) {
                    if (mask.getStorageDevice().equals(storageSystem.getId())) {
                        // found managed export mask for storage system and host initiator
                        // the mask is already managed.
                        isValid = false;
                        log.info("Found managed export mask for host {}. We will not process this host export data.", hostName);
                        break;
                    }
                }
            }
            if (!isValid) {
                // invalid, continue to the next host
                // add host to invalid hosts list, so we do not process any export volume
                // info for this host in the future (for volumes found on next pages).
                log.info("Found export info for host {} invalid. Export info: {}." +
                        " We will not process this host export data.", hostName, hostExportInfo);
                invalidExportHosts.add(hostName);
                continue;
            }

            if (unManagedMask != null) {
                // we will update this mask with additional volumes.
                URI maskId = unManagedMask.getId();
                masksToUpdateForVolumes.put(maskId, hostExportInfo);
            } else {
                // we will create new unManaged mask for host/array.
                masksToCreateForVolumes.add(hostExportInfo);
            }
        }
    }

    /**
     * This method processes export info for unmanaged volumes and returns unmanaged masks to create and existing unmanaged masks to update.
     *
     * @param storageSystem [IN]
     * @param masksToUpdateForUnManagedVolumes [IN] map: key --- mask uri, value --- volume export info to add to the mask.
     * @param masksToCreateForUnManagedVolumes [IN] list of volume export info for which we need to create new masks.
     * @param unManagedVolumeNativeIdToUriMap [IN] map of unmanaged volume native id to unmanaged volume URI
     * @param unManagedExportMasksToUpdate [OUT] list of unmanaged export masks to update
     * @param unManagedExportMasksToCreate [OUT] list of unmanaged export masks to create
     * @param dbClient
     */
    private void processUnManagedMasksForUnManagedVolumes(com.emc.storageos.db.client.model.StorageSystem storageSystem,
                                                          Map<URI, HostExportInfo> masksToUpdateForUnManagedVolumes,
                                                          List<HostExportInfo> masksToCreateForUnManagedVolumes,
                                                          Map<String, URI> unManagedVolumeNativeIdToUriMap,
                                                          Map<String, URI> managedVolumeNativeIdToUriMap,
                                                          List<UnManagedExportMask> unManagedExportMasksToUpdate,
                                                          List<UnManagedExportMask> unManagedExportMasksToCreate,
                                                          DbClient dbClient) {

        log.info("Processing unmanaged volumes: {} .", unManagedVolumeNativeIdToUriMap);
        // update/create unManaged masks for unManaged volumes
        log.info("Processing masks to update: {} .", masksToUpdateForUnManagedVolumes);
        for (Map.Entry<URI, HostExportInfo> entry : masksToUpdateForUnManagedVolumes.entrySet()) {
            URI maskUri = entry.getKey();
            HostExportInfo exportInfo = entry.getValue();

            Set<String> unManagedVolumesUris = new HashSet<>();
            Set<String> managedVolumesUris = new HashSet<>();
            List<String> volumesNativeIds = exportInfo.getStorageObjectNativeIds();
            for (String volumeNativeId : volumesNativeIds) {
                URI volumeUri = unManagedVolumeNativeIdToUriMap.get(volumeNativeId);
                if (volumeUri != null) {
                    unManagedVolumesUris.add(volumeUri.toString());
                } else {
                    volumeUri = managedVolumeNativeIdToUriMap.get(volumeNativeId);
                    if (volumeUri != null) {
                        managedVolumesUris.add(volumeUri.toString());
                    }
                }
            }
            // process unmanaged volumes
            UnManagedExportMask unManagedMask = dbClient.queryObject(UnManagedExportMask.class, maskUri);
            StringSet unmangedVolumesInMask = unManagedMask.getUnmanagedVolumeUris();
            // check for null, since existing mask may only have "known" volumes.
            if (unmangedVolumesInMask == null) {
                unmangedVolumesInMask = new StringSet();
                unManagedMask.setUnmanagedVolumeUris(unmangedVolumesInMask);
            }
            unmangedVolumesInMask.addAll(unManagedVolumesUris);
            // process managed volumes
            StringSet managedVolumesInMask = unManagedMask.getKnownVolumeUris();
            // check for null, since existing mask may only have unManaged volumes.
            if (managedVolumesInMask == null) {
                managedVolumesInMask = new StringSet();
                unManagedMask.setKnownVolumeUris(managedVolumesInMask);
            }
            managedVolumesInMask.addAll(managedVolumesUris);

            unManagedExportMasksToUpdate.add(unManagedMask);
        }

        log.info("Processing masks to create: {} .", masksToCreateForUnManagedVolumes);
        for (HostExportInfo hostExportInfo : masksToCreateForUnManagedVolumes) {
            Set<String> unManagedVolumesUris = new HashSet<>();
            Set<String> managedVolumesUris = new HashSet<>();
            List<String> volumesNativeIds = hostExportInfo.getStorageObjectNativeIds();
            for (String volumeNativeId : volumesNativeIds) {
                URI volumeUri = unManagedVolumeNativeIdToUriMap.get(volumeNativeId);
                if (volumeUri != null) {
                    unManagedVolumesUris.add(volumeUri.toString());
                } else {
                    volumeUri = managedVolumeNativeIdToUriMap.get(volumeNativeId);
                    if (volumeUri != null) {
                        managedVolumesUris.add(volumeUri.toString());
                    }
                }
            }
            // we will create new unManaged mask for host/array.
            UnManagedExportMask newMask = createUnManagedExportMask(storageSystem, hostExportInfo, unManagedVolumesUris, managedVolumesUris,
                    dbClient);
            unManagedExportMasksToCreate.add(newMask);
        }
    }

    /**
     *
     * @param storageSystem
     * @param masksToUpdateForManagedVolumes
     * @param masksToCreateForManagedVolumes
     * @param managedVolumeNativeIdToUriMap
     * @param unManagedExportMasksToUpdate
     * @param unManagedExportMasksToCreate
     * @param dbClient
     */
    private void processUnManagedMasksForManagedVolumes(com.emc.storageos.db.client.model.StorageSystem storageSystem,
                                                          Map<URI, HostExportInfo> masksToUpdateForManagedVolumes,
                                                          List<HostExportInfo> masksToCreateForManagedVolumes,
                                                          Map<String, URI> managedVolumeNativeIdToUriMap,
                                                          List<UnManagedExportMask> unManagedExportMasksToUpdate,
                                                          List<UnManagedExportMask> unManagedExportMasksToCreate,
                                                          DbClient dbClient) {

        // update/create unManaged masks for managed volumes
        for (Map.Entry<URI, HostExportInfo> entry : masksToUpdateForManagedVolumes.entrySet()) {
            URI maskUri = entry.getKey();
            HostExportInfo exportInfo = entry.getValue();

            Set<String> managedvolumesUris = new HashSet<>();
            List<String> volumesNativeIds = exportInfo.getStorageObjectNativeIds();
            for (String volumeNativeId : volumesNativeIds) {
                URI volumeUri = managedVolumeNativeIdToUriMap.get(volumeNativeId);
                managedvolumesUris.add(volumeUri.toString());
            }
            UnManagedExportMask unManagedMask = dbClient.queryObject(UnManagedExportMask.class, maskUri);
            StringSet volumesInMask = unManagedMask.getKnownVolumeUris();
            // check for null, since existing mask may only have unManaged volumes.
            if (volumesInMask == null) {
                volumesInMask = new StringSet();
                unManagedMask.setKnownVolumeUris(volumesInMask);
            }
            volumesInMask.addAll(managedvolumesUris);
            unManagedExportMasksToUpdate.add(unManagedMask);
        }

        for (HostExportInfo hostExportInfo : masksToCreateForManagedVolumes) {
            Set<String> managedvolumesUris = new HashSet<>();
            List<String> volumesNativeIds = hostExportInfo.getStorageObjectNativeIds();
            for (String volumeNativeId : volumesNativeIds) {
                URI volumeUri = managedVolumeNativeIdToUriMap.get(volumeNativeId);
                managedvolumesUris.add(volumeUri.toString());
            }
            // we will create new unManaged mask for host/array.
            UnManagedExportMask newMask = createUnManagedExportMask(storageSystem, hostExportInfo, null, managedvolumesUris,
                    dbClient);
            unManagedExportMasksToCreate.add(newMask);
        }
    }

    /**
     * Verifies that all members of the specified list have the same set of initiators ans the same set
     * of storage ports.
     *
     * @param hostExportInfoList
     * @return If validation is success return VolumeToHostExportInfo with common set of initiators, common set of
     * ports, and all volumes from all elements of input list.
     * If validation failed, return null.
     */
    private HostExportInfo verifyHostExports(List<HostExportInfo> hostExportInfoList) {
        HostExportInfo exportInfo = hostExportInfoList.get(0);

        String hostName = exportInfo.getHostName(); // FQDN of a host
        Set<String> volumeNativeIds = new HashSet<>(); // storage volumes native Ids
        Set<String> masterInitiatorNetworkIds = new HashSet<>(); // initiators port Ids
        Set<String> masterTargetNativeIds = new HashSet<>(); // target native Ids


        // get benchmark set of initiators and targets from first host export info instances
        List<Initiator> initiators = exportInfo.getInitiators(); // List of host initiators
        List<StoragePort> targets = exportInfo.getTargets();    // List of storage ports

        for (Initiator initiator : initiators) {
            masterInitiatorNetworkIds.add(initiator.getPort());
        }
        for (StoragePort port : targets) {
            masterTargetNativeIds.add(port.getNativeId());
        }

        for (HostExportInfo hostExportInfo : hostExportInfoList) {
            boolean isValid = verifyHostExports(masterInitiatorNetworkIds, masterTargetNativeIds, hostExportInfo);
            if (!isValid) {
                return null;
            }

            // Aggregate all volumes in one set.
            volumeNativeIds.addAll(hostExportInfo.getStorageObjectNativeIds());
        }

        // Create result export info
        HostExportInfo hostExportInfo = new HostExportInfo(hostName, new ArrayList<>(volumeNativeIds),
                initiators, targets);

        return hostExportInfo;
    }

    /**
     * Validates that hostExportInfo has the same set of initiators and storage ports as provided input arguments.
     * @param initiatorNetworkIds
     * @param storagePortNativeIds
     * @param hostExportInfo
     * @return
     */
    boolean verifyHostExports(Set<String> initiatorNetworkIds, Set<String> storagePortNativeIds, HostExportInfo hostExportInfo) {
        if (initiatorNetworkIds == null || storagePortNativeIds == null) {
            return false;
        }
        boolean isValid = true;
        Set<String> initiatorNetworkIdsSet = new HashSet<>();
        Set<String> targetNativeIdsSet = new HashSet<>();
        List<Initiator> initiatorsList = hostExportInfo.getInitiators();
        List<StoragePort> targetsList = hostExportInfo.getTargets();

        for (Initiator initiator : initiatorsList) {
            initiatorNetworkIdsSet.add(initiator.getPort());
        }
        for (StoragePort port : targetsList) {
            targetNativeIdsSet.add(port.getNativeId());
        }

        // compare with benchmark initiator and target set
        if (!initiatorNetworkIds.equals(initiatorNetworkIdsSet) || !storagePortNativeIds.equals(targetNativeIdsSet) ) {
            isValid = false;
        }

        return isValid;
    }

    /**
     * Get unManaged export mask for specified host and specified array.
     * Based on the enforced constraint there will be only zero or one such mask.
     *
     * @param hostName host name
     * @param dbClient
     * @param systemURI storage system
     * @return
     */
    private UnManagedExportMask getUnManagedExportMask(String hostName, DbClient dbClient, URI systemURI) {

        // try to find mask with the same initiator (at least one), this will cover case when host is known
        URIQueryResultList initiators = new URIQueryResultList();
        UnManagedExportMask uem = null;
        dbClient.queryByConstraint(AlternateIdConstraint.Factory
                .getInitiatorHostnameInitiatorConstraint(hostName), initiators);
        // check masks for each host initiator until we find one
        Iterator<URI> initiatorIterator = initiators.iterator();
        while (initiatorIterator.hasNext()) {
            com.emc.storageos.db.client.model.Initiator initiator = dbClient.queryObject(com.emc.storageos.db.client.model.Initiator.class,
                                                                                         initiatorIterator.next());
            URIQueryResultList masks = new URIQueryResultList();
            dbClient.queryByConstraint(AlternateIdConstraint.Factory
                    .getUnManagedExportMaskKnownInitiatorConstraint(initiator.getInitiatorPort()), masks);

            Iterator<URI> maskIterator = masks.iterator();
            while (maskIterator.hasNext()) {
                UnManagedExportMask potentialUem = dbClient.queryObject(UnManagedExportMask.class, maskIterator.next());
                // Check whether the unManaged export mask belongs to the specified storage system.
                if (URIUtil.identical(potentialUem.getStorageSystemUri(), systemURI)) {
                    uem = potentialUem;
                    break;
                }
            }
        }

        if (uem == null) {
            // this could be unknown host. We try to find mask by hostname
            URIQueryResultList masks = new URIQueryResultList();
            dbClient.queryByConstraint(AlternateIdConstraint.Factory
                    .getUnManagedExportMaskNameConstraint(hostName), masks);

            Iterator<URI> maskIterator = masks.iterator();
            while (maskIterator.hasNext()) {
                UnManagedExportMask potentialUem = dbClient.queryObject(UnManagedExportMask.class, maskIterator.next());
                // Check whether the unManaged export mask belongs to the specified storage system.
                if (URIUtil.identical(potentialUem.getStorageSystemUri(), systemURI)) {
                    uem = potentialUem;
                    break;
                }
            }
        }

        return uem;
    }

    /**
     * This method builds unManaged export mask from the provided hostExportInfo.
     *
     * @param hostExportInfo source for unmanaged export mask data
     * @param unManagedVolumesUris set of unManaged volumes database ids for unManaged mask
     * @param managedVolumesUris set of managed volumes database ids for unManaged mask
     * @param dbClient
     * @return unManaged export mask
     */
    private UnManagedExportMask createUnManagedExportMask(com.emc.storageos.db.client.model.StorageSystem storageSystem,
                                                          HostExportInfo hostExportInfo,
                                                          Set<String> unManagedVolumesUris, Set<String> managedVolumesUris,
                                                          DbClient dbClient) {

        UnManagedExportMask exportMask = new UnManagedExportMask();
        exportMask.setId(URIUtil.createId(UnManagedExportMask.class));

        StringSet knownInitiatorUris = new StringSet();
        StringSet knownInitiatorNetworkIds = new StringSet();
        StringSet unknownInitiatorNetworkIds = new StringSet();
        StringSet knownStoragePortUris = new StringSet();
        StringSet unmanagedStoragePortNetworkIds = new StringSet();
        StringSet unknownVolumesUris = new StringSet();
        StringSet knownVolumesUris =  new StringSet();

        List<com.emc.storageos.db.client.model.Initiator> knownFCInitiators = new ArrayList<>();
        List<com.emc.storageos.db.client.model.StoragePort> knownFCPorts = new ArrayList<>();

        String hostName = hostExportInfo.getHostName(); // FQDN of a host
        List<Initiator> initiators = hostExportInfo.getInitiators(); // List of host initiators
        List<StoragePort> targets = hostExportInfo.getTargets();    // List of storage ports

        exportMask.setMaskName(hostName);
        exportMask.setStorageSystemUri(storageSystem.getId());

        // get URIs for the initiators
        for (Initiator driverInitiator : initiators) {
            com.emc.storageos.db.client.model.Initiator knownInitiator =
                    NetworkUtil.getInitiator(driverInitiator.getPort(), dbClient);
            if (knownInitiator != null) {
                URI initiatorUri = knownInitiator.getId();
                knownInitiatorUris.add(initiatorUri.toString());
                knownInitiatorNetworkIds.add(driverInitiator.getPort());
                if (HostInterface.Protocol.FC.toString().equals(knownInitiator.getProtocol())) {
                    knownFCInitiators.add(knownInitiator);
                }
            } else {
                // unknown initiator
                unknownInitiatorNetworkIds.add(driverInitiator.getPort());
            }
        }
        exportMask.setKnownInitiatorNetworkIds(knownInitiatorNetworkIds);
        exportMask.setKnownInitiatorUris(knownInitiatorUris);
        exportMask.setUnmanagedInitiatorNetworkIds(unknownInitiatorNetworkIds);

        // get URIs for storage ports
        for (StoragePort driverPort : targets) {
            String portNativeGuid = NativeGUIDGenerator.generateNativeGuid(
                    storageSystem, driverPort.getNativeId(),
                    NativeGUIDGenerator.PORT);
            URIQueryResultList storagePortURIs = new URIQueryResultList();
            dbClient.queryByConstraint(
                    AlternateIdConstraint.Factory.getStoragePortByNativeGuidConstraint(portNativeGuid),
                    storagePortURIs);
            if (storagePortURIs.iterator().hasNext()) {
                URI portUri = storagePortURIs.iterator().next();
                knownStoragePortUris.add(portUri.toString());
                com.emc.storageos.db.client.model.StoragePort port = dbClient.
                        queryObject(com.emc.storageos.db.client.model.StoragePort.class, portUri);

                if (com.emc.storageos.db.client.model.StoragePort.TransportType.FC.toString().equals(port.getTransportType())) {
                    knownFCPorts.add(port);
                }
            } else {
                // unknown storage port
                unmanagedStoragePortNetworkIds.add(driverPort.getPortNetworkId());
            }
        }
        exportMask.setKnownStoragePortUris(knownStoragePortUris);
        exportMask.setUnmanagedStoragePortNetworkIds(unmanagedStoragePortNetworkIds);

        // set unManaged volume uris
        if (unManagedVolumesUris != null && !unManagedVolumesUris.isEmpty()) {
            unknownVolumesUris.addAll(unManagedVolumesUris);
            exportMask.setUnmanagedVolumeUris(unknownVolumesUris);
        }

        // set managed volume uris
        if (managedVolumesUris != null && !managedVolumesUris.isEmpty()) {
            knownVolumesUris.addAll(unManagedVolumesUris);
            exportMask.setKnownVolumeUris(knownVolumesUris);
        }

        // populate zone map for FC initiators and FC storage ports from the mask
        updateZoningMap(exportMask, knownFCInitiators, knownFCPorts);
        return exportMask;
    }

    private void updateZoningMap(UnManagedExportMask mask, List< com.emc.storageos.db.client.model.Initiator > initiators,
                                 List< com.emc.storageos.db.client.model.StoragePort > storagePorts) {
        ZoneInfoMap zoningMap = networkDeviceController.getInitiatorsZoneInfoMap(initiators, storagePorts);
        for (ZoneInfo zoneInfo : zoningMap.values()) {
            log.info("Found zone: {} for initiator {} and port {}", zoneInfo.getZoneName(),
                    zoneInfo.getInitiatorWwn(), zoneInfo.getPortWwn());
        }
        mask.setZoningMap(zoningMap);
    }

    /**
     * Update unmanaged volume with export data.
     *
     * @param unManagedVolumeNativeIdToUriMap
     * @param unManagedExportMasksToCreate
     * @param unManagedExportMasksToUpdate
     * @param dbClient
     * @param partitionManager
     */
    private void updateUnManagedVolumesWithExportData(Map<String, URI> unManagedVolumeNativeIdToUriMap, List<UnManagedExportMask> unManagedExportMasksToCreate,
                                                      List<UnManagedExportMask> unManagedExportMasksToUpdate,
                                                      DbClient dbClient, PartitionManager partitionManager) {
        // update unmanaged volumes with export data
        Map<String, List<UnManagedExportMask>> volumeToMasksMap = new HashMap<>(); // helper map:
                                                            // key --- volume uri, value: unmanaged masks for this volume.
        List<UnManagedVolume> unManagedVolumesToUpdate = new ArrayList<UnManagedVolume>();

        // build volume to mask map
        for (UnManagedExportMask mask : unManagedExportMasksToUpdate) {
            StringSet volumes = mask.getUnmanagedVolumeUris();
            for (String volumeUri : volumes) {
                List<UnManagedExportMask> volumeMasks = volumeToMasksMap.get(volumeUri);
                if (volumeMasks == null) {
                    volumeMasks = new ArrayList<>();
                    volumeToMasksMap.put(volumeUri, volumeMasks);
                }
                volumeMasks.add(mask);
            }
        }
        for (UnManagedExportMask mask : unManagedExportMasksToCreate) {
            StringSet volumes = mask.getUnmanagedVolumeUris();
            for (String volumeUri : volumes) {
                List<UnManagedExportMask> volumeMasks = volumeToMasksMap.get(volumeUri);
                if (volumeMasks == null) {
                    volumeMasks = new ArrayList<>();
                    volumeToMasksMap.put(volumeUri, volumeMasks);
                }
                volumeMasks.add(mask);
            }
        }

        for (URI volumeUri : unManagedVolumeNativeIdToUriMap.values()) {
            UnManagedVolume volume = dbClient.queryObject(UnManagedVolume.class, volumeUri);
            // Clean old export data
            volume.getInitiatorNetworkIds().clear();
            volume.getInitiatorUris().clear();
            volume.getUnmanagedExportMasks().clear();
            volume.getVolumeCharacterstics().put(UnManagedVolume.SupportedVolumeCharacterstics.IS_VOLUME_EXPORTED.toString(), FALSE);
            volume.getVolumeCharacterstics().put(UnManagedVolume.SupportedVolumeCharacterstics.IS_NONRP_EXPORTED.toString(), FALSE);
            List<UnManagedExportMask> volumeMasks = volumeToMasksMap.get(volumeUri.toString());
            if (volumeMasks != null && !volumeMasks.isEmpty()) {
                // update unmanaged volume with export data
                log.info("Updating volume {} with export data: {} .", volume.getNativeGuid(), volumeMasks);
                // set to  new data
                for (UnManagedExportMask mask : volumeMasks) {
                    volume.getInitiatorNetworkIds().addAll(mask.getKnownInitiatorNetworkIds());
                    volume.getInitiatorUris().addAll(mask.getKnownInitiatorUris());
                    volume.getUnmanagedExportMasks().add(mask.getId().toString());
                }
                volume.getVolumeCharacterstics().put(UnManagedVolume.SupportedVolumeCharacterstics.IS_VOLUME_EXPORTED.toString(), TRUE);
                volume.getVolumeCharacterstics().put(UnManagedVolume.SupportedVolumeCharacterstics.IS_NONRP_EXPORTED.toString(), TRUE);
                // todo set other export related properties.

                unManagedVolumesToUpdate.add(volume);
            } else {
                log.info("Volume {} does not have export masks.", volume.getNativeGuid());
            }
        }

        if (!unManagedVolumesToUpdate.isEmpty()) {
            partitionManager.updateAndReIndexInBatches(unManagedVolumesToUpdate,
                    Constants.DEFAULT_PARTITION_SIZE, dbClient, UNMANAGED_VOLUME);
            unManagedVolumesToUpdate.clear();
        }
    }
}
