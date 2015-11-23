/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.fast;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StorageTier;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.plugins.common.Processor;
import com.emc.storageos.volumecontroller.impl.ControllerUtils;
import com.emc.storageos.volumecontroller.impl.NativeGUIDGenerator;
import com.emc.storageos.volumecontroller.impl.utils.ImplicitPoolMatcher;
import com.google.common.base.Splitter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.cim.CIMInstance;
import javax.cim.CIMObjectPath;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * All FAST related Processors extends this Class
 * Reusable code across FAST Processors have been placed here.
 */
public abstract class AbstractFASTPolicyProcessor extends Processor {
    private final static Logger _logger = LoggerFactory.getLogger(AbstractFASTPolicyProcessor.class);

    /**
     * Check if Storage Tier exists in DB.
     * 
     * @param tierNativeGuid
     * @param _dbClient
     * @return StorageTier
     * @throws IOException
     */
    protected StorageTier checkStorageTierExistsInDB(String tierNativeGuid,
            DbClient _dbClient) throws IOException {
        StorageTier tier = null;
        // use NativeGuid to lookup Tiers in DB
        @SuppressWarnings("deprecation")
        List<URI> storageTierUris = _dbClient.queryByConstraint(AlternateIdConstraint.Factory
                .getStorageTierByIdConstraint(tierNativeGuid));
        if (!storageTierUris.isEmpty()) {
            tier = _dbClient.queryObject(StorageTier.class, storageTierUris.get(0));
        }
        return tier;
    }

    /**
     * get nativeGuid for vmax Tiers
     * 
     * @param tierID
     * @return
     */
    protected String getTierNativeGuidForVnx(String tierID) {
        Iterable<String> tierIDItr = Splitter.on(Constants.PATH_DELIMITER_PATTERN).limit(4)
                .split(tierID);
        String poolID = getValueAtGivenPositionFromTierId(tierIDItr, 3);
        String storageSystemId = getValueAtGivenPositionFromTierId(tierIDItr, 0)
                + Constants.PLUS + getValueAtGivenPositionFromTierId(tierIDItr, 1);
        String tierName = getValueAtGivenPositionFromTierId(tierIDItr, 2);
        return NativeGUIDGenerator.generateStorageTierNativeGuidForVnxTier(storageSystemId, poolID,
                tierName);
    }

    /**
     * get nativeGuid for vnx Tiers
     * 
     * @param tierID
     * @return
     */
    protected String getTierNativeGuidForVMax(String tierID) {
        Iterable<String> tierIDItr = Splitter.on(Constants.PATH_DELIMITER_PATTERN).limit(3)
                .split(tierID);
        String storageSystemId = getValueAtGivenPositionFromTierId(tierIDItr, 0)
                + Constants.PLUS + getValueAtGivenPositionFromTierId(tierIDItr, 1);
        String tierName = getValueAtGivenPositionFromTierId(tierIDItr, 2);
        return NativeGUIDGenerator.generateStorageTierNativeGuidForVmaxTier(storageSystemId,
                tierName);
    }

    protected String getTierNativeGuidFromTierInstance(CIMInstance tierInstance) {
        String tierID = tierInstance.getObjectPath().getKey(Constants.INSTANCEID)
                .getValue().toString();
        String tierNativeGuid = null;
        if (tierID.toLowerCase().contains(Constants.SYMMETRIX)) {
            tierNativeGuid = getTierNativeGuidForVMax(tierID);
        } else {
            tierNativeGuid = getTierNativeGuidForVnx(tierID);
        }
        return tierNativeGuid;
    }

    /**
     * Get FAST Policy Name
     * PolicyRuleName doesn't need to be same across multiple Arrays.
     * 
     * @param policyObjectPath
     * @return String format of: Array Serial ID + FASTPOLICY + policyRuleName
     */
    protected String getFASTPolicyID(CIMObjectPath policyObjectPath) {
        String array = policyObjectPath.getKey(Constants.SYSTEMNAME).getValue()
                .toString();
        String policyRuleName = policyObjectPath.getKey(Constants.POLICYRULENAME)
                .getValue().toString();
        return getFASTPolicyID(array, policyRuleName);
    }

    /**
     * Get FAST Policy Name
     * PolicyRuleName doesn't need to be same across multiple Arrays.
     * 
     * @param systemName - system name from CIMObjectPath in format of: Array Serial ID
     * @param policyRuleName - auto tier policy name, e.g., LOW
     * @return String format of: Array Serial ID + FASTPOLICY + policyRuleName
     */
    protected String getFASTPolicyID(String systemName, String policyRuleName) {
        try {
            return NativeGUIDGenerator.generateAutoTierPolicyNativeGuid(systemName,
                    policyRuleName, NativeGUIDGenerator.FASTPOLICY);
        } catch (Exception e) {
            _logger.error("FAST Policy Id creation failed", e);
        }
        return null;
    }

