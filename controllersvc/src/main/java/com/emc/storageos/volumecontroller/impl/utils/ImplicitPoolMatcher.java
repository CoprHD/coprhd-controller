/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.utils;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.MessageFormatter;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.VpoolRemoteCopyProtectionSettings;
import com.emc.storageos.db.client.model.VpoolProtectionVarraySettings;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.volumecontroller.AttributeMatcher;
import com.emc.storageos.volumecontroller.impl.ControllerServiceImpl;
import com.google.common.base.Joiner;

/**
 * Utility class to match all the pools matching with VirtualPool registered attributes. 1. Iterate
 * through all VirtualPool. 2. Run attribute matchers for "vpoolMatchers" groupName for a given list
 * of pools. 3. AttributeMatcher frame work runs the matchers for the defined attributes in
 * VirtualPool. 4. Finally a list of matched pools will be returned. 4. Now, update if there are any
 * invalid pools to update in VirtualPool. 5. Persist the VirtualPool with new set of matched pools.
 */
public class ImplicitPoolMatcher {
    private static final Logger _logger = LoggerFactory
            .getLogger(ImplicitPoolMatcher.class);
    private static volatile AttributeMatcherFramework _matcherFramework = null;

    /**
     * Match block system pools with all VirtualPool. This method will be invoked only for block
     * storage systems. This method is written as per the plugin design.
     * 
     * @param systemPools
     * @param dbClient
     * @param systemId
     * @throws Exception
     */
    @SuppressWarnings("unchecked")
    public static void matchBlockSystemPools(Object systemPools,
            Object dbClient, Object coordinator, Object systemId) throws Exception {
        List<StoragePool> modifiedPools = new ArrayList<StoragePool>(((Map<URI, StoragePool>) systemPools).values());
        matchModifiedStoragePoolsWithAllVpool(modifiedPools,
                (DbClient) dbClient, (CoordinatorClient) coordinator,
                (URI) systemId);
    }

    /**
     * Match all the Storage Pools in a StorageSystem to All Virtual Pools.
     * 
     * @param storageSystem
     */
    public static void matchStorageSystemPoolsToVPools(URI storageSystemURI,
            DbClient _dbClient, CoordinatorClient _coordinator) {
        URIQueryResultList storagePoolURIs = new URIQueryResultList();
        _dbClient.queryByConstraint(ContainmentConstraint.Factory
                .getStorageDeviceStoragePoolConstraint(storageSystemURI),
                storagePoolURIs);
        List<StoragePool> storagePools = new ArrayList<StoragePool>();
        while (storagePoolURIs.iterator().hasNext()) {
            URI storagePoolURI = storagePoolURIs.iterator().next();
            StoragePool storagePool = _dbClient.queryObject(StoragePool.class, storagePoolURI);
            if (storagePool != null && !storagePool.getInactive()) {
                storagePools.add(storagePool);
            }
        }
        ImplicitPoolMatcher.matchModifiedStoragePoolsWithAllVirtualPool(
                storagePools, _dbClient, _coordinator);
    }

    /**
     * Return the Remote protection setting objects associated with this virtual pool.
     * 
     * @param vpool
     *            the virtual pool
     * @return a mapping of virtual arrays to the remote protection settings for that copy
     */
    public static Map<String, List<VpoolRemoteCopyProtectionSettings>> getRemoteProtectionSettings(VirtualPool vpool, DbClient dbClient) {
        Map<String, List<VpoolRemoteCopyProtectionSettings>> settings = null;
        if (vpool.getProtectionRemoteCopySettings() != null) {
            settings = new HashMap<String, List<VpoolRemoteCopyProtectionSettings>>();
            for (String protectionVarray : vpool.getProtectionRemoteCopySettings().keySet()) {
                VpoolRemoteCopyProtectionSettings remoteSettings = dbClient.queryObject(VpoolRemoteCopyProtectionSettings.class,
                        URI.create(vpool.getProtectionRemoteCopySettings().get(protectionVarray)));
                if (remoteSettings.getVirtualPool() == null) {
                    _logger.info("NULL True");
                }
                String vPoolUri = null;
                if (remoteSettings.getVirtualPool() == null || remoteSettings.getVirtualPool().toString().isEmpty()) {
                    vPoolUri = vpool.getId().toString();
                } else {
                    vPoolUri = remoteSettings.getVirtualPool().toString();
                }
                if (null == settings.get(vPoolUri)) {
                    settings.put(vPoolUri, new ArrayList<VpoolRemoteCopyProtectionSettings>());
                }
                settings.get(vPoolUri).add(remoteSettings);
            }
        }
        return settings;
    }

