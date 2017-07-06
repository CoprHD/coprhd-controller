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
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.HostInterface;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.StringSetMap;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedConsistencyGroup;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedExportMask;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.ZoneInfo;
import com.emc.storageos.db.client.model.ZoneInfoMap;
import com.emc.storageos.db.client.util.StringSetUtil;
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
import com.emc.storageos.storagedriver.storagecapabilities.CapabilityDefinition;
import com.emc.storageos.storagedriver.storagecapabilities.CapabilityInstance;
import com.emc.storageos.storagedriver.storagecapabilities.HostIOLimitsCapabilityDefinition;
import com.emc.storageos.storagedriver.storagecapabilities.StorageCapabilitiesUtils;
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
    public static final long UNMANAGED_DISCOVERY_LOCK_TIMEOUT = 3 * 60; // set to 3 minutes

    private NetworkDeviceController networkDeviceController;
    private CoordinatorClient coordinator;

    public void setNetworkDeviceController(
            NetworkDeviceController networkDeviceController) {
        this.networkDeviceController = networkDeviceController;
    }

    public void setCoordinator(CoordinatorClient coordinator) {
        this.coordinator = coordinator;
    }

    /**
     * Discovers unmanaged block objects: volumes, snaps, clones, their CG information and their exports.
     * @param driver storage driver reference [IN]
     * @param storageSystem storage system [IN]
     * @param dbClient reference to db client [IN]
     * @param partitionManager partition manager [IN]
     */
    public void discoverUnManagedBlockObjects(BlockStorageDriver driver,
                                              com.emc.storageos.db.client.model.StorageSystem storageSystem,
                                              DbClient dbClient, PartitionManager partitionManager) {

        Set<URI> allCurrentUnManagedVolumeUris = new HashSet<>();
        Set<URI> allCurrentUnManagedCgURIs = new HashSet<>();
        MutableInt lastPage = new MutableInt(0);
        MutableInt nextPage = new MutableInt(0);
        List<UnManagedVolume> unManagedVolumesToCreate = new ArrayList<>();
        List<UnManagedVolume> unManagedVolumesToUpdate = new ArrayList<>();
        List<UnManagedConsistencyGroup> unManagedCGToUpdate;
        Map<String, UnManagedConsistencyGroup> unManagedCGToUpdateMap = new HashMap<>();

        // We support only single export mask concept for host-array combination for external devices.
        // If we find that storage system has volumes which are exported to the same host through
        // different initiators or different array ports (we cannot create single UnManaged export
        // mask for the host and the array in this case), we won't discover exports to this
        // host on the array; we discover only volumes.
        // The result of this limitation is that it could happen that for some volumes we are able to
        // discover all their host exports;
        // for some volumes we will be able to discover their exports to subset of hosts;
        // for some volumes we may not be able to discover their exports to hosts.
        // This limits management scope for pre-existing exports initially, but this does not
        // not present a management issue for exports going forward, since driver implementation should handle export requests based
        // on provided initiators and volumes in the requests and the current state of device.

        Set<String> invalidExportHosts = new HashSet<>(); // set of hosts for which we cannot build single export mask
        // for exported array volumes

        // get inter-process lock for exclusive discovery of unmanaged objects for a given system
        // lock is backed by curator's InterProcessMutex.
        InterProcessLock lock = null;
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
            // check that lock was not acquired. if lock was acquired for this thread, proceed.
            if (lock == null || !lock.isAcquiredInThisProcess()) {
                log.error("Error processing unmanaged discovery for storage system: {}. Failed to get lock {} for this operation.",
                        storageSystem.getNativeId(), lockName, ex);
                return;
            }
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
                Map<String, List<HostExportInfo>> hostToVolumeExportInfoMap = new HashMap<>();

                List<StorageVolume> driverVolumes = new ArrayList<>();
                Map<String, URI> unManagedVolumeNativeIdToUriMap = new HashMap<>();
                Map<String, URI> managedVolumeNativeIdToUriMap = new HashMap<>();

                log.info("Processing page {} ", nextPage);
                driver.getStorageVolumes(driverStorageSystem, driverVolumes, nextPage);
                log.info("Volume count on this page {} ", driverVolumes.size());

                for (StorageVolume driverVolume : driverVolumes) {

                    if (DiscoveryUtils.isUnmanagedDiscoveryKillSwitchOn()) {
                        log.warn("Discovery kill switch is on, discontinuing unmanaged volume discovery.");
                        return;
                    }

                    if (!DiscoveryUtils.isUnmanagedVolumeFilterMatching(driverVolume.getDisplayName())) {
                        // skipping this volume because the filter doesn't match
                        continue;
                    }

                    UnManagedVolume unManagedVolume = null;
                    try {
                        com.emc.storageos.db.client.model.StoragePool storagePool = getStoragePoolOfUnManagedVolume(storageSystem, driverVolume, dbClient);
                        if (null == storagePool) {
                            log.error("Skipping unManaged volume discovery as the volume {} storage pool doesn't exist in controller", driverVolume.getNativeId());
                            continue;
                        }
                        String managedVolumeNativeGuid = NativeGUIDGenerator.generateNativeGuidForVolumeOrBlockSnapShot(
                                storageSystem.getNativeGuid(), driverVolume.getNativeId());
                        Volume systemVolume = DiscoveryUtils.checkStorageVolumeExistsInDB(dbClient, managedVolumeNativeGuid);
                        if (null != systemVolume) {
                            log.info("Skipping volume {} as it is already managed by the system. Id: {}", managedVolumeNativeGuid, systemVolume.getId());

                            // get export data for managed volume to process later --- we need to collect export data for
                            // managed volume
                            managedVolumeNativeIdToUriMap.put(driverVolume.getNativeId(), systemVolume.getId());
                            getVolumeExportInfo(driver, driverVolume, hostToVolumeExportInfoMap);
                            getExportInfoForManagedVolumeReplicas(managedVolumeNativeIdToUriMap, hostToVolumeExportInfoMap,
                                    dbClient, storageSystem, systemVolume, driverVolume, driver);
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
                    log.info("Unmanaged volumes to create: {}", unManagedVolumesToCreate);
                    partitionManager.insertInBatches(unManagedVolumesToCreate,
                            Constants.DEFAULT_PARTITION_SIZE, dbClient, UNMANAGED_VOLUME);
                    unManagedVolumesToCreate.clear();
                }
                if (!unManagedVolumesToUpdate.isEmpty()) {
                    log.info("Unmanaged volumes to update: {}", unManagedVolumesToUpdate);
                    partitionManager.updateAndReIndexInBatches(unManagedVolumesToUpdate,
                            Constants.DEFAULT_PARTITION_SIZE, dbClient, UNMANAGED_VOLUME);
                    unManagedVolumesToUpdate.clear();
                }

                // Process export data for volumes
                processExportData(driver, storageSystem, unManagedVolumeNativeIdToUriMap,
                        managedVolumeNativeIdToUriMap,
                        hostToVolumeExportInfoMap,
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
     * @param driverVolume             storage system volume [IN]
     * @param storageSystem            storage system for unManaged volume [IN]
     * @param storagePool              storage pool for unManaged volume [IN]
     * @param unManagedVolumesToCreate list of new unManaged volumes [OUT]
     * @param unManagedVolumesToUpdate list of unManaged volumes to update [OUT]
     * @param dbClient reference to db client [IN]
     * @return unmanaged volume
     */
    private UnManagedVolume createUnManagedVolume(StorageVolume driverVolume, com.emc.storageos.db.client.model.StorageSystem storageSystem,
                                                  com.emc.storageos.db.client.model.StoragePool storagePool,
                                                  List<UnManagedVolume> unManagedVolumesToCreate, List<UnManagedVolume> unManagedVolumesToUpdate,
                                                  DbClient dbClient) {

        boolean newVolume = false;
        String unManagedVolumeNatvieGuid = NativeGUIDGenerator.generateNativeGuidForPreExistingVolume(
                storageSystem.getNativeGuid(), driverVolume.getNativeId());

        UnManagedVolume unManagedVolume = DiscoveryUtils.checkUnManagedVolumeExistsInDB(dbClient, unManagedVolumeNatvieGuid);
        if (null == unManagedVolume) {
            unManagedVolume = new UnManagedVolume();
            unManagedVolume.setId(URIUtil.createId(UnManagedVolume.class));
            unManagedVolume.setNativeGuid(unManagedVolumeNatvieGuid);
            unManagedVolume.setStorageSystemUri(storageSystem.getId());

            if (driverVolume.getWwn() == null) {
                unManagedVolume.setWwn(String.format("%s:%s", driverVolume.getStorageSystemId(), driverVolume.getNativeId()));
            } else {
                unManagedVolume.setWwn(driverVolume.getWwn());
            }
            newVolume = true;
        } else {
            // cleanup relationships from previous discoveries, will set them according to this discovery
            unManagedVolume.putVolumeCharacterstics(UnManagedVolume.SupportedVolumeCharacterstics.HAS_REPLICAS.toString(), FALSE);
            unManagedVolume.putVolumeInfo(UnManagedVolume.SupportedVolumeInformation.SNAPSHOTS.toString(), new StringSet());
            unManagedVolume.putVolumeInfo(UnManagedVolume.SupportedVolumeInformation.FULL_COPIES.toString(), new StringSet());
            // Clear old export mask information
            unManagedVolume.getUnmanagedExportMasks().clear();

            // cleanup hostiolimits from previous discoveries
            unManagedVolume.putVolumeInfo(UnManagedVolume.SupportedVolumeInformation.EMC_MAXIMUM_IO_BANDWIDTH.toString(), new StringSet());
            unManagedVolume.putVolumeInfo(UnManagedVolume.SupportedVolumeInformation.EMC_MAXIMUM_IOPS.toString(), new StringSet());
        }

        unManagedVolume.setLabel(driverVolume.getDeviceLabel());
        Boolean isVolumeExported = false;
        unManagedVolume.putVolumeCharacterstics(UnManagedVolume.SupportedVolumeCharacterstics.IS_VOLUME_EXPORTED.toString(), isVolumeExported.toString());

        // Set these default to false.
        unManagedVolume.putVolumeCharacterstics(UnManagedVolume.SupportedVolumeCharacterstics.IS_NONRP_EXPORTED.toString(), FALSE);
        unManagedVolume.putVolumeCharacterstics(UnManagedVolume.SupportedVolumeCharacterstics.IS_RECOVERPOINT_ENABLED.toString(), FALSE);

        StringSet deviceLabel = new StringSet();
        deviceLabel.add(driverVolume.getDeviceLabel());
        unManagedVolume.putVolumeInfo(UnManagedVolume.SupportedVolumeInformation.DEVICE_LABEL.toString(), deviceLabel);

        StringSet accessState = new StringSet();
        accessState.add(driverVolume.getAccessStatus().toString());
        unManagedVolume.putVolumeInfo(UnManagedVolume.SupportedVolumeInformation.ACCESS.toString(), accessState);

        StringSet provCapacity = new StringSet();
        provCapacity.add(String.valueOf(driverVolume.getProvisionedCapacity()));
        unManagedVolume.putVolumeInfo(UnManagedVolume.SupportedVolumeInformation.PROVISIONED_CAPACITY.toString(),
                provCapacity);

        StringSet allocatedCapacity = new StringSet();
        allocatedCapacity.add(String.valueOf(driverVolume.getAllocatedCapacity()));
        unManagedVolume.putVolumeInfo(UnManagedVolume.SupportedVolumeInformation.ALLOCATED_CAPACITY.toString(), allocatedCapacity);

        StringSet systemTypes = new StringSet();
        systemTypes.add(storageSystem.getSystemType());
        unManagedVolume.putVolumeInfo(UnManagedVolume.SupportedVolumeInformation.SYSTEM_TYPE.toString(),
                systemTypes);

        StringSet nativeId = new StringSet();
        nativeId.add(driverVolume.getNativeId());
        unManagedVolume.putVolumeInfo(UnManagedVolume.SupportedVolumeInformation.NATIVE_ID.toString(),
                nativeId);

        // process hostiolimits from driver volume common capabilities
        CapabilityInstance hostIOLimits =
                StorageCapabilitiesUtils.getDataStorageServiceCapability(driverVolume.getCommonCapabilities(), CapabilityDefinition.CapabilityUid.hostIOLimits);
        if (hostIOLimits != null) {
            log.info("HostIOLimits for volume {}: {} ", driverVolume.getNativeId(),hostIOLimits.toString());
            String bandwidth = hostIOLimits.getPropertyValue(HostIOLimitsCapabilityDefinition.PROPERTY_NAME.HOST_IO_LIMIT_BANDWIDTH.toString());
            String iops = hostIOLimits.getPropertyValue(HostIOLimitsCapabilityDefinition.PROPERTY_NAME.HOST_IO_LIMIT_IOPS.toString());
            if (bandwidth != null) {
                StringSet bwValue = new StringSet();
                bwValue.add(bandwidth);
                unManagedVolume.putVolumeInfo(
                        UnManagedVolume.SupportedVolumeInformation.EMC_MAXIMUM_IO_BANDWIDTH.toString(), bwValue);
            }
            if (iops != null) {
                StringSet iopsValue = new StringSet();
                iopsValue.add(iops);
                unManagedVolume.putVolumeInfo(
                        UnManagedVolume.SupportedVolumeInformation.EMC_MAXIMUM_IOPS.toString(), iopsValue);
            }
        }

        unManagedVolume.putVolumeCharacterstics(
                UnManagedVolume.SupportedVolumeCharacterstics.IS_INGESTABLE.toString(), TRUE);

        unManagedVolume.putVolumeCharacterstics(UnManagedVolume.SupportedVolumeCharacterstics.IS_THINLY_PROVISIONED.toString(),
                driverVolume.getThinlyProvisioned().toString());

        unManagedVolume.setStoragePoolUri(storagePool.getId());
        StringSet pools = new StringSet();
        pools.add(storagePool.getId().toString());
        unManagedVolume.putVolumeInfo(UnManagedVolume.SupportedVolumeInformation.STORAGE_POOL.toString(), pools);
        StringSet driveTypes = storagePool.getSupportedDriveTypes();
        if (null != driveTypes) {
            unManagedVolume.putVolumeInfo(UnManagedVolume.SupportedVolumeInformation.DISK_TECHNOLOGY.toString(), driveTypes);
        }
        StringSet matchedVPools = DiscoveryUtils.getMatchedVirtualPoolsForPool(dbClient, storagePool.getId(),
                unManagedVolume.getVolumeCharacterstics().get(UnManagedVolume.SupportedVolumeCharacterstics.IS_THINLY_PROVISIONED.toString()),
                unManagedVolume);
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
     * Get storage pool of unManaged volume.
     *
     * @param storageSystem storage system [IN]
     * @param driverVolume reference to driver [IN]
     * @param dbClient reference to db client [IN]
     * @return storage pool for unmanaged unmanged volume
     */
    private com.emc.storageos.db.client.model.StoragePool getStoragePoolOfUnManagedVolume(
                                                            com.emc.storageos.db.client.model.StorageSystem storageSystem,
                                                            StorageVolume driverVolume, DbClient dbClient) {
        com.emc.storageos.db.client.model.StoragePool storagePool = null;
        // Get system pool
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
     * @param storageSystem             storage system of the object [IN]
     * @param cgNativeId                native id of umanaged consistency group [IN]
     * @param unManagedVolume           unManaged object [IN/OUT] unmanaged obect (volume/snap/clone) with CG information
     * @param allCurrentUnManagedCgURIs set of unManaged CG uris found in the current discovery [OUT]
     * @param unManagedCGToUpdateMap    map of unManaged CG GUID to unManaged CG instance [IN/OUT]
     * @param driver                    storage driver [IN]
     * @param dbClient                  reference to db client [IN]
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
        // if the the unManaged CG is not in either, create a new one
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
     * @param driverCG reference to native CG
     * @param storageSystem storage system
     * @param dbClient reference to db client [IN]
     * @return umanaged CG
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
     * Check if unManaged snapshot should be created and create unManaged volume instance for a snapshot in such a case.
     * Add unManaged snapshot to parent volume CG if needed and update the snap with parent volume CG information.
     * Gets export information for snapshots and stores it in the provided map.
     *
     * @param driverVolume              driver volume for snap parent volume. [IN]
     * @param unManagedParentVolume     unManaged parent volume [IN/OUT]
     * @param storageSystem             storage system [IN]
     * @param storagePool               storage pool [IN]
     * @param unManagedVolumesToCreate  list of unmanaged volumes to create [OUT]
     * @param unManagedVolumesToUpdate  list of unmanaged volumes to update [OUT]
     * @param allCurrentUnManagedCgURIs set of unManaged CG uris found in the current discovery [OUT]
     * @param unManagedCGToUpdateMap    map of unManaged CG GUID to unManaged CG instance [IN/OUT]
     * @param unManagedVolumeNativeIdToUriMap map of unmanaged volumes nativeId to their uri [OUT]
     * @param hostToUnManagedVolumeExportInfoMap map  with export data for unmanaged volumes (including snaps and clones)
     * @param driver                    storage driver [IN]
     * @param dbClient                  reference to db client [IN]
     * @return                          set of URIs for unmanaged snapshots
     * @throws Exception
     */
    private Set<URI> processUnManagedSnapshots(StorageVolume driverVolume, UnManagedVolume unManagedParentVolume,
                                               com.emc.storageos.db.client.model.StorageSystem storageSystem,
                                               com.emc.storageos.db.client.model.StoragePool storagePool,
                                               List<UnManagedVolume> unManagedVolumesToCreate,
                                               List<UnManagedVolume> unManagedVolumesToUpdate,
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
                BlockSnapshot systemSnap = DiscoveryUtils.checkBlockSnapshotExistsInDB(dbClient, managedSnapNativeGuid);
                if (null != systemSnap) {
                    log.info("Skipping snapshot {} as it is already managed by the system.", managedSnapNativeGuid);
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
                    unManagedParentVolume.putVolumeInfo(
                            UnManagedVolume.SupportedVolumeInformation.SNAPSHOTS.toString(),unManagedSnaps);
                    log.info("Replaced snaps :" + Joiner.on("\t").join(unManagedVolumeInformation.get(
                            UnManagedVolume.SupportedVolumeInformation.SNAPSHOTS.toString())));
                } else {
                    unManagedParentVolume.putVolumeInfo(
                            UnManagedVolume.SupportedVolumeInformation.SNAPSHOTS.toString(), unManagedSnaps);
                }
            } else {
                log.info("All snapshots for volume {} are already managed.", unManagedParentVolume.getNativeGuid());
            }
        }
        return snapshotUris;
    }

    /**
     * Process clones of unManaged volume.
     * Check if unManaged clone should be created and create unManaged volume instance for a clone in such a case.
     * Add unManaged clone to parent volume CG if needed and update the clone with parent volume CG information.
     * Gets export information for clones and stores it in the provided map.
     *
     * @param driverVolume              driver volume for clone parent volume. [IN]
     * @param unManagedParentVolume     unManaged parent volume [IN/OUT]
     * @param storageSystem             storage system [IN]
     * @param storagePool               storage pool [IN]
     * @param unManagedVolumesToCreate  list of unmanaged volumes to create [OUT]
     * @param unManagedVolumesToUpdate  list of unmanaged volumes to update [OUT]
     * @param allCurrentUnManagedCgURIs set of unManaged CG uris found in the current discovery [OUT]
     * @param unManagedCGToUpdateMap    map of unManaged CG GUID to unManaged CG instance [IN/OUT]
     * @param unManagedVolumeNativeIdToUriMap map of unmanaged volumes nativeId to their uri [OUT]
     * @param hostToUnManagedVolumeExportInfoMap map  with export data for unmanaged volumes (including snaps and clones)
     * @param driver                    storage driver [IN]
     * @param dbClient                  reference to db client [IN]
     * @return                          set of URIs for unmanaged clones
     * @throws Exception
     */
    private Set<URI> processUnManagedClones(StorageVolume driverVolume, UnManagedVolume unManagedParentVolume,
                                            com.emc.storageos.db.client.model.StorageSystem storageSystem,
                                            com.emc.storageos.db.client.model.StoragePool storagePool,
                                            List<UnManagedVolume> unManagedVolumesToCreate,
                                            List<UnManagedVolume> unManagedVolumesToUpdate,
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
                Volume systemClone = DiscoveryUtils.checkStorageVolumeExistsInDB(dbClient, managedCloneNativeGuid);
                if (null != systemClone) {
                    log.info("Skipping clone {} as it is already managed by the system.", managedCloneNativeGuid);
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
                    log.info("Clone {} is for volume in CG. ", managedCloneNativeGuid);
                    // add clone to parent volume unManaged consistency group, update clone with parent volume CG information.
                    addObjectToUnManagedConsistencyGroup(storageSystem, driverVolume.getConsistencyGroup(), unManagedClone,
                            allCurrentUnManagedCgURIs, unManagedCGToUpdateMap, driver, dbClient);
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
                    log.info("Replaced clones :" + Joiner.on("\t").join(unManagedVolumeInformation.get(
                            UnManagedVolume.SupportedVolumeInformation.FULL_COPIES.toString())));
                } else {
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
     * Create new or update existing unManaged snapshot with driver snapshot discovery data.
     *
     * @param driverSnapshot device snap [IN]
     * @param parentUnManagedVolume unmanaged parent volume [IN]
     * @param storageSystem storage system [IN]
     * @param storagePool storage pool [IN]
     * @param unManagedVolumesToCreate list of unmanaged volumes to create [OUT]
     * @param unManagedVolumesToUpdate list of unmanaged volumes to update [OUT]
     * @param dbClient reference to db client [IN]
     * @return unmanaged volume for device snapshot
     */
    private UnManagedVolume createUnManagedSnapshot(VolumeSnapshot driverSnapshot, UnManagedVolume parentUnManagedVolume,
                                                    com.emc.storageos.db.client.model.StorageSystem storageSystem,
                                                    com.emc.storageos.db.client.model.StoragePool storagePool,
                                                    List<UnManagedVolume> unManagedVolumesToCreate, List<UnManagedVolume> unManagedVolumesToUpdate,
                                                    DbClient dbClient) {
        // We process unManaged snapshot as unManaged volume
        boolean newVolume = false;

        String unManagedVolumeNatvieGuid = NativeGUIDGenerator.generateNativeGuidForPreExistingVolume(
                storageSystem.getNativeGuid(), driverSnapshot.getNativeId());

        UnManagedVolume unManagedVolume = DiscoveryUtils.checkUnManagedVolumeExistsInDB(dbClient, unManagedVolumeNatvieGuid);
        if (null == unManagedVolume) {
            unManagedVolume = new UnManagedVolume();
            unManagedVolume.setId(URIUtil.createId(UnManagedVolume.class));
            unManagedVolume.setNativeGuid(unManagedVolumeNatvieGuid);
            unManagedVolume.setStorageSystemUri(storageSystem.getId());

            if (driverSnapshot.getWwn() == null) {
                unManagedVolume.setWwn(String.format("%s:%s", driverSnapshot.getStorageSystemId(), driverSnapshot.getNativeId()));
            } else {
                unManagedVolume.setWwn(driverSnapshot.getWwn());
            }
            newVolume = true;
        } else {
            // cleanup relationships from previous discoveries, we will set them according to this discovery
            // Make sure the unManagedVolume snapshot object does not contain parent CG information from previous discovery
            unManagedVolume.putVolumeCharacterstics(UnManagedVolume.SupportedVolumeCharacterstics.IS_VOLUME_ADDED_TO_CONSISTENCYGROUP.toString(),
                    Boolean.FALSE.toString());
            // remove uri of the unManaged CG in the unManaged volume object
            unManagedVolume.putVolumeInfo(UnManagedVolume.SupportedVolumeInformation.UNMANAGED_CONSISTENCY_GROUP_URI.toString(), new StringSet());
            // Clean old data for replication group name
            unManagedVolume.putVolumeInfo(UnManagedVolume.SupportedVolumeInformation.SNAPSHOT_CONSISTENCY_GROUP_NAME.toString(), new StringSet());
            // Clear old export mask information
            unManagedVolume.getUnmanagedExportMasks().clear();
        }

        unManagedVolume.setLabel(driverSnapshot.getDeviceLabel());
        Boolean isVolumeExported = false;
        unManagedVolume.putVolumeCharacterstics(UnManagedVolume.SupportedVolumeCharacterstics.IS_VOLUME_EXPORTED.toString(), isVolumeExported.toString());

        // Set these default to false. The individual storage discovery will change them if needed.
        unManagedVolume.putVolumeCharacterstics(UnManagedVolume.SupportedVolumeCharacterstics.IS_NONRP_EXPORTED.toString(), FALSE);
        unManagedVolume.putVolumeCharacterstics(UnManagedVolume.SupportedVolumeCharacterstics.IS_RECOVERPOINT_ENABLED.toString(), FALSE);

        StringSet deviceLabel = new StringSet();
        deviceLabel.add(driverSnapshot.getDeviceLabel());
        unManagedVolume.putVolumeInfo(UnManagedVolume.SupportedVolumeInformation.DEVICE_LABEL.toString(),
                deviceLabel);


        if (driverSnapshot.getAccessStatus() != null) {
            StringSet accessState = new StringSet();
            accessState.add(driverSnapshot.getAccessStatus().toString());
            unManagedVolume.putVolumeInfo(UnManagedVolume.SupportedVolumeInformation.ACCESS.toString(), accessState);
        }

        StringSet systemTypes = new StringSet();
        systemTypes.add(storageSystem.getSystemType());
        unManagedVolume.putVolumeInfo(UnManagedVolume.SupportedVolumeInformation.SYSTEM_TYPE.toString(),
                systemTypes);

        StringSet nativeId = new StringSet();
        nativeId.add(driverSnapshot.getNativeId());
        unManagedVolume.putVolumeInfo(UnManagedVolume.SupportedVolumeInformation.NATIVE_ID.toString(), nativeId);

        unManagedVolume.putVolumeCharacterstics(
                UnManagedVolume.SupportedVolumeCharacterstics.IS_INGESTABLE.toString(), TRUE);

        unManagedVolume.putVolumeCharacterstics(UnManagedVolume.SupportedVolumeCharacterstics.IS_SNAP_SHOT.toString(), TRUE);

        StringSet parentVol = new StringSet();
        parentVol.add(parentUnManagedVolume.getNativeGuid());
        unManagedVolume.putVolumeInfo(UnManagedVolume.SupportedVolumeInformation.LOCAL_REPLICA_SOURCE_VOLUME.toString(), parentVol);

        StringSet isSyncActive = new StringSet();
        isSyncActive.add(TRUE);
        unManagedVolume.putVolumeInfo(UnManagedVolume.SupportedVolumeInformation.IS_SYNC_ACTIVE.toString(), isSyncActive);

        StringSet isReadOnly = new StringSet();
        Boolean readOnly = Boolean.FALSE;
        if (driverSnapshot.getAccessStatus() != null) {
            readOnly = (driverSnapshot.getAccessStatus().toString()).equals(StorageObject.AccessStatus.READ_ONLY.toString()) ?
                    Boolean.TRUE : Boolean.FALSE;
        }
        isReadOnly.add(readOnly.toString());
        unManagedVolume.putVolumeInfo(UnManagedVolume.SupportedVolumeInformation.IS_READ_ONLY.toString(), isReadOnly);

        StringSet techType = new StringSet();
        techType.add(BlockSnapshot.TechnologyType.NATIVE.toString());
        unManagedVolume.putVolumeInfo(UnManagedVolume.SupportedVolumeInformation.TECHNOLOGY_TYPE.toString(), techType);

        // set snapshot consistency group information in the unmanged snapshot object
        String isParentVolumeInCG =
                parentUnManagedVolume.getVolumeCharacterstics().get(UnManagedVolume.SupportedVolumeCharacterstics.IS_VOLUME_ADDED_TO_CONSISTENCYGROUP.toString());
        if (isParentVolumeInCG.equals(Boolean.TRUE.toString())) {
            // set snapshot consistency group name
            if (driverSnapshot.getConsistencyGroup() != null && !driverSnapshot.getConsistencyGroup().isEmpty()) {
                StringSet snapCgName = new StringSet();
                snapCgName.add(driverSnapshot.getConsistencyGroup());
                unManagedVolume.putVolumeInfo(UnManagedVolume.SupportedVolumeInformation.SNAPSHOT_CONSISTENCY_GROUP_NAME.toString(),
                        snapCgName);
            }
        }

        // set from parent volume (required for snaps by ingest framework)
        unManagedVolume.putVolumeCharacterstics(UnManagedVolume.SupportedVolumeCharacterstics.IS_THINLY_PROVISIONED.toString(),
                parentUnManagedVolume.getVolumeCharacterstics().get(UnManagedVolume.SupportedVolumeCharacterstics.IS_THINLY_PROVISIONED.toString()));

        unManagedVolume.setStoragePoolUri(storagePool.getId());
        StringSet pools = new StringSet();
        pools.add(storagePool.getId().toString());
        unManagedVolume.putVolumeInfo(UnManagedVolume.SupportedVolumeInformation.STORAGE_POOL.toString(), pools);

        StringSet driveTypes = storagePool.getSupportedDriveTypes();
        if (null != driveTypes) {
            unManagedVolume.putVolumeInfo(UnManagedVolume.SupportedVolumeInformation.DISK_TECHNOLOGY.toString(), driveTypes);
        }

        StringSet provCapacity = new StringSet();
        provCapacity.add(String.valueOf(driverSnapshot.getProvisionedCapacity()));
        unManagedVolume.putVolumeInfo(UnManagedVolume.SupportedVolumeInformation.PROVISIONED_CAPACITY.toString(),
                provCapacity);

        StringSet allocatedCapacity = new StringSet();
        allocatedCapacity.add(String.valueOf(driverSnapshot.getAllocatedCapacity()));
        unManagedVolume.putVolumeInfo(UnManagedVolume.SupportedVolumeInformation.ALLOCATED_CAPACITY.toString(),
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
     * Create new or update existing unManaged clone with driver clone discovery data.
     *
     * @param driverClone device clone [IN]
     * @param parentUnManagedVolume unmanaged parent volume [IN]
     * @param storageSystem storage system [IN]
     * @param storagePool storage pool [IN]
     * @param unManagedVolumesToCreate list of unmanaged volumes to create [OUT]
     * @param unManagedVolumesToUpdate list of unmanaged volumes to update [OUT]
     * @param dbClient reference to db client [IN]
     * @return unmanaged volume for device clone
     */
    private UnManagedVolume createUnManagedClone(VolumeClone driverClone, UnManagedVolume parentUnManagedVolume,
                                                 com.emc.storageos.db.client.model.StorageSystem storageSystem,
                                                 com.emc.storageos.db.client.model.StoragePool storagePool,
                                                 List<UnManagedVolume> unManagedVolumesToCreate,
                                                 List<UnManagedVolume> unManagedVolumesToUpdate,
                                                 DbClient dbClient) {
        // We process unManaged clone as unManaged volume
        boolean newVolume = false;

        String unManagedVolumeNatvieGuid = NativeGUIDGenerator.generateNativeGuidForPreExistingVolume(
                storageSystem.getNativeGuid(), driverClone.getNativeId());

        UnManagedVolume unManagedVolume = DiscoveryUtils.checkUnManagedVolumeExistsInDB(dbClient, unManagedVolumeNatvieGuid);
        if (null == unManagedVolume) {
            unManagedVolume = new UnManagedVolume();
            unManagedVolume.setId(URIUtil.createId(UnManagedVolume.class));
            unManagedVolume.setNativeGuid(unManagedVolumeNatvieGuid);
            unManagedVolume.setStorageSystemUri(storageSystem.getId());

            if (driverClone.getWwn() == null) {
                unManagedVolume.setWwn(String.format("%s:%s", driverClone.getStorageSystemId(), driverClone.getNativeId()));
            } else {
                unManagedVolume.setWwn(driverClone.getWwn());
            }
            newVolume = true;
        } else {
            // cleanup relationships from previous discoveries, we will set them according to this discovery
            // Make sure the unManagedVolume clone object does not contain parent CG information from previous discovery
            unManagedVolume.putVolumeCharacterstics(
                    UnManagedVolume.SupportedVolumeCharacterstics.IS_VOLUME_ADDED_TO_CONSISTENCYGROUP.toString(), Boolean.FALSE.toString());
            // remove uri of the unManaged CG in the unManaged volume object
            unManagedVolume.putVolumeInfo(UnManagedVolume.SupportedVolumeInformation.UNMANAGED_CONSISTENCY_GROUP_URI.toString(), new StringSet());
            // Clean old data for replication group name
            unManagedVolume.putVolumeInfo(UnManagedVolume.SupportedVolumeInformation.FULL_COPY_CONSISTENCY_GROUP_NAME.toString(), new StringSet());
            // Clear old export mask information
            unManagedVolume.getUnmanagedExportMasks().clear();
        }

        unManagedVolume.setLabel(driverClone.getDeviceLabel());
        unManagedVolume.putVolumeCharacterstics(UnManagedVolume.SupportedVolumeCharacterstics.IS_VOLUME_EXPORTED.toString(), FALSE);

        // Set these default to false. The individual storage discovery will change them if needed.
        unManagedVolume.putVolumeCharacterstics(UnManagedVolume.SupportedVolumeCharacterstics.IS_NONRP_EXPORTED.toString(), FALSE);
        unManagedVolume.putVolumeCharacterstics(UnManagedVolume.SupportedVolumeCharacterstics.IS_RECOVERPOINT_ENABLED.toString(), FALSE);

        StringSet deviceLabel = new StringSet();
        deviceLabel.add(driverClone.getDeviceLabel());
        unManagedVolume.putVolumeInfo(UnManagedVolume.SupportedVolumeInformation.DEVICE_LABEL.toString(), deviceLabel);

        if (driverClone.getAccessStatus() != null) {
            StringSet accessState = new StringSet();
            accessState.add(driverClone.getAccessStatus().toString());
            unManagedVolume.putVolumeInfo(UnManagedVolume.SupportedVolumeInformation.ACCESS.toString(), accessState);
        }

        StringSet systemTypes = new StringSet();
        systemTypes.add(storageSystem.getSystemType());
        unManagedVolume.putVolumeInfo(UnManagedVolume.SupportedVolumeInformation.SYSTEM_TYPE.toString(), systemTypes);

        StringSet nativeId = new StringSet();
        nativeId.add(driverClone.getNativeId());
        unManagedVolume.putVolumeInfo(UnManagedVolume.SupportedVolumeInformation.NATIVE_ID.toString(), nativeId);

        unManagedVolume.putVolumeCharacterstics(
                UnManagedVolume.SupportedVolumeCharacterstics.IS_INGESTABLE.toString(), TRUE);

        unManagedVolume.putVolumeCharacterstics(UnManagedVolume.SupportedVolumeCharacterstics.IS_THINLY_PROVISIONED.toString(),
                driverClone.getThinlyProvisioned().toString());

        unManagedVolume.putVolumeCharacterstics(UnManagedVolume.SupportedVolumeCharacterstics.IS_FULL_COPY.toString(), TRUE);

        StringSet parentVol = new StringSet();
        parentVol.add(parentUnManagedVolume.getNativeGuid());
        unManagedVolume.putVolumeInfo(UnManagedVolume.SupportedVolumeInformation.LOCAL_REPLICA_SOURCE_VOLUME.toString(), parentVol);

        StringSet isSyncActive = new StringSet();
        isSyncActive.add(TRUE);
        unManagedVolume.putVolumeInfo(UnManagedVolume.SupportedVolumeInformation.IS_SYNC_ACTIVE.toString(), isSyncActive);

        StringSet isReadOnly = new StringSet();
        Boolean readOnly = Boolean.FALSE;
        if (driverClone.getAccessStatus() != null) {
            readOnly = (driverClone.getAccessStatus().toString()).equals(StorageObject.AccessStatus.READ_ONLY.toString()) ?
                    Boolean.TRUE : Boolean.FALSE;
        }
        isReadOnly.add(readOnly.toString());
        unManagedVolume.putVolumeInfo(UnManagedVolume.SupportedVolumeInformation.IS_READ_ONLY.toString(), isReadOnly);

        StringSet techType = new StringSet();
        techType.add(BlockSnapshot.TechnologyType.NATIVE.toString());
        unManagedVolume.putVolumeInfo(UnManagedVolume.SupportedVolumeInformation.TECHNOLOGY_TYPE.toString(), techType);

        // set clone consistency group information in the unmanged clone object
        String isParentVolumeInCG =
                parentUnManagedVolume.getVolumeCharacterstics().get(UnManagedVolume.SupportedVolumeCharacterstics.IS_VOLUME_ADDED_TO_CONSISTENCYGROUP.toString());
        if (isParentVolumeInCG.equals(Boolean.TRUE.toString())) {
            // set clone consistency group name
            if (driverClone.getConsistencyGroup() != null && !driverClone.getConsistencyGroup().isEmpty()) {
                StringSet cloneCgName = new StringSet();
                cloneCgName.add(driverClone.getConsistencyGroup());
                unManagedVolume.putVolumeInfo(UnManagedVolume.SupportedVolumeInformation.FULL_COPY_CONSISTENCY_GROUP_NAME.toString(),
                        cloneCgName);
            }
        }

        unManagedVolume.setStoragePoolUri(storagePool.getId());
        StringSet pools = new StringSet();
        pools.add(storagePool.getId().toString());
        unManagedVolume.putVolumeInfo(UnManagedVolume.SupportedVolumeInformation.STORAGE_POOL.toString(), pools);

        StringSet driveTypes = storagePool.getSupportedDriveTypes();
        if (null != driveTypes) {
            unManagedVolume.putVolumeInfo(UnManagedVolume.SupportedVolumeInformation.DISK_TECHNOLOGY.toString(), driveTypes);
        }

        StringSet provCapacity = new StringSet();
        provCapacity.add(String.valueOf(driverClone.getProvisionedCapacity()));
        unManagedVolume.putVolumeInfo(UnManagedVolume.SupportedVolumeInformation.PROVISIONED_CAPACITY.toString(), provCapacity);

        StringSet allocatedCapacity = new StringSet();
        allocatedCapacity.add(String.valueOf(driverClone.getAllocatedCapacity()));
        unManagedVolume.putVolumeInfo(UnManagedVolume.SupportedVolumeInformation.ALLOCATED_CAPACITY.toString(), allocatedCapacity);

        StringSet replicaState = new StringSet();
        replicaState.add(driverClone.getReplicationState().toString());
        unManagedVolume.putVolumeInfo(UnManagedVolume.SupportedVolumeInformation.REPLICA_STATE.toString(), replicaState);

        StringSet accessStatus = new StringSet();
        accessStatus.add(driverClone.getAccessStatus().toString());
        unManagedVolume.putVolumeInfo(UnManagedVolume.SupportedVolumeInformation.ACCESS.toString(), accessStatus);

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
     * @param driver                    driver to call [IN]
     * @param driverVolume              volume for which we need to get export info [IN]
     * @param hostToVolumeExportInfoMap map, key --- host FQDN, value: list with export info for volumes exported to this host [IN/OUT]
     */
    private void getVolumeExportInfo(BlockStorageDriver driver, StorageVolume driverVolume,
                                     Map<String, List<HostExportInfo>> hostToVolumeExportInfoMap) {
        // get VolumeToHostExportInfo data for this volume from driver
        Map<String, HostExportInfo> volumeToHostExportInfo = driver.getVolumeExportInfoForHosts(driverVolume);
        log.info("Export info for volume {} is {}:", driverVolume, volumeToHostExportInfo);
        if (volumeToHostExportInfo == null || volumeToHostExportInfo.isEmpty()) {
            return;
        }

        //add HostExportInfo data to hostToVolumeExportInfoMap
        for (Map.Entry<String, HostExportInfo> entry : volumeToHostExportInfo.entrySet()) {
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
     * @param driver                    driver to call (input)
     * @param driverSnapshot            snapshot for which we need to get export info (input)
     * @param hostToVolumeExportInfoMap map, key --- host FQDN, value: list with export info for block objects exported
     *                                  to this host (input/output)
     */
    private void getSnapshotExportInfo(BlockStorageDriver driver, VolumeSnapshot driverSnapshot, Map<String, List<HostExportInfo>> hostToVolumeExportInfoMap) {
        // get HostExportInfo data for this snap from driver
        Map<String, HostExportInfo> volumeToHostExportInfo = driver.getSnapshotExportInfoForHosts(driverSnapshot);
        log.info("Export info for snapshot {} is {}:", driverSnapshot, volumeToHostExportInfo);
        if (volumeToHostExportInfo == null || volumeToHostExportInfo.isEmpty()) {
            return;
        }

        //add volumeToHostExportInfo data to hostToVolumeExportInfoMap
        for (Map.Entry<String, HostExportInfo> entry : volumeToHostExportInfo.entrySet()) {
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
     * @param driver                    driver to call (input)
     * @param driverClone               clone for which we need to get export info (input)
     * @param hostToVolumeExportInfoMap map, key --- host FQDN, value: list with export info for block objects exported
     *                                  to this host (input/output)
     */
    private void getCloneExportInfo(BlockStorageDriver driver, VolumeClone driverClone, Map<String, List<HostExportInfo>> hostToVolumeExportInfoMap) {
        // get HostExportInfo data for this clone from driver
        Map<String, HostExportInfo> volumeToHostExportInfo = driver.getCloneExportInfoForHosts(driverClone);
        log.info("Export info for clone {} is {}:", driverClone, volumeToHostExportInfo);
        if (volumeToHostExportInfo == null || volumeToHostExportInfo.isEmpty()) {
            return;
        }

        //add volumeToHostExportInfo data to hostToVolumeExportInfoMap
        for (Map.Entry<String, HostExportInfo> entry : volumeToHostExportInfo.entrySet()) {
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
     * We expect that all replicas of managed volume should be managed (known to the system) ---
     * enforced by  ingest framework, plus we do not support coexistence .
     * Warning log message is generated for each replica which is unmanaged.
     *
     * @param managedVolumeNativeIdToUriMap    [OUT], map: key --- managed volume native id, value: volume uri.
     * @param hostToManagedVolumeExportInfoMap [OUT], map: key --- host name, value: list of export infos for volumes exported
     *                                         to this host.
     * @param dbClient                         reference to db client [IN]
     * @param storageSystem                    storage system [IN]
     * @param systemVolume                     system volume  [IN]
     * @param driverVolume                     native volume [IN]
     * @param driver                           reference to driver [IN]
     * @throws Exception
     */
    private void getExportInfoForManagedVolumeReplicas(Map<String, URI> managedVolumeNativeIdToUriMap,
                                                       Map<String, List<HostExportInfo>> hostToManagedVolumeExportInfoMap,
                                                       DbClient dbClient,
                                                       com.emc.storageos.db.client.model.StorageSystem storageSystem,
                                                       Volume systemVolume, StorageVolume driverVolume,
                                                       BlockStorageDriver driver) throws Exception {

        // get export info for managed volume  snapshots
        log.info("Processing snapshots for managed volume {} ", systemVolume.getNativeGuid());
        List<VolumeSnapshot> driverSnapshots = driver.getVolumeSnapshots(driverVolume);
        if (driverSnapshots == null || driverSnapshots.isEmpty()) {
            log.info("There are no snapshots for volume {} ", systemVolume.getNativeGuid());
        } else {
            log.info("Snapshots for managed volume {}:" + Joiner.on("\t").join(driverSnapshots), systemVolume.getNativeGuid());
            for (VolumeSnapshot driverSnapshot : driverSnapshots) {
                String managedSnapNativeGuid = NativeGUIDGenerator.generateNativeGuidForVolumeOrBlockSnapShot(
                        storageSystem.getNativeGuid(), driverSnapshot.getNativeId());
                BlockSnapshot systemSnap = DiscoveryUtils.checkBlockSnapshotExistsInDB(dbClient, managedSnapNativeGuid);
                if (systemSnap == null) {
                    log.warn("Found unmanaged snapshot of managed volume --- this is unexpected! Skipping this snapshot {}.",
                            driverSnapshot.getNativeId());
                    continue;
                } else {
                    log.info("Processing managed {} snapshot of managed volume ().",
                            systemSnap.getNativeId(), systemVolume.getNativeGuid());
                }

                // get export data for the snapshot
                managedVolumeNativeIdToUriMap.put(driverSnapshot.getNativeId(), systemSnap.getId());
                getSnapshotExportInfo(driver, driverSnapshot, hostToManagedVolumeExportInfoMap);
            }
        }
        // get export info for managed volume  clones
        log.info("Processing clones for managed volume {} ", systemVolume.getNativeGuid());
        List<VolumeClone> driverClones = driver.getVolumeClones(driverVolume);
        if (driverClones == null || driverClones.isEmpty()) {
            log.info("There are no clones for volume {} ", systemVolume.getNativeGuid());
        } else {
            log.info("Clones for managed volume {}:" + Joiner.on("\t").join(driverClones), systemVolume.getNativeGuid());
            for (VolumeClone driverClone : driverClones) {
                String managedCloneNativeGuid = NativeGUIDGenerator.generateNativeGuidForVolumeOrBlockSnapShot(
                        storageSystem.getNativeGuid(), driverClone.getNativeId());
                Volume systemClone = DiscoveryUtils.checkStorageVolumeExistsInDB(dbClient, managedCloneNativeGuid);
                if (systemClone == null) {
                    log.warn("Found unmanaged clone of managed volume --- this is unexpected! Skipping this clone {}.",
                            driverClone.getNativeId());
                    continue;
                } else {
                    log.info("Processing managed {} clone of managed volume ().",
                            systemClone.getNativeId(), systemVolume.getNativeGuid());
                }

                // get export data for the clone
                managedVolumeNativeIdToUriMap.put(driverClone.getNativeId(), systemClone.getId());
                getCloneExportInfo(driver, driverClone, hostToManagedVolumeExportInfoMap);
            }
        }
    }

    /**
     * Processes export info for unmanaged and managed volumes found on storage array during volume discovery.
     * Analyses export info and builds/updates UnManagedExport masks for the exports.
     *
     * @param driver                          driver reference [IN]
     * @param storageSystem                   storage system [IN]
     * @param unManagedVolumeNativeIdToUriMap helper map of unmanaged volumes native ids to persistent Uris [IN]
     * @param managedVolumeNativeIdToUriMap   helper map of managed volumes native ids to persistent Uris [IN]
     * @param hostToVolumeExportInfoMap       map with export info for volumes found on storage array.
     *                                        key: host FQDN, value: list of volume export info for this host.[IN]
     * @param invalidExportHosts              Set of host FQDN for which we found invalid exports to the storage system. [IN/OUT]
     *                                        We will exclude exports for these hosts from processing.
     * @param dbClient                        reference to db client [IN]
     * @param partitionManager                partition manager [IN]
     */
    private void processExportData(BlockStorageDriver driver, com.emc.storageos.db.client.model.StorageSystem storageSystem,
                                   Map<String, URI> unManagedVolumeNativeIdToUriMap,
                                   Map<String, URI> managedVolumeNativeIdToUriMap,
                                   Map<String, List<HostExportInfo>> hostToVolumeExportInfoMap,
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
        Map<URI, HostExportInfo> exportInfosForExistingMasks = new HashMap<>();
        List<HostExportInfo> exportInfosForNewMasks = new ArrayList<>();

        // process export info for volumes to get exportInfosForExistingMasks and exportInfosForNewMasks
        determineUnManagedExportMasksForExportInfo(storageSystem,
                hostToVolumeExportInfoMap,
                invalidExportHosts,
                dbClient, exportInfosForExistingMasks, exportInfosForNewMasks);
        log.info("Export info for unmanaged masks to create for volumes: {}", exportInfosForNewMasks);
        log.info("Export info for unmanaged masks to update for volumes: {}", exportInfosForExistingMasks);

        // process unmanaged export masks for volumes
        if (!(exportInfosForNewMasks.isEmpty() && exportInfosForExistingMasks.isEmpty())) {
            processUnManagedMasksForVolumes(storageSystem, exportInfosForExistingMasks,
                    exportInfosForNewMasks, unManagedVolumeNativeIdToUriMap, managedVolumeNativeIdToUriMap,
                    unManagedExportMasksToUpdate, unManagedExportMasksToCreate, dbClient);
        }

        log.info("Unmanaged Masks to create for volumes: {}", unManagedExportMasksToCreate);
        log.info("Unmanaged Masks to update for volumes: {}", unManagedExportMasksToUpdate);
        // update db with results
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

    }


    /**
     * This method processes hostToVolumeExportInfoMap to find out which existing unmanaged masks has to be updated,
     * and which unmanaged masks have to be created new for this export info. It also identifies hosts with unsupported
     * export info data (exported host volumes are not seen through the same set of initiators and the same set of storage
     * ports --- which require more than one mask per host) and adds these hosts to invalidExportHosts set.
     *
     * @param storageSystem
     * @param hostToVolumeExportInfoMap [IN] map: key --- host FQDN, value --- list of volume export info instances
     * @param invalidExportHosts        [IN, OUT] set of invalid hosts, for which we skip export processing for a given array
     * @param dbClient                  reference to db client [IN]
     * @param masksToUpdateForVolumes   [OUT] map: key --- URI of existing unmanaged export mask, value --- export info to use
     *                                  to update the mask.
     * @param masksToCreateForVolumes   [OUT] list of export info instances for which we need to create new unmanaged masks.
     */
    private void determineUnManagedExportMasksForExportInfo(com.emc.storageos.db.client.model.StorageSystem storageSystem,
                                                            Map<String, List<HostExportInfo>> hostToVolumeExportInfoMap,
                                                            Set<String> invalidExportHosts,
                                                            DbClient dbClient, Map<URI, HostExportInfo> masksToUpdateForVolumes,
                                                            List<HostExportInfo> masksToCreateForVolumes) {

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
            String maskName = getUnManagedMaskName(hostName, storageSystem.getNativeGuid());
            HostExportInfo hostExportInfo = verifyHostExports(volumeToHostExportInfoList);
            if (hostExportInfo == null) {
                // invalid, continue to the next host
                invalidExportHosts.add(hostName);
                log.info("Found export info for host {} invalid. We will not process this host export data.", hostName);
                // check existing UnManaged export mask for host/array: the mask could be discovered for volumes on previous
                // pages (all unmanaged masks from previous discovery have been deactivated at the begging).
                UnManagedExportMask unManagedMask = getUnManagedExportMask(maskName, dbClient, storageSystem.getId());
                if (unManagedMask != null) {
                    log.info("Found existing unmanaged export mask for host {} and array {} --- {} . We will deactivate this mask.",
                            hostName, storageSystem.getNativeId(), unManagedMask);
                    removeInvalidMaskDataFromVolumes(unManagedMask, dbClient);
                    unManagedMask.setInactive(true);
                    dbClient.updateObject(unManagedMask);
                }
                continue;
            }
            log.info("The result export info for host {} and array {} : {} .", hostName,
                    storageSystem.getNativeId(), hostExportInfo);

            // check existing UnManaged export mask for host/array: the mask could be discovered for volumes on previous
            // pages (all unmanaged masks from previous discovery have been deactivated at the begging).
            UnManagedExportMask unManagedMask = getUnManagedExportMask(maskName, dbClient, storageSystem.getId());
            boolean isValid = true;
            if (unManagedMask != null) {
                log.info("Found existing unmanaged export mask for host {} and array {} --- {} .", hostName, storageSystem.getNativeId(),
                        unManagedMask);
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
                    // Invalid, we deactivate existing unmanaged mask --- make sure we do not discover invalid export
                    // masks. We also, remove this mask from "unmanagedExportMasks" set in its unmanaged storage volumes.
                    log.info("The result export info for host {} and storage array {} does not comply with existing mask.",
                            hostName, storageSystem.getNativeId());
                    removeInvalidMaskDataFromVolumes(unManagedMask, dbClient);
                    unManagedMask.setInactive(true);
                    dbClient.updateObject(unManagedMask);
                }
            } else {
                // Check if export mask for host/array is already managed. If host/array mask is managed, check that hostExportInfo has the same
                // storage ports and the same host initiators as in the managed mask. If we have a match for ports/initiators between the mask and hostExportInfo, we will process this
                // host export info and create a new UnManagedExportMask for the host.
                log.info("There is no existing unmanaged export mask for host {} and array {} .", hostName, storageSystem.getNativeId());
                List<String> initiatorPorts = new ArrayList<>();
                for (Initiator initiator : hostExportInfo.getInitiators()) {
                    initiatorPorts.add(initiator.getPort());
                }
                // We enforce single export mask for host/array for ingested masks, so if only one initiator port match, the mask is a match.
                Map<URI, ExportMask> uriToExportMask = ExportMaskUtils.getExportMasksWithInitiatorPorts(dbClient, initiatorPorts);
                // Look for export mask for the storage system under processing.
                for (ExportMask mask : uriToExportMask.values()) {
                    if (URIUtil.identical(mask.getStorageDevice(), storageSystem.getId())) {
                        // found managed export mask for storage system and host initiator
                        // the mask is already managed.
                        log.info("Found managed export mask for host {} and array {} --- {}." +
                                " We will process this host export data to see if we can add volumes to this mask.", hostName, storageSystem.getNativeId(), mask.getId());

                        // check that this managed mask has the same initiators and ports as in the hostExportInfo
                        StringSet storagePortsUris = mask.getStoragePorts();
                        StringSet initiatorsUris = mask.getInitiators();
                        List<com.emc.storageos.db.client.model.StoragePort> ports = dbClient.queryObjectField(com.emc.storageos.db.client.model.StoragePort.class,
                                "nativeId", StringSetUtil.stringSetToUriList(storagePortsUris));
                        List<com.emc.storageos.db.client.model.Initiator> initiators = dbClient.queryObjectField(com.emc.storageos.db.client.model.Initiator.class,
                                "iniport", StringSetUtil.stringSetToUriList(initiatorsUris));
                        Set<String> maskStoragePortsNativeIds = new HashSet<>();
                        Set<String> maskInitiatorPorts = new HashSet<>();

                        for (com.emc.storageos.db.client.model.StoragePort storagePort : ports) {
                            maskStoragePortsNativeIds.add(storagePort.getNativeId());
                        }

                        for (com.emc.storageos.db.client.model.Initiator initiator : initiators) {
                            maskInitiatorPorts.add(initiator.getInitiatorPort());
                        }
                        log.info("Managed ExportMask {} has the following storage ports {}", mask.getId(), maskStoragePortsNativeIds);
                        log.info("Managed ExportMask {} has the following initiator ports {}", mask.getId(), maskInitiatorPorts);

                        // check that hostExportInfo has the same ports and initiators as in the export mask
                        isValid = verifyHostExports(maskInitiatorPorts, maskStoragePortsNativeIds, hostExportInfo);
                        if (isValid ) {
                            // we will create unmanaged mask for this hostExportInfo
                            // we rely on ingestion to add new volumes to the managed mask.
                            log.info("Managed export mask {} has the same initiators and ports as in hostExportInfo. We will create unmanaged mask for new volumes.", mask.getId());
                            break;
                        } else {
                            log.info("Managed export mask {} has different initiators or ports as those in hostExportInfo.", mask.getId());
                        }
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
     * This method processes export info for volumes and returns unmanaged masks to create and existing unmanaged masks to update.
     *
     * @param storageSystem                   storage system [IN]
     * @param exportInfosForExistingMasks     [IN] map: key --- mask uri, value --- volume export info to add to the mask.
     * @param exportInfosForNewMasks          [IN] list of volume export info for which we need to create new masks.
     * @param unManagedVolumeNativeIdToUriMap [IN] map of unmanaged volume native id to unmanaged volume URI
     * @param unManagedExportMasksToUpdate    [OUT] list of unmanaged export masks to update
     * @param unManagedExportMasksToCreate    [OUT] list of unmanaged export masks to create
     * @param dbClient                        reference to db client [IN]
     */
    private void processUnManagedMasksForVolumes(com.emc.storageos.db.client.model.StorageSystem storageSystem,
                                                 Map<URI, HostExportInfo> exportInfosForExistingMasks,
                                                 List<HostExportInfo> exportInfosForNewMasks,
                                                 Map<String, URI> unManagedVolumeNativeIdToUriMap,
                                                 Map<String, URI> managedVolumeNativeIdToUriMap,
                                                 List<UnManagedExportMask> unManagedExportMasksToUpdate,
                                                 List<UnManagedExportMask> unManagedExportMasksToCreate,
                                                 DbClient dbClient) {

        log.info("Processing unmanaged volumes: {} .", unManagedVolumeNativeIdToUriMap);
        // update/create unManaged masks for unManaged volumes
        log.info("Processing masks to update: {} .", exportInfosForExistingMasks);
        for (Map.Entry<URI, HostExportInfo> entry : exportInfosForExistingMasks.entrySet()) {
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

        log.info("Processing masks to create: {} .", exportInfosForNewMasks);
        for (HostExportInfo hostExportInfo : exportInfosForNewMasks) {
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
     * Verifies that all members of the specified list have the same set of initiators ans the same set
     * of storage ports.
     *
     * @param hostExportInfoList list of HostExportInfo data
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
     *
     * @param initiatorNetworkIds [IN] Set of initiator network ids
     * @param storagePortNativeIds [IN] Set of storage ports network ids
     * @param hostExportInfo [IN] host export info to verify
     * @return true if verification passed, false otherwise
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
        if (!initiatorNetworkIds.equals(initiatorNetworkIdsSet) || !storagePortNativeIds.equals(targetNativeIdsSet)) {
            isValid = false;
        }

        return isValid;
    }

    /**
     * Get unManaged export mask for specified mask name and specified array.
     * Based on the enforced constraint for unmanaged export masks,
     * there will be only zero or one such mask.
     *
     * @param maskName  mask name
     * @param dbClient  reference to db client [IN]
     * @param systemURI storage system
     * @return unmanaged export mask or null, if there is no mask this name and storage array
     */
    private UnManagedExportMask getUnManagedExportMask(String maskName, DbClient dbClient, URI systemURI) {

        UnManagedExportMask uem = null;
        URIQueryResultList masks = new URIQueryResultList();
            dbClient.queryByConstraint(AlternateIdConstraint.Factory
                    .getUnManagedExportMaskNameConstraint(maskName), masks);

            Iterator<URI> maskIterator = masks.iterator();
            while (maskIterator.hasNext()) {
                UnManagedExportMask potentialUem = dbClient.queryObject(UnManagedExportMask.class, maskIterator.next());
                // Check whether the unManaged export mask belongs to the specified storage system.
                if (URIUtil.identical(potentialUem.getStorageSystemUri(), systemURI)) {
                    uem = potentialUem;
                    break;
                }
            }
        return uem;
    }

    /**
     * This method builds unManaged export mask from the provided hostExportInfo.
     *
     * @param hostExportInfo       source for unmanaged export mask data
     * @param unManagedVolumesUris set of unManaged volumes database ids for unManaged mask
     * @param managedVolumesUris   set of managed volumes database ids for unManaged mask
     * @param dbClient             reference to db client [IN]
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
        StringSet knownVolumesUris = new StringSet();

        List<com.emc.storageos.db.client.model.Initiator> knownFCInitiators = new ArrayList<>();
        List<com.emc.storageos.db.client.model.StoragePort> knownFCPorts = new ArrayList<>();

        String hostName = hostExportInfo.getHostName(); // FQDN of a host
        List<Initiator> initiators = hostExportInfo.getInitiators(); // List of host initiators
        List<StoragePort> targets = hostExportInfo.getTargets();    // List of storage ports

        exportMask.setMaskName(getUnManagedMaskName(hostName, storageSystem.getNativeGuid()));
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
            knownVolumesUris.addAll(managedVolumesUris);
            exportMask.setKnownVolumeUris(knownVolumesUris);
        }

        // populate zone map for FC initiators and FC storage ports from the mask.
        // we zone only known FC initiators to known FC ports defined in the mask.
        updateZoningMap(exportMask, knownFCInitiators, knownFCPorts);
        return exportMask;
    }

    /**
     * Updates zoning information in the unmanaged export mask for a given set of initiators and storage ports.
     *
     * @param mask unmanaged export [IN/OUT]
     * @param initiators initiators for zone map [IN]
     * @param storagePorts storage ports for zone map [IN]
     */
    private void updateZoningMap(UnManagedExportMask mask, List<com.emc.storageos.db.client.model.Initiator> initiators,
                                 List<com.emc.storageos.db.client.model.StoragePort> storagePorts) {
        ZoneInfoMap zoningMap = networkDeviceController.getInitiatorsZoneInfoMap(initiators, storagePorts);
        for (ZoneInfo zoneInfo : zoningMap.values()) {
            log.info("Found zone: {} for initiator {} and port {}", zoneInfo.getZoneName(),
                    zoneInfo.getInitiatorWwn(), zoneInfo.getPortWwn());
        }
        mask.setZoningMap(zoningMap);
    }

    /**
     * Update unmanaged volumes from export masks with export data.
     *
     * @param unManagedVolumeNativeIdToUriMap [IN] helper map
     * @param unManagedExportMasksToCreate [IN] list of masks with volumes
     * @param unManagedExportMasksToUpdate [IN] list of masks with volumes
     * @param dbClient reference to db client [IN]
     * @param partitionManager partition manager [IN]
     */
    private void updateUnManagedVolumesWithExportData(Map<String, URI> unManagedVolumeNativeIdToUriMap,
                                                      List<UnManagedExportMask> unManagedExportMasksToCreate,
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

    /**
     * Removes id of invalid unmanaged export mask from unmanaged volumes in this mask.
     * Removes initiators for this mask from volumes and does other cleanup in the volumes properties
     * related to export as required.
     *
     * @param unManagedMask unmanaged mask [IN]
     * @param dbClient reference to db client [IN]
     */
    void removeInvalidMaskDataFromVolumes(UnManagedExportMask unManagedMask, DbClient dbClient) {
            Set<UnManagedVolume> unManagedVolumesToUpdate = new HashSet<>();
            String unManagedMaskId = unManagedMask.getId().toString();
            StringSet knownInitiatorUris = unManagedMask.getKnownInitiatorUris();
            StringSet knownInitiatorNetworkIds = unManagedMask.getKnownInitiatorNetworkIds();
            StringSet volumeUris = unManagedMask.getUnmanagedVolumeUris();
            for (String volumeUriString : volumeUris) {
                URI volumeUri = URI.create(volumeUriString);
                UnManagedVolume volume = dbClient.queryObject(UnManagedVolume.class, volumeUri);
                if (volume != null) {
                    StringSet unManagedMasks = volume.getUnmanagedExportMasks();
                    unManagedMasks.remove(unManagedMaskId);
                    volume.getInitiatorUris().removeAll(knownInitiatorUris);
                    volume.getInitiatorNetworkIds().removeAll(knownInitiatorNetworkIds);
                    if (unManagedMasks.isEmpty()) {
                        volume.getVolumeCharacterstics().put(UnManagedVolume.SupportedVolumeCharacterstics.IS_VOLUME_EXPORTED.toString(), FALSE);
                        volume.getVolumeCharacterstics().put(UnManagedVolume.SupportedVolumeCharacterstics.IS_NONRP_EXPORTED.toString(), FALSE);
                    }
                    unManagedVolumesToUpdate.add(volume);
                }
            }
            dbClient.updateObject(unManagedVolumesToUpdate);
    }

    private String getUnManagedMaskName(String hostName, String storageSystemNativeGuid) {
        return hostName+"_"+storageSystemNativeGuid;

    }
}

