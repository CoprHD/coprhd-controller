/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.utils.attrmatchers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;

import com.emc.storageos.db.client.model.AutoTieringPolicy;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StorageTier;
import com.emc.storageos.db.client.model.StorageTier.SupportedTiers;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.volumecontroller.AttributeMatcher;
import com.google.common.base.Joiner;

/**
 * VNXBlockAutoTieringPolicyMatcher is responsible to match all the pools matching FAST policy name
 * given CoS. This is applicable only to VNX.
 * During provisioning, we should pick the right pool based on the ranking algorithm.
 * 
 */
public class VNXBlockAutoTieringPolicyMatcher extends AttributeMatcher {
    private static final Logger _logger = LoggerFactory
            .getLogger(VNXBlockAutoTieringPolicyMatcher.class);

    @Override
    protected boolean isAttributeOn(Map<String, Object> attributeMap) {
        if (null != attributeMap
                && attributeMap.containsKey(Attributes.auto_tiering_policy_name.toString())
                && checkVNXBlockPolicyNames(attributeMap)) {
            return true;
        }
        return false;
    }

    /**
     * Return true if the policy is matching with the default VNX policy names.
     * 
     * @return
     */
    private boolean checkVNXBlockPolicyNames(Map<String, Object> attributeMap) {
        String autoTierPolicyName = attributeMap.get(Attributes.auto_tiering_policy_name.toString()).toString();
        _logger.info("Policy Name : {}", autoTierPolicyName);
        // Unique Policy names field in vPool will not be set for VNX. (CTRL-8911)
        // vPool will have policy name, not the policy's nativeGuid

        if (AutoTieringPolicy.VnxFastPolicy.DEFAULT_NO_MOVEMENT.toString().equalsIgnoreCase(autoTierPolicyName)
                || AutoTieringPolicy.VnxFastPolicy.DEFAULT_AUTOTIER.toString().equalsIgnoreCase(autoTierPolicyName)
                || AutoTieringPolicy.VnxFastPolicy.DEFAULT_HIGHEST_AVAILABLE.toString().equalsIgnoreCase(autoTierPolicyName)
                || AutoTieringPolicy.VnxFastPolicy.DEFAULT_LOWEST_AVAILABLE.toString().equalsIgnoreCase(autoTierPolicyName)
                || AutoTieringPolicy.VnxFastPolicy.DEFAULT_START_HIGH_THEN_AUTOTIER.toString().equalsIgnoreCase(autoTierPolicyName)) {
            return true;
        }
        return false;
    }

    @Override
    protected List<StoragePool> matchStoragePoolsWithAttributeOn(List<StoragePool> pools, Map<String, Object> attributeMap,
            StringBuffer errorMessage) {
        _logger.info("Auto Tiering Policy Matcher Started :" + Joiner.on("\t").join(getNativeGuidFromPools(pools)));

        String autoTieringPolicyName = attributeMap.get(Attributes.auto_tiering_policy_name.toString()).toString();
        _logger.info("Policy Name : {}", autoTieringPolicyName);

        /*
         * If matcher is called during provisioning, then run ranking Algorithm
         * to get vnx matched pool. The reason for moving ranking algorithm to
         * provisioning is, ranking algorithm was basically designed to get a
         * single best matching Pool, which can be used in provisioning.Hence,
         * during vpool creation, if we run ranking algorithm, we may end up in a
         * single Pool in matched Pools List, which is not desired.
         */

        /** Run Ranking Algorithm to get matched Pools on VNX */
        List<StoragePool> filteredPoolList = returnMatchedVNXPoolsForGivenAutoTieringPolicy(pools, autoTieringPolicyName);

        if (CollectionUtils.isEmpty(filteredPoolList)) {
            errorMessage
                    .append(String.format("No matching storage pools found for the VNX Auto-tiering policy %s. ", autoTieringPolicyName));
            _logger.info(errorMessage.toString());
        }

        return filteredPoolList;
    }

    /**
     * Ranking Algorithm to find matched Pools
     * 
     * NO_DATA_MOVEMENT : return all the Pools
     * AUTO_TIER :
     * Find Pools which contains more than one tier, if not found return all the Pools
     * HIGHEST_TIER:
     * Find Pools which contains max tierPercentage looping through each Derive Type starting from
     * SSD,FC,SAS,NL_SAS,SATA. If a pool is being found with max tier Percentage for a given drive type,
     * return the matched Pool back.If more than one pool is having the same max tier percentage, then pass
     * matching pools to find max tier Percentage for the next Drive Type, and continues till we get a single
     * matched Pool or the processing of all drive Types is done.
     * LOWEST_TIER:
     * The same logic works , by passing drive Types in reverse order.
     * 
     * @param pools
     * @param auto_tier_policy_name
     * @return
     */
    private List<StoragePool> returnMatchedVNXPoolsForGivenAutoTieringPolicy(
            List<StoragePool> pools, String auto_tier_policy_name) {

        if (AutoTieringPolicy.VnxFastPolicy.DEFAULT_NO_MOVEMENT.toString()
                .equalsIgnoreCase(auto_tier_policy_name)) {
            _logger.info("Auto Tiering {} Matcher Ended  {} :",
                    auto_tier_policy_name,
                    Joiner.on("\t").join(getNativeGuidFromPools(pools)));
            return pools;
        }

        if (AutoTieringPolicy.VnxFastPolicy.DEFAULT_AUTOTIER.toString().equalsIgnoreCase(auto_tier_policy_name) ||
                AutoTieringPolicy.VnxFastPolicy.DEFAULT_START_HIGH_THEN_AUTOTIER.toString().equalsIgnoreCase(auto_tier_policy_name)) {
            return getMatchingPoolsForVNXAutoTier(pools, auto_tier_policy_name);
        }

        return runRankingAlgorithmToGetMatchedPoolsForHighAndLowTiers(
                auto_tier_policy_name, pools);
    }

