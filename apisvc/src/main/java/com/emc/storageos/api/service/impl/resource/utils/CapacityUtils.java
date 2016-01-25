/*
 * Copyright (c) 2012-2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.utils;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.ArrayList;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.util.SumPrimitiveFieldAggregator;
import com.emc.storageos.volumecontroller.impl.utils.ObjectLocalCache;
import com.emc.storageos.volumecontroller.impl.utils.ProvisioningAttributeMapBuilder;
import com.emc.storageos.volumecontroller.impl.utils.attrmatchers.NeighborhoodsMatcher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.service.impl.resource.ArgValidator;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.Bucket;
import com.emc.storageos.db.client.model.DiscoveredDataObject.CompatibilityStatus;
import com.emc.storageos.db.client.model.DiscoveredDataObject.DiscoveryStatus;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StoragePool.PoolServiceType;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.TenantOrg;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.model.vpool.CapacityResponse;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.volumecontroller.impl.utils.attrmatchers.CapacityMatcher;
import com.emc.storageos.volumecontroller.impl.utils.attrmatchers.MaxResourcesMatcher;

public class CapacityUtils {

    private static final Logger _log = LoggerFactory.getLogger(CapacityUtils.class);

    public static final long BASE = 1024L;
    public static final long KB = BASE;
    public static final long MB = KB * BASE;
    public static final long GB = MB * BASE;

    public static final long kbToGb = GB / KB;

    private static final BigDecimal kbToGB_BD = new BigDecimal(kbToGb);
    private static final BigInteger kbToGB_BI = BigInteger.valueOf(kbToGb);

    private static final int DEFAULT_BATCH_SIZE = 1000;

    private static final String CAPACITY_STR = "capacity";
    private static final String USED_CAPACITY_STR = "usedCapacity";
    private static final String PROVISIONED_CAPACITY_STR = "provisionedCapacity";
    private static final String ALLOCATED_CAPACITY_STR = "allocatedCapacity";
    private static final String POOL_STR = "pool";
    private static final BigDecimal MINUS_ONE = new BigDecimal("-1");
    private static final long MINUS_ONE_LONG = -1;

    public static class Capacity {
        public double _usedCapacity;
        public double _provisionedCapacity;
    }

    /**
     * Names for storage capacity metrics.
     */
    public enum StorageMetrics {
        USABLE,
        FREE,
        SUBSCRIBED,
        USED,
        PERCENT_USED,
        NET_FREE,
        PERCENT_SUBSCRIBED
    }

    /**
     * Returns aggregate capacity data for storage pools. Capacity is returned
     * in the same unit as it was discovered and stored in storage pools (KB).
     * Calculation is using BigInteger type to prevent overflow.
     * 
     * @param storagePools storage pools
     * @return map of capacity metrics
     * @throws ServiceCodeException
     */
    public static Map<String, BigInteger> getPoolCapacityMetrics(StoragePool storagePool) {

        Map<String, BigInteger> capacityMetrics = new HashMap<String, BigInteger>();

        if (storagePool.getTotalCapacity() == 0) {
            _log.error("Storage pool: {} has zero total capacity, skipping the pool.",
                    storagePool.getId().toString());
            capacityMetrics.put(StorageMetrics.USABLE.toString(), BigInteger.ZERO);
            capacityMetrics.put(StorageMetrics.FREE.toString(), BigInteger.ZERO);
            capacityMetrics.put(StorageMetrics.SUBSCRIBED.toString(), BigInteger.ZERO);
        }
        else {
            BigInteger totalPoolCapacity = BigInteger.valueOf(storagePool.getTotalCapacity());
            BigInteger freePoolCapacity = BigInteger.valueOf(storagePool.getFreeCapacity());
            BigInteger subscribedPoolCapacity = BigInteger.ZERO;

            if (null != storagePool.getSubscribedCapacity()) {
                subscribedPoolCapacity = BigInteger.valueOf(storagePool.getSubscribedCapacity());
            }

            long usedPoolCapacity = storagePool.getTotalCapacity() - storagePool.getFreeCapacity();
            // calculate subscribed capacity only for block CoS
            if (PoolServiceType.block.toString().equalsIgnoreCase(storagePool.getPoolServiceType())) {
                if (storagePool.getSubscribedCapacity() < 0) { // device pool
                    subscribedPoolCapacity = BigInteger.valueOf(usedPoolCapacity);
                } else {
                    subscribedPoolCapacity = BigInteger.valueOf(storagePool.getSubscribedCapacity());
                }
            }

            capacityMetrics.put(StorageMetrics.USABLE.toString(), totalPoolCapacity);
            capacityMetrics.put(StorageMetrics.FREE.toString(), freePoolCapacity);
            capacityMetrics.put(StorageMetrics.SUBSCRIBED.toString(), subscribedPoolCapacity);
        }

        return capacityMetrics;
    }

    /**
     * Returns aggregate capacity data for storage pools. Capacity is returned
     * in the same unit as it was discovered and stored in storage pools (KB).
     * Calculation is using BigInteger type to prevent overflow.
     * 
     * @param storagePools storage pools
     * @param DbClient dbClient
     * @param VirtualPool vPool
     * @return map of capacity metrics
     * @throws ServiceCodeException
     */
    private static Map<String, BigInteger> getPoolCapacityMetrics(List<StoragePool> storagePools,
            VirtualPool vPool,
            DbClient dbClient,
            CoordinatorClient coordinator) {

        BigInteger totalCapacity = BigInteger.ZERO;
        BigInteger freeCapacity = BigInteger.ZERO;
        BigInteger subscribedCapacity = BigInteger.ZERO;
        BigInteger netFreeCapacity = BigInteger.ZERO;

        Map<String, BigInteger> capacityMetrics = new HashMap<String, BigInteger>();

        if (storagePools == null || storagePools.isEmpty()) {
            _log.warn("There are no pools passed to this method. Returning zero capacity metrics.");
            capacityMetrics.put(StorageMetrics.USABLE.toString(), totalCapacity);
            capacityMetrics.put(StorageMetrics.FREE.toString(), freeCapacity);
            capacityMetrics.put(StorageMetrics.SUBSCRIBED.toString(), subscribedCapacity);
            capacityMetrics.put(StorageMetrics.NET_FREE.toString(), netFreeCapacity);
            return capacityMetrics;
        }

        for (StoragePool storagePool : storagePools) {
            if (storagePool.getTotalCapacity() == 0) {
                _log.error(
                        "Storage pool: {} has zero total capacity, skipping the pool.",
                        storagePool.getId().toString());
                continue;
            }

            totalCapacity = totalCapacity.add(BigInteger.valueOf(storagePool.getTotalCapacity()));
            freeCapacity = freeCapacity.add(BigInteger.valueOf(storagePool.getFreeCapacity()));

            long usedPoolCapacity = storagePool.getTotalCapacity() - storagePool.getFreeCapacity();
            long subscribedPoolCapacity = 0;
            long netFreeSubscription = 0;

            boolean isBlock = VirtualPool.Type.block.name().equalsIgnoreCase(vPool.getType());
            // calculate subscribed capacity only for block CoS
            if (isBlock) {
                subscribedPoolCapacity = storagePool.getSubscribedCapacity();
                if (storagePool.getSubscribedCapacity() < 0) { // device pool
                    subscribedPoolCapacity = usedPoolCapacity;
                }
                subscribedCapacity = subscribedCapacity.add(BigInteger.valueOf(subscribedPoolCapacity));

                // 3) for thin pools check thin Pool Subscription percentage
                double maxSubscribedPercentage = CapacityMatcher.getMaxPoolSubscriptionPercentage(storagePool, coordinator);
                long poolMaximumSubscribedCapacity = (long) (storagePool.getTotalCapacity() *
                        maxSubscribedPercentage / 100);
                netFreeSubscription = poolMaximumSubscribedCapacity - subscribedPoolCapacity;
            } else { // if isFile
                // For the file system we do not have information on its subscribe limit.
                // Instead we used all available capacity as its netSubscription for thin provisioning.
                netFreeSubscription = storagePool.getTotalCapacity() - usedPoolCapacity;
            }

            // Compute Net Free capacity.

            // 1) Check that the maximum resource limit is not reached for the pool or the storage system
            if (MaxResourcesMatcher.checkPoolMaximumResourcesApproached(storagePool, dbClient, 0)) {
                // ignore this pool, it does not contain any net free capacity
                continue;
            }

            // 2) Check against Maximum Utilizaiton Percentage
            double maxPoolUtilizationPercentage = CapacityMatcher.getMaxPoolUtilizationPercentage(storagePool, coordinator);
            long poolMaximumCapacity = (long) (storagePool.getTotalCapacity()
                    * maxPoolUtilizationPercentage / 100);
            long netFreeUtilization = poolMaximumCapacity - usedPoolCapacity;
            if (netFreeUtilization < 0) {
                // ignore this pool
                continue;
            }

            // THICK virtual pool
            if (VirtualPool.ProvisioningType.Thick.name().equals(vPool.getSupportedProvisioningType())) {
                netFreeCapacity = netFreeCapacity.add(BigInteger.valueOf(netFreeUtilization));
            }
            else { // THIN virtual polls
                   // 3) Check against Maximum Subscription. Ignore if it pool is oversubscribed.
                if (netFreeSubscription > 0) {
                    netFreeCapacity = netFreeCapacity.add(BigInteger.valueOf(netFreeSubscription));
                }
                // else ignore.
            }
        }

        capacityMetrics.put(StorageMetrics.USABLE.toString(), totalCapacity);
        capacityMetrics.put(StorageMetrics.FREE.toString(), freeCapacity);
        capacityMetrics.put(StorageMetrics.SUBSCRIBED.toString(), subscribedCapacity);
        capacityMetrics.put(StorageMetrics.NET_FREE.toString(), netFreeCapacity);

        return capacityMetrics;
    }

    /**
     * Prepares capacity metrics. Transforms metrics to GB (required unit) and
     * calculates "used", "percent_used" and "percent_subscribed" metrics.
     * 
     * @param capacityMetrics
     * @return map with capacity metrics
     * @throws ServiceCodeException
     */
    public static Map<String, Long> preparePoolCapacityMetrics(Map<String, BigInteger> capacityMetrics) {

        // Get integer value of GBs rounded down.
        BigInteger totalCapacity = capacityMetrics.get(StorageMetrics.USABLE.toString());
        BigDecimal totalCapacityGb = new BigDecimal(totalCapacity).divideToIntegralValue(kbToGB_BD);
        long totalCapacityGbLong = totalCapacityGb.longValue();

        // Get integer value of GBs rounded down.
        BigInteger freeCapacity = capacityMetrics.get(StorageMetrics.FREE.toString());
        BigDecimal freeCapacityGb = new BigDecimal(freeCapacity).divideToIntegralValue(kbToGB_BD);
        long freeCapacityGbLong = freeCapacityGb.longValue();

        // Get integer value of GBs rounded up.
        BigInteger subscribedCapacity = capacityMetrics.get(StorageMetrics.SUBSCRIBED.toString());
        long subscribedCapacityGbLong;
        if (!subscribedCapacity.toString().equals(MINUS_ONE.toString())) {
            BigDecimal[] result = new BigDecimal(subscribedCapacity).divideAndRemainder(kbToGB_BD);
            subscribedCapacityGbLong = result[0].longValue();
            if (!result[1].equals(BigDecimal.ZERO)) {
                subscribedCapacityGbLong += 1;
            }
        } else {
            subscribedCapacityGbLong = MINUS_ONE_LONG;
        }

        // Calculate used capacity
        long usedCapacityGbLong = totalCapacityGbLong - freeCapacityGbLong;

        long percentUsed = 0;
        long percentSubscribed = 0;

        // if total capacity is 0, do not calculate percent metrics
        if (totalCapacityGbLong > 0) {
            // Calculate percent used --- integer value rounded up
            long temp = usedCapacityGbLong * 100;
            percentUsed = (temp % totalCapacityGbLong == 0) ? temp / totalCapacityGbLong : temp / totalCapacityGbLong + 1;

            // Calculate percent subscribed --- integer value rounded up
            temp = subscribedCapacityGbLong * 100;
            percentSubscribed = (temp % totalCapacityGbLong == 0) ? temp / totalCapacityGbLong : temp / totalCapacityGbLong + 1;
        }

        Map<String, Long> metrics = new HashMap<String, Long>();
        metrics.put(StorageMetrics.USABLE.toString(), totalCapacityGbLong);
        metrics.put(StorageMetrics.FREE.toString(), freeCapacityGbLong);
        metrics.put(StorageMetrics.USED.toString(), usedCapacityGbLong);
        metrics.put(StorageMetrics.SUBSCRIBED.toString(), subscribedCapacityGbLong);
        metrics.put(StorageMetrics.PERCENT_USED.toString(), percentUsed);
        metrics.put(StorageMetrics.PERCENT_SUBSCRIBED.toString(), percentSubscribed);

        return metrics;
    }

    /**
     * Convert the bytes to 2-digit decimal value after precision.
     * Ex: 1376787345L bytes => 1.28GB
     * 
     * @param size : size in bytes.
     * @return String: size in GB.
     */
    public static String convertBytesToGBInStr(Long size) {
        if (size == null) {
            return String.format("0");
        }
        return String.format("%.2f", (size / (double) GB));
    }

    /**
     * Convert the bytes to 2-digit decimal value after precision.
     * Ex: 1376787345L bytes => 1.28GB
     * 
     * @param size : size in bytes.
     * @return size in GB.
     */
    public static Double convertBytesToGB(Long size) {
        if (size == null) {
            return 0.0;
        }
        return size / (double) GB;
    }

    /**
     * Convert the KB to GB.
     * 
     * @param size : size in KB.
     * @return Long: size in GB.
     */
    public static Long convertKBToGB(Long size) {
        if (size == null) {
            return 0L;
        }
        return size / MB;
    }

    /**
     * Finds if a pool is file storage pool
     * 
     * @param storagePool
     * @return true for file pool, false block pools
     * @throws ServiceCodeException
     */
    public static boolean isFileStoragePool(StoragePool storagePool, DbClient dbClient) {
        URI storageSystemUri = storagePool.getStorageDevice();
        StorageSystem storageSystem = dbClient.queryObject(StorageSystem.class, storageSystemUri);
        ArgValidator.checkEntity(storageSystem, storageSystemUri, false);

        StorageSystem.Type storageSystemType = StorageSystem.Type.valueOf(storageSystem.getSystemType());
        if (storageSystemType.equals(DiscoveredDataObject.Type.isilon) || storageSystemType.equals(DiscoveredDataObject.Type.vnxfile)) {
            return true;
        } else {
            return false;
        }
    }

    private static Capacity getVirtualPoolCapacityForPools(DbClient dbClient, URI cosId, VirtualPool.Type cosType, Set<String> pools) {
        Capacity capacity = new Capacity();
        if (cosType == VirtualPool.Type.block) {
            URIQueryResultList list = new URIQueryResultList();
            dbClient.queryByConstraint(ContainmentConstraint.Factory.getContainedObjectsConstraint(cosId,
                    Volume.class, "virtualPool"), list);
            Iterator<URI> volumeList = CustomQueryUtility.filterDataObjectsFieldValueInSet(dbClient, Volume.class, POOL_STR,
                    list.iterator(), pools);

            SumPrimitiveFieldAggregator agg = CustomQueryUtility.aggregateActiveObject(
                    dbClient, Volume.class,
                    new String[] { PROVISIONED_CAPACITY_STR },
                    volumeList);
            capacity._provisionedCapacity += agg.getAggregate(PROVISIONED_CAPACITY_STR);

            agg = CustomQueryUtility.aggregateActiveObject(
                    dbClient, Volume.class,
                    new String[] { ALLOCATED_CAPACITY_STR },
                    volumeList);
            capacity._usedCapacity += agg.getAggregate(ALLOCATED_CAPACITY_STR);
        }
        else {
            URIQueryResultList list = new URIQueryResultList();
            dbClient.queryByConstraint(ContainmentConstraint.Factory.getContainedObjectsConstraint(cosId,
                    FileShare.class, "virtualPool"), list);
            Iterator<URI> fileList = CustomQueryUtility.filterDataObjectsFieldValueInSet(dbClient, FileShare.class, POOL_STR,
                    list.iterator(), pools);

            SumPrimitiveFieldAggregator agg = CustomQueryUtility.aggregateActiveObject(
                    dbClient, FileShare.class,
                    new String[] { CAPACITY_STR },
                    fileList);
            capacity._provisionedCapacity += agg.getAggregate(CAPACITY_STR);
            agg = CustomQueryUtility.aggregateActiveObject(
                    dbClient, FileShare.class,
                    new String[] { USED_CAPACITY_STR },
                    fileList);
            capacity._usedCapacity += agg.getAggregate(USED_CAPACITY_STR);
        }
        return capacity;
    }

    public static double getVirtualPoolCapacity(DbClient dbClient, URI cosId, VirtualPool.Type cosType) {

        double capacity = 0;

        if (cosType == VirtualPool.Type.block) {
            capacity = CustomQueryUtility.aggregatedPrimitiveField(dbClient, Volume.class, "virtualPool",
                    cosId.toString(), PROVISIONED_CAPACITY_STR).
                    getValue();
        } else if (cosType == VirtualPool.Type.file) {
            capacity = CustomQueryUtility.aggregatedPrimitiveField(dbClient, FileShare.class, "virtualPool",
                    cosId.toString(), CAPACITY_STR).
                    getValue();
        } else if (cosType == VirtualPool.Type.object) {
            capacity = CustomQueryUtility.aggregatedPrimitiveField(dbClient, Bucket.class, "virtualPool",
                    cosId.toString(), CAPACITY_STR).
                    getValue();
        }
        return capacity;
    }

    public static double getProjectCapacity(DbClient dbClient, URI projectID)
    {

        double capacity = CustomQueryUtility.aggregatedPrimitiveField(dbClient, Volume.class, "project",
                projectID.toString(), PROVISIONED_CAPACITY_STR).
                getValue();
        capacity += CustomQueryUtility.aggregatedPrimitiveField(dbClient, FileShare.class, "project",
                projectID.toString(), CAPACITY_STR).
                getValue();
        return capacity;
    }

    public static double getTenantCapacity(DbClient dbClient, URI tenantId)
    {
        double projectCap = 0.0;
        List<Project> projects = CustomQueryUtility.
                queryActiveResourcesByRelation(dbClient, tenantId, Project.class, "tenantOrg");
        for (Project project : projects) {
            projectCap += getProjectCapacity(dbClient, project.getId());
        }

        return projectCap;
    }

    public static long totalSubtenantQuota(DbClient dbClient, URI tenantId) {
        long totalQuota = 0L;
        URIQueryResultList subtenants = new URIQueryResultList();
        dbClient.queryByConstraint(
                ContainmentConstraint.Factory.getTenantOrgSubTenantConstraint(tenantId), subtenants);
        while (subtenants.iterator().hasNext()) {
            TenantOrg subtenant = dbClient.queryObject(TenantOrg.class, subtenants.iterator().next());
            if (!subtenant.getInactive() && subtenant.getQuotaEnabled()) {
                totalQuota += subtenant.getQuota();
            }
        }
        return totalQuota;
    }

    public static long totalProjectQuota(DbClient dbClient, URI tenantId) {
        long totalQuota = 0L;
        URIQueryResultList projects = new URIQueryResultList();
        dbClient.queryByConstraint(
                ContainmentConstraint.Factory.getTenantOrgProjectConstraint(tenantId), projects);
        while (projects.iterator().hasNext()) {
            Project project = dbClient.queryObject(Project.class, projects.iterator().next());
            if (!project.getInactive() && project.getQuotaEnabled()) {
                totalQuota += project.getQuota();
            }
        }
        return totalQuota;
    }

    public static boolean validateVirtualPoolQuota(DbClient dbClient, VirtualPool cos, long requestedSize) {
        if (!cos.getQuotaEnabled()) {
            return true;
        }
        else {
            double cap = getVirtualPoolCapacity(dbClient, cos.getId(), VirtualPool.Type.valueOf(cos.getType()));
            return ((double) cos.getQuota() * GB >= cap + requestedSize);
        }
    }

    public static boolean validateProjectQuota(DbClient dbClient, Project proj, long requestedSize) {
        if (!proj.getQuotaEnabled()) {
            return true;
        }
        else {
            double cap = getProjectCapacity(dbClient, proj.getId());
            return ((double) proj.getQuota() * GB >= cap + requestedSize);
        }
    }

    public static boolean validateTenantQuota(DbClient dbClient, TenantOrg tenant, long requestedSize) {
        if (!tenant.getQuotaEnabled()) {
            return true;
        }
        else {
            double cap = getTenantCapacity(dbClient, tenant.getId());
            return ((double) tenant.getQuota() * GB >= cap + requestedSize);
        }
    }

    public static boolean validateQuotasForProvisioning(DbClient dbClient,
            VirtualPool cos,
            Project proj,
            TenantOrg tenant,
            long requestedSize, String type) {
        _log.debug("Requested UnManagedVolume Capacity {}", requestedSize);
        if (cos != null && !CapacityUtils.validateVirtualPoolQuota(dbClient, cos, requestedSize)) {
            throw APIException.badRequests.insufficientQuotaForVirtualPool(cos.getLabel(), type);
        }
        if (proj != null && !CapacityUtils.validateProjectQuota(dbClient, proj, requestedSize)) {
            throw APIException.badRequests.insufficientQuotaForProject(proj.getLabel(), type);
        }
        if (proj != null && !proj.getQuotaEnabled() &&
                tenant != null && !CapacityUtils.validateTenantQuota(dbClient, tenant, requestedSize)) {
            throw APIException.badRequests.insufficientQuotaForTenant(tenant.getLabel(), type);
        }

        return true;
    }

    public static CapacityResponse getCapacityForVirtualPoolAndVirtualArray(VirtualPool vPool, URI vArrayId, DbClient dbClient,
            CoordinatorClient coordinator) {

        List<StoragePool> validPoolsOfvPool = VirtualPool.getValidStoragePools(vPool, dbClient, false);
        List<StoragePool> invalidPoolsOfvPool = VirtualPool.getInvalidStoragePools(vPool, dbClient);

        Map<String, Object> attributeMap = new ProvisioningAttributeMapBuilder(0L, vArrayId.toString(), 0L).buildMap();

        NeighborhoodsMatcher matcher = new NeighborhoodsMatcher();
        matcher.setCoordinatorClient(coordinator);
        matcher.setObjectCache(new ObjectLocalCache(dbClient));

        validPoolsOfvPool = matcher.runMatchStoragePools(validPoolsOfvPool, attributeMap);
        invalidPoolsOfvPool = matcher.runMatchStoragePools(invalidPoolsOfvPool, attributeMap);

        List<StoragePool> validPools = new ArrayList<StoragePool>();
        for (StoragePool pool : validPoolsOfvPool) {
            if (StoragePool.RegistrationStatus.REGISTERED.toString().equals(pool.getRegistrationStatus())
                    && CompatibilityStatus.COMPATIBLE.toString().equals(pool.getCompatibilityStatus())
                    && DiscoveryStatus.VISIBLE.toString().equals(pool.getDiscoveryStatus())) {
                validPools.add(pool);
            }
            else {
                invalidPoolsOfvPool.add(pool);
            }
        }

        Map<String, BigInteger> rawCapacityMetrics = getPoolCapacityMetrics(validPools, vPool, dbClient, coordinator);

        Set<String> poolSet = new HashSet<String>();
        for (StoragePool pool : validPools) {
            poolSet.add(pool.getId().toString());
        }
        Capacity capacity = getVirtualPoolCapacityForPools(dbClient, vPool.getId(),
                VirtualPool.Type.valueOf(vPool.getType()), poolSet);

        poolSet.clear();
        for (StoragePool pool : invalidPoolsOfvPool) {
            poolSet.add(pool.getId().toString());
        }
        Capacity invalidPoolCapacity = getVirtualPoolCapacityForPools(dbClient, vPool.getId(),
                VirtualPool.Type.valueOf(vPool.getType()), poolSet);

        // Free Capacity is rounded down
        BigInteger freeCapacity = rawCapacityMetrics.get(StorageMetrics.FREE.toString());
        freeCapacity = freeCapacity.divide(kbToGB_BI);
        long freeCapacityGb = freeCapacity.longValue();

        BigInteger netFreeCapacity = rawCapacityMetrics.get(StorageMetrics.NET_FREE.toString());
        netFreeCapacity = netFreeCapacity.divide(kbToGB_BI);
        long netFreeCapacityGb = netFreeCapacity.longValue();

        // 4) Check netFreeCapacity against Quota
        if (vPool.getQuotaEnabled()) {
            long netFreeQuota = vPool.getQuota() -
                    (long) ((capacity._provisionedCapacity + invalidPoolCapacity._provisionedCapacity) / GB);
            if (netFreeQuota < 0) {
                netFreeCapacityGb = 0;
            }
            else if (netFreeQuota < netFreeCapacityGb) {
                netFreeCapacityGb = netFreeQuota;
            }
        }

        // Used Capacity is rounded up.
        BigDecimal[] result = new BigDecimal(capacity._usedCapacity + invalidPoolCapacity._usedCapacity)
                .divideAndRemainder(new BigDecimal(GB));
        long usedCapacityGb = result[0].longValue();
        if (!result[1].equals(BigDecimal.ZERO)) {
            usedCapacityGb += 1;
        }

        // Subscribed Capacity is rounded up.
        result = new BigDecimal(capacity._provisionedCapacity + invalidPoolCapacity._provisionedCapacity)
                .divideAndRemainder(new BigDecimal(GB));
        long subscribedCapacityGb = result[0].longValue();
        if (!result[1].equals(BigDecimal.ZERO)) {
            subscribedCapacityGb += 1;
        }

        long totalCapacityGB = freeCapacityGb + usedCapacityGb;

        CapacityResponse response = new CapacityResponse();
        response.setFreeGb(Long.toString(freeCapacityGb));
        response.setNetFreeGb(Long.toString(netFreeCapacityGb));
        response.setProvissionedGb(Long.toString(subscribedCapacityGb));
        response.setUsedGb(Long.toString(usedCapacityGb));

        if (totalCapacityGB != 0) {
            result = new BigDecimal(subscribedCapacityGb * 100).divideAndRemainder(new BigDecimal(
                    totalCapacityGB));
            int percentage = result[0].intValue();
            if (!result[1].equals(BigDecimal.ZERO)) {
                percentage += 1;
            }
            response.setPercentProvisioned(Integer.toString(percentage));

            result = new BigDecimal(usedCapacityGb * 100).divideAndRemainder(new BigDecimal(
                    totalCapacityGB));
            percentage = result[0].intValue();
            if (!result[1].equals(BigDecimal.ZERO)) {
                percentage += 1;
            }
            response.setPercentUsed(Integer.toString(percentage));
        }
        return response;
    }
}
