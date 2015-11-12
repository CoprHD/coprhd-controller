/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.fast.vnx;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.cim.CIMInstance;
import javax.cim.CIMObjectPath;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.plugins.AccessProfile;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.plugins.common.domainmodel.Operation;
import com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.PoolProcessor;

/**
 * Check whether the Storage Pool Setting already Exists in Provider for this Storage Pool
 * This processor would have been called as part of the below executions
 * 1. Associators SMI-S call
 * CIMPath : VNXPoolCapabilities
 * ResultClass : EMC_StoragePoolSetting
 * The resultant list of existing Storage pool Settings is being returned as Result to this Processor.
 * 
 * Goal of this Processor:
 * To verify, one StoragePoolSetting Object per InitialStorageTierMethodology is returned.
 * By default, Bourne expects 4 different Pool Settings (1 per InitialStorageTierMethodology value)
 * should exist for each PoolCapabilities.
 * 
 * If yes, then we never need to create a new Setting, if not, we have to create Setting.
 * Create Setting is the next SMI-S operation to be run. This processor updates the List of
 * Settings which needs to be created, so that Create Setting call runs above the updated List.
 * 
 * For each received Setting,get its InitialStorageTierMethodology value.
 * InitialStorageTierMethodology :
 * 0 - No_Data_Movement
 * 2 - Auto_Tier
 * 6- High_Available_tier
 * 7 - Low_Available_tier
 * 
 * Compare the setting ID from returned Settings object against the setting Id in pool.
 * If exists, then remove the entry from the default expected Setting list.
 * 
 * Default expected Setting list for each capability is being populated in PoolcapabilitiesProcessor.
 * Format : vnxcapabilitiesPath-2;vnxcapabilitiesPath-0;vnxcapabilitiesPath-6;vnxcapabilitiesPath-7;
 * If the result includes item 1 and 2, the list would be updated to
 * vnxcapabilitiesPath-6;vnxcapabilitiesPath-7
 * 
 * Create Setting would run only above this new updated list
 */
public class CheckPoolSettingExistenceProcessor extends PoolProcessor {
    private Logger _logger = LoggerFactory
            .getLogger(CheckPoolSettingExistenceProcessor.class);
    private List<Object> _args;
    private DbClient _dbClient;

    @SuppressWarnings("unchecked")
    @Override
    public void processResult(
            Operation operation, Object resultObj, Map<String, Object> keyMap)
            throws BaseCollectionException {
        @SuppressWarnings("unchecked")
        final Iterator<CIMInstance> it = (Iterator<CIMInstance>) resultObj;
        _dbClient = (DbClient) keyMap.get(Constants.dbClient);
        AccessProfile profile = (AccessProfile) keyMap.get(Constants.ACCESSPROFILE);
        try {
            StorageSystem device = getStorageSystem(_dbClient, profile.getSystemId());
            CIMObjectPath poolCapabilitiesPath = getObjectPathfromCIMArgument();
            String poolID = getPoolIdFromCapabilities(poolCapabilitiesPath);
            // get Pool from DB
            StoragePool pool = checkStoragePoolExistsInDB(poolID, _dbClient, device);

            List<String> poolSettingsList = (List<String>) keyMap
                    .get(Constants.VNXPOOLCAPABILITIES_TIER);
            while (it.hasNext()) {
                CIMInstance settingInstance = it.next();
                _logger.debug("settingInstance :{}", settingInstance);
                int tierMethodology = Integer.parseInt(settingInstance.getPropertyValue(
                        Constants.INITIAL_STORAGE_TIER_METHODOLOGY).toString());
                int tierSelection = Integer.parseInt(settingInstance.getPropertyValue(
                        Constants.INITIAL_STORAGE_TIERING_SELECTION).toString());
                String poolSettingId = settingInstance.getPropertyValue(
                        Constants.INSTANCEID).toString();
                // remove already existing settings from list

                if (checkPoolSettingExistsBasedOnTierMethodology(tierMethodology, pool,
                        poolSettingId) && tierSelection == Constants.RELATIVE_PERFORMANCE_ORDER) {
                    poolSettingsList.remove(poolCapabilitiesPath.toString() + Constants.HYPHEN + tierMethodology);
                }

            }
        } catch (Exception e) {
            _logger.error("CheckPoolExistence Processor failed:", e);
        }
    }

    /**
     * check whether pool Setting already exists
     * 
     * @param tierMethodology
     * @param pool
     * @param poolSettingId
     * @return
     * @throws IOException
     */
    private boolean checkPoolSettingExistsBasedOnTierMethodology(
            int tierMethodology, StoragePool pool, String poolSettingId)
            throws IOException {
        boolean poolChanged = false;
        if (null == pool) {
            return poolChanged;
        }
        switch (tierMethodology) {
            case Constants.NO_DATA_MOVEMENT:
                if (null == pool.getNoDataMovementId()
                        || !pool.getNoDataMovementId().equalsIgnoreCase(poolSettingId)) {
                    pool.setNoDataMovementId(poolSettingId);
                }
                poolChanged = true;
                break;
            case Constants.AUTO_TIER:
                if (null == pool.getAutoTierSettingId()
                        || !pool.getAutoTierSettingId().equalsIgnoreCase(poolSettingId)) {
                    pool.setAutoTierSettingId(poolSettingId);
                }
                poolChanged = true;
                break;
            case Constants.HIGH_AVAILABLE_TIER:
                if (null == pool.getHighAvailableTierId()
                        || !pool.getHighAvailableTierId().equalsIgnoreCase(poolSettingId)) {
                    pool.setHighAvailableTierId(poolSettingId);
                }
                poolChanged = true;
                break;
            case Constants.LOW_AVAILABLE_TIER:
                if (null == pool.getLowAvailableTierId()
                        || !pool.getLowAvailableTierId().equalsIgnoreCase(poolSettingId)) {
                    pool.setLowAvailableTierId(poolSettingId);
                }
                poolChanged = true;
                break;
            case Constants.START_HIGH_THEN_AUTO_TIER:
                if (null == pool.getStartHighThenAutoTierId()
                        || !pool.getStartHighThenAutoTierId().equalsIgnoreCase(poolSettingId)) {
                    pool.setStartHighThenAutoTierId(poolSettingId);
                }
                poolChanged = true;
                break;
            default:
                _logger.warn(
                        "Found Invalid Storage tier methodology '{}' for Storage Pool {}",
                        tierMethodology, pool.getId());
                break;
        }
        if (poolChanged) {
            _dbClient.persistObject(pool);
        }
        return poolChanged;
    }

    /**
     * return 1st Argument in inputArguments used to
     * call this SMI-S call.
     * 
     * @return
     */
    private CIMObjectPath getObjectPathfromCIMArgument() {
        Object[] arguments = (Object[]) _args.get(0);
        return (CIMObjectPath) arguments[0];
    }

    @Override
    protected void setPrerequisiteObjects(List<Object> inputArgs)
            throws BaseCollectionException {
        _args = inputArgs;
    }
}