    /**
     * Get all VirtualPool in DB and invokes matchStoragePoolsWithAllVpool which actually match all
     * given pools with all Vpooles in database. This method is invoked after completion of
     * discovery of a system.
     * 
     * @param systemModifiedPools
     *            : modified pools of the discovered system.
     * @param dbClient
     *            : dbClient instance.
     * @param systemId
     *            : system id.
     * @throws Exception
     */
    public static void matchModifiedStoragePoolsWithAllVpool(List<StoragePool> systemModifiedPools, DbClient dbClient,
            CoordinatorClient coordinator, URI systemId) throws DeviceControllerException {
        if (null == systemModifiedPools || systemModifiedPools.isEmpty()) {
            _logger.info("No StoragePools found to run implicit pool matching for systemId: {}", systemId);
            return;
        }
        _logger.info(
                "matchModifiedStoragePoolsWithAllVirtualPool for system {}", systemId);
        ControllerServiceImpl.Lock lock = ControllerServiceImpl.Lock
                .getLock(Constants.POOL_MATCHER);
        /*
         * Serialization of VirtualPool should be done by acquiring a lock else the data will be
         * updated by multiple thread and cause inaccurate data. Hence each thread will wait till it
         * gets the lock. Once persisted, lock will be released and next thread will persist the
         * data. We have set 15min as the lock acquire time. If a thread doesn't get lock within
         * 15min interval, it should come out & throw exception.
         */
        try {
            lock.acquire();
            _logger.info("Acquired lock to update vpool-pool relation for system {}", systemId);
            matchModifiedStoragePoolsWithAllVirtualPool(systemModifiedPools, dbClient, coordinator);
        } catch (Exception e) {
            _logger.error("Failed to match pools", e);
            throw new DeviceControllerException(e, "Failed to match pools. Caused by : {0}",
                    new Object[] { e.getMessage() });
        } finally {
            try {
                lock.release();
            } catch (Exception e) {
                _logger.error("Failed to release  Lock while matching pools {} -->{}", lock.toString(), e.getMessage());
            }
        }
    }

    /**
     * Persist all the VirtualPool which are updated with matched pools.
     * 
     * @param updatedVpoolList
     *            : updated VirtualPool list.
     * @param dbClient
     *            : dbClient.
     */
    private static void persistUpdatedVpoolList(List<VirtualPool> updatedVpoolList, DbClient dbClient) {
        if (!updatedVpoolList.isEmpty()) {
            dbClient.updateAndReindexObject(updatedVpoolList);
        }
    }

    /**
     * Get all VirtualPool from DB and invokes matchVpoolWithStoragePools which actually match all
     * pools and all VirtualPool in database. This method is invoked if there is any pool to varray
     * association changes. StoragePoolAssociationHelper.changePoolAssociation() => This is a common
     * method which will be invoked if there any association changes in varray.
     * 
     * @param updatedPoolList
     *            : List of pools updated.
     * @param dbClient
     *            : dbClient instance.
     */
    public static void matchModifiedStoragePoolsWithAllVirtualPool(List<StoragePool> updatedPoolList,
            DbClient dbClient, CoordinatorClient coordinator) {
        List<URI> vpoolURIs = dbClient.queryByType(VirtualPool.class, true);
        Iterator<VirtualPool> vpoolListItr = dbClient.queryIterativeObjects(VirtualPool.class, vpoolURIs);
        List<VirtualPool> vPoolsToUpdate = new ArrayList<VirtualPool>();
        while (vpoolListItr.hasNext()) {
            VirtualPool vpool = vpoolListItr.next();
            matchvPoolWithStoragePools(vpool, updatedPoolList, dbClient, coordinator, null);
            vPoolsToUpdate.add(vpool);
        }
        if (!vPoolsToUpdate.isEmpty()) {
            persistUpdatedVpoolList(vPoolsToUpdate, dbClient);
        }
    }

