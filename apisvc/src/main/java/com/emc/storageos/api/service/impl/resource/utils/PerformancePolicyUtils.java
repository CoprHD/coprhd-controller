/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.utils;

import java.net.URI;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.util.CollectionUtils;

import com.emc.storageos.api.service.impl.placement.VirtualPoolUtil;
import com.emc.storageos.api.service.impl.resource.ArgValidator;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.PerformancePolicy;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.VolumeTopology;
import com.emc.storageos.db.client.model.VolumeTopology.VolumeTopologyRole;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.model.block.BlockPerformancePolicyMap;
import com.emc.storageos.model.block.VolumeCreatePerformancePolicies;
import com.emc.storageos.volumecontroller.impl.utils.VirtualPoolCapabilityValuesWrapper;

/**
 * PerformancePolicy utility class.
 */
public class PerformancePolicyUtils {

    /**
     * Transform the passed performance policies to an instance of a volume topology
     * that captures the performance policies for the component volumes that comprise
     * the topology.
     * 
     * @param performancePolicies The performance policy overrides or null.
     * 
     * @return A VolumeTopology capturing the performance policies.
     */
    public static VolumeTopology transformPerformancePolicies(VolumeCreatePerformancePolicies performancePolicies) {
        VolumeTopology volumeTopology = null;
        if (performancePolicies != null) {
            // Translate source performance policies to a Map
            Map<VolumeTopologyRole, URI> sourcePerformancePolicies = null;
            Map<URI, Map<VolumeTopologyRole, URI>> sourcePoliciesMap = transformPerformancePolicies(Arrays.asList(performancePolicies.getSourcePolicies()));
            if (!CollectionUtils.isEmpty(sourcePoliciesMap)) {
                // There is always only one when specified as there is only one source
                // in a volume topology.
                sourcePerformancePolicies = sourcePoliciesMap.values().iterator().next();
            } else {
                sourcePerformancePolicies = new HashMap<>();
            }
            
            // Translate copy performance policies.
            Map<URI, Map<VolumeTopologyRole, URI>> copyPerformancePolicies = transformPerformancePolicies(performancePolicies.getCopyPolicies());
            if (copyPerformancePolicies == null) {
                copyPerformancePolicies = new HashMap<>();
            }
            
            // Create the volume topology to capture these performance policy settings.
            volumeTopology = new VolumeTopology(sourcePerformancePolicies, copyPerformancePolicies);
        } else {
            volumeTopology = new VolumeTopology();
        }
        return volumeTopology;
    }

    /**
     * Transform the performance policies client model map instances to java.util.Map instances.
     * 
     * @param performancePoliciesMapList The performance policies to transform.
     * 
     * @return A java.util.Map specifying the performance policies.
     */
    public static Map<URI, Map<VolumeTopologyRole, URI>> transformPerformancePolicies(List<BlockPerformancePolicyMap> performancePoliciesMapList) {
        Map<URI, Map<VolumeTopologyRole, URI>> resultMap = new HashMap<>();
        if (!CollectionUtils.isEmpty(performancePoliciesMapList)) {
            for (BlockPerformancePolicyMap performancePolicyMap : performancePoliciesMapList) {
                if (performancePolicyMap != null) {
                    resultMap.put(performancePolicyMap.getVirtualArray(), transformPerformancePolicies(performancePolicyMap));
                }
            }
        }
        return resultMap;
    }

    /**
     * Transform the performance policy client model map to java.util.Map.
     * 
     * @param performancePoliciesMap The performance policies to transform or null.
     * 
     * @return A java.util.Map specifying the performance policies.
     */
    public static Map<VolumeTopologyRole, URI> transformPerformancePolicies(BlockPerformancePolicyMap performancePoliciesMap) {
        Map<VolumeTopologyRole, URI> resultMap = new HashMap<>();
        if (performancePoliciesMap != null) {
            for (VolumeTopologyRole role : VolumeTopologyRole.values()) {
                URI performancePolicyURI = performancePoliciesMap.findPerformancePolicyForRole(role.toString());
                if (!NullColumnValueGetter.isNullURI(performancePolicyURI)) {
                    resultMap.put(role,  performancePolicyURI);
                }
            }
        }
        return resultMap;
    }

