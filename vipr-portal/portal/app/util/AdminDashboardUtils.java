/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package util;

import static util.BourneUtil.getSysClient;
import static util.BourneUtil.getViprClient;

import java.util.Date;
import java.util.List;
import java.util.concurrent.Callable;

import jobs.vipr.CallableHelper;
import play.cache.Cache;
import play.libs.F.Promise;

import com.emc.vipr.client.ViPRSystemClient;
import com.emc.vipr.client.core.BulkResources;
import com.emc.vipr.model.sys.ClusterInfo;
import com.emc.vipr.model.sys.healthmonitor.NodeHealth;
import com.emc.vipr.model.sys.healthmonitor.NodeStats;
import com.emc.vipr.model.sys.healthmonitor.StorageStats;
import com.emc.vipr.model.sys.licensing.License;
import com.emc.vipr.model.sys.recovery.DbRepairStatus;

public class AdminDashboardUtils {

    private static final String OBJECT_CONFIGURED = "object.configured";
    private static final String FABRIC_CONFIGURED = "fabric.configured";
    private static final String CONFIGURED_EXPIRES = "5mn";

    private static String NODE_HEALTH_LIST_KEY = "NODE_HEALTH_LIST_KEY";
    private static String NODE_HEALTH_LIST_EXPIRES = "15s";

    private static String NODE_STATS_LIST_KEY = "NODE_STATS_LIST_KEY";
    private static String NODE_STATS_LIST_EXPIRES = "3mn";

    private static String CLUSTER_INFO_KEY = "CLUSTER_INFO_KEY";

    private static String LICENSE_KEY = "LICENSE_KEY";

    private static String ASSET_COUNT_EXPIRES = "1mn";

    private static String FABRIC_NODE_COUNT_KEY = "FABRIC_NODE_COUNT_KEY";
    private static String FABRIC_STATUS_KEY = "FABRIC_STATUS_KEY";
    private static String FABRIC_CAPACITY_KEY = "FABRIC_CAPACITY_KEY";
    private static String STORAGE_ARRAY_COUNT_KEY = "STORAGE_ARRAY_COUNT_KEY";
    private static String SMIS_PROVIDER_COUNT_KEY = "SMIS_PROVIDER_COUNT_KEY";
    private static String FABRIC_MANAGER_COUNT_KEY = "FABRIC_MANAGER_COUNT_KEY";
    private static String DATA_PROTECTION_SYSTEM_COUNT_KEY = "DATA_PROTECTION_SYSTEM_COUNT_KEY";
    private static String COMPUTE_SYSTEM_COUNT_KEY = "COMPUTE_SYSTEM_COUNT_KEY";
    private static String COMPUTE_IMAGE_COUNT_KEY = "COMPUTE_IMAGE_COUNT_KEY";
    private static String COMPUTE_IMAGE_SERVER_COUNT_KEY = "COMPUTE_IMAGE_SERVER_COUNT_KEY";
    private static String HOST_COUNT_KEY = "HOST_COUNT_KEY";
    private static String VCENTER_COUNT_KEY = "VCENTER_COUNT_KEY";
    private static String CLUSTER_COUNT_KEY = "CLUSTER_COUNT_KEY";
    private static String VIRTUAL_STORAGE_ARRAY_COUNT_KEY = "VIRTUAL_STORAGE_ARRAY_COUNT_KEY";
    private static String BLOCK_VIRTUAL_POOL_COUNT_KEY = "BLOCK_VIRTUAL_POOL_COUNT_KEY";
    private static String FILE_VIRTUAL_POOL_COUNT_KEY = "FILE_VIRTUAL_POOL_COUNT_KEY";
    private static String OBJECT_VIRTUAL_POOL_COUNT_KEY = "OBJECT_VIRTUAL_POOL_COUNT_KEY";
    private static String COMPUTE_VIRTUAL_POOL_COUNT_KEY = "COMPUTE_VIRTUAL_POOL_COUNT_KEY";
    private static String DATASTORE_COUNT_KEY = "DATASTORE_COUNT_KEY";
    private static String NETWORKS_COUNT_KEY = "NETWORKS_COUNT_KEY";

    private static final String LAST_UPDATED_SUFFIX = "LU";

