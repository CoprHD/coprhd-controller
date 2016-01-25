/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.utils.attrmatchers;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StoragePool.PoolServiceType;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.volumecontroller.AttributeMatcher;
import com.emc.storageos.volumecontroller.impl.ControllerUtils;
import com.google.common.base.Joiner;

/**
 * Capacity Matcher- Pools which satisfy the required capacity values will be retained.
 * This is invoked in following scenarios:
 * 1. During Provisioning to Match pools based on provisioning attributes.
 * 2. Invoked independently to find the recommendation pool based on no. of resources & its capacity.
 * 
 */
public class CapacityMatcher extends AttributeMatcher {
    private static final int BYTESCONVERTER = 1024;
    public static final String MAX_THIN_POOL_SUBSCRIPTION_PERCENTAGE = "controller_max_thin_pool_subscription_percentage"; // default
                                                                                                                           // percentage of
                                                                                                                           // subscription
                                                                                                                           // rate.
    public static final String MAX_POOL_UTILIZATION_PERCENTAGE = "controller_max_pool_utilization_percentage"; // percentage of pool
                                                                                                               // utilization limit.

    public static final String ZERO = "0L";
    private static final Logger _log = LoggerFactory.getLogger(CapacityMatcher.class);

    @Override
    protected boolean isAttributeOn(Map<String, Object> attributeMap) {
        if (null != attributeMap && attributeMap.containsKey(Attributes.size.toString())) {
            return true;
        }
        return false;
    }

    @Override
    protected List<StoragePool> matchStoragePoolsWithAttributeOn(List<StoragePool> pools, Map<String, Object> attributeMap) {
        _log.info("Pools Matching capacity  Started:" + Joiner.on("\t").join(getNativeGuidFromPools(pools)));
        List<StoragePool> filteredPoolList = new ArrayList<StoragePool>(pools);
        Long resourceSize = (Long) attributeMap.get(Attributes.size.toString());
        // During initial pool matching it will be always one. This is need as the logic used to check
        // multiVolume Size.
        long requiredCapacity = 1L * resourceSize;
        Iterator<StoragePool> poolIterator = pools.iterator();
        boolean supportsThinProvisioning = false;
        long thinVolumePreAllocationSize = 0;
        if (attributeMap.containsKey(Attributes.provisioning_type.toString())
                && VirtualPool.ProvisioningType.Thin.toString().equalsIgnoreCase(
                        attributeMap.get(Attributes.provisioning_type.toString()).toString())) {
            supportsThinProvisioning = true;
            if (attributeMap.containsKey(Attributes.thin_volume_preallocation_size.toString())) {
                thinVolumePreAllocationSize = (Long) attributeMap
                        .get(Attributes.thin_volume_preallocation_size.toString());
            }
        }
        while (poolIterator.hasNext()) {
            StoragePool pool = poolIterator.next();
            // During provisioning matching, this matcher runs against the volume size requested.
            if (!poolMatchesCapacity(pool, requiredCapacity, resourceSize, true, supportsThinProvisioning, thinVolumePreAllocationSize)) {
               // filteredPoolList.remove(pool);
            }
        }
        _log.info("Pools Matching capacity Ended :" + Joiner.on("\t").join(getNativeGuidFromPools(filteredPoolList)));
        return filteredPoolList;
    }

