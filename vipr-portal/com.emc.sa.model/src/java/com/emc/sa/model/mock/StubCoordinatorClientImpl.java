/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.model.mock;

/**
 *  Copyright (c) 2008-2012 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

import com.emc.storageos.coordinator.client.service.*;
import com.emc.storageos.coordinator.client.service.impl.CoordinatorClientInetAddressMap;
import com.emc.storageos.coordinator.client.service.impl.DistributedQueueConsumer;
import com.emc.storageos.coordinator.client.service.impl.CoordinatorClientImpl;
import com.emc.storageos.coordinator.client.service.impl.DualInetAddress;
import com.emc.storageos.coordinator.common.Configuration;
import com.emc.storageos.coordinator.common.Service;
import com.emc.storageos.coordinator.common.impl.ServiceImpl;
import com.emc.storageos.coordinator.exceptions.CoordinatorException;
import com.emc.storageos.model.property.PropertyInfo;
import org.apache.curator.framework.recipes.leader.LeaderSelector;
import org.apache.curator.framework.recipes.leader.LeaderSelectorListener;
import org.apache.curator.framework.recipes.locks.InterProcessLock;
import org.apache.curator.framework.recipes.queue.QueueSerializer;

import java.io.IOException;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Dummy coordinator client for use with dbsvc unit tests
 */
public class StubCoordinatorClientImpl extends CoordinatorClientImpl {
    private Service _dbinfo;
    private Map<String, Configuration> _configMap = new HashMap<String, Configuration>();
    private Map<String, InterProcessLock> _locks = new HashMap<String, InterProcessLock>();

    public StubCoordinatorClientImpl(URI endpoint) {
        ServiceImpl svc = new ServiceImpl();
        svc.setId(UUID.randomUUID().toString());
        svc.setEndpoint(endpoint);
        svc.setName("dbsvc");
        svc.setVersion("1");
        _dbinfo = svc;
        setInetAddessLookupMap(createLocalAddressLookupMap());
    }

    /**
     * Creates a local address lookup map for use in local tests.
     * 
     * @return the lookup map.
     */
    public static CoordinatorClientInetAddressMap createLocalAddressLookupMap() {
        CoordinatorClientInetAddressMap addressMap = new CoordinatorClientInetAddressMap();
        addressMap.setNodeName("localhost");
        try {
            addressMap.setDualInetAddress(DualInetAddress.fromAddress("127.0.0.1"));
        } catch (UnknownHostException e) {
            // Should never happen, ignore
        }

        Map<String, DualInetAddress> ips = new HashMap<>();
        ips.put(addressMap.getNodeName(), addressMap.getDualInetAddress());
        addressMap.setControllerNodeIPLookupMap(ips);
        return addressMap;
    }

    @Override
    public <T> T locateService(Class<T> clazz, String name, String version, String tag, String endpointKey) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<Service> locateAllServices(String name, String version, String tag, String endpointKey) {
        return Arrays.asList(_dbinfo);
    }

    @Override
    public <T> DistributedQueue<T> getQueue(String name, DistributedQueueConsumer<T> consumer, QueueSerializer<T> serializer,
            int maxThreads, int maxItem) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> DistributedQueue<T>
            getQueue(String name, DistributedQueueConsumer<T> consumer, QueueSerializer<T> serializer, int maxThreads) {
        throw new UnsupportedOperationException();
    }

    @Override
    public WorkPool getWorkPool(String name, WorkPool.WorkAssignmentListener listener) {
        throw new UnsupportedOperationException();
    }

    @Override
    public DistributedSemaphore getSemaphore(String name, int maxPermits) {
        throw new UnsupportedOperationException();
    }

    @Override
    public InterProcessLock getLock(final String name) {
        return new InterProcessLock() {
            private String _lockName = name;

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
    public DistributedPersistentLock getPersistentLock(String name) {
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
    public void persistServiceConfiguration(Configuration... config) {
        for (int i = 0; i < config.length; i++) {
            Configuration c = config[i];
            _configMap.put(getKey(c.getKind(), c.getId()), c);
        }
    }

    @Override
    public boolean isDbSchemaVersionChanged() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void removeServiceConfiguration(Configuration... config) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<Configuration> queryAllConfiguration(String kind) {
        List<Configuration> configs = new ArrayList<Configuration>();
        for (String key : _configMap.keySet()) {
            if (key.startsWith(kind)) {
                configs.add(_configMap.get(key));
            }
        }
        return configs;
    }

    @Override
    public Configuration queryConfiguration(String kind, String id) {
        return _configMap.get(getKey(kind, id));
    }

    @Override
    public void setConnectionListener(ConnectionStateListener listener) {
        throw new UnsupportedOperationException();
    }

    @Override
    public PropertyInfo getPropertyInfo() {
        return null;
    }

    public DistributedDataManager createDistributedDataManager(String basePath) {
        throw new UnsupportedOperationException();
    }

    public DistributedDataManager getWorkflowDataManager() {
        throw new UnsupportedOperationException();
    }

    public LeaderSelector getLeaderSelector(String leaderPath, LeaderSelectorListener listener) throws CoordinatorException {
        return null;
    }
}
