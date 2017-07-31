/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.utils.attrmatchers;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;

import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.AutoTieringPolicy;
import com.emc.storageos.db.client.model.DiscoveredDataObject.Type;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.util.StorageDriverManager;
import com.emc.storageos.vnxe.models.StorageResource;
import com.emc.storageos.volumecontroller.AttributeMatcher;
import com.google.common.base.Joiner;

/**
 * FastPolicyAttrMatcher is responsible to match all the pools matching FAST policy name
 * given CoS.
 * 
 */
public class AutoTieringPolicyMatcher extends AttributeMatcher {

    private static final Logger _logger = LoggerFactory.getLogger(AutoTieringPolicyMatcher.class);

    @Override
    protected List<StoragePool> matchStoragePoolsWithAttributeOn(List<StoragePool> pools, Map<String, Object> attributeMap,
            StringBuffer errorMessage) {
        String autoTieringPolicyName = attributeMap.get(Attributes.auto_tiering_policy_name.toString()).toString();

        _logger.info("Pools Matching Auto Tiering Policy name attribute {} Started:{}", autoTieringPolicyName, Joiner
                .on("\t").join(getNativeGuidFromPools(pools)));
        // defensive copy
        List<StoragePool> filteredPoolList = new ArrayList<StoragePool>();
        Set<String> fastPolicyPools = null;

        StringSet deviceTypes = (StringSet) attributeMap.get(Attributes.system_type.toString());

        /*
         * If matcher is called during provisioning, then run ranking Algorithm to get
         * vnx matched pool. The reason for moving ranking algorithm to provisioning is,
         * ranking algorithm was basically designed to get a single best matching Pool, which
         * can be used in provisioning.Hence, during vpool creation, if we run ranking algorithm,
         * we may end up in a single Pool in matched Pools List, which is not desired.
         */

        // called during implicit Pool matching
        // deviceTypes other than vmax or vnxblock's control will not come to this matcher itself.
        if (deviceTypes.contains(VirtualPool.SystemType.vnxblock.toString())) {
            /** return pools whose Storage System is Auto Tiering enabled */
            filteredPoolList = getAutoTieringPoolsOnVnx(pools);
        } else if (deviceTypes.contains(VirtualPool.SystemType.vmax.toString())) {
            fastPolicyPools = getAutoTieringPoolsOnVMAX(autoTieringPolicyName, attributeMap);
            Iterator<StoragePool> poolIterator = pools.iterator();
            while (poolIterator.hasNext()) {
                StoragePool pool = poolIterator.next();
                // check whether pool matching with vpool or not.
                // if it doesn't match remove it from all pools.
                if (fastPolicyPools.contains(pool.getId().toString())) {
                    filteredPoolList.add(pool);
                } else {
                    _logger.info("Ignoring pool {} as it doesn't belongs to FAST policy.", pool.getNativeGuid());
                }
            }
        } else if (deviceTypes.contains(VirtualPool.SystemType.hds.name())) {
            filteredPoolList = getAutoTieringPoolsOnHDS(autoTieringPolicyName, attributeMap, pools);
        } else if (deviceTypes.contains(VirtualPool.SystemType.vnxe.toString())
                   || deviceTypes.contains(VirtualPool.SystemType.unity.toString())) {
            filteredPoolList = getPoolsWithAutoTieringEnabled(pools);
        } else {
            Iterator<String> deviceTypesIter = deviceTypes.iterator();
            if (deviceTypesIter.hasNext()) {
                String deviceType = deviceTypes.iterator().next();
                StorageDriverManager storageDriverManager = StorageDriverManager.getInstance();
                if (storageDriverManager.isDriverManaged(deviceType)) {
                    filteredPoolList = getAutoTieringPoolsOnExternalSystem(autoTieringPolicyName, attributeMap, pools);
                }
            }
        }
        _logger.info("Pools Matching Auto Tiering name Ended:{}",
                Joiner.on("\t").join(getNativeGuidFromPools(filteredPoolList)));
        if (CollectionUtils.isEmpty(filteredPoolList)) {
            errorMessage.append(String.format("No matching storage pool found with auto tiering policy %s. ", autoTieringPolicyName));
            _logger.error(errorMessage.toString());
        }

        return filteredPoolList;
    }

