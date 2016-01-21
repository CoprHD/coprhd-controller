/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.auth;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import org.apache.curator.framework.recipes.leader.LeaderLatch;
import org.apache.curator.framework.recipes.leader.LeaderSelector;
import org.apache.curator.framework.recipes.leader.LeaderSelectorListener;
import org.apache.curator.framework.recipes.locks.InterProcessLock;
import org.apache.curator.framework.recipes.locks.InterProcessReadWriteLock;
import org.apache.curator.framework.recipes.locks.InterProcessSemaphoreMutex;
import org.apache.curator.framework.recipes.queue.QueueSerializer;

import com.emc.storageos.coordinator.client.model.CoordinatorSerializable;
import com.emc.storageos.coordinator.client.model.DbVersionInfo;
import com.emc.storageos.coordinator.client.model.MigrationStatus;
import com.emc.storageos.coordinator.client.model.SiteState;
import com.emc.storageos.coordinator.client.service.*;
import com.emc.storageos.coordinator.client.service.ConnectionStateListener;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.coordinator.client.service.DistributedAroundHook;
import com.emc.storageos.coordinator.client.service.DistributedDataManager;
import com.emc.storageos.coordinator.client.service.DistributedLockQueueManager;
import com.emc.storageos.coordinator.client.service.DistributedPersistentLock;
import com.emc.storageos.coordinator.client.service.DistributedQueue;
import com.emc.storageos.coordinator.client.service.DistributedSemaphore;
import com.emc.storageos.coordinator.client.service.NodeListener;
import com.emc.storageos.coordinator.client.service.WorkPool;
import com.emc.storageos.coordinator.client.service.WorkPool.WorkAssignmentListener;
import com.emc.storageos.coordinator.client.service.impl.CoordinatorClientImpl;
import com.emc.storageos.coordinator.client.service.impl.CoordinatorClientInetAddressMap;
import com.emc.storageos.coordinator.client.service.impl.DistributedLockQueueTaskConsumer;
import com.emc.storageos.coordinator.client.service.impl.DistributedQueueConsumer;
import com.emc.storageos.coordinator.common.Configuration;
import com.emc.storageos.coordinator.common.Service;
import com.emc.storageos.coordinator.exceptions.CoordinatorException;
import com.emc.storageos.model.property.PropertyInfo;
import com.emc.vipr.model.sys.ClusterInfo;

/**
 * Stub coordinator class for use with unit tests
 */
public class TestCoordinator extends CoordinatorClientImpl {

    private ConcurrentHashMap<String, HashMap<String, Configuration>> configurations =
            new ConcurrentHashMap<String, HashMap<String, Configuration>>();

    @Override
    public <T> T locateService(Class<T> clazz, String name, String version,
            String tag, String endpointKey) throws CoordinatorException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public List<Service> locateAllServices(String name, String version,
            String tag, String endpointKey) throws CoordinatorException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public List<Service> locateAllSvcsAllVers(String name) throws CoordinatorException {
        return null;
    }

    @Override
    public <T> DistributedQueue<T> getQueue(String name,
            DistributedQueueConsumer<T> consumer,
            QueueSerializer<T> serializer, int maxThreads, int maxItem)
            throws CoordinatorException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public <T> DistributedQueue<T> getQueue(String name,
            DistributedQueueConsumer<T> consumer,
            QueueSerializer<T> serializer, int maxThreads) throws CoordinatorException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public <T> DistributedLockQueueManager getLockQueue(DistributedLockQueueTaskConsumer<T> consumer) throws CoordinatorException {
        return null;
    }

    @Override
    public WorkPool getWorkPool(String name, WorkAssignmentListener listener)
            throws CoordinatorException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public DistributedSemaphore getSemaphore(String name, int maxPermits)
            throws CoordinatorException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public InterProcessSemaphoreMutex getSemaphoreLock(String name) throws CoordinatorException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public InterProcessReadWriteLock getReadWriteLock(String name) throws CoordinatorException {
        // TODO Auto-generated method stub
        return null;
    }

    private HashMap<String, TestLock> _locks = new HashMap<String, TestLock>();

    @Override
    public synchronized InterProcessLock getLock(String name) throws CoordinatorException {
        if (_locks.containsKey(name)) {
            return _locks.get(name);
        } else {
            TestLock l = new TestLock();
            _locks.put(name, l);
            return l;
        }
    }

    @Override
    public DistributedPersistentLock getPersistentLock(String name)
            throws CoordinatorException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void start() throws IOException {
        // TODO Auto-generated method stub

    }

    @Override
    public void stop() {
        // TODO Auto-generated method stub
    }