    /**
     * Get FAST Policy Name from DeviceGroup CIMObjectPath
     * 
     * @param deviceGroupPath
     * @return FASTPolicyName
     */
    protected String getFASTPolicyNameFromDeviceGroupID(CIMObjectPath deviceGroupPath) {
        String groupID = deviceGroupPath.getKey(Constants.INSTANCEID).getValue()
                .toString();
        // always bourne created Group will have fixed format SerialID+FASTPolicyName_DeviceGroup
        String bourneCreatedDeviceGroupName = groupID.split(Constants.PATH_DELIMITER_REGEX)[2];
        String fastPolicyName = bourneCreatedDeviceGroupName.substring(0,
                bourneCreatedDeviceGroupName.lastIndexOf(Constants.HYPHEN));
        return fastPolicyName;
    }

    /**
     * get Device GroupName
     * 
     * @param deviceGroupPath
     * @return bourneCreatedDeviceGroupName
     */
    protected String getDeviceGroupName(CIMObjectPath deviceGroupPath) {
        String groupID = deviceGroupPath.getKey(Constants.INSTANCEID).getValue()
                .toString();
        // always bourne created Group will have fixed format
        // SerialID+FASTPolicyName_DeviceGroup
        String bourneCreatedDeviceGroupName = groupID.split(Constants.PATH_DELIMITER_REGEX)[2];
        return bourneCreatedDeviceGroupName;
    }

    /**
     * add DeviceGroup Path to Map, and generate sDeviceGroup--->FASTPolicy Mapping
     * 
     * @param keyMap
     * @param deviceGroupPath
     * @param resultKey
     * @throws BaseCollectionException
     */
    protected void addDeviceGroupPathToMap(
            Map<String, Object> keyMap, CIMObjectPath deviceGroupPath, String resultKey)
            throws BaseCollectionException {
        addPath(keyMap, resultKey, deviceGroupPath);
        // generate DeviceGroup--->FASTPolicy Mapping
        String fastPolicy = getFASTPolicyNameFromDeviceGroupID(deviceGroupPath);
        keyMap.put(deviceGroupPath.getKey(Constants.INSTANCEID).getValue().toString(),
                (CIMObjectPath) keyMap.get(fastPolicy));
    }

    /**
     * Get storage Pool from DB
     * TODO move DB related methods to DBUtil
     * 
     * @param poolObjectPath
     * @param dbClient
     * @return
     */
    protected URI getStoragePoolURI(CIMObjectPath poolObjectPath, DbClient dbClient) {
        URI poolURI = null;
        try {
            String poolNativeGuid = NativeGUIDGenerator
                    .generateNativeGuidForPool(poolObjectPath);
            URIQueryResultList result = new URIQueryResultList();
            dbClient.queryByConstraint(AlternateIdConstraint.Factory
                    .getStoragePoolByNativeGuidConstraint(poolNativeGuid), result);
            while (result.iterator().hasNext()) {
                poolURI = result.iterator().next();
            }
        } catch (Exception e) {
            _logger.error("Extracting StoragePool URi for PoolID {} failed -->",
                    poolObjectPath, e);
        }
        return poolURI;
    }

    /**
     * add StoragePools to FAST Policy Object
     * Existing Pools associated with Policy will get replaced.
     * 
     * @param policyObjectPath
     * @param it
     * @param dbClient
     * @throws IOException
     */
    @SuppressWarnings("unchecked")
    protected void addStoragePoolstoPolicy(List<CIMObjectPath> policyObjectPaths,
            Iterator<CIMObjectPath> it, DbClient dbClient, Map<String, Object> keyMap, String tierID)
            throws IOException {
        // PolicyToPoolInfo ---> Map <PolicyName, Set<String> Pools>
        // set already at runtime
        Map<String, Set<String>> policytopoolMapping = (Map<String, Set<String>>) keyMap
                .get(Constants.POLICY_TO_POOLS_MAPPING);

        while (it.hasNext()) {
            CIMObjectPath poolObjectPath = it.next();
            URI poolURI = getStoragePoolURI(poolObjectPath, dbClient);
            if (null != poolURI) {
                for (CIMObjectPath policyObjectPath : policyObjectPaths) {
                    String policyID = getFASTPolicyID(policyObjectPath);
                    if (!policytopoolMapping.containsKey(policyID)) {
                        policytopoolMapping.put(policyID, new HashSet<String>());
                    }
                    policytopoolMapping.get(policyID).add(poolURI.toString());
                }
            }
        }
    }

