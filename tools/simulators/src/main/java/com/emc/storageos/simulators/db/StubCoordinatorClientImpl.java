/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.simulators.db;

import com.emc.storageos.coordinator.client.model.CoordinatorSerializable;
import com.emc.storageos.coordinator.client.model.DbVersionInfo;
import com.emc.storageos.coordinator.client.model.MigrationStatus;
import com.emc.storageos.coordinator.client.service.*;
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
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Dummy coordinator client for use with dbsvc unit tests
 */
public class StubCoordinatorClientImpl implements CoordinatorClient {
    private final Service _dbinfo;

    public StubCoordinatorClientImpl(String endpoint) {
        ServiceImpl svc = new ServiceImpl();
        svc.setId(UUID.randomUUID().toString());
        svc.setEndpoint(URI.create(endpoint));
        svc.setName("dbsvc");
        svc.setVersion("1");
        _dbinfo = svc;
    }

    @Override
    public <T> T locateService(Class<T> clazz, String name, String version, String tag, String endpointKey) throws CoordinatorException {
        throw new UnsupportedOperationException();
    }

    public <T> T locateService(Class<T> clazz, String name, String version,
            List<String> tagList, String endpointKey) throws CoordinatorException {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<Service> locateAllServices(String name, String version, String tag, String endpointKey) throws CoordinatorException {
        return Arrays.asList(_dbinfo);
    }

    public List<Service> locateAllServices(String name, String version,
            List<String> tag, String endpointKey) throws CoordinatorException {
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
    public InterProcessLock getLock(String name) throws CoordinatorException {
        return new InterProcessLock() {
            @Override
            public void acquire() throws Exception {
            }

            @Override
            public boolean acquire(long time, TimeUnit unit) throws Exception {
                return true;
            }

            @Override
            public void release() throws Exception {
            }

            @Override
            public boolean isAcquiredInThisProcess() {
                return true;
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

    @Override
    public void persistServiceConfiguration(Configuration... config) throws CoordinatorException {
        return;
    }

    @Override
    public void removeServiceConfiguration(Configuration... config) throws CoordinatorException {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<Configuration> queryAllConfiguration(String kind) throws CoordinatorException {
        return Arrays.asList(new Configuration[0]);
    }

    @Override
    public Configuration queryConfiguration(String kind, String id) throws CoordinatorException {
        return null;
    }

    @Override
    public void setConnectionListener(ConnectionStateListener listener) {
        throw new UnsupportedOperationException();
    }

    @Override
    public PropertyInfo getPropertyInfo() throws CoordinatorException {
        return null;
    }

    @Override
    public boolean isStorageProductLicensed(LicenseType licenseType) {
        return false;
    }

    @Override
    public DistributedDataManager createDistributedDataManager(String basePath)
            throws CoordinatorException {
        return null;
    }

    @Override
    public DistributedDataManager createDistributedDataManager(String basePath,
            long maxNodes) throws CoordinatorException {
        return null;
    }

    @Override
    public DistributedDataManager getWorkflowDataManager()
            throws CoordinatorException {
        return null;
    }

    @Override
    public LeaderSelector getLeaderSelector(String leaderPath, LeaderSelectorListener listener) throws CoordinatorException {
        return null;
    }

    @Override
    public LeaderLatch getLeaderLatch(String latchPath) throws CoordinatorException {
        throw new UnsupportedOperationException();
    }

    @Override
    public ClusterInfo.ClusterState getControlNodesState() {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T extends CoordinatorSerializable> T getNodeInfo(Service service, String nodeId, Class<T> clazz)
            throws Exception {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T extends CoordinatorSerializable> T queryRuntimeState(String key, Class<T> clazz) throws CoordinatorException {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T extends CoordinatorSerializable> void persistRuntimeState(String key, T state) throws CoordinatorException {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T extends CoordinatorSerializable> Map<Service,
            T> getAllNodeInfos(Class<T> clazz, Pattern nodeIdFilter) throws Exception {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T extends CoordinatorSerializable> T getTargetInfo(final Class<T> clazz) throws Exception {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T extends CoordinatorSerializable> T getTargetInfo(final Class<T> clazz, String id, String kind)
            throws Exception {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getUpgradeLockOwner(String lockId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setDbVersionInfo(DbVersionInfo info) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getCurrentDbSchemaVersion() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getTargetDbSchemaVersion() {
        throw new UnsupportedOperationException();
    }

    @Override
    public MigrationStatus getMigrationStatus() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isDbSchemaVersionChanged() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getDbConfigPath(String serviceName) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isClusterUpgradable() {
        throw new UnsupportedOperationException();
    }

    public CoordinatorClientInetAddressMap getInetAddessLookupMap() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setInetAddessLookupMap(CoordinatorClientInetAddressMap inetAddessLookupMap) {
        throw new UnsupportedOperationException();
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
    public String getVersionedDbConfigPath(String serviceName, String dbVersion) {
        throw new UnsupportedOperationException();
    }
}
