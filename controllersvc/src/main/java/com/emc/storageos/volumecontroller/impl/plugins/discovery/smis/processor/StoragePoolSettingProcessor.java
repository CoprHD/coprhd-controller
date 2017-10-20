/*
 * Copyright (c) 2008-2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.AutoTieringPolicy;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StoragePoolSetting;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.plugins.AccessProfile;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.plugins.common.domainmodel.Operation;
import com.emc.storageos.volumecontroller.impl.NativeGUIDGenerator;
import com.emc.storageos.volumecontroller.impl.utils.ImplicitPoolMatcher;
import com.google.common.base.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.cim.CIMInstance;
import javax.cim.CIMObjectPath;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.AutoTieringPolicyProcessorHelper.getAutoTieringPolicyByNameFromDB;

/**
 * Each StoragePool will be associated with multiple StoragePoolSettings.
 * 1. potted settings and user Settings.
 * Both these settings would get discovered for a StoragePool.
 * 
 * StoragePool Setting refers to physicalSetting. With use of physical,we determine the RAID
 * level and use it to update the RAID and parity details on the corresponding StoragePools.
 */
public class StoragePoolSettingProcessor extends PoolProcessor {
    private static final String INSTANCEID = "InstanceID";
    private static final String DATAREDUNDANCYGOAL = "DataRedundancyGoal";
    private static final String DATAREDUNDANCYMAX = "DataRedundancyMax";
    private static final String DATAREDUNDANCYMIN = "DataRedundancyMin";
    private static final String EMCRAIDLEVEL = "EMCRaidLevel";
    private static final String EXTENTSTRIPELENGTH = "ExtentStripeLength";
    private static final String EXTENTSTRIPELENGTHMAX = "ExtentStripeLengthMax";
    private static final String EXTENTSTRIPELENGTHMIN = "ExtentStripeLengthMin";
    private static final String PACKAGEREDUNDANCYGOAL = "PackageRedundancyGoal";
    private static final String PACKAGEREDUNDANCYMAX = "PackageRedundancyMax";
    private static final String PACKAGEREDUNDANCYMIN = "PackageRedundancyMin";
    private static final String EMC_POTTED_SETTING = "EMCPottedSetting";

