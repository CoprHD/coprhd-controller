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

package com.emc.storageos.coordinator.client.service.impl;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.data.Stat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.client.service.WorkPool;
import com.emc.storageos.coordinator.common.impl.ZkConnection;
import com.emc.storageos.coordinator.exceptions.CoordinatorException;
import com.emc.storageos.services.util.NamedThreadPoolExecutor;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.api.CuratorEvent;
import org.apache.curator.framework.api.CuratorEventType;
import org.apache.curator.framework.api.CuratorListener;
import org.apache.curator.framework.state.ConnectionState;
import org.apache.curator.framework.state.ConnectionStateListener;
import org.apache.curator.utils.EnsurePath;

/**
 * Default WorkPool implementation
 */
public class WorkPoolImpl implements WorkPool {
    private static final Logger _log = LoggerFactory.getLogger(WorkPoolImpl.class);

    private final CuratorFramework _zkClient;
    private final WorkAssignmentListener _assignmentListener;
    private final String _workItemPath;
    private final String _workItemLockPath;
    private final ExecutorService _refreshWorker;

    /**
     * Rearm work item and assignment znodes on reconnect
     */
    private final ConnectionStateListener _connectionStateListener = new ConnectionStateListener() {
        @Override
        public void stateChanged(CuratorFramework client, ConnectionState newState) {
            switch (newState) {
                case RECONNECTED: {
                    refresh();
                }
            }
        }
    };

    /**
     * Default Work implementation
     */
    private class WorkImpl implements Work {
        private final String _id;

        WorkImpl(String id) {
            _id = id;
        }

        @Override
        public String getId() {
            return _id;
        }

