/*
 * Copyright (c) 2008-2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.detailedDiscovery;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
import com.google.common.base.Strings;

/**
 * Each StoragePool will be associated with multiple Storage PoolSettings.
 * Process all the poolSettings and populate them in a datastructure [Pool_NativeGuid, List<SLO_NAME>]
 * to utilize them in finding the right SLO Name for an exported unmanaged volume.
 * 
 * This is currently supported only vmax3 systems.
 * 
 * an(ref-poolcapability, EMC_StorageSettingsAssociatedToCapabilities, EMC_StoragePoolSetting...)
 * => Instance of EMC_StoragePoolSetting.
 * 
 */
public class StoragePoolSettingProcessor extends PoolProcessor {
    private Logger _logger = LoggerFactory.getLogger(StoragePoolSettingProcessor.class);
    private DbClient _dbClient;
    private AccessProfile profile = null;
    private List<Object> _args;

    /**
     * {@inheritDoc}
     */
    @Override
    public void processResult(
            Operation operation, Object resultObj, Map<String, Object> keyMap)
            throws BaseCollectionException {
        try {
            @SuppressWarnings("unchecked")
            final Iterator<CIMInstance> it = (Iterator<CIMInstance>) resultObj;
            profile = (AccessProfile) keyMap.get(Constants.ACCESSPROFILE);
            _dbClient = (DbClient) keyMap.get(Constants.dbClient);
            StorageSystem device = _dbClient.queryObject(StorageSystem.class,
                    profile.getSystemId());
            // Process the response only for vmax3 systems.
            if (device.checkIfVmax3()) {
                Set<String> sloNames = new HashSet<String>();
                CIMObjectPath poolCapabilitiesPath = getObjectPathfromCIMArgument();
                while (it.hasNext()) {
                    CIMInstance settingInstance = null;
                    try {
                        settingInstance = it.next();
                        processVMAX3SLO(device, settingInstance, sloNames);
                    } catch (Exception e) {
                        _logger.warn("Pool Setting detailed discovery failed for {}-->{}",
                                settingInstance.getObjectPath(), getMessage(e));
                    }
                }
                String poolId = getPoolIdFromCapabilities(poolCapabilitiesPath)
                        .replaceAll(Constants.SMIS80_DELIMITER_REGEX, Constants.PLUS);
                StoragePool pool = checkStoragePoolExistsInDB(poolId, _dbClient, device);
                if (null != pool) {
                    _logger.info("Pool:{} SloNames:{}", pool.getNativeGuid(), sloNames);
                    keyMap.put(pool.getNativeGuid(), sloNames);
                }
            }
        } catch (Exception e) {
            _logger.error("Pool Setting Discovery failed -->{}", getMessage(e));
        }
    }

    @Override
    protected void setPrerequisiteObjects(List<Object> args)
            throws BaseCollectionException {
        _args = args;
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

    /**
     * Validate that this represents a SLO setting. If it does, add slo policy name to slonames collection.
     * 
     * If the CIMInstance has a its EMCFastSetting populated, then this is a SLO policy
     * based StoragePoolSetting. We will extract the SLOName, Workload, and Average response time.
     * Updates 'sloNames' list
     * 
     * @param storageSystem [in] - StorageSystem that the setting belongs to
     * @param settingInstance [in] - Should be an instance of Symm_StoragePoolSetting.
     * @param sloNames [in] - List of sloNames found for the Symm_StoragePoolSetting.
     */
    private void processVMAX3SLO(StorageSystem storageSystem, CIMInstance settingInstance, Set<String> sloNames) {
        String emcFastSetting = (String) settingInstance.getPropertyValue(Constants.EMC_FAST_SETTING);
        if (!Strings.isNullOrEmpty(emcFastSetting)) {
            _logger.info("Setting {} has non-null EMCFastSetting property = '{}'", settingInstance.getObjectPath().toString(),
                    emcFastSetting);
            String slo = (String) settingInstance.getPropertyValue(Constants.EMC_SLO);
            Float avgResponseTimeValue = (Float) settingInstance.getPropertyValue(Constants.EMC_AVG_RESPONSE_TIME);
            if (!Strings.isNullOrEmpty(slo)) {
                String avgResponseTime = Constants.NOT_AVAILABLE;
                if (!checkForNull(avgResponseTimeValue)) {
                    avgResponseTime = avgResponseTimeValue.toString();
                } else {
                    _logger.info("EMCApproxAverageResponseTime property is null or empty for SLO: {}.", slo);
                }
                String workload = (String) settingInstance.getPropertyValue(Constants.EMC_WORKLOAD);
                workload = Strings.isNullOrEmpty(workload) ? Constants.NONE : workload;
                String sloName = generateSLOPolicyName(slo, workload, avgResponseTime);
                sloNames.add(sloName);
            } else {
                _logger.warn("EMCSLO property is null or empty.");
            }
        }
    }

}
