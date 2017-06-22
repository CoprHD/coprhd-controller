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
     * Transform the performance params to a Java map.
     * 
     * @param performanceParams The performance parameter overrides or null.
     * 
     * @return A Map specifying the performance parameters.
     */
    public static Map<VolumeTopologySite, Map<URI, Map<VolumeTopologyRole, URI>>> transformPerformanceParams(
            VolumeCreatePerformanceParams performanceParams) {
        Map<VolumeTopologySite, Map<URI, Map<VolumeTopologyRole, URI>>> performanceParamsMap = new HashMap<>();
        if (performanceParams != null) {
            // Translate the source site performance parameters.
            performanceParamsMap.put(VolumeTopologySite.SOURCE, 
                    transformPerformanceParams(Arrays.asList(performanceParams.getSourceParams())));

            // Translate the copy site performance parameters.
            performanceParamsMap.put(VolumeTopologySite.COPY, 
                    transformPerformanceParams(performanceParams.getCopyParams()));
        } else {
            performanceParamsMap.put(VolumeTopologySite.SOURCE, new HashMap<>());
            performanceParamsMap.put(VolumeTopologySite.COPY, new HashMap<>());
        }
        return performanceParamsMap;
    }

    /**
     * Transform the performance params client model maps to java.util.map.
     * 
     * @param performanceParams The performance params for a volume create request or null.
     * 
     * @return A Map specifying the performance parameters.
     */
    public static Map<URI, Map<VolumeTopologyRole, URI>> transformPerformanceParams(List<BlockPerformanceParamsMap> performanceParamsMapList) {
        Map<URI, Map<VolumeTopologyRole, URI>> resultMap = new HashMap<>();
        if (!CollectionUtils.isEmpty(performanceParamsMapList)) {
            for (BlockPerformanceParamsMap performanceParamsMap : performanceParamsMapList) {
                resultMap.put(performanceParamsMap.getVirtualArray(), transformPerformanceParams(performanceParamsMap));
            }
        }
        return resultMap;
    }
    
    /**
     * Transform the performance params client model maps to java.util.map.
     * 
     * @param performanceParams The performance params for a volume create request or null.
     * 
     * @return A Map specifying the performance parameters.
     */
    public static Map<VolumeTopologyRole, URI> transformPerformanceParams(BlockPerformanceParamsMap performanceParamsMap) {
        Map<VolumeTopologyRole, URI> resultMap = new HashMap<>();
        if (performanceParamsMap != null) {
            for (VolumeTopologyRole role : VolumeTopologyRole.values()) {
                URI performanceParamsURI = performanceParamsMap.findPerformanceParamsForRole(role.toString());
                if (!NullColumnValueGetter.isNullURI(performanceParamsURI)) {
                    resultMap.put(role,  performanceParamsURI);
                }
            }
        }
        return resultMap;
    }    

    /**
     * Validates the performance parameters for the passed role are valid and active.
     * 
     * @param performanceParamsMap A map of performance parameter URIs by VolumeTopologyRole
     * @param roles A list of VolumeTopologyRoles to validate.
     * @param dbClient A reference to a DbClient.
     */
    public static void validatePerformanceParamsForRoles(BlockPerformanceParamsMap performanceParamsMap,
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
     * Get the auto tiering policy name. If set in the passed performance parameters,
     * return this value, otherwise the value comes from the passed virtual pool.
     * 
     * @param performanceParamsURI URI of a performance parameters instance.
     * @param vpool A reference to a VirtualPool.
     * @param dbClient A reference to a DbClient.
     * 
     * @return The auto tiering policy name or null if not set.
     */
    public static String getAutoTierinigPolicyName(URI performanceParamsURI, VirtualPool vpool, DbClient dbClient) {
        String autoTieringPolicyName = null;
        if (performanceParamsURI != null) {
            PerformanceParams performanceParams = dbClient.queryObject(PerformanceParams.class, performanceParamsURI);
            if (performanceParams != null) {
                // There will always be a value for the auto tiering policy
                // name, so return that value.
                return performanceParams.getAutoTierPolicyName();
            }
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
     * Get the thin volume pre-allocation percentage. If set in the passed performance 
     * parameters, return this value, otherwise the value comes from the passed virtual pool.
     * 
     * @param performanceParamsURI The URI of a performance parameters instance.
     * @param vpool A reference to a VirtualPool.
     * @param dbClient A reference to a DbClient.
     * 
     * @return The thin volume pre-allocation percentage or 0 if not set.
     */
    public static Integer getThinVolumePreAllocPercentage(URI performanceParamsURI, VirtualPool vpool, DbClient dbClient) {
        if (performanceParamsURI != null) {
            PerformanceParams performanceParams = dbClient.queryObject(PerformanceParams.class, performanceParamsURI);
            if (performanceParams != null) {
                // There will always be a value for the thin volume pre-allocation
                // percentage, so return that value.
                return performanceParams.getThinVolumePreAllocationPercentage();
            }
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
     * Get the deduplication capable setting. If set in the passed performance 
     * parameters, return this value, otherwise the value comes from the passed
     * virtual pool.
     * 
     * @param performanceParamsURI The URI of a performance parameters instance
     * @param vpool A reference to a VirtualPool.
     * @param dbClient A reference to a DbClient.
     * 
     * @return True is set, False otherwise.
     */
    public static Boolean getIsDedupCapable(URI performanceParamsURI, VirtualPool vpool, DbClient dbClient) {
        Boolean dedupCapable = Boolean.FALSE;
        if (performanceParamsURI != null) {
            PerformanceParams performanceParams = dbClient.queryObject(PerformanceParams.class, performanceParamsURI);
            if (performanceParams != null) {
                // There will always be a value for dedup capable, so return that value.
                return performanceParams.getDedupCapable();
            }
        }

        // If here, use the value from virtual pool.
        dedupCapable = vpool.getDedupCapable();

        return dedupCapable != null ? dedupCapable : Boolean.FALSE;
    }    
    
    /**
     * Get the fast expansion setting. If set in the passed performance 
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
    public static Boolean getFastExpansion(Map<VolumeTopologyRole, URI> performanceParamsMap,
            VolumeTopologyRole role, VirtualPool vpool, DbClient dbClient) {
        Boolean fastExpansion = Boolean.FALSE;
        PerformanceParams performanceParams = getPerformanceParamsForRole(performanceParamsMap, role, dbClient);
        if (performanceParams != null) {
            // There will always be a value for fast expansion, so return that value.
            return performanceParams.getFastExpansion();
        }

        // If here, use the value from virtual pool.
        fastExpansion = vpool.getFastExpansion();

        return fastExpansion != null ? fastExpansion : Boolean.FALSE;
    }    
    
    /**
     * Get the fast expansion setting. If set in the passed performance 
     * parameters, return this value, otherwise the value comes from the passed
     * virtual pool.
     * 
     * @param performanceParamsURI The URI of a performance parameters instance.
     * @param vpool A reference to a VirtualPool.
     * @param dbClient A reference to a DbClient.
     * 
     * @return True is set, False otherwise.
     */
    public static Boolean getFastExpansion(URI performanceParamsURI, VirtualPool vpool, DbClient dbClient) {
        Boolean fastExpansion = Boolean.FALSE;
        if (performanceParamsURI != null) {
            PerformanceParams performanceParams = dbClient.queryObject(PerformanceParams.class, performanceParamsURI);
            if (performanceParams != null) {
                // There will always be a value for fast expansion, so return that value.
                return performanceParams.getFastExpansion();
            }
        }

        // If here, use the value from virtual pool.
        fastExpansion = vpool.getFastExpansion();

        return fastExpansion != null ? fastExpansion : Boolean.FALSE;
    }    

    /**
     * Override the passed source, primary side capabilities to get the capabilities used when
     * placing ha side of a distributed volume or an SRDF/RP copy. Used the passed virtual pool
     * and performance parameters.
     *  
     * @param vpool The virtual pool.
     * @param performanceParams The performance parameters.
     * @param role The site role.
     * @param primaryCapabilities The capabilities for the source, primary volume.
     * @param dbClient A reference to a db client.
     * 
     * @return The overridden capabilities.
     */
    public static VirtualPoolCapabilityValuesWrapper overrideCapabilitiesForVolumePlacement(
            VirtualPool vpool, Map<VolumeTopologyRole, URI> performanceParams, VolumeTopologyRole role,
            VirtualPoolCapabilityValuesWrapper primaryCapabilities, DbClient dbClient) {
        
        // Initialize override capabilities.
        VirtualPoolCapabilityValuesWrapper overrideCapabilities = new VirtualPoolCapabilityValuesWrapper(primaryCapabilities);

        // Override the auto tiering policy name.
        String autoTierPolicyName = getAutoTierinigPolicyName(
                performanceParams, role, vpool, dbClient);
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
                performanceParams, role, vpool, dbClient);
        if (null != thinVolumePreAllocPercentage && 0 < thinVolumePreAllocPercentage) {
            overrideCapabilities.put(VirtualPoolCapabilityValuesWrapper.THIN_VOLUME_PRE_ALLOCATE_SIZE, VirtualPoolUtil
                    .getThinVolumePreAllocationSize(thinVolumePreAllocPercentage, overrideCapabilities.getSize()));
        } else {
            overrideCapabilities.removeCapabilityEntry(VirtualPoolCapabilityValuesWrapper.THIN_VOLUME_PRE_ALLOCATE_SIZE); 
        }

        // Override the dedup capable setting.
        Boolean dedupCapable = getIsDedupCapable(performanceParams, role, vpool, dbClient);
        overrideCapabilities.put(VirtualPoolCapabilityValuesWrapper.DEDUP, dedupCapable);

        return overrideCapabilities;
    }
}
