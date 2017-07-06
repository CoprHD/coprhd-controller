/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.hds.discovery;

import java.io.IOException;
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

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.AutoTieringPolicy.HitachiTieringPolicy;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.StringSetMap;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume.SupportedVolumeCharacterstics;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume.SupportedVolumeInformation;
import com.emc.storageos.hds.HDSConstants;
import com.emc.storageos.hds.api.HDSApiClient;
import com.emc.storageos.hds.api.HDSApiFactory;
import com.emc.storageos.hds.api.HDSApiVolumeManager;
import com.emc.storageos.hds.model.LDEV;
import com.emc.storageos.hds.model.LogicalUnit;
import com.emc.storageos.hds.model.ObjectLabel;
import com.emc.storageos.plugins.AccessProfile;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.plugins.common.PartitionManager;
import com.emc.storageos.volumecontroller.impl.NativeGUIDGenerator;
import com.emc.storageos.volumecontroller.impl.hds.prov.utils.HDSUtils;
import com.emc.storageos.volumecontroller.impl.utils.DiscoveryUtils;
import com.google.common.base.Joiner;

/**
 * 
 * Discovers all volumes from Hitachi array.
 * 
 */
public class HDSVolumeDiscoverer {

    private static final Logger log = LoggerFactory.getLogger(HDSVolumeDiscoverer.class);

    private HDSApiFactory hdsApiFactory;

    /**
     * @param hdsApiFactory the hdsApiFactory to set
     */
    public void setHdsApiFactory(HDSApiFactory hdsApiFactory) {
        this.hdsApiFactory = hdsApiFactory;
    }

    public void discoverUnManagedVolumes(AccessProfile accessProfile, DbClient dbClient,
            CoordinatorClient coordinator, PartitionManager partitionManager) throws Exception {

        log.info("Started discovery of UnManagedVolumes for system {}", accessProfile.getSystemId());
        HDSApiClient hdsApiClient = hdsApiFactory.getClient(
                HDSUtils.getHDSServerManagementServerInfo(accessProfile),
                accessProfile.getUserName(), accessProfile.getPassword());
        List<UnManagedVolume> newUnManagedVolumeList = new ArrayList<UnManagedVolume>();
        List<UnManagedVolume> updateUnManagedVolumeList = new ArrayList<UnManagedVolume>();
        Set<URI> allDiscoveredUnManagedVolumes = new HashSet<URI>();
        HDSApiVolumeManager volumeManager = hdsApiClient.getHDSApiVolumeManager();
        StorageSystem storageSystem = dbClient.queryObject(StorageSystem.class,
                accessProfile.getSystemId());
        String systemObjectId = HDSUtils.getSystemObjectID(storageSystem);
        List<LogicalUnit> luList = volumeManager.getAllLogicalUnits(systemObjectId);
        if (null != luList && !luList.isEmpty()) {
            log.info("Processing {} volumes received from HiCommand server.", luList.size());
            URIQueryResultList storagePoolURIs = new URIQueryResultList();
            dbClient.queryByConstraint(ContainmentConstraint.Factory
                    .getStorageDeviceStoragePoolConstraint(storageSystem.getId()),
                    storagePoolURIs);
            HashMap<String, StoragePool> pools = new HashMap<String, StoragePool>();
            Iterator<URI> poolsItr = storagePoolURIs.iterator();
            while (poolsItr.hasNext()) {
                URI storagePoolURI = poolsItr.next();
                StoragePool storagePool = dbClient.queryObject(StoragePool.class, storagePoolURI);
                pools.put(storagePool.getNativeGuid(), storagePool);
            }
            for (LogicalUnit logicalUnit : luList) {

                if (!DiscoveryUtils.isUnmanagedVolumeFilterMatching(logicalUnit.getObjectID())) {
                    // skipping this volume because the filter doesn't match
                    continue;
                }

                
                log.info("Processing LogicalUnit: {}", logicalUnit.getObjectID());
                UnManagedVolume unManagedVolume = null;
                String managedVolumeNativeGuid = NativeGUIDGenerator.generateNativeGuidForVolumeOrBlockSnapShot(
                        storageSystem.getNativeGuid(), String.valueOf(logicalUnit.getDevNum()));
                if (null != DiscoveryUtils.checkStorageVolumeExistsInDB(dbClient, managedVolumeNativeGuid)) {
                    log.info("Skipping volume {} as it is already managed by ViPR", managedVolumeNativeGuid);
                }

                String unManagedVolumeNativeGuid = NativeGUIDGenerator.generateNativeGuidForPreExistingVolume(
                        storageSystem.getNativeGuid(), String.valueOf(logicalUnit.getDevNum()));

                unManagedVolume = DiscoveryUtils.checkUnManagedVolumeExistsInDB(dbClient,
                        unManagedVolumeNativeGuid);
                boolean unManagedVolumeExists = (null != unManagedVolume) ? true : false;
                StoragePool storagePool = getStoragePoolOfUnManagedVolume(logicalUnit, storageSystem, pools, dbClient);
                if (null != storagePool) {
                    if (!unManagedVolumeExists) {
                        unManagedVolume = createUnManagedVolume(unManagedVolumeNativeGuid, logicalUnit, storageSystem, storagePool,
                                dbClient);
                        newUnManagedVolumeList.add(unManagedVolume);
                    } else {
                        updateUnManagedVolumeInfo(logicalUnit, storageSystem, storagePool, unManagedVolume, dbClient);
                        updateUnManagedVolumeList.add(unManagedVolume);
                    }
                    allDiscoveredUnManagedVolumes.add(unManagedVolume.getId());
                } else {
                    log.error("Skipping unmanaged volume discovery as the volume {} storage pool doesn't exist in ViPR",
                            logicalUnit.getObjectID());
                }

                performUnManagedVolumesBookKeepting(newUnManagedVolumeList,
                        updateUnManagedVolumeList, partitionManager, dbClient,
                        Constants.DEFAULT_PARTITION_SIZE);

            }
            performUnManagedVolumesBookKeepting(newUnManagedVolumeList,
                    updateUnManagedVolumeList, partitionManager, dbClient, 0);

            // Process those active unmanaged volume objects available in database but not in newly discovered items, to mark them inactive.
            DiscoveryUtils.markInActiveUnManagedVolumes(storageSystem, allDiscoveredUnManagedVolumes, dbClient, partitionManager);
        } else {
            log.info("No volumes retured by HiCommand Server for system {}", storageSystem.getId());
        }
    }

