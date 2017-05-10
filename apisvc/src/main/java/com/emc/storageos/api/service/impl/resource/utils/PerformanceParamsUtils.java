/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.utils;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.emc.storageos.api.service.impl.placement.VirtualPoolUtil;
import com.emc.storageos.api.service.impl.resource.ArgValidator;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.PerformanceParams;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.VolumeTopology.VolumeTopologyRole;
import com.emc.storageos.db.client.model.VolumeTopology.VolumeTopologySite;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.model.block.BlockPerformanceParamsMap;
import com.emc.storageos.model.block.VolumeCreatePerformanceParams;
import com.emc.storageos.volumecontroller.impl.utils.VirtualPoolCapabilityValuesWrapper;

/**
 * PerformanceParams utility class.
 */
public class PerformanceParamsUtils {
    
    /**
     * Get the performance parameters for the passed role.
     * 
     * @param performanceParams The performance parameter overrides.
     * @param role A VolumeTopologyRole.
     * @param dbClient A reference to a DbClient.
     * 
     * @return The URI of a PerformanceParams instance or null.
     */
    public static URI getPerformanceParamsIdForSourceRole(VolumeCreatePerformanceParams performanceParams,
            VolumeTopologyRole role, DbClient dbClient) {
        URI performanceParamsURI = null;
        if (performanceParams != null) {
            BlockPerformanceParamsMap sourceParams = performanceParams.getSourceParams();
            if (sourceParams != null) {
                performanceParamsURI = sourceParams.findPerformanceParamsForRole(role.name());
            }
        }
        return performanceParamsURI;
    }

    /**
     * Transform the performance params to a Java map.
     * 
     * @param performanceParams The performance parameter overrides or null.
     * 
     * @return A Map specifying the performance parameters.
     */
    public static Map<VolumeTopologySite, List<Map<VolumeTopologyRole, URI>>> transformPerformanceParams(VolumeCreatePerformanceParams performanceParams) {
        Map<VolumeTopologySite, List<Map<VolumeTopologyRole, URI>>> performanceParamsMap = new HashMap<>();
        if (performanceParams != null) {
            // Translate the source site performance parameters.
            List<Map<VolumeTopologyRole, URI>> sourceParamsList = new ArrayList<>();
            sourceParamsList.add(transformPerformanceParams(performanceParams.getSourceParams()));
            performanceParamsMap.put(VolumeTopologySite.SOURCE, sourceParamsList);

            // Translate the copy site performance parameters.
            List<Map<VolumeTopologyRole, URI>> copyParamsList = new ArrayList<>();
            for (BlockPerformanceParamsMap copyParams : performanceParams.getCopyParams()) {
                copyParamsList.add(transformPerformanceParams(copyParams));
            }
            performanceParamsMap.put(VolumeTopologySite.COPY, copyParamsList);
        } else {
            performanceParamsMap.put(VolumeTopologySite.SOURCE, new ArrayList<Map<VolumeTopologyRole, URI>>());
            performanceParamsMap.put(VolumeTopologySite.COPY, new ArrayList<Map<VolumeTopologyRole, URI>>());
        }
        return performanceParamsMap;
    }

    /**
     * Transform the performance params client model map to a java.util.map.
     * 
     * @param performanceParams The performance params map for a volume create request or null.
     * 
     * @return A Map specifying the performance parameters.
     */
    public static Map<VolumeTopologyRole, URI> transformPerformanceParams(BlockPerformanceParamsMap performanceParamsMap) {
        Map<VolumeTopologyRole, URI> result = new HashMap<>();
        if (performanceParamsMap != null) {
            for (VolumeTopologyRole role : VolumeTopologyRole.values()) {
                URI performanceParamsURI = performanceParamsMap.findPerformanceParamsForRole(role.toString());
                if (!NullColumnValueGetter.isNullURI(performanceParamsURI)) {
                    result.put(role,  performanceParamsURI);
                }
            }
        }
        return result;
    }    

