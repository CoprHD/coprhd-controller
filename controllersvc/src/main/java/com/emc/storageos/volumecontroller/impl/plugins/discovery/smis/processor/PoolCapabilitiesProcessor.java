/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.cim.CIMInstance;
import javax.cim.CIMObjectPath;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.plugins.AccessProfile;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.plugins.common.domainmodel.Operation;

/**
 * Refer StoragePool processor before looking into below
 * For each VNX Pool Capability, generate default expected Pool Setting keys
 * Each VNX Storage Pool, we need 4 default pool Setting Objects.
 * 
 * Each pool Setting is associated with InitialStorageTierMethodology
 * Setting with InitailStorageTierMethodology to 0, means NO_DATA_MOVEMENT
 * Setting would be set to 2 , means this setting would be used to create Volumes
 * configured to be put under Auto_Tier.
 * Setting with InitailStorageTierMethodology to 6, refers this setting would be used to
 * create Volumes configured to be put under High_Available_tier.
 * Setting with InitailStorageTierMethodology to 7, refers this setting would be used to
 * create Volumes configured to be put under Low_Available_tier.
 * 
 * This generated Setting Names plays the key role of this design, would be used later.
 * 
 */
public class PoolCapabilitiesProcessor extends PoolProcessor {

    private Logger _logger = LoggerFactory.getLogger(PoolCapabilitiesProcessor.class);
    private DbClient _dbClient;

    @Override
    public void processResult(
            Operation operation, Object resultObj, Map<String, Object> keyMap)
            throws BaseCollectionException {
        try {
            @SuppressWarnings("unchecked")
            final Iterator<CIMInstance> it = (Iterator<CIMInstance>) resultObj;
            _dbClient = (DbClient) keyMap.get(Constants.dbClient);
            AccessProfile profile = (AccessProfile) keyMap.get(Constants.ACCESSPROFILE);
            StorageSystem device = getStorageSystem(_dbClient, profile.getSystemId());
            while (it.hasNext()) {
                CIMInstance capabilitiesInstance = null;
                try {
                    capabilitiesInstance = it.next();
                    String instanceID = capabilitiesInstance.getPropertyValue(
                            Constants.INSTANCEID).toString();
                    if (DiscoveredDataObject.Type.vnxblock.toString().equalsIgnoreCase(
                            device.getSystemType())) {
                        insertExpectedPoolSettingsPerTier(
                                capabilitiesInstance.getObjectPath(), keyMap);
                        addPath(keyMap, Constants.VNXPOOLCAPABILITIES,
                                capabilitiesInstance.getObjectPath());
                    }
                    addPath(keyMap, operation.getResult(),
                            capabilitiesInstance.getObjectPath());
                } catch (Exception e) {
                    _logger.warn("Pool Capabilities Discovery failed for {}-->{}",
                            capabilitiesInstance.getObjectPath(), getMessage(e));
                }
            }
        } catch (Exception e) {
            _logger.error("Pool Capabilities Discovery failed -->{}", getMessage(e));
        }
    }

    /**
     * In VNX, 4 default policies are present, and this method creates expected Settings per PoolCapability.
     * 
     * @param capabilitiesPath
     * @param keyMap
     */
    private void insertExpectedPoolSettingsPerTier(
            CIMObjectPath capabilitiesPath, Map<String, Object> keyMap) {
        List<String> expectedPoolSettingsTier = (List<String>) keyMap
                .get(Constants.VNXPOOLCAPABILITIES_TIER);
        expectedPoolSettingsTier.add(capabilitiesPath.toString() + Constants.HYPHEN
                + Constants.NO_DATA_MOVEMENT);
        expectedPoolSettingsTier.add(capabilitiesPath.toString() + Constants.HYPHEN
                + Constants.AUTO_TIER);
        expectedPoolSettingsTier.add(capabilitiesPath.toString() + Constants.HYPHEN
                + Constants.HIGH_AVAILABLE_TIER);
        expectedPoolSettingsTier.add(capabilitiesPath.toString() + Constants.HYPHEN
                + Constants.LOW_AVAILABLE_TIER);
        expectedPoolSettingsTier.add(capabilitiesPath.toString() + Constants.HYPHEN
                + Constants.START_HIGH_THEN_AUTO_TIER);
    }

    /**
     * Commenting this out, as we don't have Pool Capabilities Model in place
     * once we have that, will enable this.
     */
    /*
     * private void addStoragePoolCapabilities(
     * StoragePool pool, CIMInstance capabilitiesInstance) throws IOException {
     * StringMap capabilities;
     * if ( null == pool.getPoolCapabilities()) {
     * capabilities = new StringMap();
     * } else {
     * capabilities = pool.getPoolCapabilities();
     * capabilities.clear();
     * }
     * capabilities.put(DATAREDUNDANCYGOAL, getCIMPropertyValue(capabilitiesInstance, DATAREDUNDANCYGOAL));
     * capabilities.put(DATAREDUNDANCYMAX, getCIMPropertyValue(capabilitiesInstance, DATAREDUNDANCYMAX));
     * capabilities.put(DATAREDUNDANCYMIN, getCIMPropertyValue(capabilitiesInstance, DATAREDUNDANCYMIN));
     * capabilities.put(PACKAGEREDUNDANCYGOAL, getCIMPropertyValue(capabilitiesInstance,
     * PACKAGEREDUNDANCYGOAL));
     * capabilities.put(PACKAGEREDUNDANCYMAX, getCIMPropertyValue(capabilitiesInstance,
     * PACKAGEREDUNDANCYMAX));
     * capabilities.put(PACKAGEREDUNDANCYMIN, getCIMPropertyValue(capabilitiesInstance,
     * PACKAGEREDUNDANCYMIN));
     * 
     * _dbClient.persistObject(pool);
     * }
     */
    @Override
    protected void setPrerequisiteObjects(List<Object> inputArgs)
            throws BaseCollectionException {
        // TODO Auto-generated method stub
    }
}