    private Logger _logger = LoggerFactory.getLogger(StoragePoolSettingProcessor.class);
    private DbClient _dbClient;
    private AccessProfile profile = null;
    private List<StoragePoolSetting> _poolsettingList = null;
    private List<Object> _args;
    private List<AutoTieringPolicy> newSLOList = null;
    private List<AutoTieringPolicy> updateSLOList = null;
    private Set<String> sloNames = null;

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
            Map<URI, StoragePool> poolsToMatchWithVpool = (Map<URI, StoragePool>) keyMap
                    .get(Constants.MODIFIED_STORAGEPOOLS);
            _poolsettingList = new ArrayList<StoragePoolSetting>();
            StorageSystem device = _dbClient.queryObject(StorageSystem.class,
                    profile.getSystemId());
            boolean isVmax3 = device.checkIfVmax3();
            if (isVmax3) {
                sloNames = (Set<String>) keyMap.get(Constants.SLO_NAMES);
                newSLOList = new ArrayList<>();
                updateSLOList = new ArrayList<>();
            }
            Set<String> raidLevels = new HashSet<String>();
            CIMObjectPath poolCapabilitiesPath = getObjectPathfromCIMArgument();
            while (it.hasNext()) {
                CIMInstance settingInstance = null;
                try {
                    settingInstance = it.next();
                    String emcRaidLevel = (String) settingInstance
                            .getPropertyValue(EMCRAIDLEVEL);
                    if (!Strings.isNullOrEmpty(emcRaidLevel) && isPottedSetting(settingInstance)) {
                        raidLevels
                                .addAll(extractRaidLevelsFromSettingInstance(settingInstance));
                    }
                    if (isVmax3) {
                        processVMAX3SLO(device, settingInstance);
                    }
                    addPath(keyMap, operation.getResult(),
                            settingInstance.getObjectPath());
                } catch (Exception e) {
                    _logger.warn("Pool Setting Discovery failed for {}-->{}",
                            settingInstance.getObjectPath(), getMessage(e));
                }
            }
            String poolId = getPoolIdFromCapabilities(poolCapabilitiesPath);
            StoragePool pool = checkStoragePoolExistsInDB(poolId, _dbClient, device);
            if (null != pool) {
                // add to modified pool list if pool's property which is required for vPool matcher, has changed.
                // If the modified list already has this pool, skip the check.
                if (!poolsToMatchWithVpool.containsKey(pool.getId()) &&
                        ImplicitPoolMatcher.checkPoolPropertiesChanged(pool.getSupportedRaidLevels(), raidLevels)) {
                    poolsToMatchWithVpool.put(pool.getId(), pool);
                }
                pool.addSupportedRaidLevels(raidLevels);
                _dbClient.persistObject(pool);
                attachPoolsToSLOBasedAutoTierPolicies(pool, newSLOList, updateSLOList);
            }
            processSLOBasedAutoTierPolicies(device, sloNames, newSLOList, updateSLOList);
        } catch (Exception e) {
            _logger.error("Pool Setting Discovery failed -->{}", getMessage(e));
        } finally {
            _poolsettingList = null;
        }
    }

    private Set<String> extractRaidLevelsFromSettingInstance(CIMInstance settingInstance) {
        // format : RAID1;RADI0;RAID2
        String emcRaidLevels = (String) settingInstance.getPropertyValue(EMCRAIDLEVEL);
        List<String> emcRaidLevelList = new ArrayList<String>(Arrays.asList(emcRaidLevels
                .split(";")));
        return new HashSet<String>(emcRaidLevelList);
    }

    private boolean isPottedSetting(CIMInstance settingInstance) {
        return (Boolean) settingInstance.getPropertyValue(EMC_POTTED_SETTING);
    }

    /**
     * createStoragePoolSetting
     * 
     * @param setting
     * @param settingInstance
     */
    private void createStoragePoolSetting(
            StoragePoolSetting setting, CIMInstance settingInstance, StorageSystem device) {
        if (setting == null) {
            setting = new StoragePoolSetting();
            setting.setId(URIUtil.createId(StoragePoolSetting.class));
            setting.setStorageSystem(profile.getSystemId());
        }
        setting.setPoolsettingID(getCIMPropertyValue(settingInstance, INSTANCEID));
        setting.setDataRedundancyGoal(getCIMPropertyValue(settingInstance,
                DATAREDUNDANCYGOAL));
        setting.setDataRedundancyMax(getCIMPropertyValue(settingInstance,
                DATAREDUNDANCYMAX));
        setting.setDataRedundancyMin(getCIMPropertyValue(settingInstance,
                DATAREDUNDANCYMIN));
        setting.setExtentStripeLength(getCIMPropertyValue(settingInstance,
                EXTENTSTRIPELENGTH));
        setting.setExtentStripeLengthMax(getCIMPropertyValue(settingInstance,
                EXTENTSTRIPELENGTHMAX));
        setting.setExtentStripeLengthMin(getCIMPropertyValue(settingInstance,
                EXTENTSTRIPELENGTHMIN));
        setting.setRaidLevel(getCIMPropertyValue(settingInstance, EMCRAIDLEVEL));
        setting.setPackageRedundancyGoal(getCIMPropertyValue(settingInstance,
                PACKAGEREDUNDANCYGOAL));
        setting.setPackageRedundancyMax(getCIMPropertyValue(settingInstance,
                PACKAGEREDUNDANCYMAX));
        setting.setPackageRedundancyMin(getCIMPropertyValue(settingInstance,
                PACKAGEREDUNDANCYMIN));
        setStoragePoolReference(settingInstance, setting, device);
        _poolsettingList.add(setting);
    }

    /**
     * set PoolSetting's corresponding StoragePool.
     * 
     * @param settingInstance
     * @param setting
     * 
     */
    private void setStoragePoolReference(
            CIMInstance settingInstance, StoragePoolSetting setting, StorageSystem device) {
        // CLARiiON+APM00062103520+P+F+0+C+0001
        String[] idformat = null;
        try {
            idformat = getCIMPropertyValue(settingInstance, INSTANCEID).toString().split(
                    "\\+");
            URIQueryResultList result = new URIQueryResultList();
            _dbClient.queryByConstraint(AlternateIdConstraint.Factory
                    .getStoragePoolByNativeGuidConstraint(NativeGUIDGenerator
                            .generateNativeGuid(device, idformat[6],
                                    NativeGUIDGenerator.POOL)), result);
            if (result.iterator().hasNext()) {
                URI poolID = result.iterator().next();
                setting.setStoragePool(poolID);
            }
        } catch (Exception e) {
            _logger.warn("Storage Pool not Found :{}", idformat[6].toString());
        }
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
     * isStoragePoolSetting present in DB.
     * 
     * @param settingInstance
     * @return
     * @throws IOException
     */
    private StoragePoolSetting checkStoragePoolSettingExistsInDB(
            CIMInstance settingInstance) throws IOException {
        StoragePoolSetting setting = null;
        @SuppressWarnings("deprecation")
        List<URI> settingURIs = _dbClient.queryByConstraint(AlternateIdConstraint.Factory
                .getStoragePoolSettingByIDConstraint(settingInstance.getPropertyValue(
                        INSTANCEID).toString()));
        if (!settingURIs.isEmpty()) {
            setting = _dbClient.queryObject(StoragePoolSetting.class, settingURIs.get(0));
        }
        return setting;
    }

    @Override
    protected void setPrerequisiteObjects(List<Object> args)
            throws BaseCollectionException {
        _args = args;
    }

    /**
     * For each AutoTierPolicy in the the given lists, asociated them to the pool
     * 
     * @param pool [in] - StoragePool object
     * @param newList [in] - List of AutoTierPolicies that were not found in the database
     * @param updateList [in] - List of AutoTierPolicies that were already in the database
     */
    private void attachPoolsToSLOBasedAutoTierPolicies(StoragePool pool, List<AutoTieringPolicy> newList,
            List<AutoTieringPolicy> updateList) {
        if (newList != null) {
            for (AutoTieringPolicy newPolicy : newList) {
                newPolicy.addPool(pool.getId().toString());
            }
        }
        if (updateList != null) {
            for (AutoTieringPolicy updatePolicy : updateList) {
                updatePolicy.addPool(pool.getId().toString());
            }
        }
    }

    /**
     * Persist the AutoTieringPolicies found in the passed in lists. Perform some checks
     * and update policies that may have been removed.
     * 
     * @param storageSystem [in] - StorageSystem that the AutoTierPolicies will belong to
     * @param sloNames [in] - SLO policy native GUID names found
     * @param newList [in] - List of AutoTierPolicies that were not found in the database
     * @param updateList [in] - List of AutoTierPolicies that were already in the database
     * @throws IOException
     */
    private void processSLOBasedAutoTierPolicies(StorageSystem storageSystem, Set<String> sloNames,
            List<AutoTieringPolicy> newList, List<AutoTieringPolicy> updateList)
            throws IOException {
        if (newList != null) {
            _dbClient.createObject(newList);
        }
        if (updateList != null) {
            _dbClient.updateAndReindexObject(updateList);
        }
    }

    /**
     * Validate that this represents a SLO setting. If it does, then process it as an AutoTierPolicy
     * 
     * If the CIMInstance has a its EMCFastSetting populated, then this is a SLO policy
     * based StoragePoolSetting. We will extract the SLOName, Workload, and Average response time.
     * These will be populated in the AutoTieringPolicy object (if one needs to be created).
     * 
     * Updates 'sloNames' list
     * 
     * @param storageSystem [in] - StorageSystem that the setting belongs to
     * @param settingInstance [in] - Should be an instance of Symm_StoragePoolSetting.
     */
    private void processVMAX3SLO(StorageSystem storageSystem, CIMInstance settingInstance) {
        String emcFastSetting = (String) settingInstance.getPropertyValue(Constants.EMC_FAST_SETTING);
        if (!Strings.isNullOrEmpty(emcFastSetting)) {
            String slo = (String) settingInstance.getPropertyValue(Constants.EMC_SLO);
            Float avgResponseTimeValue = (Float) settingInstance.getPropertyValue(Constants.EMC_AVG_RESPONSE_TIME);
            if (!Strings.isNullOrEmpty(slo)) {
                String avgResponseTime = Constants.NOT_AVAILABLE;
                if (!checkForNull(avgResponseTimeValue)) {
                    avgResponseTime = avgResponseTimeValue.toString();
                }
                String workload = (String) settingInstance.getPropertyValue(Constants.EMC_WORKLOAD);
                workload = Strings.isNullOrEmpty(workload) ? Constants.NONE : workload;
                String sloName = generateSLOPolicyName(slo, workload, avgResponseTime);
                sloNames.add(sloName);
                // This is a SLO policy setting. We can use this to create/update an AutoTieringPolicy
                String sloID = NativeGUIDGenerator.generateAutoTierPolicyNativeGuid(storageSystem.getNativeGuid(),
                        sloName, NativeGUIDGenerator.SLO_POLICY);

                AutoTieringPolicy autoTieringPolicy = getAutoTieringPolicyByNameFromDB(sloID, _dbClient);
                createOrUpdateSLOBasedAutoTierPolicy(storageSystem, autoTieringPolicy, sloID, sloName, slo,
                        workload, avgResponseTime);
            } else {
                _logger.warn(String.format("Setting %s had non-null EMCFastSetting property = '%s', " +
                        "but its EMCSLO and/or EMCApproxAverageResponseTime property is null/empty.",
                        settingInstance.getObjectPath().toString(), emcFastSetting));
            }
        }
    }

    /**
     * Create or update an AutoTieringPolicy object with the passed parameters.
     * 
     * Updates 'newSLOList' and 'updateSLOList'
     * 
     * @param storageSystem [in] - StorageSystem that the AutoTierPolicy will belong to
     * @param policy [in] - If null, implies we have to create a new one, otherwise it's an update
     * @param sloID [in] - Native ID constructed based on the slo and workload
     * @param sloName [in] - SLO + Workload
     * @param avgResponseTime [in] - Average Expected Response time for the SLO + Workload @throws IOException
     */
    private void createOrUpdateSLOBasedAutoTierPolicy(StorageSystem storageSystem, AutoTieringPolicy policy,
            String sloID, String sloName, String slo, String workload,
            String avgResponseTime) {
        boolean newPolicy = false;
        if (null == policy) {
            newPolicy = true;
            policy = new AutoTieringPolicy();
            policy.setId(URIUtil.createId(AutoTieringPolicy.class));
            policy.setStorageSystem(storageSystem.getId());
            policy.setNativeGuid(sloID);
            policy.setSystemType(storageSystem.getSystemType());
            policy.setVmaxSLO(slo);
            policy.setVmaxWorkload(workload);
        }

        policy.setLabel(sloName);
        policy.setPolicyName(sloName);
        policy.setPolicyEnabled(true);
        if (!Constants.NOT_AVAILABLE.equalsIgnoreCase(avgResponseTime)) {
            policy.setAvgExpectedResponseTime(Double.parseDouble(avgResponseTime));
        }
        // SLO is on V3 VMAX, which only supports Thin
        policy.setProvisioningType(AutoTieringPolicy.ProvisioningType.ThinlyProvisioned.name());
        if (newPolicy) {
            newSLOList.add(policy);
        } else {
            updateSLOList.add(policy);
        }
    }

}