    /**
     * Validates the performance parameters for the passed role are valid and active.
     * 
     * @param performanceParamsMap A map of performance parameter URIs by VolumeTopologyRole
     * @param roles A list of VolumeTopologyRoles to validate.
     * @param dbClient A reference to a DbClient.
     */
    public static void validatePerformanceParamsForRole(BlockPerformanceParamsMap performanceParamsMap,
            List<VolumeTopologyRole> roles, DbClient dbClient) {
        if (performanceParamsMap != null) {
            for (VolumeTopologyRole role : roles) {
                URI performanceParamsURI = performanceParamsMap.findPerformanceParamsForRole(role.name());
                if (!NullColumnValueGetter.isNullURI(performanceParamsURI)) {        
                    // Validate the performance params exist and are active.
                    ArgValidator.checkUri(performanceParamsURI);
                    PerformanceParams performanceParams = dbClient.queryObject(PerformanceParams.class, performanceParamsURI);
                    ArgValidator.checkEntity(performanceParams, performanceParamsURI, false);
                }
            }
        }
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
        URI performanceParamsURI = getPerformanceParamsIdForRole(performanceParamsMap, role, dbClient);
        if (performanceParamsURI != null) {
            performanceParams = dbClient.queryObject(PerformanceParams.class, performanceParamsURI);
        }
        return performanceParams;
    }
    
    /**
     * Get the URI of the performance parameters for the passed role.
     * 
     * @param performanceParamsMap A map of performance parameter URIs by VolumeTopologyRole
     * @param role A VolumeTopologyRole.
     * @param dbClient A reference to a DbClient.
     * 
     * @return The URI of the PerformanceParams instance or null.
     */
    public static URI getPerformanceParamsIdForRole(BlockPerformanceParamsMap performanceParamsMap,
            VolumeTopologyRole role, DbClient dbClient) {
        URI performanceParamsURI = null;
        if (performanceParamsMap != null) {
            performanceParamsURI = performanceParamsMap.findPerformanceParamsForRole(role.name());
        }
        return performanceParamsURI;
    }    