    /**
     * Matches given set of virtual pools with list of storage pools.
     * @param updatedPoolList list of storage pools
     * @param vpoolURIs list of virtual pools
     * @param dbClient
     * @param coordinator
     * @param matcherGroupName group name of attribute matchers
     */
    public static void matchModifiedStoragePoolsWithVirtualPools(List<StoragePool> updatedPoolList, List<URI> vpoolURIs,
                                                                 DbClient dbClient, CoordinatorClient coordinator, String matcherGroupName) {
        Iterator<VirtualPool> vpoolListItr = dbClient.queryIterativeObjects(VirtualPool.class, vpoolURIs);
        List<VirtualPool> vPoolsToUpdate = new ArrayList<VirtualPool>();
        while (vpoolListItr.hasNext()) {
            VirtualPool vpool = vpoolListItr.next();
            matchvPoolWithStoragePools(vpool, updatedPoolList, dbClient, coordinator, matcherGroupName);
            vPoolsToUpdate.add(vpool);
        }
        if (!vPoolsToUpdate.isEmpty()) {
            persistUpdatedVpoolList(vPoolsToUpdate, dbClient);
        }
    }

    /**
     * Matches given VirtualPool with list of pools provided and update matched/invalid pools in
     * VirtualPool.
     * 
     * @param vpool
     *            : vpool to match.
     * @param pools
     *            : pools to match.
     * @param dbClient
     * @param matcherGroupName group name of attribute matchers to run
     */
    public static void matchvPoolWithStoragePools(VirtualPool vpool, List<StoragePool> pools, DbClient dbClient,
            CoordinatorClient coordinator, String matcherGroupName) {
        List<StoragePool> filterPools = getMatchedPoolWithStoragePools(vpool, pools,
                VirtualPool.getProtectionSettings(vpool, dbClient),
                VirtualPool.getRemoteProtectionSettings(vpool, dbClient),
                VirtualPool.getFileRemoteProtectionSettings(vpool, dbClient), dbClient, coordinator, matcherGroupName);
        updateInvalidAndMatchedPoolsForVpool(vpool, filterPools, pools, dbClient);
    }

    /**
     * Matches given VirtualPool with list of pools provided and update matched/invalid pools in
     * VirtualPool.
     * 
     */
    public static List<StoragePool> getMatchedPoolWithStoragePools(VirtualPool vpool,
            List<StoragePool> pools,
            Map<URI, VpoolProtectionVarraySettings> protectionVarraySettings,
            Map<URI, VpoolRemoteCopyProtectionSettings> remoteSettingsMap,
            Map<URI, VpoolRemoteCopyProtectionSettings> fileRemoteSettingsMap,
            DbClient dbClient,
            CoordinatorClient coordinator, String matcherGroupName) {
        // By default use all vpool matchers.
        if (matcherGroupName == null) {
            matcherGroupName = AttributeMatcher.VPOOL_MATCHERS;
        }
        _logger.info("Started matching pools with {} vpool, matcher group {}", vpool.getId(), matcherGroupName);
        AttributeMapBuilder vpoolMapBuilder = new VirtualPoolAttributeMapBuilder(vpool, protectionVarraySettings,
                VirtualPool.groupRemoteCopyModesByVPool(vpool.getId(), remoteSettingsMap),
                VirtualPool.groupRemoteCopyModesByVPool(vpool.getId(), fileRemoteSettingsMap));
        Map<String, Object> attributeMap = vpoolMapBuilder.buildMap();
        _logger.info("Implict Pool matching populated attribute map: {}", attributeMap);
        List<StoragePool> filterPools = _matcherFramework.matchAttributes(pools, attributeMap, dbClient, coordinator,
                matcherGroupName);
        _logger.info("Ended matching pools with vpool attributes. Found {} matching pools", filterPools.size());
        return filterPools;
    }