    private void performUnManagedVolumesBookKeepting(
            List<UnManagedVolume> newUnManagedVolumeList,
            List<UnManagedVolume> updateUnManagedVolumeList,
            PartitionManager partitionManager, DbClient dbClient, int limit) {
        if (!newUnManagedVolumeList.isEmpty() && newUnManagedVolumeList.size() > limit) {
            partitionManager.insertInBatches(newUnManagedVolumeList,
                    Constants.DEFAULT_PARTITION_SIZE, dbClient,
                    HDSConstants.UNMANAGED_VOLUME);
            newUnManagedVolumeList.clear();
        }
        if (!updateUnManagedVolumeList.isEmpty()
                && updateUnManagedVolumeList.size() > limit) {
            partitionManager.updateAndReIndexInBatches(updateUnManagedVolumeList,
                    Constants.DEFAULT_PARTITION_SIZE, dbClient,
                    HDSConstants.UNMANAGED_VOLUME);
            updateUnManagedVolumeList.clear();
        }
    }

    /**
     * Updates the UnManagedVolumeInfo.
     * 
     * @param logicalUnit
     * @param system
     * @param pool
     * @param unManagedVolume
     * @param dbClient
     */
    private void updateUnManagedVolumeInfo(LogicalUnit logicalUnit, StorageSystem system,
            StoragePool pool, UnManagedVolume unManagedVolume, DbClient dbClient) {
        StringSetMap unManagedVolumeInformation = new StringSetMap();
        Map<String, String> unManagedVolumeCharacteristics = new HashMap<String, String>();
        StringSet systemTypes = new StringSet();
        systemTypes.add(system.getSystemType());

        StringSet provCapacity = new StringSet();
        provCapacity.add(String.valueOf(Long.parseLong(logicalUnit.getCapacityInKB()) * 1024));
        unManagedVolumeInformation.put(SupportedVolumeInformation.PROVISIONED_CAPACITY.toString(),
                provCapacity);

        StringSet allocatedCapacity = new StringSet();
        allocatedCapacity.add(String.valueOf(Long.parseLong(logicalUnit.getCapacityInKB()) * 1024));
        unManagedVolumeInformation.put(SupportedVolumeInformation.ALLOCATED_CAPACITY.toString(),
                allocatedCapacity);

        unManagedVolumeInformation.put(SupportedVolumeInformation.SYSTEM_TYPE.toString(),
                systemTypes);

        StringSet deviceLabel = new StringSet();
        String luLabel = getLabelFromLogicalUnit(logicalUnit);
        if (null != luLabel) {
            deviceLabel.add(luLabel);
        }
        unManagedVolumeInformation.put(SupportedVolumeInformation.DEVICE_LABEL.toString(),
                deviceLabel);

        unManagedVolumeCharacteristics.put(
                SupportedVolumeCharacterstics.IS_INGESTABLE.toString(), Boolean.TRUE.toString());

        if (logicalUnit.getPath() == 1) {
            unManagedVolumeCharacteristics.put(
                    SupportedVolumeCharacterstics.IS_VOLUME_EXPORTED.toString(),
                    Boolean.TRUE.toString());
        } else {
            unManagedVolumeCharacteristics.put(
                    SupportedVolumeCharacterstics.IS_VOLUME_EXPORTED.toString(),
                    Boolean.FALSE.toString());
        }

        if (logicalUnit.getDpType().equals(HDSConstants.DPTYPE_THIN)) {
            unManagedVolumeCharacteristics.put(
                    SupportedVolumeCharacterstics.IS_THINLY_PROVISIONED.toString(),
                    Boolean.TRUE.toString());
        } else if (logicalUnit.getDpType().equals(HDSConstants.DPTYPE_THICK)) {
            unManagedVolumeCharacteristics.put(
                    SupportedVolumeCharacterstics.IS_THINLY_PROVISIONED.toString(),
                    Boolean.FALSE.toString());
        } else {
            log.info("Provisioning type not found for volume: {}", logicalUnit.getObjectID());
        }

        String raidType = logicalUnit.getRaidType();
        if (null != raidType) {
            StringSet raidLevels = new StringSet();
            raidLevels.add(raidType);
            unManagedVolumeInformation.put(
                    SupportedVolumeInformation.RAID_LEVEL.toString(), raidLevels);
        }

        StringSet pools = new StringSet();
        pools.add(pool.getId().toString());
        unManagedVolumeInformation.put(
                SupportedVolumeInformation.STORAGE_POOL.toString(), pools);
        unManagedVolume.setWwn(HDSUtils.generateHitachiWWN(logicalUnit.getObjectID(), String.valueOf(logicalUnit.getDevNum())));

        StringSet nativeId = new StringSet();
        nativeId.add(String.valueOf(logicalUnit.getDevNum()));
        unManagedVolumeInformation.put(SupportedVolumeInformation.NATIVE_ID.toString(),
                nativeId);
        String luTieringPolicy = fetchLogicalUnitTieringPolicy(system, logicalUnit, dbClient);
        if (null != luTieringPolicy) {
            StringSet volumeTieringPolicy = new StringSet();
            volumeTieringPolicy.add(luTieringPolicy);
            unManagedVolumeInformation.put(SupportedVolumeInformation.AUTO_TIERING_POLICIES.toString(),
                    volumeTieringPolicy);
            unManagedVolumeCharacteristics.put(
                    SupportedVolumeCharacterstics.IS_AUTO_TIERING_ENABLED.toString(),
                    Boolean.TRUE.toString());
        } else {
            unManagedVolumeCharacteristics.put(
                    SupportedVolumeCharacterstics.IS_AUTO_TIERING_ENABLED.toString(),
                    Boolean.FALSE.toString());
        }

        StringSet driveTypes = pool.getSupportedDriveTypes();
        if (null != driveTypes) {
            unManagedVolumeInformation.put(
                    SupportedVolumeInformation.DISK_TECHNOLOGY.toString(), driveTypes);
        }
        StringSet matchedVPools = DiscoveryUtils.getMatchedVirtualPoolsForPool(dbClient,
                pool.getId(), unManagedVolumeCharacteristics
                        .get(SupportedVolumeCharacterstics.IS_THINLY_PROVISIONED
                                .name()).toString(), unManagedVolume);

        log.debug("Matched Pools : {}", Joiner.on("\t").join(matchedVPools));
        if (null == matchedVPools || matchedVPools.isEmpty()) {
            // clear all matched vpools
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
    }

    /**
     * return the label of the LDEV if user is set else return null.
     * 
     * @param logicalUnit
     * @return
     */
    private String getLabelFromLogicalUnit(LogicalUnit logicalUnit) {
        String ldevLabel = null;
        if (null != logicalUnit.getLdevList()) {
            Iterator<LDEV> ldevItr = logicalUnit.getLdevList().iterator();
            if (ldevItr.hasNext()) {
                LDEV ldev = ldevItr.next();
                ObjectLabel label = ldev.getLabel();
                if (null != label) {
                    ldevLabel = label.getLabel();
                }
            }
        }
        return ldevLabel;
    }

    /**
     * Iterate through the logicalUnit LDEV and find the tierLevel of the volume.
     * 
     * @param logicalUnit
     * @return
     */
    private String fetchLogicalUnitTieringPolicy(StorageSystem system, LogicalUnit logicalUnit, DbClient dbClient) {
        String tieringPolicyName = null;
        if (logicalUnit.getDpType().equals(HDSConstants.DPTYPE_THIN)) {
            if (null != logicalUnit.getLdevList()) {
                Iterator<LDEV> ldevItr = logicalUnit.getLdevList().iterator();
                if (ldevItr.hasNext()) {
                    LDEV ldev = ldevItr.next();
                    URIQueryResultList tieringPolicyList = new URIQueryResultList();
                    if (-1 != ldev.getTierLevel()) {
                        tieringPolicyName = HitachiTieringPolicy.getType(String.valueOf(ldev.getTierLevel()))
                                .replaceAll(HDSConstants.UNDERSCORE_OPERATOR, HDSConstants.SLASH_OPERATOR);

                    }
                }
            }
        }
        return tieringPolicyName;
    }

    /**
     * Creates a new UnManagedVolume with the given arguments.
     * 
     * @param unManagedVolumeNativeGuid
     * @param logicalUnit
     * @param system
     * @param pool
     * @param dbClient
     * @return
     */
    private UnManagedVolume createUnManagedVolume(String unManagedVolumeNativeGuid,
            LogicalUnit logicalUnit, StorageSystem system, StoragePool pool,
            DbClient dbClient) {

        UnManagedVolume newUnManagedVolume = new UnManagedVolume();
        newUnManagedVolume.setId(URIUtil.createId(UnManagedVolume.class));
        newUnManagedVolume.setNativeGuid(unManagedVolumeNativeGuid);
        newUnManagedVolume.setStorageSystemUri(system.getId());
        newUnManagedVolume.setStoragePoolUri(pool.getId());
        updateUnManagedVolumeInfo(logicalUnit, system, pool, newUnManagedVolume, dbClient);
        return newUnManagedVolume;
    }

    /**
     * Return the pool of the UnManaged volume.
     * 
     * @param logicalUnit
     * @param system
     * @param dbClient
     * @return
     * @throws IOException
     */
    private StoragePool getStoragePoolOfUnManagedVolume(LogicalUnit logicalUnit,
            StorageSystem system, Map<String, StoragePool> pools, DbClient dbClient) throws IOException {
        String poolNativeId = null;
        if (null != logicalUnit.getArrayGroup()
                && logicalUnit.getDpType().equals(HDSConstants.DPTYPE_THICK)) {
            poolNativeId = getArrayGroupNativeId(system, logicalUnit);
        } else if (null != logicalUnit.getDpPoolID()
                && logicalUnit.getDpType().equals(HDSConstants.DPTYPE_THIN)) {
            poolNativeId = getJournalPoolNativeId(system, logicalUnit);
        }
        String poolNativeGuid = NativeGUIDGenerator.generateNativeGuid(system, poolNativeId, NativeGUIDGenerator.POOL);
        if (pools.containsKey(poolNativeGuid)) {
            return pools.get(poolNativeGuid);
        }
        return null;
    }

    /**
     * 
     * @param system
     * @param logicalUnit
     * @return
     */
    private String getJournalPoolNativeId(StorageSystem system, LogicalUnit logicalUnit) {
        StringBuffer journalPoolNativeId = new StringBuffer(HDSConstants.JOURNALPOOL);
        journalPoolNativeId.append(HDSConstants.DOT_OPERATOR)
                .append(HDSUtils.getSystemModelSerialNum(system))
                .append(HDSConstants.DOT_OPERATOR).append(HDSConstants.DP_POOL_FUNCTION)
                .append(HDSConstants.DOT_OPERATOR).append(logicalUnit.getDpPoolID());
        return journalPoolNativeId.toString();
    }

    /**
     * 
     * @param system
     * @param logicalUnit
     * @return
     */
    private String getArrayGroupNativeId(StorageSystem system, LogicalUnit logicalUnit) {
        StringBuffer arrayGroupId = new StringBuffer(HDSConstants.ARRAYGROUP);
        arrayGroupId.append(HDSConstants.DOT_OPERATOR)
                .append(HDSUtils.getSystemModelSerialNum(system))
                .append(HDSConstants.DOT_OPERATOR).append(logicalUnit.getChassis())
                .append(HDSConstants.DOT_OPERATOR).append(logicalUnit.getArrayGroup());
        return arrayGroupId.toString();
    }
}