    /**
     * Decision is made as following:
     * 1. Check volume size against maximum volume size limit of storage pool (if required).
     * 2.
     * --- Thick pool: solely based on the pool utilization capacity including the current request
     * & the requested capacity < pool freeCapacity.
     * --- Thin pool: It depends on two factors.
     * 1. pool utilization -> Pool should be less utilized
     * 2. pool subscribedCapacity -> pool should subscribed less than user configured.
     * 
     * @param pool
     *            storage pool
     * @param requiredCapacity : requested size
     *            This capacity changes as per the provisioning logic.
     *            It could be requiredCapacity = resourceSize * resourceCount.
     *            This should be computed whoever calls this method.
     * @param resourceSize : always size of the requested value capacity
     * @param checkPoolMaxSizeLimit
     *            indicates if the size should be checked against max. limits for volume
     *            size in the pool
     * @param supportsThinProvisioning : flag tells whether Thin or Thick.
     * @return true/false
     */
    public boolean poolMatchesCapacity(StoragePool pool, long requiredCapacity, long resourceSize,
            boolean checkPoolMaxSizeLimit, boolean supportsThinProvisioning, Long thinVolumePreAllocationResourceSize) {
        if (null == pool.getTotalCapacity() || pool.getTotalCapacity() == 0) {
            return false;
        }
        long preAllocationSizeInKB = 0;
        // requiredCapacity in KB.
        long sizeInKB = getSizeInKB(requiredCapacity);
        // single resource size in KB.
        long resourceSizeInKB = getSizeInKB(resourceSize);

        // Step 1: Check for Maximum Volume size limit if block type pool.
        if ((PoolServiceType.block.toString().equalsIgnoreCase(pool.getPoolServiceType()) ||
                PoolServiceType.block_file.name().equalsIgnoreCase(pool.getPoolServiceType()))
                && checkPoolMaxSizeLimit) {
            // check against maximum volume size limit of storage pool
            // limit in kilobytes
            Long maxVolumeSizeLimit = getMaxVolumeSizeLimit(pool, supportsThinProvisioning);

            if (maxVolumeSizeLimit == null || maxVolumeSizeLimit == 0) {
                String errorMsg = String.format("Pool %s does not have maximum size limit for %s volumes set.",
                        pool.getId(), pool.getSupportedResourceTypes());
                _log.error(errorMsg);
                return false;
            }

            // Clarion volumes in Unified pools can not be created as composite
            // volumes (meta volumes).
            if (null != pool.getPoolClassName()
                    && (pool.getPoolClassName().equals(StoragePool.PoolClassNames.Clar_UnifiedStoragePool.toString())
                    || pool.getPoolClassName().equals(StoragePool.PoolClassNames.VNXe_Pool.name()))
                    && resourceSizeInKB > maxVolumeSizeLimit) {
                _log.info(String
                        .format("Pool %s is not matching as the pool's maximum volume size (%s KB) is below the requested volume size %s KB.",
                                pool.getId(), maxVolumeSizeLimit, resourceSizeInKB));
                return false;
            }
        }
        // Step 2: Check whether StoragePool is Thick or not.
        if (pool.getSupportedResourceTypes() != null
                && pool.getSupportedResourceTypes().equals(StoragePool.SupportedResourceTypes.THICK_ONLY.name())
                || !supportsThinProvisioning) {
            // If it is thick then check whether pool has more utilization or not.
            return isPoolMatchesCapacityForThickProvisioning(pool, sizeInKB);
        }
        if (null != thinVolumePreAllocationResourceSize) {
            preAllocationSizeInKB = getSizeInKB(thinVolumePreAllocationResourceSize);
        }
        // Step 3: Check whether StoragePool is Thin or not.
        return isPoolMatchesCapacityForThinProvisioning(pool, sizeInKB, preAllocationSizeInKB, _coordinator);
    }

    /**
     * return size in KB
     * 
     * @param resourceSize
     * @return
     */
    private long getSizeInKB(long resourceSize) {
        return (resourceSize % BYTESCONVERTER == 0) ? resourceSize / BYTESCONVERTER : resourceSize / BYTESCONVERTER + 1;
    }

    /**
     * Return VolumeSizeLimit based on supportsThinProvisioning flag.
     * 
     * @param pool
     * @param supportsThinProvisioning
     * @return
     */
    private Long getMaxVolumeSizeLimit(StoragePool pool, boolean supportsThinProvisioning) {
        Long maxVolumeSizeLimit = null;
        if (supportsThinProvisioning) {
            maxVolumeSizeLimit = pool.getMaximumThinVolumeSize();
        } else {
            maxVolumeSizeLimit = pool.getMaximumThickVolumeSize();
        }
        return maxVolumeSizeLimit;
    }

