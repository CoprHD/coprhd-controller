/*
 * Copyright (c) 2008-2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.coordinator.client.service.impl;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.zookeeper.data.Stat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.client.service.DistributedSemaphore;
import com.emc.storageos.coordinator.common.impl.ZkConnection;
import com.emc.storageos.coordinator.exceptions.CoordinatorException;
import com.emc.storageos.services.util.NamedScheduledThreadPoolExecutor;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.locks.InterProcessSemaphore;
import org.apache.curator.framework.recipes.locks.Lease;
import org.apache.curator.framework.state.ConnectionState;
import org.apache.curator.framework.state.ConnectionStateListener;
import org.apache.curator.utils.EnsurePath;
import org.apache.curator.utils.ZKPaths;

/**
 * ZK based distributed semaphore implementation.
 * Wrapper over the curator recipe (InterProcessSemaphore).
 * Ensures SEMAPHORE namespace exists, for InterProcessSemaphore.
 */
public class DistributedSemaphoreImpl implements DistributedSemaphore {

    private static final Logger _logger = LoggerFactory.getLogger(DistributedSemaphoreImpl.class);
    private InterProcessSemaphore _semaphore;
    private final CuratorFramework _zkClient;
    private final String _semaphorePath;
    private final int _maxPermits;
    private final ExecutorService _leaseCleanupExecutor;
    private static final String POOL_NAME = "DSCleaner";

    /**
     * If there is any connection issue, we release the leases; else we risk leaking them.
     */
    private final ConnectionStateListener _connectionListener = new ConnectionStateListener() {
        @Override
        public void stateChanged(final CuratorFramework client, final ConnectionState newState) {
            if (newState == ConnectionState.RECONNECTED) {
                _leaseCleanupExecutor.execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            final long sessionId = _zkClient.getZookeeperClient().getZooKeeper().getSessionId();
                            List<String> leaseNodes = _zkClient.getChildren().forPath(_semaphorePath);
                            for (int i = 0; i < leaseNodes.size(); i++) {
                                String leaseNode = leaseNodes.get(i);
                                Stat stat = _zkClient.checkExists().forPath(ZKPaths.makePath(_semaphorePath, leaseNode));
                                if (stat == null || stat.getEphemeralOwner() != sessionId) {
                                    continue;
                                }
                                client.delete().guaranteed().inBackground().forPath(
                                        String.format("%1$s/%2$s", _semaphorePath, leaseNode));
                            }
                        }
                        catch (Exception e) {
                            _logger.warn("Problem while attempting to clean up lease nodes on reconnect.", e);
                        }
                    }
                });
            }
        }
    };

    /**
     * Constructor
     * 
     * @param conn ZK connection
     * @param semaphorePath ZK path under which semaphore entrants are managed
     * @param maxPermits Maximum number of permits the semaphore grants (before clients block)
     */
    public DistributedSemaphoreImpl(ZkConnection conn, String semaphorePath, int maxPermits) {
        _zkClient = conn.curator();
        _semaphorePath = semaphorePath;
        _maxPermits = maxPermits;
        _leaseCleanupExecutor = new NamedScheduledThreadPoolExecutor(POOL_NAME, 1);
        _logger.debug("Created a distributed semaphore with permits: " + maxPermits);
    }

    @Override
    public synchronized void start() {
        if (_semaphore != null) {
            return;
        }
        try {
            EnsurePath path = new EnsurePath(_semaphorePath);
            path.ensure(_zkClient.getZookeeperClient());
            _zkClient.getConnectionStateListenable().addListener(_connectionListener);
            _semaphore = new InterProcessSemaphore(_zkClient, _semaphorePath, _maxPermits);
        } catch (Exception e) {
            throw CoordinatorException.fatals.failedToStartDistributedSemaphore(e);
        }
    }

    @Override
    public synchronized void stop() {
        if (_semaphore == null) {
            return;
        }
        _zkClient.getConnectionStateListenable().removeListener(_connectionListener);
    }

    @Override
    public Lease acquireLease() throws Exception {
        return _semaphore.acquire();
    }

    @Override
    public Lease acquireLease(long waitTime, TimeUnit waitTimeUnit) throws Exception {
        return _semaphore.acquire(waitTime, waitTimeUnit);
    }

    @Override
    public void returnLease(Lease lease) throws Exception {
        _semaphore.returnLease(lease);
    }
}