    /**
     * Validates the performance policy for the passed role are valid and active.
     * 
     * @param performancePoliciesMap A map of performance policy URIs by VolumeTopologyRole
     * @param roles A list of VolumeTopologyRoles to validate.
     * @param dbClient A reference to a DbClient.
     */
    public static void validatePerformancePoliciesForRoles(BlockPerformancePolicyMap performancePoliciesMap,
            List<VolumeTopologyRole> roles, DbClient dbClient) {
        if (performancePoliciesMap != null) {
            for (VolumeTopologyRole role : roles) {
                URI performancePolicyURI = performancePoliciesMap.findPerformancePolicyForRole(role.name());
                if (!NullColumnValueGetter.isNullURI(performancePolicyURI)) {        
                    // Validate the performance policy exists and is active.
                    ArgValidator.checkUri(performancePolicyURI);
                    PerformancePolicy performancePolicy = dbClient.queryObject(PerformancePolicy.class, performancePolicyURI);
                    ArgValidator.checkEntity(performancePolicy, performancePolicyURI, false);
                }
            }
        }
    }

    /**
     * Get the performance policy for the passed role.
     * 
     * @param performancePoliciesMap A map of performance policy URIs by VolumeTopologyRole
     * @param role A VolumeTopologyRole.
     * @param dbClient A reference to a DbClient.
     * 
     * @return A reference to a PerformancePolicy instance or null.
     */
    public static PerformancePolicy getPerformancePolicyForRole(BlockPerformancePolicyMap performancePoliciesMap,
            VolumeTopologyRole role, DbClient dbClient) {
        PerformancePolicy performancePolicy = null;
        URI performancePolicyURI = getPerformancePolicyIdForRole(performancePoliciesMap, role, dbClient);
        if (performancePolicyURI != null) {
            performancePolicy = dbClient.queryObject(PerformancePolicy.class, performancePolicyURI);
        }
        return performancePolicy;
    }

    /**
     * Get the URI of the performance policy for the passed role.
     * 
     * @param performancePoliciesMap A map of performance policy URIs by VolumeTopologyRole
     * @param role A VolumeTopologyRole.
     * @param dbClient A reference to a DbClient.
     * 
     * @return The URI of the PerformancePolicy instance or null.
     */
    public static URI getPerformancePolicyIdForRole(BlockPerformancePolicyMap performancePoliciesMap,
            VolumeTopologyRole role, DbClient dbClient) {
        URI performancePolicyURI = null;
        if (performancePoliciesMap != null) {
            performancePolicyURI = performancePoliciesMap.findPerformancePolicyForRole(role.name());
        }
        return performancePolicyURI;
    }

    /**
     * Get the URI of the performance policy for the passed role.
     * 
     * @param performancePoliciesMap A map of performance policy URIs by VolumeTopologyRole
     * @param role A VolumeTopologyRole.
     * @param dbClient A reference to a DbClient.
     * 
     * @return The URI of the PerformancePolicy instance or null.
     */
    public static URI getPerformancePolicyIdForRole(Map<VolumeTopologyRole, URI> performancePoliciesMap,
            VolumeTopologyRole role, DbClient dbClient) {
        URI performancePolicyURI = null;
        if (performancePoliciesMap != null) {
            performancePolicyURI = performancePoliciesMap.get(role);
        }
        return performancePolicyURI;
    }