    /**
     * 1. Loop thru each processed pool. 2. Get the previously matched VirtualPool for this pool by
     * doing constraint query. 3. If the pool is not in any of the previously matched VirtualPool,
     * then add all matched pools to VirtualPool. 4. Now, check processed pool is in current matched
     * pools, then check whether it is in invalidMatched pools or not. 5. If it is in invalid
     * Matched pools, then remove it. means an invalid pool become active now. 6. Also verify the
     * VirtualPool is a previously matched VirtualPool. If it is not, then add it to
     * newMatchedPools. 6. If processed pools is not in matched pools, then check if the pools is
     * not in previously matched pool or not. 7. If it is in the previously matched pool then add it
     * to invalidMatched pools.
     * 
     * @param vpool
     *            : vpool to update.
     * @param matchedPools
     *            : List of pools matched after running attribute matchers.
     * @param storagePools
     *            : List of processed pools.
     * @param dbClient
     *            : dbClient reference.
     */
    private static void updateInvalidAndMatchedPoolsForVpool(VirtualPool vpool, List<StoragePool> matchedPools,
            List<StoragePool> storagePools, DbClient dbClient) {
        URI currentVpoolId = vpool.getId();
        StringSet newMatchedPools = new StringSet();
        StringSet newInvalidPools = new StringSet();
        if (null != vpool.getMatchedStoragePools()) {
            newMatchedPools.addAll(vpool.getMatchedStoragePools());
        }
        if (null != vpool.getInvalidMatchedPools()) {
            newInvalidPools.addAll(vpool.getInvalidMatchedPools());
        }
        for (StoragePool pool : storagePools) {
            String poolIdStr = pool.getId().toString();
            URIQueryResultList queryResult = new URIQueryResultList();
            dbClient.queryByConstraint(
                    ContainmentConstraint.Factory.getMatchedPoolVirtualPoolConstraint(pool.getId()), queryResult);
            Iterator<URI> oldMatchedVpoolItr = queryResult.iterator();
            if (!oldMatchedVpoolItr.hasNext()) {
                if (matchedPools.contains(pool)) {
                    _logger.debug("New pool found {}", poolIdStr);
                    newMatchedPools.add(poolIdStr);
                    // current vpool is already active but check whether the invalid pool became
                    // active.
                    removeInvalidPools(vpool, newInvalidPools, poolIdStr);
                }
            }
            // If the processed pool is in matched pools.
            if (matchedPools.contains(pool)) {
                // Get the previously matched VirtualPool for this pool.
                while (oldMatchedVpoolItr.hasNext()) {
                    URI oldMatchedVpoolURI = oldMatchedVpoolItr.next();
                    // current vpool is already active but check whether the invalid pool became
                    // active.
                    removeInvalidPools(vpool, newInvalidPools, poolIdStr);
                    // old vpool is not matching with the current VirtualPool.
                    // then add the pool to matched list.
                    if (!currentVpoolId.equals(oldMatchedVpoolURI)) {
                        _logger.debug("Adding pool {}", poolIdStr);
                        newMatchedPools.add(poolIdStr);
                    }
                }
            } else { // processed pool is not in matched pools
                // Since it was matched pool and now became invalid
                if (newMatchedPools.contains(poolIdStr)) {
                    _logger.debug("pool {} became invalid now.", poolIdStr);
                    newMatchedPools.remove(poolIdStr);
                    newInvalidPools.add(poolIdStr);
                }
            }
        }
        _logger.info(MessageFormatter.arrayFormat(
                "Updating VPool {} with Matched Pools:{}, Invalid pools:{}", new Object[] { vpool.getId(),
                        newMatchedPools.size(), newInvalidPools.size() }).getMessage());
        vpool.addMatchedStoragePools(newMatchedPools);
        vpool.addInvalidMatchedPools(newInvalidPools);
    }

    /**
     * Remove if new pool from invalid pools if it becomes active.
     * 
     * @param vpool
     * @param newInvalidPools
     * @param poolIdStr
     */
    private static void removeInvalidPools(VirtualPool vpool, StringSet newInvalidPools, String poolIdStr) {
        if (null != vpool.getInvalidMatchedPools()
                && vpool.getInvalidMatchedPools().contains(poolIdStr)) {
            _logger.debug("Invalid Pool {} became active now.", poolIdStr);
            newInvalidPools.remove(poolIdStr);
        }
    }