    @Override
    public Map<String, Set<String>> getAvailableAttribute(List<StoragePool> neighborhoodPools,
            URI vArrayId) {
        try {
            Set<String> policyNameSet = new HashSet<String>();
            Map<String, Set<String>> availableAttrMap = new HashMap<String, Set<String>>(1);
            for (StoragePool pool : neighborhoodPools) {
                // First verify whether system has autoTieringEnabled.
                // If FAST is enabled on System then check Whether pool has
                // policies with FAST enabled.
                StorageSystem system = _objectCache.queryObject(StorageSystem.class, pool.getStorageDevice());
                if (null != system && system.getAutoTieringEnabled()) {
                    if (Type.vmax.toString().equalsIgnoreCase(system.getSystemType())) {
                        policyNameSet.addAll(fetchFastPoliciesForVMAX(pool.getId()));
                    } else if (Type.vnxblock.toString().equalsIgnoreCase(system.getSystemType())) {
                        policyNameSet.add(AutoTieringPolicy.VnxFastPolicy.DEFAULT_START_HIGH_THEN_AUTOTIER.toString());
                        policyNameSet.add(AutoTieringPolicy.VnxFastPolicy.DEFAULT_AUTOTIER.toString());
                        policyNameSet.add(AutoTieringPolicy.VnxFastPolicy.DEFAULT_HIGHEST_AVAILABLE.toString());
                        policyNameSet.add(AutoTieringPolicy.VnxFastPolicy.DEFAULT_LOWEST_AVAILABLE.toString());
                        policyNameSet.add(AutoTieringPolicy.VnxFastPolicy.DEFAULT_NO_MOVEMENT.toString());
                    } else if (Type.vnxe.toString().equalsIgnoreCase(system.getSystemType())
                                || Type.unity.toString().equalsIgnoreCase(system.getSystemType())) {
                        policyNameSet.add(StorageResource.TieringPolicyEnum.AUTOTIER_HIGH.name());
                        policyNameSet.add(StorageResource.TieringPolicyEnum.AUTOTIER.name());
                        policyNameSet.add(StorageResource.TieringPolicyEnum.HIGHEST.name());
                        policyNameSet.add(StorageResource.TieringPolicyEnum.LOWEST.name());
                        policyNameSet.add(StorageResource.TieringPolicyEnum.MIXED.name());
                        policyNameSet.add(StorageResource.TieringPolicyEnum.NO_DATA_MOVEMENT.name());
                    } else if (Type.hds.name().equalsIgnoreCase(system.getSystemType())) {
                        policyNameSet.addAll(fetchTieringPoliciesForHDS(system));
                    }
                }
            }
            if (!policyNameSet.isEmpty()) {
                availableAttrMap.put(Attributes.fast.toString(), policyNameSet);
                return availableAttrMap;
            }
        } catch (Exception e) {
            _logger.error("Exception occurred while getting available attributes using AutoTieringPolicy Matcher.", e);
        }
        return Collections.emptyMap();
    }

    @Override
    protected boolean isAttributeOn(Map<String, Object> attributeMap) {
        boolean status = false;
        if (null != attributeMap && attributeMap.containsKey(Attributes.auto_tiering_policy_name.toString())
                && !((String) attributeMap.get(Attributes.auto_tiering_policy_name.toString())).equalsIgnoreCase("NONE")) {
            StringSet deviceTypes = (StringSet) attributeMap.get(Attributes.system_type.toString());
            if (deviceTypes.contains(VirtualPool.SystemType.vmax.toString()) ||
                    deviceTypes.contains(VirtualPool.SystemType.vnxblock.toString()) ||
                    deviceTypes.contains(VirtualPool.SystemType.vnxe.toString()) ||
                    deviceTypes.contains(VirtualPool.SystemType.unity.toString()) ||
                    deviceTypes.contains(VirtualPool.SystemType.hds.name())) {
                status = true;
            }
        }
        return status;
    }

    /**
     * Get all storagepools associated with all the policies based on the policy name passed.
     * 
     * @param policyName
     * @return
     */
    private Set<String> getAutoTieringPoolsOnVMAX(String policyName, Map<String, Object> attributeMap) {
        Set<String> fastPolicyPools = new HashSet<String>();
        URIQueryResultList result = getAutoTierPolicies(attributeMap, policyName);
        Iterator<URI> iterator = result.iterator();
        while (iterator.hasNext()) {
            AutoTieringPolicy policy = _objectCache.queryObject(AutoTieringPolicy.class,
                    iterator.next());
            if (isValidAutoTieringPolicy(policy)
                    && isAutoTieringEnabledOnStorageSystem(policy.getStorageSystem())
                    && doesGivenProvisionTypeMatchFastPolicy(
                            attributeMap.get(Attributes.provisioning_type.toString()).toString(), policy)) {
                if (null != policy.getPools()) {
                    fastPolicyPools.addAll(policy.getPools());
                }
            }
        }
        return fastPolicyPools;
    }