    /**
     * Get the performance policy for the passed role.
     * 
     * @param performancePoliciesMap A map of performance policy URIs by VolumeTopologyRole
     * @param role A VolumeTopologyRole.
     * @param dbClient A reference to a DbClient.
     * 
     * @return A reference to a PerformancePolicy instance or null.
     */
    public static PerformancePolicy getPerformancePolicyForRole(Map<VolumeTopologyRole, URI> performancePoliciesMap,
            VolumeTopologyRole role, DbClient dbClient) {
        PerformancePolicy performancePolicy = null;
        URI performancePolicyURI = getPerformancePolicyIdForRole(performancePoliciesMap, role, dbClient);
        if (performancePolicyURI != null) {
            performancePolicy = dbClient.queryObject(PerformancePolicy.class, performancePolicyURI);
        }
        return performancePolicy;
    }

    /**
     * Get the auto tiering policy name. If set in the passed performance policy,
     * return this value, otherwise the value comes from the passed virtual pool.
     * 
     * @param performancePoliciesMap A map of performance policy URIs by VolumeTopologyRole.
     * @param role A VolumeTopologyRole.
     * @param vpool A reference to a VirtualPool.
     * @param dbClient A reference to a DbClient.
     * 
     * @return The auto tiering policy name or null if not set.
     */
    public static String getAutoTierinigPolicyName(BlockPerformancePolicyMap performancePoliciesMap, VolumeTopologyRole role, 
            VirtualPool vpool, DbClient dbClient) {
        PerformancePolicy performancePolicy = getPerformancePolicyForRole(performancePoliciesMap, role, dbClient);
        return getAutoTierinigPolicyName(performancePolicy, vpool);
    }

    /**
     * Get the auto tiering policy name. If set in the passed performance policy,
     * return this value, otherwise the value comes from the passed virtual pool.
     * 
     * @param performancePoliciesMap A map of performance policy URIs by VolumeTopologyRole.
     * @param role A VolumeTopologyRole.
     * @param vpool A reference to a VirtualPool.
     * @param dbClient A reference to a DbClient.
     * 
     * @return The auto tiering policy name or null if not set.
     */
    public static String getAutoTierinigPolicyName(Map<VolumeTopologyRole, URI> performancePoliciesMap, VolumeTopologyRole role, 
            VirtualPool vpool, DbClient dbClient) {
        PerformancePolicy performancePolicy = getPerformancePolicyForRole(performancePoliciesMap, role, dbClient);
        return getAutoTierinigPolicyName(performancePolicy, vpool);
    }

    /**
     * Get the auto tiering policy name. If set in the passed performance policy,
     * return this value, otherwise the value comes from the passed virtual pool.
     * 
     * @param performancePolicyURI URI of a performance policy instance.
     * @param vpool A reference to a VirtualPool.
     * @param dbClient A reference to a DbClient.
     * 
     * @return The auto tiering policy name or null if not set.
     */
    public static String getAutoTierinigPolicyName(URI performancePolicyURI, VirtualPool vpool, DbClient dbClient) {
        PerformancePolicy performancePolicy = null;
        if (performancePolicyURI != null) {
            performancePolicy = dbClient.queryObject(PerformancePolicy.class, performancePolicyURI);
        }
        return getAutoTierinigPolicyName(performancePolicy, vpool);
    }

    /**
     * Get the auto tiering policy name. If set in the passed performance policy,
     * return this value, otherwise the value comes from the passed virtual pool.
     * 
     * @param performancePolicy A reference to a performance policy instance.
     * @param vpool A reference to a VirtualPool.
     * 
     * @return The auto tiering policy name or null if not set.
     */
    public static String getAutoTierinigPolicyName(PerformancePolicy performancePolicy, VirtualPool vpool) {
        String autoTieringPolicyName = null;
        if (performancePolicy != null) {
            // There will always be a value for the auto tiering policy
            // name, so return that value.
            return performancePolicy.getAutoTierPolicyName();
     
        }

        // If here, use the value from the vpool.
        if (vpool != null) {
            autoTieringPolicyName = vpool.getAutoTierPolicyName();
        }

        return autoTieringPolicyName;
    }

