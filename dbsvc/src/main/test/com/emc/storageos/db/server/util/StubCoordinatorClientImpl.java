/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.server.util;

import com.emc.storageos.coordinator.client.model.Constants;
import com.emc.storageos.coordinator.client.model.CoordinatorSerializable;
import com.emc.storageos.coordinator.client.model.DbVersionInfo;
import com.emc.storageos.coordinator.client.model.MigrationStatus;
import com.emc.storageos.coordinator.client.model.SiteState;
import com.emc.storageos.coordinator.client.service.*;
import com.emc.storageos.coordinator.client.service.impl.CoordinatorClientImpl;
import com.emc.storageos.coordinator.client.service.impl.CoordinatorClientInetAddressMap;
import com.emc.storageos.coordinator.client.service.impl.DistributedLockQueueTaskConsumer;
import com.emc.storageos.coordinator.client.service.impl.DistributedQueueConsumer;
import com.emc.storageos.coordinator.common.Configuration;
import com.emc.storageos.coordinator.common.Service;
import com.emc.storageos.coordinator.common.impl.ServiceImpl;
import com.emc.storageos.coordinator.exceptions.CoordinatorException;
import com.emc.storageos.model.property.PropertyInfo;
import com.emc.vipr.model.sys.ClusterInfo;

import org.apache.curator.framework.recipes.leader.LeaderLatch;
import org.apache.curator.framework.recipes.leader.LeaderSelector;
import org.apache.curator.framework.recipes.leader.LeaderSelectorListener;
import org.apache.curator.framework.recipes.locks.InterProcessLock;
import org.apache.curator.framework.recipes.locks.InterProcessReadWriteLock;
import org.apache.curator.framework.recipes.locks.InterProcessSemaphoreMutex;
import org.apache.curator.framework.recipes.queue.QueueSerializer;

import java.io.IOException;
import java.net.URI;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static com.emc.storageos.coordinator.client.model.Constants.*;

/**
 * Dummy coordinator client for use with dbsvc unit tests
 */
public class StubCoordinatorClientImpl extends CoordinatorClientImpl {
    private final Service _dbinfo;
    private DbVersionInfo dbVersionInfo;
    private CoordinatorClientInetAddressMap inetAddessLookupMap;
    private Map<String, Configuration> _configMap = new HashMap<String, Configuration>();
    private Map<String, InterProcessLock> _locks = new HashMap<String, InterProcessLock>();
    private static Properties defaultProperties;
    private static Properties ovfProperties;

    public StubCoordinatorClientImpl(URI endpoint) {
        ServiceImpl svc = new ServiceImpl();
        svc.setId(UUID.randomUUID().toString());
        svc.setEndpoint(endpoint);
        svc.setName("dbsvc");
        svc.setVersion("1");
        _dbinfo = svc;

        // Suppress Sonar violation of Lazy initialization of static fields should be synchronized
        // Junit test will be called in single thread by default, it's safe to ignore this violation
        defaultProperties = new Properties(); // NOSONAR ("squid:S2444")
        defaultProperties.put("controller_max_pool_utilization_percentage", "300");
    }

    public static void setDefaultProperties(Properties defaults) {
        // Suppress Sonar violation of Lazy initialization of static fields should be synchronized
        // Junit test will be called in single thread by default, it's safe to ignore this violation
        defaultProperties = defaults; // NOSONAR ("squid:S2444")
    }

    public static void setOvfProperties(Properties ovfProps) {
        // Suppress Sonar violation of Lazy initialization of static fields should be synchronized
        // Junit test will be called in single thread by default, it's safe to ignore this violation
        ovfProperties = ovfProps; // NOSONAR ("squid:S2444")
    }