        @Override
        public void release() throws Exception {
            String lockPath = lockPath(_id);
            Stat stat = _zkClient.checkExists().forPath(lockPath);
            if (stat == null || stat.getEphemeralOwner() !=
                    _zkClient.getZookeeperClient().getZooKeeper().getSessionId()) {
                // doesn't exist or someone else took assignment
                return;
            }
            try {
                _zkClient.delete().guaranteed().withVersion(stat.getVersion()).forPath(lockPath);
            } catch (KeeperException.NoNodeException ignore) {
                _log.debug("Caught exception but ignoring it: " + ignore);
            } catch (KeeperException.BadVersionException ignore) {
                // someone else took it
               // Ignore exception, don't re-throw
                _log.debug("Caught exception but ignoring it: " + ignore);
            }
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof Work)) {
                return false;
            }
            return _id.equals(((Work)obj).getId());
        }

        @Override
        public int hashCode() {
            return _id.hashCode();
        }
    }

    /**
     * Refreshes assignment list on modification
     */
    private final CuratorListener _changeWatcher = new CuratorListener() {
        @Override
        public void eventReceived(CuratorFramework client, CuratorEvent event) throws Exception {
            if (event.getType() == CuratorEventType.WATCHED) {
                if (event.getWatchedEvent().getType() == Watcher.Event.EventType.NodeChildrenChanged &&
                    (event.getPath().startsWith(_workItemLockPath) || event.getPath().startsWith(_workItemPath))) {
                    refresh();
                }
            }
        }
    };

    /**
     * Constructor
     *
     * @param conn         ZK connection
     * @param listener     assignment listener
     * @param workPoolPath
     */
    public WorkPoolImpl(ZkConnection conn, WorkAssignmentListener listener, String workPoolPath) {
        _zkClient = conn.curator();
        _assignmentListener = listener;
        _workItemPath = String.format("%1$s/items", workPoolPath);
        _workItemLockPath = String.format("%1$s/locks", workPoolPath);
        _refreshWorker = new NamedThreadPoolExecutor(listener.getClass().getSimpleName(), 1, 1,
                0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<Runnable>(2));

    }

    @Override
    public void addWork(String workId) throws CoordinatorException {
        try {
            _zkClient.create().forPath(itemPath(workId));
        } catch (KeeperException.NodeExistsException ignore) {
            // already added
        } catch (Exception e) {
            throw CoordinatorException.retryables.unableToAddWork(workId, e);
        }
    }

    @Override
    public void removeWork(String workId) throws CoordinatorException {
        try {
            _zkClient.delete().guaranteed().forPath(itemPath(workId));
        } catch (KeeperException.NoNodeException ignore) {
            // already removed
            // Ignore exception, don't re-throw
            _log.debug("Caught exception but ignoring it: " + ignore);
        } catch (Exception e) {
            throw CoordinatorException.retryables.unableToRemoveWork(workId, e);
        }
    }

    @Override
    public synchronized void start() throws CoordinatorException {
        // setup group, lock, and item paths
        EnsurePath path = new EnsurePath(_workItemPath);
        try {
            path.ensure(_zkClient.getZookeeperClient());
            path = new EnsurePath(_workItemLockPath);
            path.ensure(_zkClient.getZookeeperClient());
        } catch (Exception e) {
            throw CoordinatorException.retryables.unableToStartWork(_workItemPath, e);
        }

        // add listeners
        _zkClient.getConnectionStateListenable().addListener(_connectionStateListener);
        _zkClient.getCuratorListenable().addListener(_changeWatcher);

        // prime assignment loop
        refresh();
    }

    @Override
    public synchronized void stop() {
        _zkClient.getConnectionStateListenable().removeListener(_connectionStateListener);
        _refreshWorker.shutdown();
    }

    /**
     * Computes assignment list and calls assignment listener.   Attempts to take new work items.
     */
    private void refresh() {
        try {
            _refreshWorker.submit(new Callable<Object>() {
                @Override
                public Object call() throws Exception {
                    long sessionId = _zkClient.getZookeeperClient().getZooKeeper().getSessionId();

                    Set<String> workSet = new HashSet<String>(_zkClient.getChildren().watched()
                            .forPath(_workItemPath));
                    Set<String> assignedSet = new HashSet<String>(_zkClient.getChildren().watched()
                            .forPath(_workItemLockPath));

                    Set<Work> assigned = new HashSet<Work>();
                    // prune removed work items from assigned set and collect assignments for this worker
                    Iterator<String> assignedIt = assignedSet.iterator();
                    while (assignedIt.hasNext()) {
                        String assignedId = assignedIt.next();
                        String lockPath = lockPath(assignedId);
                        Stat stat = _zkClient.checkExists().forPath(lockPath);
                        if (stat == null || stat.getEphemeralOwner() != sessionId) {
                            continue;
                        }
                        if (!workSet.contains(assignedId)) {
                            // work item was removed - delete my assignment.  Notification comes
                            // in next refresh
                            _zkClient.delete().guaranteed().inBackground().forPath(lockPath);
                            continue;
                        }
                        assigned.add(new WorkImpl(assignedId));
                    }
                    try {
                        _assignmentListener.assigned(assigned);
                    } catch (Exception e) {
                        _log.warn("Assignment listener threw", e);
                    }
                    // take unassigned work
                    workSet.removeAll(assignedSet);
                    Iterator<String> unassignedIt = workSet.iterator();
                    while (unassignedIt.hasNext()) {
                        String lockPath = lockPath(unassignedIt.next());
                        // assignment processed in next refresh
                        _zkClient.create().withMode(CreateMode.EPHEMERAL).inBackground().forPath(lockPath);
                    }
                    return null;
                }
            });
        } catch (RejectedExecutionException e) {
            // OK to be rejected because currently pending refresh() will take care of latest changes
        }
    }

    /**
     * Helper to construct lock path from work ID
     *
     * @param id
     * @return
     */
    private String lockPath(String id) {
        return String.format("%1$s/%2$s", _workItemLockPath, id);
    }

    /**
     * Helper to construct item path from work ID
     *
     * @param id
     * @return
     */
    private String itemPath(String id) {
        return String.format("%1$s/%2$s", _workItemPath, id);
    }
}