    private static <T> T cacheValue(String key, T value, String expiry) {
        Cache.set(key, value, expiry);
        Cache.set(key + LAST_UPDATED_SUFFIX, new Date());
        return value;
    }

    private static void clearValue(String key) {
        Cache.delete(key);
        Cache.delete(key + LAST_UPDATED_SUFFIX);
    }

    private static Date getLastUpdated(String key) {
        return (Date) Cache.get(key + LAST_UPDATED_SUFFIX);
    }

    public static Promise<StorageStats> storageStats() {
        return CallableHelper.createPromise(new StorageStatsCall(getSysClient()));
    }

    public static Promise<List<NodeHealth>> nodeHealthList() {
        return new NodeHealthList(getSysClient()).asPromise();
    }

    public static Promise<List<NodeStats>> nodeStatsList(ViPRSystemClient client) {
        return new NodeStatsList(getSysClient()).asPromise();
    }

    public static ClusterInfo getClusterInfo() {
        return BourneUtil.getSysClient().upgrade().getClusterInfo();
    }

    public static DbRepairStatus gethealthdb() {
        return BourneUtil.getSysClient().control().getdbhealth();
    }

    public static Promise<ClusterInfo> clusterInfo() {
        return CallableHelper.createPromise(new ClusterInfoCall(getSysClient()));
    }

    public static License getLicense() {
        return LicenseUtils.getLicense();
    }

    public static Promise<Integer> storageArrayCount() {
        return new BulkResourceCount(STORAGE_ARRAY_COUNT_KEY, getViprClient().storageSystems()).asPromise();
    }

    public static Promise<Integer> storageProviderCount() {
        return new BulkResourceCount(SMIS_PROVIDER_COUNT_KEY, getViprClient().storageProviders()).asPromise();
    }

    public static Promise<Integer> fabricManagerCount() {
        return new BulkResourceCount(FABRIC_MANAGER_COUNT_KEY, getViprClient().networkSystems()).asPromise();
    }

    public static Promise<Integer> dataProtectionSystemCount() {
        return new BulkResourceCount(DATA_PROTECTION_SYSTEM_COUNT_KEY, getViprClient().protectionSystems()).asPromise();
    }

    public static Promise<Integer> computeSystemCount() {
        return new BulkResourceCount(COMPUTE_SYSTEM_COUNT_KEY, getViprClient().computeSystems()).asPromise();
    }

    public static Promise<Integer> computeImageCount() {
        return new BulkResourceCount(COMPUTE_IMAGE_COUNT_KEY, getViprClient().computeImages()).asPromise();
    }

    public static Promise<Integer> computeImageServerCount() {
        return new BulkResourceCount(COMPUTE_IMAGE_SERVER_COUNT_KEY, getViprClient().computeImageServers()).asPromise();
    }

    public static Promise<Integer> hostCount() {
        return new BulkResourceCount(HOST_COUNT_KEY, getViprClient().hosts()).asPromise();
    }

    public static Promise<Integer> vCenterCount() {
        return new BulkResourceCount(VCENTER_COUNT_KEY, getViprClient().vcenters()).asPromise();
    }

    public static Promise<Integer> clusterCount() {
        return new BulkResourceCount(CLUSTER_COUNT_KEY, getViprClient().clusters()).asPromise();
    }

    public static Promise<Integer> virutalStorageArrayCount() {
        return new BulkResourceCount(VIRTUAL_STORAGE_ARRAY_COUNT_KEY, getViprClient().varrays()).asPromise();
    }

    public static Promise<Integer> blockVirtualPoolCount() {
        return new BulkResourceCount(BLOCK_VIRTUAL_POOL_COUNT_KEY, getViprClient().blockVpools()).asPromise();
    }

    public static Promise<Integer> fileVirtualPoolCount() {
        return new BulkResourceCount(FILE_VIRTUAL_POOL_COUNT_KEY, getViprClient().fileVpools()).asPromise();
    }
    
    public static Promise<Integer> objectVirtualPoolCount() {
    	return new BulkResourceCount(OBJECT_VIRTUAL_POOL_COUNT_KEY, getViprClient().objectVpools()).asPromise();
    }