    /**
     * Matches given VpoolList with all systems present in DB. This will be invoked during
     * VirtualPool Create/Update.
     * 
     * @param vpool
     *            : List of VirtualPool
     * @param dbClient
     * @return
     * @throws IOException
     */
    public static void matchVirtualPoolWithAllStoragePools(VirtualPool vpool, DbClient dbClient, CoordinatorClient coordinator) {
        List<URI> storagePoolURIs = dbClient.queryByType(StoragePool.class, true);
        Iterator<StoragePool> storagePoolList = dbClient.queryIterativeObjects(StoragePool.class, storagePoolURIs);
        List<StoragePool> allPoolsToProcess = new ArrayList<StoragePool>();
        while (storagePoolList.hasNext()) {
            allPoolsToProcess.add(storagePoolList.next());
        }
        if (!allPoolsToProcess.isEmpty()) {
            matchvPoolWithStoragePools(vpool, allPoolsToProcess, dbClient, coordinator, null);
        }
    }

    /**
     * compares the two given Sets. Caller is using this to check whether a pool property has
     * changed.
     * 
     * @param existingValue
     *            the existing value
     * @param newValue
     *            the new value
     * @return true if the property has changed, otherwise false.
     */
    public static boolean checkPoolPropertiesChanged(Set<String> existingValue,
            Set<String> newValue) {
        boolean propertyChanged = false;
        // if only is null and the other contains some values, then property has changed.
        // (when one is null and the other is not null but empty, not necessarily say that property
        // has changed)
        // if both are not null, then check their size and their values.
        if (existingValue == null && (newValue != null && !newValue.isEmpty())) {
            propertyChanged = true;
        } else if (newValue == null && (existingValue != null && !existingValue.isEmpty())) {
            propertyChanged = true;
        } else if (existingValue != null && newValue != null) {
            if (existingValue.size() != newValue.size()
                    || !(existingValue.containsAll(newValue))) {
                propertyChanged = true;
            }
        }
        return propertyChanged;
    }

    /**
     * compares the two given Strings. Caller is using this to check whether a pool property has
     * changed.
     * 
     * @param existingValue
     *            the existing value
     * @param newValue
     *            the new value
     * @return true if the property has changed, otherwise false.
     */
    public static boolean checkPoolPropertiesChanged(String existingValue,
            String newValue) {
        boolean propertyChanged = false;
        // if only one of them is null, then property has changed.
        // if both are not null, then compare their values.
        if ((existingValue == null && newValue != null) || (existingValue != null && newValue == null)) {
            propertyChanged = true;
        } else if (existingValue != null && newValue != null) {
            if (existingValue.compareToIgnoreCase(newValue) != 0) {
                propertyChanged = true;
            }
        }
        return propertyChanged;
    }

    public static void removeStoragePoolsfromVPools(Set<String> poolUris, DbClient dbClient) {
        List<URI> vpoolURIs = dbClient.queryByType(VirtualPool.class, true);
        List<VirtualPool> vpoolList = dbClient.queryObject(VirtualPool.class, vpoolURIs);
        for (VirtualPool vpool : vpoolList) {
            if (null != vpool.getMatchedStoragePools()) {
                _logger.info("Removing poolUris {} from matchedStorage Pools", Joiner.on("\t").join(poolUris));
                vpool.getMatchedStoragePools().removeAll(poolUris);
            }
            if (null != vpool.getInvalidMatchedPools()) {
                _logger.info("Removing poolUris {} from invalid Storage Pools", Joiner.on("\t").join(poolUris));
                vpool.getInvalidMatchedPools().removeAll(poolUris);
            }
            if (null != vpool.getAssignedStoragePools()) {
                _logger.info("Removing poolUris {} from assigned Storage Pools", Joiner.on("\t").join(poolUris));
                vpool.getAssignedStoragePools().removeAll(poolUris);
            }
        }
        dbClient.updateAndReindexObject(vpoolList);
    }

    public static void setMatcherFramework(AttributeMatcherFramework matcherFramework) {
        _matcherFramework = matcherFramework;
    }
}