    /**
     * Get the URI of the performance parameters for the passed role.
     * 
     * @param performanceParamsMap A map of performance parameter URIs by VolumeTopologyRole
     * @param role A VolumeTopologyRole.
     * @param dbClient A reference to a DbClient.
     * 
     * @return The URI of the PerformanceParams instance or null.
     */
    public static URI getPerformanceParamsIdForRole(Map<VolumeTopologyRole, URI> performanceParamsMap,
            VolumeTopologyRole role, DbClient dbClient) {
        URI performanceParamsURI = null;
        if (performanceParamsMap != null) {
            performanceParamsURI = performanceParamsMap.get(role);
        }
        return performanceParamsURI;
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
    public static PerformanceParams getPerformanceParamsForRole(Map<VolumeTopologyRole, URI> performanceParamsMap,
            VolumeTopologyRole role, DbClient dbClient) {
        PerformanceParams performanceParams = null;
        URI performanceParamsURI = getPerformanceParamsIdForRole(performanceParamsMap, role, dbClient);
        if (performanceParamsURI != null) {
            performanceParams = dbClient.queryObject(PerformanceParams.class, performanceParamsURI);
        }
        return performanceParams;
    }  

    /**
     * Get the auto tiering policy name. If set in the passed performance parameters,
     * return this value, otherwise the value comes from the passed virtual pool.
     * 
     * @param performanceParamsMap A map of performance parameter URIs by VolumeTopologyRole.
     * @param role A VolumeTopologyRole.
     * @param vpool A reference to a VirtualPool.
     * @param dbClient A reference to a DbClient.
     * 
     * @return The auto tiering policy name or null if not set.
     */
    public static String getAutoTierinigPolicyName(BlockPerformanceParamsMap performanceParamsMap, VolumeTopologyRole role, 
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
     * Get the auto tiering policy name. If set in the passed performance parameters,
     * return this value, otherwise the value comes from the passed virtual pool.
     * 
     * @param performanceParamsMap A map of performance parameter URIs by VolumeTopologyRole.
     * @param role A VolumeTopologyRole.
     * @param vpool A reference to a VirtualPool.
     * @param dbClient A reference to a DbClient.
     * 
     * @return The auto tiering policy name or null if not set.
     */
    public static String getAutoTierinigPolicyName(Map<VolumeTopologyRole, URI> performanceParamsMap, VolumeTopologyRole role, 
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
    public static Integer getThinVolumePreAllocPercentage(Map<VolumeTopologyRole, URI> performanceParamsMap,
            VolumeTopologyRole role, VirtualPool vpool, DbClient dbClient) {
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
    public static Boolean getIsDedupCapable(Map<VolumeTopologyRole, URI> performanceParamsMap,
            VolumeTopologyRole role, VirtualPool vpool, DbClient dbClient) {
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
    
    /**
     * Override the passed primary side capabilities to take into account the values in
     * the HA virtual pool and the performance parameters for the HA side of a VPLEX
     * volume.
     *  
     * @param haVpool The HA virtual pool.
     * @param performanceParams The performance parameters.
     * @param haRole The HA role.
     * @param primaryCapabilities The capabilities for the primary side of the VPLEX volume.
     * @param dbClient A reference to a db client.
     * 
     * @return The capabilities to use when matching pools for the HA side of the volume.
     */
    public static VirtualPoolCapabilityValuesWrapper overridePrimaryCapabilitiesForVplexHA(
            VirtualPool haVpool, Map<VolumeTopologyRole, URI> performanceParams,
            VolumeTopologyRole haRole, VirtualPoolCapabilityValuesWrapper primaryCapabilities, DbClient dbClient) {
        
        // Initialize HA capabilities.
        VirtualPoolCapabilityValuesWrapper haCapabilities = new VirtualPoolCapabilityValuesWrapper(primaryCapabilities);

        // Set the auto tiering policy name for the HA side into the HA capabilities.
        String autoTierPolicyName = getAutoTierinigPolicyName(
                performanceParams, haRole, haVpool, dbClient);
        if (NullColumnValueGetter.isNotNullValue(autoTierPolicyName)) {
            haCapabilities.put(VirtualPoolCapabilityValuesWrapper.AUTO_TIER__POLICY_NAME, autoTierPolicyName);
        } else {
            haCapabilities.removeCapabilityEntry(VirtualPoolCapabilityValuesWrapper.AUTO_TIER__POLICY_NAME);            
        }

        // Set is thin provisioning for the HA side.
        if (VirtualPool.ProvisioningType.Thin.toString().equalsIgnoreCase(
                haVpool.getSupportedProvisioningType())) {
            haCapabilities.put(VirtualPoolCapabilityValuesWrapper.THIN_PROVISIONING, Boolean.TRUE);
        }

        // Set the thin volume pre-allocation size for the HA side into the HA capabilities.
        Integer thinVolumePreAllocPercentage = getThinVolumePreAllocPercentage(
                performanceParams, haRole, haVpool, dbClient);
        if (null != thinVolumePreAllocPercentage && 0 < thinVolumePreAllocPercentage) {
            haCapabilities.put(VirtualPoolCapabilityValuesWrapper.THIN_VOLUME_PRE_ALLOCATE_SIZE, VirtualPoolUtil
                    .getThinVolumePreAllocationSize(thinVolumePreAllocPercentage, haCapabilities.getSize()));
        }

        // Set the dedup capable for the HA side into the HA capabilities.
        Boolean dedupCapable = getIsDedupCapable(performanceParams, haRole, haVpool, dbClient);
        if (dedupCapable) {
            haCapabilities.put(VirtualPoolCapabilityValuesWrapper.DEDUP, dedupCapable);
        } else {
            haCapabilities.removeCapabilityEntry(VirtualPoolCapabilityValuesWrapper.DEDUP);
        }

        return haCapabilities;
    }
}