    @Override
    public <T> T locateService(Class<T> clazz, String name, String version, String tag, String endpointKey) throws CoordinatorException {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<Service> locateAllServices(String name, String version, String tag, String endpointKey) throws CoordinatorException {
        return Arrays.asList(_dbinfo);
    }

    @Override
    public List<Service> locateAllSvcsAllVers(String name) throws CoordinatorException {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> DistributedQueue<T> getQueue(String name, DistributedQueueConsumer<T> consumer, QueueSerializer<T> serializer,
            int maxThreads, int maxItem) throws CoordinatorException {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> DistributedQueue<T>
            getQueue(String name, DistributedQueueConsumer<T> consumer, QueueSerializer<T> serializer, int maxThreads)
                    throws CoordinatorException {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> DistributedLockQueueManager getLockQueue(DistributedLockQueueTaskConsumer<T> consumer) throws CoordinatorException {
        throw new UnsupportedOperationException();
    }

    @Override
    public WorkPool getWorkPool(String name, WorkPool.WorkAssignmentListener listener) throws CoordinatorException {
        throw new UnsupportedOperationException();
    }

    @Override
    public DistributedSemaphore getSemaphore(String name, int maxPermits) throws CoordinatorException {
        throw new UnsupportedOperationException();
    }

    @Override
    public InterProcessSemaphoreMutex getSemaphoreLock(String name) throws CoordinatorException {
        throw new UnsupportedOperationException();
    }

    @Override
    public InterProcessReadWriteLock getReadWriteLock(String name) throws CoordinatorException {
        throw new UnsupportedOperationException();
    }

    @Override
    public InterProcessLock getLock(final String name) throws CoordinatorException {
        return new InterProcessLock() {
            private final String _lockName = name;

            @Override
            public void acquire() throws Exception {
                synchronized (_locks) {
                    if (_locks.containsKey(_lockName)) {
                        throw new Exception("cannot get lock");
                    }
                    _locks.put(_lockName, this);
                }
            }

            @Override
            public boolean acquire(long time, TimeUnit unit) throws Exception {
                acquire();
                return true;
            }

            @Override
            public void release() throws Exception {
                synchronized (_locks) {
                    InterProcessLock locked = _locks.get(_lockName);
                    if (locked == this) {
                        _locks.remove(_lockName);
                    }
                }
            }

            @Override
            public boolean isAcquiredInThisProcess() {
                InterProcessLock locked;
                synchronized (_locks) {
                    locked = _locks.get(_lockName);
                }
                if (locked == this) {
                    return true;
                }
                return false;
            }
        };
    }

    @Override
    public DistributedPersistentLock getPersistentLock(String name) throws CoordinatorException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void start() throws IOException {
    }

    @Override
    public void stop() {
    }

    @Override
    public boolean isConnected() {
        return true;
    }

    private String getKey(String kind, String id) {
        return String.format("%s/%s", kind, id);
    }

    @Override
    public void persistServiceConfiguration(Configuration... config) throws CoordinatorException {
        for (int i = 0; i < config.length; i++) {
            Configuration c = config[i];
            _configMap.put(getKey(c.getKind(), c.getId()), c);
        }
    }

    @Override
    public void removeServiceConfiguration(Configuration... config) throws CoordinatorException {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<Configuration> queryAllConfiguration(String kind) throws CoordinatorException {
        List<Configuration> configs = new ArrayList<Configuration>();
        for (String key : _configMap.keySet()) {
            if (key.startsWith(kind)) {
                configs.add(_configMap.get(key));
            }
        }
        return configs;
    }

    @Override
    public Configuration queryConfiguration(String kind, String id) throws CoordinatorException {
        return _configMap.get(getKey(kind, id));
    }

    @Override
    public void setConnectionListener(ConnectionStateListener listener) {
        throw new UnsupportedOperationException();
    }

    @Override
    public PropertyInfo getPropertyInfo() throws CoordinatorException {
        PropertyInfo info = new PropertyInfo();
        info.setProperties((Map) defaultProperties);
        return info;
    }

    /**
     * Merge properties
     * 
     * @param defaultProps
     * @param overrideProps
     * @return map containing key, value pair
     */
    public static Map<String, String> mergeProps(Map<String, String> defaultProps, Map<String, String> overrideProps) {
        Map<String, String> mergedProps = new HashMap<String, String>(defaultProps);
        for (Map.Entry<String, String> entry : overrideProps.entrySet()) {
            mergedProps.put(entry.getKey(), entry.getValue());
        }
        return mergedProps;
    }

    @Override
    public boolean isStorageProductLicensed(LicenseType licenseType) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public DistributedDataManager createDistributedDataManager(String basePath)
            throws CoordinatorException {
        throw new UnsupportedOperationException();
    }

    @Override
    public DistributedDataManager getWorkflowDataManager()
            throws CoordinatorException {
        throw new UnsupportedOperationException();
    }

    @Override
    public LeaderSelector getLeaderSelector(String leaderPath, LeaderSelectorListener listener) throws CoordinatorException {
        throw new UnsupportedOperationException();
    }

    @Override
    public LeaderLatch getLeaderLatch(String latchPath) throws CoordinatorException {
        throw new UnsupportedOperationException();
    }

    @Override
    public DistributedDataManager createDistributedDataManager(String basePath,
            long maxNodes) throws CoordinatorException {
        throw new UnsupportedOperationException();
    }

    @Override
    public ClusterInfo.ClusterState getControlNodesState() {
        throw new UnsupportedOperationException();
    }

    @Override
    public ClusterInfo.ClusterState getControlNodesState(String siteId, int nodeCount) {
        return null;
    }

    @Override
    public <T extends CoordinatorSerializable> T getNodeInfo(Service service, String nodeId, Class<T> clazz)
            throws Exception {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T extends CoordinatorSerializable> T queryRuntimeState(String key, Class<T> clazz) throws CoordinatorException {
        return null; 
    }

    @Override
    public <T extends CoordinatorSerializable> void persistRuntimeState(String key, T state) throws CoordinatorException {
        return;
    }

    @Override
    public <T extends CoordinatorSerializable> Map<Service,
            T> getAllNodeInfos(Class<T> clazz, Pattern nodeIdFilter) throws Exception {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T extends CoordinatorSerializable> T getTargetInfo(final Class<T> clazz) throws CoordinatorException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setTargetInfo(final CoordinatorSerializable info) throws CoordinatorException {
        
    }
    
    @Override
    public <T extends CoordinatorSerializable> T getTargetInfo(final Class<T> clazz, String id, String kind)
            throws CoordinatorException {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getUpgradeLockOwner(String lockId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setDbVersionInfo(DbVersionInfo info) {
        dbVersionInfo = info;
    }

    @Override
    public String getCurrentDbSchemaVersion() {
        Configuration config = queryConfiguration(DB_CONFIG, GLOBAL_ID);
        if (config == null) {
            return null;
        }

        return config.getConfig(SCHEMA_VERSION);
    }

    @Override
    public String getTargetDbSchemaVersion() {
        return dbVersionInfo.getSchemaVersion();
    }

    @Override
    public MigrationStatus getMigrationStatus() {
        Configuration config = queryConfiguration(getVersionedDbConfigPath(Constants.DBSVC_NAME, getTargetDbSchemaVersion()), GLOBAL_ID);
        if (config == null || config.getConfig(MIGRATION_STATUS) == null) {
            return null;
        }
        MigrationStatus status = MigrationStatus.valueOf(config.getConfig(MIGRATION_STATUS));
        return status;
    }

    @Override
    public String getVersionedDbConfigPath(String serviceName, String version) {
        String kind = DB_CONFIG;

        if (version != null) {
            kind = String.format("%s/%s", kind, version);
        }

        return kind;
    }

    @Override
    public boolean isClusterUpgradable() {
        throw new UnsupportedOperationException();
    }

    public CoordinatorClientInetAddressMap getInetAddessLookupMap() {
        return inetAddessLookupMap;
    }

    @Override
    public void setInetAddessLookupMap(CoordinatorClientInetAddressMap inetAddessLookupMap) {
        this.inetAddessLookupMap = inetAddessLookupMap;
        this.inetAddessLookupMap.setCoordinatorClient(this);
    }

    @Override
    public void addNodeListener(NodeListener listener) throws Exception {
        throw new UnsupportedOperationException();
    }

    @Override
    public void removeNodeListener(NodeListener listener) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isDistributedOwnerLockAvailable(String lockPath) throws Exception {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setDistributedOwnerLockAroundHook(DistributedAroundHook ownerLockAroundHook) {
        throw new UnsupportedOperationException();
    }

    @Override
    public DistributedAroundHook getDistributedOwnerLockAroundHook() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getDbConfigPath(String serviceName) {
        return DB_CONFIG;
    }

    @Override
    public boolean isDbSchemaVersionChanged() {
        String currentVersion = getCurrentDbSchemaVersion();
        String targetVersion = getTargetDbSchemaVersion();
        return !(currentVersion.equals(targetVersion));
    }
    
    @Override
    public String getSiteId() {
    	return "testsiteid";
    }

    @Override
    public void addSite(String siteId) {
        throw new UnsupportedOperationException();
    }
    
    @Override
    public void removeRuntimeState(String key) throws CoordinatorException {
    }
    
    @Override
    public InterProcessLock getSiteLocalLock(String name) throws CoordinatorException {
        return this.getLock(name);
    }

    @Override
    public void removeServiceConfiguration(String siteId, Configuration... configs) throws CoordinatorException {
        this.removeServiceConfiguration(configs);
    }

    @Override
    public List<Configuration> queryAllConfiguration(String siteId, String kind) throws CoordinatorException {
        return this.queryAllConfiguration(kind);
    }

    @Override
    public void persistServiceConfiguration(String siteId, Configuration... configs) throws CoordinatorException {
        this.persistServiceConfiguration(configs);
    }

    @Override
    public List<Service> locateAllServices(String siteId, String name, String version, String tag, String endpointKey)
            throws CoordinatorException {
        return this.locateAllServices(name, version, tag, endpointKey);
    }

    @Override
    public Configuration queryConfiguration(String siteId, String kind, String id) throws CoordinatorException {
        return this.queryConfiguration(kind, id);
    }

    @Override
    public void setTargetInfo(String siteId, CoordinatorSerializable info) throws CoordinatorException {
        this.setTargetInfo(info);
    }
}
