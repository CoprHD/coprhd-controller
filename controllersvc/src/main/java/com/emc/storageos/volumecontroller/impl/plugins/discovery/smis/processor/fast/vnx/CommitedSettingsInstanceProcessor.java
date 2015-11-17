/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.fast.vnx;

import java.util.List;
import java.util.Map;

import javax.cim.CIMInstance;
import javax.cim.CIMObjectPath;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.cimadapter.connections.cim.CimObjectPathCreator;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.plugins.AccessProfile;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.plugins.common.domainmodel.Operation;
import com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.PoolProcessor;

/**
 * refer ModifiedSettingsInstanceProcessor before looking into below
 * For each Created Storage Pool Setting Instance, run commitInstance
 * which would commit the instance to Provider.
 * 
 * The logic here is,
 * For each VNX storage Pool, 4 default settings are created.
 * i.e.1st setting with InitailStorageTierMethodology to 0, means NO_DATA_MOVEMENT
 * 2nd setting would be set to 2 , means this setting would be used to create Volumes
 * configured to be put under Auto_Tier.
 * Setting with InitailStorageTierMethodology to 6, refers this setting would be used to
 * create Volumes configured to be put under High_Available_tier.
 * Setting with InitailStorageTierMethodology to 7, refers this setting would be used to
 * create Volumes configured to be put under Low_Available_tier.
 * 
 * For each successful commit of this setting instance into Provider, this processor gets invoked
 * Get the corresponding Pool Object, and update the setting id , based on InitailStorageTierMethod
 * 
 */
public class CommitedSettingsInstanceProcessor extends PoolProcessor {
    private Logger _logger = LoggerFactory
            .getLogger(CommitedSettingsInstanceProcessor.class);
    private List<Object> _args;

    @Override
    public void processResult(
            Operation operation, Object resultObj, Map<String, Object> keyMap)
            throws BaseCollectionException {
        try {
            CIMInstance modifiedInstance = getObjectPathfromCIMArgument();
            DbClient _dbClient = (DbClient) keyMap.get(Constants.dbClient);
            AccessProfile profile = (AccessProfile) keyMap.get(Constants.ACCESSPROFILE);
            CIMObjectPath poolSettingPath = modifiedInstance.getObjectPath();
            String poolSettingId = poolSettingPath.getKey(Constants.INSTANCEID)
                    .getValue().toString();
            CIMObjectPath poolCapabilities_Associated_With_Setting = CimObjectPathCreator.createInstance(
                    keyMap.get(poolSettingPath.toString()).toString());
            String poolID = getNativeIDFromInstance(poolCapabilities_Associated_With_Setting
                    .getKey(Constants.INSTANCEID).getValue().toString());
            StorageSystem device = getStorageSystem(_dbClient, profile.getSystemId());
            StoragePool pool = checkStoragePoolExistsInDB(poolID, _dbClient, device);
            int tierMethodologyUsedForThisCreatedSetting = Integer
                    .parseInt((String) keyMap.get(poolSettingPath.toString()
                            + Constants.HYPHEN + Constants.TIERMETHODOLOGY));
            if (null != pool) {
                updatePoolSettingId(tierMethodologyUsedForThisCreatedSetting, pool,
                        poolSettingId);
                _dbClient.persistObject(pool);
            }
        } catch (Exception e) {
            _logger.error("Commiting Modified Settign Instance failed", e);
        }
    }

    /**
     * return 1st Argument in inputArguments used to
     * call this SMI-S call.
     * 
     * @return
     */
    private CIMInstance getObjectPathfromCIMArgument() {
        Object[] arguments = (Object[]) _args.get(0);
        return (CIMInstance) arguments[0];
    }

    @Override
    protected void setPrerequisiteObjects(List<Object> inputArgs)
            throws BaseCollectionException {
        _args = inputArgs;
    }

    private void updatePoolSettingId(
            int tierMethodology, StoragePool pool, String poolSettingId) {
        switch (tierMethodology) {
            case Constants.NO_DATA_MOVEMENT:
                pool.setNoDataMovementId(poolSettingId);
                break;
            case Constants.AUTO_TIER:
                pool.setAutoTierSettingId(poolSettingId);
                break;
            case Constants.HIGH_AVAILABLE_TIER:
                pool.setHighAvailableTierId(poolSettingId);
                break;
            case Constants.LOW_AVAILABLE_TIER:
                pool.setLowAvailableTierId(poolSettingId);
                break;
            case Constants.START_HIGH_THEN_AUTO_TIER:
                pool.setStartHighThenAutoTierId(poolSettingId);
                break;
            default:
                _logger.warn(
                        "Found Invalid Storage tier methodology '{}' for Storage Pool {}",
                        tierMethodology, pool.getId());
                break;
        }
    }
}