    /**
     * return Pools, whose associated StorageSystem is Fast Enabled.
     * 
     * @param pools
     * @return
     */
    private List<StoragePool> getAutoTieringPoolsOnVnx(List<StoragePool> pools) {
        List<StoragePool> filteredPools = new ArrayList<StoragePool>();
        // Unique Policy names field in vPool will not be set for VNX. (CTRL-8911)
        // vPool will have policy name, not the policy's nativeGuid
        for (StoragePool pool : pools) {
            if (isAutoTieringEnabledOnStorageSystem(pool.getStorageDevice())) {
                filteredPools.add(pool);
            }
        }
        return filteredPools;
    }

    /**
     * return Pools, whose storagepool is tiering capable or not.
     * 
     * @param pools
     * @return
     */
    private List<StoragePool> getPoolsWithAutoTieringEnabled(List<StoragePool> pools) {
        List<StoragePool> filteredPools = new ArrayList<StoragePool>();
        for (StoragePool pool : pools) {
            if (pool.getAutoTieringEnabled()) {
                filteredPools.add(pool);
            }
        }
        return filteredPools;
    }

    /**
     * Match the pools based on the policyName selected during vpool create/update.
     * 
     * @param policyName - Vpool PolicyName to match.
     * @param attributeMap - AttributeMap.
     * @param pools - Pools to match.
     * @return - list of matched pools.
     */
    private List<StoragePool> getAutoTieringPoolsOnHDS(String policyName, Map<String, Object> attributeMap,
            List<StoragePool> pools) {
        List<StoragePool> filteredPools = new ArrayList<StoragePool>();
        List<URI> systemURIs = new ArrayList<URI>();
        URIQueryResultList result = getAutoTierPolicies(attributeMap, policyName);
        // Iterate through the policies
        Iterator<URI> iterator = result.iterator();
        while (iterator.hasNext()) {
            AutoTieringPolicy policy = _objectCache.queryObject(AutoTieringPolicy.class, iterator.next());
            // If policy is tiering capable.
            if (policy.getPolicyEnabled() && !systemURIs.contains(policy.getStorageSystem())) {
                systemURIs.add(policy.getStorageSystem());
            }
        }
        // process the pools and check if the pool's system matches with the policy system.
        for (StoragePool pool : pools) {
            if (pool.getAutoTieringEnabled() && systemURIs.contains(pool.getStorageDevice())) {
                filteredPools.add(pool);
            }
        }
        return filteredPools;
    }

    private boolean isAutoTieringEnabledOnStorageSystem(URI storageSystemURI) {
        StorageSystem system = _objectCache.queryObject(StorageSystem.class,
                storageSystemURI);
        // if fast is disabled then skip it too.
        if (null != system && system.getAutoTieringEnabled()) {
            return true;
        }
        return false;
    }

    private boolean doesGivenProvisionTypeMatchFastPolicy(
            String provisioningtype, AutoTieringPolicy policy) {
        if (null == provisioningtype || VirtualPool.ProvisioningType.NONE.toString().equalsIgnoreCase(provisioningtype)) {
            return true;
        }
        if (AutoTieringPolicy.ProvisioningType.All.toString().equalsIgnoreCase(
                policy.getProvisioningType())) {
            return true;
        }
        if (provisioningtype.equalsIgnoreCase(VirtualPool.ProvisioningType.Thick.toString())
                && AutoTieringPolicy.ProvisioningType.ThicklyProvisioned.toString()
                        .equalsIgnoreCase(policy.getProvisioningType())) {
            return true;
        }
        if (provisioningtype.equalsIgnoreCase(VirtualPool.ProvisioningType.Thin.toString())
                && AutoTieringPolicy.ProvisioningType.ThinlyProvisioned.toString()
                        .equalsIgnoreCase(policy.getProvisioningType())) {
            return true;
        }
        return false;
    }

    private boolean isValidAutoTieringPolicy(AutoTieringPolicy policy) {
        if (null == policy) {
            return false;
        }
        if (!policy.getPolicyEnabled()) {
            return false;
        }
        return true;
    }