    /**
     * Get the thin volume pre-allocation percentage. If set in the passed performance 
     * policy, return this value, otherwise the value comes from the passed virtual pool.
     * 
     * @param performancePoliciesMap A map of performance policy URIs by VolumeTopologyRole
     * @param role A VolumeTopologyRole.
     * @param vpool A reference to a VirtualPool.
     * @param dbClient A reference to a DbClient.
     * 
     * @return The thin volume pre-allocation percentage or 0 if not set.
     */
    public static Integer getThinVolumePreAllocPercentage(BlockPerformancePolicyMap performancePoliciesMap, VolumeTopologyRole role,
            VirtualPool vpool, DbClient dbClient) {
        PerformancePolicy performancePolicy = getPerformancePolicyForRole(performancePoliciesMap, role, dbClient);
        return getThinVolumePreAllocPercentage(performancePolicy, vpool);
    }

    /**
     * Get the thin volume pre-allocation percentage. If set in the passed performance 
     * policy, return this value, otherwise the value comes from the passed virtual pool.
     * 
     * @param performancePoliciesMap A map of performance policy URIs by VolumeTopologyRole
     * @param role A VolumeTopologyRole.
     * @param vpool A reference to a VirtualPool.
     * @param dbClient A reference to a DbClient.
     * 
     * @return The thin volume pre-allocation percentage or 0 if not set.
     */
    public static Integer getThinVolumePreAllocPercentage(Map<VolumeTopologyRole, URI> performancePoliciesMap,
            VolumeTopologyRole role, VirtualPool vpool, DbClient dbClient) {
        PerformancePolicy performancePolicy = getPerformancePolicyForRole(performancePoliciesMap, role, dbClient);
        return getThinVolumePreAllocPercentage(performancePolicy, vpool);
    }

    /**
     * Get the thin volume pre-allocation percentage. If set in the passed performance 
     * policy, return this value, otherwise the value comes from the passed virtual pool.
     * 
     * @param performancePolicyURI The URI of a performance policy instance.
     * @param vpool A reference to a VirtualPool.
     * @param dbClient A reference to a DbClient.
     * 
     * @return The thin volume pre-allocation percentage or 0 if not set.
     */
    public static Integer getThinVolumePreAllocPercentage(URI performancePolicyURI, VirtualPool vpool, DbClient dbClient) {
        PerformancePolicy performancePolicy = null;
        if (performancePolicyURI != null) {
            performancePolicy = dbClient.queryObject(PerformancePolicy.class, performancePolicyURI);
        }
        return getThinVolumePreAllocPercentage(performancePolicy, vpool);
    }