    private List<StoragePool> runRankingAlgorithmToGetMatchedPoolsForHighAndLowTiers(
            String fastPolicyName, List<StoragePool> initialPools) {
        List<StoragePool> percentageFilteredPool = new ArrayList<StoragePool>(
                initialPools);
        List<SupportedTiers> tierTypes = getTierTypeOrderBasedOnPolicy(fastPolicyName);

        // Pools got based on ordered drive Type
        for (SupportedTiers tierType : tierTypes) {
            // max percentage logic
            percentageFilteredPool = getAvailablePoolWithMaxUtilizationPercentage(
                    percentageFilteredPool, tierType.toString());
            if (percentageFilteredPool.size() == 1) {
                break;
            }
        }
        _logger.info("Auto Tiering {} Matcher Ended  {} :", fastPolicyName,
                Joiner.on("\t").join(getNativeGuidFromPools(percentageFilteredPool)));
        return percentageFilteredPool;
    }

    private List<SupportedTiers> getTierTypeOrderBasedOnPolicy(
            String autoTierPolicy) {
        if (AutoTieringPolicy.VnxFastPolicy.DEFAULT_HIGHEST_AVAILABLE.toString()
                .equalsIgnoreCase(autoTierPolicy)) {
            return Arrays.asList(StorageTier.SupportedTiers.SSD,
                    StorageTier.SupportedTiers.FC,
                    StorageTier.SupportedTiers.SAS,
                    StorageTier.SupportedTiers.SATA);
        } else if (AutoTieringPolicy.VnxFastPolicy.DEFAULT_LOWEST_AVAILABLE.toString()
                .equalsIgnoreCase(autoTierPolicy)) {
            return Arrays.asList(StorageTier.SupportedTiers.SATA,
                    StorageTier.SupportedTiers.SAS,
                    StorageTier.SupportedTiers.FC,
                    StorageTier.SupportedTiers.SSD);
        }
        return Collections.EMPTY_LIST;
    }

    private List<StoragePool> getAvailablePoolWithMaxUtilizationPercentage(
            List<StoragePool> initialPools, String driveType) {
        _logger.info(
                "Pools Sent for finding Max Utilization Percentage for Drive Type: {} --> {}",
                driveType, Joiner.on("\t").join(getNativeGuidFromPools(initialPools)));
        int maxPercentage = 0;
        List<StoragePool> filteredPoolList = new ArrayList<StoragePool>();
        for (StoragePool pool : initialPools) {
            StringMap tierUtilizationPercentage = pool.getTierUtilizationPercentage();
            if (tierUtilizationPercentage == null) {
                continue;
            }
            if (null == tierUtilizationPercentage.get(driveType)) {
                continue;
            }
            if (Integer.parseInt(tierUtilizationPercentage.get(driveType)) > maxPercentage) {
                maxPercentage = Integer
                        .parseInt(tierUtilizationPercentage.get(driveType));
                filteredPoolList.clear();
            }
            if (Integer.parseInt(tierUtilizationPercentage.get(driveType)) == maxPercentage) {
                filteredPoolList.add(pool);
            }
        }
        if (filteredPoolList.isEmpty()) {
            _logger.info(
                    " None of the Pools matching the Drive Type {}-->returning all Pools{}:",
                    driveType, Joiner.on("\t").join(getNativeGuidFromPools(initialPools)));
            return initialPools;
        }
        _logger.info(" Pools matching the Drive Type {}-->returning MatchedPools{}:",
                driveType, Joiner.on("\t").join(getNativeGuidFromPools(filteredPoolList)));
        return filteredPoolList;
    }

    private List<StoragePool> getMatchingPoolsForVNXAutoTier(
            List<StoragePool> initialPools, String autoTierPolicyName) {
        List<StoragePool> filteredPoolList = new ArrayList<StoragePool>();
        for (StoragePool pool : initialPools) {
            if (null != pool.getSupportedDriveTypes()
                    && !pool.getSupportedDriveTypes().isEmpty()) {
                filteredPoolList.add(pool);
            }
        }
        if (filteredPoolList.isEmpty()) {
            _logger.info(
                    "Auto Tiering Policy Matcher Ended : None of the Pools have more than 1 Tier, returning all Pools: {}-->{}",
                    autoTierPolicyName, Joiner.on("\t").join(getNativeGuidFromPools(initialPools)));
            return initialPools;
        }
        _logger.info(
                "Auto Tiering Policy Matcher Ended , and returned pools having more than 1 Tier : {}-->{}",
                autoTierPolicyName, Joiner.on("\t").join(getNativeGuidFromPools(filteredPoolList)));
        return filteredPoolList;
    }
}