    public static Promise<Integer> computeVirtualPoolCount() {
        return new BulkResourceCount(COMPUTE_VIRTUAL_POOL_COUNT_KEY, getViprClient().computeVpools()).asPromise();
    }

    public static Promise<Integer> networksCount() {
        return new BulkResourceCount(NETWORKS_COUNT_KEY, getViprClient().networks()).asPromise();
    }

    public static Date getNodeHealthListLastUpdated() {
        return getLastUpdated(NODE_HEALTH_LIST_KEY);
    }

    public static Date getNodeStatsListLastUpdated() {
        return getLastUpdated(NODE_STATS_LIST_KEY);
    }

    public static Date getClusterInfoLastUpdated() {
        return getLastUpdated(CLUSTER_INFO_KEY);
    }

    public static Date getLicenseLastUpdated() {
        return getLastUpdated(LICENSE_KEY);
    }

    public static Date getStorageArrayCountLastUpdated() {
        return getLastUpdated(STORAGE_ARRAY_COUNT_KEY);
    }

    public static Date getVirtualStorageArrayCountLastUpdated() {
        return getLastUpdated(VIRTUAL_STORAGE_ARRAY_COUNT_KEY);
    }

    public static void clearNodeHealthListCache() {
        clearValue(NODE_HEALTH_LIST_KEY);
    }

    public static void clearClusterInfoCache() {
        clearValue(CLUSTER_INFO_KEY);
    }

    public static void clearLicenseCache() {
        clearValue(LICENSE_KEY);
    }

    public static void clearCache() {
        clearNodeHealthListCache();
        clearClusterInfoCache();
        clearLicenseCache();
    }

    /**
     * Caching wrapper around a call that will cache the result, and only perform the call if the value is not
     * already in the cache.
     * 
     * @param <T>
     */
    public static abstract class CachingCallable<T> implements Callable<T> {
        private String key;
        private String expiration;

        public CachingCallable(String key) {
            this(key, null);
        }

        public CachingCallable(String key, String expiration) {
            this.key = key;
            this.expiration = expiration;
        }

        @Override
        public T call() throws Exception {
            @SuppressWarnings("unchecked")
            T value = (T) Cache.get(key);
            if (value == null) {
                value = doCall();
                cacheValue(key, value, expiration);
            }
            return value;
        }

        protected abstract T doCall() throws Exception;

        public Promise<T> asPromise() {
            return CallableHelper.createPromise(this);
        }
    }

    public static class BulkResourceCount extends CachingCallable<Integer> {
        private BulkResources<?> resources;

        public BulkResourceCount(String key, BulkResources<?> resources) {
            super(key, ASSET_COUNT_EXPIRES);
            this.resources = resources;
        }

        @Override
        protected Integer doCall() throws Exception {
            return resources.listBulkIds().size();
        }
    }

    public static class NodeHealthList extends CachingCallable<List<NodeHealth>> {
        private ViPRSystemClient client;

        public NodeHealthList(ViPRSystemClient client) {
            super(NODE_HEALTH_LIST_KEY, NODE_HEALTH_LIST_EXPIRES);
            this.client = client;
        }

        @Override
        protected List<NodeHealth> doCall() throws Exception {
            return MonitorUtils.getNodeHealth(client);
        }
    }

    public static class NodeStatsList extends CachingCallable<List<NodeStats>> {
        private ViPRSystemClient client;

        public NodeStatsList(ViPRSystemClient client) {
            super(NODE_STATS_LIST_KEY, NODE_STATS_LIST_EXPIRES);
            this.client = client;
        }

        @Override
        protected List<NodeStats> doCall() throws Exception {
            return MonitorUtils.getNodeStats(client);
        }
    }

    public static class ClusterInfoCall implements Callable<ClusterInfo> {
        private ViPRSystemClient client;

        public ClusterInfoCall(ViPRSystemClient client) {
            this.client = client;
        }

        @Override
        public ClusterInfo call() throws Exception {
            return client.upgrade().getClusterInfo();
        }
    }

    public static class StorageStatsCall implements Callable<StorageStats> {
        private ViPRSystemClient client;

        public StorageStatsCall(ViPRSystemClient client) {
            this.client = client;
        }

        @Override
        public StorageStats call() throws Exception {
            return client.health().getStorageStats();
        }
    }
}