    /**
     * Check whether Thick pool is matching with the capacity requirement or not.
     * 
     * @param pool
     * @param requiredCapacityInKB
     * @return
     */
    private boolean isPoolMatchesCapacityForThickProvisioning(StoragePool pool, long requiredCapacityInKB) {
        if (requiredCapacityInKB > pool.getFreeCapacity()) {
            _log.info(String
                    .format("Pool %s is not matching as it doesn't have enough space to create resources. Pool has %dKB, Required capacity of request %dKB",
                            pool.getId(),
                            pool.getFreeCapacity().longValue(),
                            requiredCapacityInKB));
            return false;
        }
        if (!checkThickPoolCandidacy(pool, requiredCapacityInKB, _coordinator)) {
            String msg = String
                    .format("Pool %s is not matching as it will have utilization of %s percent after allocation. Pool's max utilization percentage is %s percent .",
                            pool.getId(), 100 - getThickPoolFreeCapacityPercentage(pool, requiredCapacityInKB),
                            getMaxPoolUtilizationPercentage(pool, _coordinator));
            _log.info(msg);
            return false;
        }
        return true;
    }

    /**
     * Return the thickPoolFreeCapacity percentage.
     * 
     * @param pool
     * @param requiredCapacityInKB
     * @return
     */
    public static double getThickPoolFreeCapacityPercentage(StoragePool pool, long requiredCapacityInKB) {
        return ((pool.getFreeCapacity().doubleValue() - requiredCapacityInKB) / pool
                .getTotalCapacity()) * 100;
    }

    /**
     * Verifies whether Thick pool is more utilized or less utilized based on the user configured max utilized value.
     * 
     * Step 1: Calculate the new freeCapacity Percentage including the current requested capacity.
     * Step 2: Determine the maximumPoolUtlization Percentage If user is configured else use the default.
     * Step 3: Check whether requiredCapacityInKB is less than Pool FreeCapacity & isPoolLessUtilized.
     * 
     * Formulae : [newPoolUtilizationPercentage <= poolMaxUtilizationPercentage]
     * 
     * newPoolUtilizationPercentage = [100 - poolFreeCapacityPercentage]
     * 
     * poolFreeCapacityPercentage = [(poolFreeCapacity - requestedCapacity)/poolTotalCapacity) * 100]
     * 
     * @param pool : Pool to verify
     * @param requiredCapacityInKB : requiredCapacity to check against.
     * @return
     */
    public static boolean checkThickPoolCandidacy(StoragePool pool, long requiredCapacityInKB, CoordinatorClient coordinator) {
        return (100 - getThickPoolFreeCapacityPercentage(pool, requiredCapacityInKB)) <= getMaxPoolUtilizationPercentage(pool, coordinator);
    }

    /**
     * return the pool max utilization if it is set else return default value.
     * 
     * @param pool
     * @return
     */
    public static double getMaxPoolUtilizationPercentage(StoragePool pool, CoordinatorClient coordinator) {
        return (pool.getMaxPoolUtilizationPercentage() != null && pool
                .getMaxPoolUtilizationPercentage() != 0L) ? pool.getMaxPoolUtilizationPercentage()
                : Integer.valueOf(ControllerUtils.getPropertyValueFromCoordinator(coordinator, MAX_POOL_UTILIZATION_PERCENTAGE));
    }