    /**
     * create Storage Tier
     * 
     * @param tierInstance
     * @param tierObject
     * @param tierNativeGuid
     * @param vmaxFastPolicyUri
     * @param tierList
     * @return
     */
    protected StorageTier createStorageTier(
            CIMInstance tierInstance, StorageTier tierObject, String tierNativeGuid,
            URI vmaxFastPolicyUri, List<StorageTier> newTierList, List<StorageTier> updateTierList, String driveTechnology) {
        boolean isNewTier = false;
        if (null == tierObject) {
            tierObject = new StorageTier();
            tierObject.setId(URIUtil.createId(StorageTier.class));
            tierObject.setNativeGuid(tierNativeGuid);
            isNewTier = true;
        }
        tierObject.setPercentage(getCIMPropertyValue(tierInstance,
                Constants.PERCENTAGE));
        tierObject.setLabel(getCIMPropertyValue(tierInstance, Constants.ELEMENTNAME));
        tierObject.setDiskDriveTechnology(driveTechnology);
        String totalCapacity = getCIMPropertyValue(tierInstance, Constants.TOTAL_CAPACITY);
        tierObject.setTotalCapacity(ControllerUtils.convertBytesToKBytes(totalCapacity));
        if (isNewTier) {
            newTierList.add(tierObject);
        } else {
            updateTierList.add(tierObject);
        }
        return tierObject;
    }

    /**
     * Create Storage Tiers for VNX, if not present already
     * Add Tier Uris to Storage Pool Object for both vmax and vnx
     * 
     * @param storagePoolpath
     * @param it
     * @param dbClient
     * @param keyMap
     * @throws IOException
     */
    protected void addTiersToPool(
            CIMObjectPath storagePoolpath, Iterator<CIMInstance> it, DbClient dbClient,
            Map<String, Object> keyMap) throws IOException {

        Map<URI, StoragePool> poolsToMatchWithVpool = (Map<URI, StoragePool>) keyMap
                .get(Constants.MODIFIED_STORAGEPOOLS);
        Map<String, String> tierUtilizationPercentageMap = new HashMap<String, String>();
        CIMInstance tierInstance = null;
        List<StorageTier> _tierList = new ArrayList<StorageTier>();
        Set<String> tierUris = new HashSet<String>();
        while (it.hasNext()) {
            try {
                tierInstance = it.next();
                String tierUtilizationPercentage = tierInstance.getPropertyValue(
                        Constants.PERCENTAGE).toString();
                String driveTechnologyIdentifier = tierInstance.getPropertyValue(
                        Constants.TECHNOLOGY).toString();
                String driveTechnology = StorageTier.SupportedTiers
                        .getTier(driveTechnologyIdentifier);
                if (null == driveTechnology) {
                    _logger.info("Unsupported Drive Type :" + driveTechnologyIdentifier);
                } else {
                    tierUtilizationPercentageMap.put(driveTechnology,
                            tierUtilizationPercentage);
                }
                String tierNativeGuid = getTierNativeGuidFromTierInstance(tierInstance);
                StorageTier tierObject = checkStorageTierExistsInDB(tierNativeGuid,
                        dbClient);
                if (null == tierObject) {
                    tierObject = createStorageTier(tierInstance, tierObject,
                            tierNativeGuid, null, _tierList, null, driveTechnology);
                    dbClient.createObject(tierObject);
                }
                tierUris.add(tierObject.getId().toString());

            } catch (Exception e) {
                _logger.error("Determing Drive Type, Tier Info failed for {} : ",
                        tierInstance.getObjectPath(), e);
            }
        }
        URI poolURI = getStoragePoolURI(storagePoolpath, dbClient);
        StoragePool pool = dbClient.queryObject(StoragePool.class, poolURI);
        if (null != pool) {
            pool.addTierUtilizationPercentage(tierUtilizationPercentageMap);
            // add to modified pool list if pool's property which is required for vPool matcher, has changed.
            // If the modified list already has this pool, skip the check.
            if (!poolsToMatchWithVpool.containsKey(pool.getId()) &&
                    ImplicitPoolMatcher.checkPoolPropertiesChanged(pool.getTiers(), tierUris)) {
                poolsToMatchWithVpool.put(pool.getId(), pool);
            }
            pool.addTiers(tierUris);
            dbClient.persistObject(pool);
        }
    }
}