    /**
     * Get the thin volume pre-allocation percentage. If set in the passed performance 
     * policy, return this value, otherwise the value comes from the passed virtual pool.
     * 
     * @param performancePolicy A reference to a performance policy instance.
     * @param vpool A reference to a VirtualPool.
     * 
     * @return The thin volume pre-allocation percentage or 0 if not set.
     */
    public static Integer getThinVolumePreAllocPercentage(PerformancePolicy performancePolicy, VirtualPool vpool) {
        if (performancePolicy != null) {
            // There will always be a value for the thin volume pre-allocation
            // percentage, so return that value.
            return performancePolicy.getThinVolumePreAllocationPercentage();
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
     * policy, return this value, otherwise the value comes from the passed
     * virtual pool.
     * 
     * @param performancePoliciesMap A map of performance policy URIs by VolumeTopologyRole
     * @param role A VolumeTopologyRole.
     * @param vpool A reference to a VirtualPool.
     * @param dbClient A reference to a DbClient.
     * 
     * @return True is set, False otherwise.
     */
    public static Boolean getIsDedupCapable(BlockPerformancePolicyMap performancePoliciesMap, VolumeTopologyRole role,
            VirtualPool vpool, DbClient dbClient) {
        PerformancePolicy performancePolicy = getPerformancePolicyForRole(performancePoliciesMap, role, dbClient);
        return getIsDedupCapable(performancePolicy, vpool);
    }

    /**
     * Get the deduplication capable setting. If set in the passed performance 
     * policy, return this value, otherwise the value comes from the passed
     * virtual pool.
     * 
     * @param performancePoliciesMap A map of performance policy URIs by VolumeTopologyRole
     * @param role A VolumeTopologyRole.
     * @param vpool A reference to a VirtualPool.
     * @param dbClient A reference to a DbClient.
     * 
     * @return True is set, False otherwise.
     */
    public static Boolean getIsDedupCapable(Map<VolumeTopologyRole, URI> performancePoliciesMap,
            VolumeTopologyRole role, VirtualPool vpool, DbClient dbClient) {
        PerformancePolicy performancePolicy = getPerformancePolicyForRole(performancePoliciesMap, role, dbClient);
        return getIsDedupCapable(performancePolicy, vpool);
    }

    /**
     * Get the deduplication capable setting. If set in the passed performance 
     * policy, return this value, otherwise the value comes from the passed
     * virtual pool.
     * 
     * @param performancePolicyURI The URI of a performance policy instance
     * @param vpool A reference to a VirtualPool.
     * @param dbClient A reference to a DbClient.
     * 
     * @return True is set, False otherwise.
     */
    public static Boolean getIsDedupCapable(URI performancePolicyURI, VirtualPool vpool, DbClient dbClient) {
        PerformancePolicy performancePolicy = null;
        if (performancePolicyURI != null) {
            performancePolicy = dbClient.queryObject(PerformancePolicy.class, performancePolicyURI);
        }
        return getIsDedupCapable(performancePolicy, vpool);
    }

    /**
     * Get the deduplication capable setting. If set in the passed performance 
     * policy, return this value, otherwise the value comes from the passed
     * virtual pool.
     * 
     * @param performancePolicy A reference to a performance policy instance.
     * @param vpool A reference to a VirtualPool.
     * 
     * @return True is set, False otherwise.
     */
    public static Boolean getIsDedupCapable(PerformancePolicy performancePolicy, VirtualPool vpool) {
        if (performancePolicy != null) {
            // There will always be a value for dedup capable, so return that value.
            return performancePolicy.getDedupCapable();
        }

        // If here, use the value from virtual pool.
        Boolean dedupCapable = vpool.getDedupCapable();

        return dedupCapable != null ? dedupCapable : Boolean.FALSE;
    }

    /**
     * Get the fast expansion setting. If set in the passed performance 
     * policy, return this value, otherwise the value comes from the passed
     * virtual pool.
     * 
     * @param performancePoliciesMap A map of performance policy URIs by VolumeTopologyRole
     * @param role A VolumeTopologyRole.
     * @param vpool A reference to a VirtualPool.
     * @param dbClient A reference to a DbClient.
     * 
     * @return True is set, False otherwise.
     */
    public static Boolean getFastExpansion(Map<VolumeTopologyRole, URI> performancePoliciesMap,
            VolumeTopologyRole role, VirtualPool vpool, DbClient dbClient) {
        PerformancePolicy performancePolicy = getPerformancePolicyForRole(performancePoliciesMap, role, dbClient);
        return getFastExpansion(performancePolicy, vpool);
    }

    /**
     * Get the fast expansion setting. If set in the passed performance 
     * policy, return this value, otherwise the value comes from the passed
     * virtual pool.
     * 
     * @param performancePolicyURI The URI of a performance policy instance.
     * @param vpool A reference to a VirtualPool.
     * @param dbClient A reference to a DbClient.
     * 
     * @return True is set, False otherwise.
     */
    public static Boolean getFastExpansion(URI performancePolicyURI, VirtualPool vpool, DbClient dbClient) {
        PerformancePolicy performancePolicy = null;
        if (performancePolicyURI != null) {
            performancePolicy = dbClient.queryObject(PerformancePolicy.class, performancePolicyURI);
        }
        return getFastExpansion(performancePolicy, vpool);
    }

    /**
     * Get the fast expansion setting. If set in the passed performance 
     * policy, return this value, otherwise the value comes from the passed
     * virtual pool.
     * 
     * @param performancePolicy A reference to a performance policy instance.
     * @param vpool A reference to a VirtualPool.
     * 
     * @return True is set, False otherwise.
     */
    public static Boolean getFastExpansion(PerformancePolicy performancePolicy, VirtualPool vpool) {
        if (performancePolicy != null) {
            // There will always be a value for fast expansion, so return that value.
            return performancePolicy.getFastExpansion();
        }

        // If here, use the value from virtual pool.
        Boolean fastExpansion = vpool.getFastExpansion();

        return fastExpansion != null ? fastExpansion : Boolean.FALSE;
    }

    /**
     * Override performance policy settings in the passed capabilities.
     * Use the passed virtual pool and performance policy.
     *  
     * @param vpool The virtual pool.
     * @param performancePoliciesMap The performance policies.
     * @param role The site role.
     * @param capabilities The capabilities to override.
     * @param dbClient A reference to a db client.
     * 
     * @return The overridden capabilities.
     */
    public static VirtualPoolCapabilityValuesWrapper overrideCapabilitiesForVolumePlacement(
            VirtualPool vpool, Map<VolumeTopologyRole, URI> performancePoliciesMap, VolumeTopologyRole role,
            VirtualPoolCapabilityValuesWrapper capabilities, DbClient dbClient) {
        
        // Initialize override capabilities.
        VirtualPoolCapabilityValuesWrapper overrideCapabilities = new VirtualPoolCapabilityValuesWrapper(capabilities);

        // Override the auto tiering policy name.
        String autoTierPolicyName = getAutoTierinigPolicyName(
                performancePoliciesMap, role, vpool, dbClient);
        if (NullColumnValueGetter.isNotNullValue(autoTierPolicyName)) {
            overrideCapabilities.put(VirtualPoolCapabilityValuesWrapper.AUTO_TIER__POLICY_NAME, autoTierPolicyName);
        } else {
            overrideCapabilities.removeCapabilityEntry(VirtualPoolCapabilityValuesWrapper.AUTO_TIER__POLICY_NAME);            
        }

        // Override thin provisioning.
        if (VirtualPool.ProvisioningType.Thin.toString().equalsIgnoreCase(
                vpool.getSupportedProvisioningType())) {
            overrideCapabilities.put(VirtualPoolCapabilityValuesWrapper.THIN_PROVISIONING, Boolean.TRUE);
        } else {
            overrideCapabilities.put(VirtualPoolCapabilityValuesWrapper.THIN_PROVISIONING, Boolean.FALSE);
        }

        // Override the thin volume pre-allocation size.
        Integer thinVolumePreAllocPercentage = getThinVolumePreAllocPercentage(
                performancePoliciesMap, role, vpool, dbClient);
        if (null != thinVolumePreAllocPercentage && 0 < thinVolumePreAllocPercentage) {
            overrideCapabilities.put(VirtualPoolCapabilityValuesWrapper.THIN_VOLUME_PRE_ALLOCATE_SIZE, VirtualPoolUtil
                    .getThinVolumePreAllocationSize(thinVolumePreAllocPercentage, overrideCapabilities.getSize()));
        } else {
            overrideCapabilities.removeCapabilityEntry(VirtualPoolCapabilityValuesWrapper.THIN_VOLUME_PRE_ALLOCATE_SIZE); 
        }

        // Override the dedup capable setting.
        Boolean dedupCapable = getIsDedupCapable(performancePoliciesMap, role, vpool, dbClient);
        overrideCapabilities.put(VirtualPoolCapabilityValuesWrapper.DEDUP, dedupCapable);

        return overrideCapabilities;
    }
}
