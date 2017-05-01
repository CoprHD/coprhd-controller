/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.utils;

import java.net.URI;
import java.util.Map;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.PerformanceParams;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.VolumeTopology.VolumeTopologyRole;
import com.emc.storageos.model.block.BlockPerformanceParamsMap;

/**
 * PerformanceParams utility class.
 */
public class PerformanceParamsUtils {

    /**
     * Get the performance parameters for the passed role.
     * 
     * @param performanceParamsMap A map of performance parameter URIs keyed VolumeTopologyRole.
     * @param role A VolumeTopologyRole.
     * @param dbClient A reference to a DbClient.
     * 
     * @return A reference to a PerformanceParams instance or null.
     */
    public static PerformanceParams getPerformanceParamsForRole(Map<String, URI> performanceParamsMap,
            VolumeTopologyRole role, DbClient dbClient) {
        PerformanceParams performanceParams = null;
        if (performanceParamsMap != null) {
            URI performanceParamsURI = performanceParamsMap.get(role.name());
            if (performanceParamsURI != null) {
                performanceParams = dbClient.queryObject(PerformanceParams.class, performanceParamsURI);
            }
        }
        return performanceParams;
    }    

    /**
     * Get the performance parameters for the passed role.
     * 
     * @param performanceParamsMap A map of performance parameter URIs by VolumeTopologyRole
     * @param role A VolumeTopologyRole.
     * @param dbClient A reference to a DbClient.
     * 
     * @return A reference to a PerformanceParams instance or null.
     */
    public static PerformanceParams getPerformanceParamsForRole(BlockPerformanceParamsMap performanceParamsMap,
            VolumeTopologyRole role, DbClient dbClient) {
        PerformanceParams performanceParams = null;
        if (performanceParamsMap != null) {
            URI performanceParamsURI = performanceParamsMap.findPerformanceParamsForRole(role.name());
            if (performanceParamsURI != null) {
                performanceParams = dbClient.queryObject(PerformanceParams.class, performanceParamsURI);
            }
        }
        return performanceParams;
    }

    /**
     * Get the auto tiering policy name. If set in the passed performance parameters,
     * return this value, otherwise the value comes from the passed virtual pool.
     * 
     * @param performanceParamsMap A map of performance parameter URIs keyed VolumeTopologyRole.
     * @param role A VolumeTopologyRole.
     * @param vpool A reference to a VirtualPool.
     * @param dbClient A reference to a DbClient.
     * 
     * @return The auto tiering policy name or null if not set.
     */
    public static String getAutoTierinigPolicyName(Map<String, URI> performanceParamsMap, VolumeTopologyRole role, 
            VirtualPool vpool, DbClient dbClient) {
        String autoTieringPolicyName = null;
        PerformanceParams performanceParams = getPerformanceParamsForRole(performanceParamsMap, role, dbClient);
        if (performanceParams != null) {
            // There will always be a value for the auto tiering policy
            // name, so return that value.
            return performanceParams.getAutoTierPolicyName();
        }

        // If here, use the value from the vpool.
        if (vpool != null) {
            autoTieringPolicyName = vpool.getAutoTierPolicyName();
        }

        return autoTieringPolicyName;
    }
    
    /**
     * Get the thin volume pre-allocation percentage. If set in the passed performance 
     * parameters, return this value, otherwise the value comes from the passed virtual pool.
     * 
     * @param performanceParamsMap A map of performance parameter URIs by VolumeTopologyRole
     * @param role A VolumeTopologyRole.
     * @param vpool A reference to a VirtualPool.
     * @param dbClient A reference to a DbClient.
     * 
     * @return The thin volume pre-allocation percentage or 0 if not set.
     */
    public static Integer getThinVolumePreAllocPercentage(BlockPerformanceParamsMap performanceParamsMap, VolumeTopologyRole role,
            VirtualPool vpool, DbClient dbClient) {
        PerformanceParams performanceParams = getPerformanceParamsForRole(performanceParamsMap, role, dbClient);
        if (performanceParams != null) {
            // There will always be a value for the thin volume pre-allocation
            // percentage, so return that value.
            return performanceParams.getThinVolumePreAllocationPercentage();
        }

        // If here, use the value from virtual pool.
        Integer thinVolumePreAllocPercentage = null;
        if (vpool != null) {
            thinVolumePreAllocPercentage = vpool.getThinVolumePreAllocationPercentage();
        } 

        return thinVolumePreAllocPercentage != null ? thinVolumePreAllocPercentage : 0;
    }

    /**
     * Get the deduplication capable setting. If set in the passed performance 
     * parameters, return this value, otherwise the value comes from the passed
     * virtual pool.
     * 
     * @param performanceParamsMap A map of performance parameter URIs by VolumeTopologyRole
     * @param role A VolumeTopologyRole.
     * @param vpool A reference to a VirtualPool.
     * @param dbClient A reference to a DbClient.
     * 
     * @return True is set, False otherwise.
     */
    public static Boolean getIsDedupCapable(BlockPerformanceParamsMap performanceParamsMap, VolumeTopologyRole role,
            VirtualPool vpool, DbClient dbClient) {
        Boolean dedupCapable = Boolean.FALSE;
        PerformanceParams performanceParams = getPerformanceParamsForRole(performanceParamsMap, role, dbClient);
        if (performanceParams != null) {
            // There will always be a value for dedup capable, so return that value.
            return performanceParams.getDedupCapable();
        }

        // If here, use the value from virtual pool.
        dedupCapable = vpool.getDedupCapable();

        return dedupCapable != null ? dedupCapable : Boolean.FALSE;
    }
}