    /**
     * Check whether tiering is enabled on the given system or not.
     * If true: fetch & return all tiering policies of the system.
     * If false: return nothing.
     * 
     * @param device : device of the pool.
     * @return
     */
    private Set<String> fetchTieringPoliciesForHDS(StorageSystem device) {
        Set<String> policyNameSet = new HashSet<String>();
        URIQueryResultList tieringPolicyResult = new URIQueryResultList();
        _objectCache.getDbClient().queryByConstraint(ContainmentConstraint.Factory
                .getStorageDeviceFASTPolicyConstraint(device.getId()),
                tieringPolicyResult);
        Iterator<URI> tieringPolicyItr = tieringPolicyResult.iterator();
        while (tieringPolicyItr.hasNext()) {
            AutoTieringPolicy tierPolicy = _objectCache.queryObject(
                    AutoTieringPolicy.class, tieringPolicyItr.next());
            if (null != tierPolicy && tierPolicy.getPolicyEnabled()) {
                policyNameSet.add(tierPolicy.getPolicyName());
            }
        }
        return policyNameSet;
    }

    /**
     * Verify whether policies associated with pool are FAST enabled or not for VMAX storage systems.
     * true: pool has FAST enabled policies.
     * false: pool doesn't support FAST.
     * 
     * @param poolID : ID of the Pool.
     * @return
     */
    private Set<String> fetchFastPoliciesForVMAX(URI poolID) {
        Set<String> policyNameSet = new HashSet<String>();
        URIQueryResultList fastPolicyResult = new URIQueryResultList();
        _objectCache.getDbClient().queryByConstraint(AlternateIdConstraint.Factory.getPoolFASTPolicyConstraint(poolID.toString()),
                fastPolicyResult);
        Iterator<URI> fastPolicyItr = fastPolicyResult.iterator();
        while (fastPolicyItr.hasNext()) {
            AutoTieringPolicy tierPolicy = _objectCache.queryObject(AutoTieringPolicy.class, fastPolicyItr.next());
            if (null != tierPolicy && tierPolicy.getPolicyEnabled()) {
                policyNameSet.add(tierPolicy.getPolicyName());
            }
        }
        return policyNameSet;
    }

    /**
     * Fetches the AutoTieringPolicies from the DB for a given policyName and uniquepolicyname selection.
     * 
     * @param attributeMap
     * @param policyName
     * @return
     */
    private URIQueryResultList getAutoTierPolicies(Map<String, Object> attributeMap, String policyName) {
        URIQueryResultList result = new URIQueryResultList();
        boolean uniquePolicyNames = (boolean) attributeMap.get(Attributes.unique_policy_names.toString());
        // check if pool fast policy name is not
        if (!uniquePolicyNames) {
            _objectCache.getDbClient().queryByConstraint(
                    AlternateIdConstraint.Factory.getAutoTieringPolicyByNativeGuidConstraint(policyName), result);
        } else {
            _objectCache.getDbClient().queryByConstraint(
                    AlternateIdConstraint.Factory.getFASTPolicyByNameConstraint(policyName),
                    result);
        }
        return result;
    }
    
    /**
     * Filter the passed list of storage pools to only those that match the passed auto tiering policy.
     * 
     * @param policyName The auto tiering policy name.
     * @param attributeMap The matcher attribute map.
     * @param pools The complete list of pools to be filtered.
     * 
     * @return The filtered list of pools matching the policy with the passed name.
     */
    private List<StoragePool> getAutoTieringPoolsOnExternalSystem(String policyName, Map<String, Object> attributeMap, List<StoragePool> pools) {
        Set<String> policyPools = new HashSet<String>();
        URIQueryResultList result = getAutoTierPolicies(attributeMap, policyName);
        Iterator<URI> iterator = result.iterator();
        while (iterator.hasNext()) {
            AutoTieringPolicy policy = _objectCache.queryObject(AutoTieringPolicy.class, iterator.next());
            if (isValidAutoTieringPolicy(policy)
                    && isAutoTieringEnabledOnStorageSystem(policy.getStorageSystem())
                    && doesGivenProvisionTypeMatchFastPolicy(attributeMap.get(
                            Attributes.provisioning_type.toString()).toString(), policy)) {
                if (null != policy.getPools()) {
                    policyPools.addAll(policy.getPools());
                }
            }
        }
        
        List<StoragePool> filteredPools = new ArrayList<StoragePool>();
        Iterator<StoragePool> poolIterator = pools.iterator();
        while (poolIterator.hasNext()) {
            StoragePool pool = poolIterator.next();
            // check whether pool matching with vpool or not.
            // if it doesn't match remove it from all pools.
            if (policyPools.contains(pool.getId().toString())) {
                filteredPools.add(pool);
            } else {
                _logger.info("Ignoring pool {} as it doesn't belongs to FAST policy.", pool.getNativeGuid());
            }
        }

        return filteredPools;
    }
}