    /**
     * Check whether Thin Pool matches with the pool capacity requirement or not.
     * 
     * Formulae: [isPoolLessUtilized && isPoolLessSubscribed]
     * 
     * @param pool
     * @return
     */
    public static boolean isPoolMatchesCapacityForThinProvisioning(StoragePool pool,
            long requestedCapacityInKB, long preAllocationSize,
            CoordinatorClient coordinator) {
        if (!isThinPoolLessUtilized(pool, preAllocationSize, coordinator)) {
            String msg = String
                    .format("Thin pool %s is not matching as it will have utilization of %s percent after allocation. Pool's max utilization percentage is %s percent .",
                            pool.getId(), 100 - getThinPoolFreeCapacityPercentage(pool, preAllocationSize),
                            getMaxPoolUtilizationPercentage(pool, coordinator));
            _log.info(msg);

            return false;
        }
        if (!isThinPoolLessSubscribed(pool, requestedCapacityInKB, coordinator)) {
            String msg = String
                    .format("Thin pool %s is not matching as it will have %s percent subscribed after allocation. Pool's max subscription percentage is %s percent .",
                            pool.getId(), getThinPoolSubscribedCapacityPercentage(pool, requestedCapacityInKB),
                            getMaxPoolSubscriptionPercentage(pool, coordinator));
            _log.info(msg);

            return false;
        }
        return true;
    }

    /**
     * Verifies whether pool is more utilized or less utilized based on the user configured max utilized value.
     * 
     * Step 1: Calculate the pool freeCapacity Percentage.
     * Step 2: Determine the maximumPoolUtlization Percentage If user is configured else use the default.
     * Step 3: Check whether pool is isLessUtilized or not.
     * 
     * Formulae: [(100 - poolFreeCapacityPercentage) <= maximumPoolUtilizationPercentage]
     * 
     * @param pool : Pool to verify
     * @return
     */
    private static boolean isThinPoolLessUtilized(StoragePool pool, long preAllocationSize, CoordinatorClient coordinator) {
        final boolean isPoolUtilizedLess = (100 - getThinPoolFreeCapacityPercentage(pool, preAllocationSize)) <= getMaxPoolUtilizationPercentage(
                pool, coordinator);
        return isPoolUtilizedLess;
    }

    /**
     * Calculates the ThinPoolFreeCapacityPercentage.
     * 
     * Formulae: [(poolFreeCapacity - thinVolumePreAllocationSize/poolTotalCapacity) * 100]
     * 
     * @param pool
     * @return
     */
    private static double getThinPoolFreeCapacityPercentage(StoragePool pool, long preAllocationSize) {
        return ((pool.getFreeCapacity().doubleValue() - preAllocationSize) / pool.getTotalCapacity()) * 100;
    }

    /**
     * Returns true if Thin pool is subscribed less than user configured value else false.
     * 
     * Formulae: [poolSubscribedCapacityPercentage <= poolMaxSubscriptionPercentage]
     * 
     * @param pool
     * @param requestedCapacityInKB
     * @return
     */
    public static boolean isThinPoolLessSubscribed(StoragePool pool, long requestedCapacityInKB, CoordinatorClient coordinator) {
        return getThinPoolSubscribedCapacityPercentage(pool, requestedCapacityInKB) <= getMaxPoolSubscriptionPercentage(pool, coordinator);
    }

    /**
     * return thinPoolSubscribeCapacityPercentage which include the current request resource size.
     * 
     * Formulae: [((poolSubscribedCapacity+requestedCapacity)/poolTotalCapacity) * 100]
     * 
     * @param pool
     * @param requestedCapacityInKB
     * @return
     */
    private static double getThinPoolSubscribedCapacityPercentage(StoragePool pool, long requestedCapacityInKB) {
        // thinPoolSubscribedCapacity includes the current resource capacity.
        return (((pool.getSubscribedCapacity().doubleValue() + requestedCapacityInKB) / pool.getTotalCapacity()) * 100);
    }

    /**
     * return the pool subscribed percentage if it is set else return default value.
     * 
     * @param pool
     * @return
     */
    public static double getMaxPoolSubscriptionPercentage(StoragePool pool, CoordinatorClient coordinator) {
        return (pool.getMaxThinPoolSubscriptionPercentage() != null && pool.getMaxThinPoolSubscriptionPercentage() != 0L)
                ? pool.getMaxThinPoolSubscriptionPercentage() : Integer.valueOf(
                        ControllerUtils.getPropertyValueFromCoordinator(coordinator, MAX_THIN_POOL_SUBSCRIPTION_PERCENTAGE));
    }

}
