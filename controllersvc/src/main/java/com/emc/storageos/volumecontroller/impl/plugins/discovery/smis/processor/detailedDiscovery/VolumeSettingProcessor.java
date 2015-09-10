/*
 * Copyright (c) 2014-2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.detailedDiscovery;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.cim.CIMInstance;
import javax.cim.CIMObjectPath;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.AutoTieringPolicy;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume.SupportedVolumeCharacterstics;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume.SupportedVolumeInformation;
import com.emc.storageos.plugins.AccessProfile;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.plugins.common.domainmodel.Operation;
import com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.StorageProcessor;
import com.emc.storageos.volumecontroller.impl.utils.DiscoveryUtils;

/**
 * This processor gets invoked only for VNX unManaged volume discoveries.
 * Settings returned will always and only be Volumes with Auto_Tier configured.
 * The reason being, both Auto_tier and Start_High_then_Auto are represented as Auto_Tier in Provider.
 * Hence, it stores the objectPaths of Auto_tier alone. These object paths will be used to get
 * VolumeSettings (next SMI-S operation), from which the exact policy is being found.
 */
public class VolumeSettingProcessor extends StorageProcessor {

    private Logger _logger = LoggerFactory.getLogger(VolumeSettingProcessor.class);
    private List<Object> _args;
    private DbClient _dbClient;

    @Override
    public void processResult(Operation operation, Object resultObj, Map<String, Object> keyMap)
            throws BaseCollectionException {

        try {
            _dbClient = (DbClient) keyMap.get(Constants.dbClient);
            AccessProfile profile = (AccessProfile) keyMap.get(Constants.ACCESSPROFILE);
            StorageSystem system = _dbClient.queryObject(StorageSystem.class, profile.getSystemId());
            final Iterator<CIMInstance> it = (Iterator<CIMInstance>) resultObj;
            CIMObjectPath volumePath = getObjectPathfromCIMArgument(_args);
            String nativeGuid = getUnManagedVolumeNativeGuidFromVolumePath(volumePath);
            UnManagedVolume unManagedVolume = checkUnManagedVolumeExistsInDB(
                    nativeGuid, _dbClient);
            if (null == unManagedVolume) {
                return;
            }
            while (it.hasNext()) {
                CIMInstance settingInstance = it.next();
                String initialTierSetting = settingInstance.getPropertyValue(Constants.INITIAL_STORAGE_TIER_METHODOLOGY).toString();
                String policyName = null;
                if ("4".equalsIgnoreCase(initialTierSetting)) {
                    policyName = AutoTieringPolicy.VnxFastPolicy.DEFAULT_START_HIGH_THEN_AUTOTIER.name();
                } else {
                    policyName = AutoTieringPolicy.VnxFastPolicy.DEFAULT_AUTOTIER.name();
                }
                _logger.info("Adding {} Policy Rule to UnManaged Volume {}", policyName, nativeGuid);
                unManagedVolume.getVolumeInformation().put(SupportedVolumeInformation.AUTO_TIERING_POLICIES.toString(), policyName);
                unManagedVolume.putVolumeCharacterstics(
                        SupportedVolumeCharacterstics.IS_AUTO_TIERING_ENABLED.toString(),
                        "true");

                // StorageVolumeInfoProcessor updated supported_vpool_list based on its pool's presence in vPool
                // Now, filter those vPools based on policy associated
                DiscoveryUtils.filterSupportedVpoolsBasedOnTieringPolicy(unManagedVolume, policyName, system, _dbClient);

                _dbClient.updateAndReindexObject(unManagedVolume);
            }
        } catch (Exception e) {
            _logger.error("Updating Auto Tier Policies failed on unmanaged volumes", e);
        }
    }

    @Override
    protected void setPrerequisiteObjects(List<Object> inputArgs) throws BaseCollectionException {
        _args = inputArgs;
    }

}