    @Override
    public boolean isConnected() {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public void persistServiceConfiguration(Configuration... config)
            throws CoordinatorException {
        HashMap<String, Configuration> configMap = configurations.get(config[0].getKind());
        if (configMap == null) {
            configMap = new HashMap<String, Configuration>();
        }
        configMap.put(config[0].getId(), config[0]);
        configurations.put(config[0].getKind(), configMap);
    }

    @Override
    public void removeServiceConfiguration(Configuration... config)
            throws CoordinatorException {
        if (config == null) {
            configurations.clear();
            return;
        }
        HashMap<String, Configuration> configMap = configurations.get(config[0].getKind());
        configMap.remove(configMap.get(config[0].getId()));
    }

    @Override
    public List<Configuration> queryAllConfiguration(String kind)
            throws CoordinatorException {
        HashMap<String, Configuration> configMap = configurations.get(kind);
        if (configMap != null) {
            Set<Entry<String, Configuration>> asSet = configMap.entrySet();
            ArrayList<Configuration> configs = new ArrayList<Configuration>();
            for (Entry<String, Configuration> e : asSet) {
                configs.add(e.getValue());
            }
            return configs;
        }
        return null;
    }

    @Override
    public Configuration queryConfiguration(String kind, String id)
            throws CoordinatorException {
        HashMap<String, Configuration> configMap = configurations.get(kind);
        if (configMap != null) {
            return configMap.get(id);
        }
        return null;
    }

    @Override
    public void setConnectionListener(ConnectionStateListener listener) {
        // TODO Auto-generated method stub

    }

    @Override
    public PropertyInfo getPropertyInfo() throws CoordinatorException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void setInetAddessLookupMap(CoordinatorClientInetAddressMap inetAddessLookupMap) {
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
    public CoordinatorClientInetAddressMap getInetAddessLookupMap() {
        return null;
    }

    private class TestConfiguration implements Configuration {

        private final String _updateTime = null;
        Properties _properties = new Properties();
        private String _kind = null;
        private String _id = null;

        @Override
        public String getKind() {
            // TODO Auto-generated method stub
            return null;
        }

        public void setKind(String k) {
            _kind = k;
        }

        @Override
        public String getId() {
            // TODO Auto-generated method stub
            return _id;
        }

        public void setId(String id) {
            _id = id;
        }

        @Override
        public String getConfig(String key) {
            return _properties.getProperty(key);
        }

        @Override
        public void setConfig(String key, String val) {
            _properties.setProperty(key, val);
            return;
        }

        @Override
        public void removeConfig(String key) {
            _properties.remove(key);
            return;
        }

        @Override
        public byte[] serialize() throws IOException {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public Map<String, String> getAllConfigs(boolean customProps) {
            // TODO Auto-generated method stub.
            return null;
        }
    }

    private class TestLock implements InterProcessLock {

        boolean _isAcquired = false;
        long _threadId = 0;

        @Override
        public synchronized void acquire() throws Exception {
            while (_isAcquired) {
                Thread.sleep(100);
            }
            _isAcquired = true;
            _threadId = Thread.currentThread().getId();
        }

        @Override
        public boolean acquire(long arg0, TimeUnit arg1) throws Exception {
            // TODO Auto-generated method stub
            return false;
        }

        @Override
        public boolean isAcquiredInThisProcess() {
            if (_threadId != Thread.currentThread().getId()) {
                return false;
            }
            return _isAcquired;
        }

        @Override
        public void release() throws Exception {
            if (_threadId == Thread.currentThread().getId()) {
                _isAcquired = false;
            }
        }

    }

    @Override
    public boolean isStorageProductLicensed(LicenseType licenseType) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public DistributedDataManager createDistributedDataManager(String basePath)
            throws CoordinatorException {
        return null;
    }

    @Override
    public DistributedDataManager getWorkflowDataManager()
            throws CoordinatorException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public LeaderSelector getLeaderSelector(String leaderPath, LeaderSelectorListener listener) throws CoordinatorException {
        return null;
    }

    @Override
    public LeaderLatch getLeaderLatch(String latchPath) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public DistributedDataManager createDistributedDataManager(String basePath,
            long maxNodes) throws CoordinatorException {
        // Pass in null instead ZK for tests only
        TestDistributedDataManager dataMgr = new TestDistributedDataManager(null, basePath, maxNodes);
        return dataMgr;

    }

    @Override
    public ClusterInfo.ClusterState getControlNodesState() {
        return null;
    }

    @Override
    public ClusterInfo.ClusterState getControlNodesState(String siteId, int nodeCount) {
        return null;
    }

    @Override
    public <T extends CoordinatorSerializable> T getNodeInfo(Service service, String nodeId, Class<T> clazz)
            throws Exception {
        return null;
    }

    @Override
    public <T extends CoordinatorSerializable> T queryRuntimeState(String key, Class<T> clazz) throws CoordinatorException {
        return null;
    }

    @Override
    public <T extends CoordinatorSerializable> void persistRuntimeState(String key, T state) throws CoordinatorException {
    }

    @Override
    public <T extends CoordinatorSerializable> Map<Service,
            T> getAllNodeInfos(Class<T> clazz, Pattern nodeIdFilter) throws Exception {
        return null;
    }

    @Override
    public <T extends CoordinatorSerializable> T getTargetInfo(final Class<T> clazz) throws CoordinatorException {
        return null;
    }

    @Override
    public void setTargetInfo(final CoordinatorSerializable info) throws CoordinatorException {
        
    }
    
    @Override
    public <T extends CoordinatorSerializable> T getTargetInfo(final Class<T> clazz, String id, String kind)
            throws CoordinatorException {
        return null;
    }

    @Override
    public String getUpgradeLockOwner(String lockId) {
        return null;
    }

    @Override
    public void setDbVersionInfo(DbVersionInfo info) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getCurrentDbSchemaVersion() {
        return null;
    }

    @Override
    public String getTargetDbSchemaVersion() {
        return null;
    }

    @Override
    public MigrationStatus getMigrationStatus() {
        return null;
    }

    @Override
    public String getDbConfigPath(String serviceName) {
        return null;
    }

    @Override
    public boolean isClusterUpgradable() {
        return false;
    }

    @Override
    public String getVersionedDbConfigPath(String serviceName, String dbVersion) {
        return null;
    }

    @Override
    public boolean isDbSchemaVersionChanged() {
        // TODO Auto-generated method stub.
        return false;
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
        return;
    }
}
