/*
 * Copyright (c) 2008-2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.coordinator.client.service.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.api.CuratorEvent;
import org.apache.curator.framework.api.CuratorEventType;
import org.apache.curator.framework.api.CuratorListener;
import org.apache.curator.framework.recipes.queue.QueueSerializer;
import org.apache.curator.framework.state.ConnectionState;
import org.apache.curator.framework.state.ConnectionStateListener;
import org.apache.curator.utils.EnsurePath;
import org.apache.curator.utils.ZKPaths;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.data.Stat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.client.service.DistributedQueue;
import com.emc.storageos.coordinator.common.impl.ZkConnection;
import com.emc.storageos.coordinator.common.impl.ZkPath;
import com.emc.storageos.coordinator.exceptions.CoordinatorException;
import com.emc.storageos.services.util.NamedThreadPoolExecutor;

/**
 * ZK backed distributed queue implementation. Differs from Netflix Curator distributed queue
 * recipe in the following manner
 * <p/>
 * 1. Multithreaded queue consumer callbacks 2. Queue item is always processed with lock safety (meaning they are not removed from queue
 * until successfully processed / lock released)
 */
public class DistributedQueueImpl<T> implements DistributedQueue<T> {
    private static final Logger _log = LoggerFactory.getLogger(DistributedQueueImpl.class);
    private static final String _queuePrefix = "queue-";
    private static final String WORKER_POOL_NAME = "DQWorkers";
    private static final String STATE_LISTENER_POOL_NAME = "DQStateListener";

    // default max of 100K requests
    private static final int DEFAULT_MAX_ITEM = 100000;

    private final CuratorFramework _zkClient;
    private final DistributedQueueConsumer<T> _consumer;
    private final QueueSerializer<T> _serializer;
    private final String _name;
    private final String _queuePath;
    private final String _queueName;
    private final String _lockPath;
    private final ExecutorService _notifyExecutor;
    private final ThreadPoolExecutor _workers;
    private int _maxItem = DEFAULT_MAX_ITEM;
    private int _maxThreads = 10; // this is for distributed queue consumer threads

    /**
     * Responds to connection drops / reconnects.
     */
    private final ConnectionStateListener _connectionListener = new ConnectionStateListener() {
        @Override
        public void stateChanged(final CuratorFramework client, final ConnectionState newState) {
            _log.info("Entering stateChanged method : {}", newState);
            // Curator would send RECONNECTED event to listeners in the following two cases.
            // a) session reconnected with original session id.
            // b) a new session created and connected with the zookeeper.
            if (newState == ConnectionState.CONNECTED || newState == ConnectionState.RECONNECTED) {
                _workers.submit(new Callable<Object>() {
                    @Override
                    public Object call() throws Exception {
                        _log.debug("Inside call method");
                        final long sessionId = _zkClient.getZookeeperClient().getZooKeeper().getSessionId();
                        List<String> locks = _zkClient.getChildren().forPath(_lockPath);
                        for (int i = 0; i < locks.size(); i++) {
                            String lock = locks.get(i);
                            Stat stat = _zkClient.checkExists().forPath(ZKPaths.makePath(_lockPath, lock));
                            if (stat == null || stat.getEphemeralOwner() != sessionId) {
                                continue;
                            }

                            /*
                             * If there are some tasks need to be re-tasked, we should find the root cause
                             * and find a proper way instead of retrying during quick session reconnection.
                             */
                            String lockPath = ZKPaths.makePath(_lockPath, lock);
                            _log.debug("Leave alone lock {} during quick session reconnection", lockPath);
                        }

                        // needs to wake up consumer thread and rearm
                        // the watch since last watch most likely has
                        // gone away due to connection loss
                        _notifyExecutor.execute(new Runnable() {
                            @Override
                            public void run() {
                                notifyPendingChange();
                            }
                        });
                        return null;
                    }
                });
            }
        }
    };

    /**
     * Reacts to additional queue items and/or changes in their lock state
     */
    private final CuratorListener _childListener = new CuratorListener() {
        @Override
        public void eventReceived(CuratorFramework client, CuratorEvent event) throws Exception {
            if (event.getType() == CuratorEventType.WATCHED) {
                if (event.getWatchedEvent().getType() == Watcher.Event.EventType.NodeChildrenChanged &&
                        (event.getPath().startsWith(_queuePath) || event.getPath().startsWith(_lockPath))) {
                    _notifyExecutor.execute(new Runnable() {
                        @Override
                        public void run() {
                            notifyPendingChange();
                        }
                    });
                }
            }
        }
    };

    /**
     * Wakes up main dispatch loop
     */
    private synchronized void notifyPendingChange() {
        // This is not a naked notify, we do status mutation outside this method, safe to suppress
        notifyAll(); // NOSONAR("findbugs:NN_NAKED_NOTIFY")
    }

    /**
     * Constructor
     * 
     * @param conn ZK connection
     * @param consumer task consumer
     * @param serializer task serializer
     * @param name name of the distributed queue
     * @param maxThreads maximum number of threads for dispatching work
     * @param maxItem maximum number of items in queue
     */
    public DistributedQueueImpl(ZkConnection conn, DistributedQueueConsumer<T> consumer,
            QueueSerializer<T> serializer, String name, int maxThreads, int maxItem) {
        _zkClient = conn.curator();
        _consumer = consumer;
        _serializer = serializer;
        _name = name;
        _queuePath = String.format("%1$s/%2$s/queue", ZkPath.QUEUE.toString(), name);
        _queueName = getQueueName(_queuePath);
        _notifyExecutor = new NamedThreadPoolExecutor(DistributedQueueImpl.class.getSimpleName()
                + "_Notification", 1);
        _maxThreads = maxThreads;

        // _workers thread pool is for distributed queue framework,
        // it needs to has at least two threads -- one for dispatch, one for state change.
        _workers = new NamedThreadPoolExecutor(WORKER_POOL_NAME, 2);

        _lockPath = String.format("%1$s/%2$s/lock", ZkPath.QUEUE.toString(), name);
        _maxItem = maxItem;
    }

    /**
     * Construct distributed queue with default max item (of 100K)
     */
    public DistributedQueueImpl(
            ZkConnection conn, DistributedQueueConsumer<T> consumer, QueueSerializer<T> serializer,
            String name, int maxThreads) {
        this(conn, consumer, serializer, name, maxThreads, DEFAULT_MAX_ITEM);
    }

    public DistributedQueueConsumer<T> getConsumer() {
        return _consumer;
    }

    @Override
    public synchronized void start() {
        if (_workers.isTerminated()) {
            throw CoordinatorException.fatals.failedToStartDistributedQueue();
        }
        _zkClient.getConnectionStateListenable().addListener(_connectionListener);
        _zkClient.getCuratorListenable().addListener(_childListener, new NamedThreadPoolExecutor(STATE_LISTENER_POOL_NAME, 1));
        try {
            EnsurePath path = new EnsurePath(_queuePath);
            path.ensure(_zkClient.getZookeeperClient());
            path = new EnsurePath(_lockPath);
            path.ensure(_zkClient.getZookeeperClient());
        } catch (Exception e) {
            throw CoordinatorException.fatals.failedToStartDistributedQueue(e);
        }

        if (_consumer != null) {
            _consumer.init(_queueName, this, _maxThreads);
            _workers.submit(new Callable<Object>() {
                @Override
                public Object call() throws Exception {
                    dispatch();
                    return null;
                }
            });
        }
    }

    @Override
    public synchronized boolean stop(long timeoutMs) {
        if (_workers.isTerminated()) {
            return true;
        }

        _log.info("Stopping consumer with timeout: {}", timeoutMs);
        if (_consumer != null) {
            if (!_consumer.uninit(timeoutMs)) {
                return false;
            }
        }

        _log.info("Stopping dispatcher");
        _zkClient.getConnectionStateListenable().removeListener(_connectionListener);
        _zkClient.getCuratorListenable().removeListener(_childListener);
        _workers.shutdownNow();
        return true;
    }

    @Override
    public void put(T item) throws Exception {
        Stat stat = _zkClient.checkExists().forPath(_queuePath);
        if (stat.getNumChildren() > _maxItem) {
            _log.error("Queue is too busy. Found " + stat.getNumChildren() + " items. Max allowed items are " + _maxItem);
            throw CoordinatorException.retryables.queueTooBusy();
        }
        String path = ZKPaths.makePath(_queuePath, _queuePrefix);
        byte[] data = _serializer.serialize(item);
        _zkClient.create().withMode(CreateMode.PERSISTENT_SEQUENTIAL).forPath(path, data);
    }

    /**
     * Removes the specified item from the distributed queue.
     * Removes associated lock.
     * 
     * @param itemName Name of the item to be removed from the queue.
     * 
     * @throws Exception
     */
    public void remove(String itemName) throws Exception {
        String itemPath = null;
        try {
            itemPath = ZKPaths.makePath(_queuePath, itemName);
            _zkClient.delete().guaranteed().forPath(itemPath);
        } catch (Exception e) {
            _log.warn("Problem deleting queue item: {} e={}", itemPath, e);
        } finally {
            String lockPath = ZKPaths.makePath(_lockPath, itemName);
            try {
                _log.info("delete lock {}", lockPath);
                _zkClient.delete().guaranteed().forPath(lockPath);
            } catch (KeeperException.NoNodeException ex) {
                _log.warn("The lock {} has been removed e={}", lockPath, ex);
            } catch (Exception ex) {
                _log.warn("Problem deleting lock item: {} e={}", lockPath, ex);
            }
        }
    }

    /**
     * Dispatcher loop that
     * <p/>
     * 1. scans queued items and attempts to lock 2. if locked, queues it to pending item list
     */
    private void dispatch() throws Exception {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                List<String> children;
                List<String> locks;
                synchronized (this) {
                    // Need to re-scan the zk for any possible changes happened before it is waked up.
                    boolean needRescan = false;
                    do {
                        locks = _zkClient.getChildren().watched().forPath(_lockPath);
                        children = _zkClient.getChildren().watched().forPath(_queuePath);
                        _log.info("Processing queue {} - #items: {}, #locks: {}",
                                new Object[] { _name, children.size(), locks.size() });
                        children.removeAll(locks);
                        if (children.isEmpty()) {
                            wait();
                            needRescan = true;
                        } else if (_consumer.isBusy(_queueName)) {
                            // Wait till consumer has enough resources.
                            // Note:
                            // It needs to ALWAYS wait under "watch armed". Because WATCH is one-time trigger,
                            // after it is waked, it needs to re-arm watch during getChildren() again.
                            _log.info("The consumer {} is busy", _consumer);
                            wait();
                            needRescan = true;
                        } else {
                            needRescan = false;
                        }
                    } while (needRescan);
                }

                if (!children.isEmpty()) {
                    // Note: multiple zkClients might see the same child at the same time,
                    // if one processChildren finish(deleted both queue item and lock) quickly,
                    // the other zkClients still be able to create lock but failed while handling
                    // the queue item, it is harmless but will raise a warning in spawnWork.
                    processChildren(children);
                }
            } catch (KeeperException e) {
                // In load env, the KeeperException would be thrown occasionally.
                // So we need to keep monitoring the queue with the dispatch loop
                _log.warn("KeeperException in dispatch loop, retrying in dispatch loop", e);
                continue;
            } catch (Exception e) {
                _log.error("Exception in dispatch loop, quiting", e);
                throw e;
            }
        }
    }

    /**
     * Starts working on an item after lock is successfully obtained
     * 
     * @param child queue item name
     */
    private void spawnWork(final String child) {
        if (_consumer == null) {
            return;
        }

        final String itemPath = ZKPaths.makePath(_queuePath, child);
        byte[] data = null;
        try {
            data = _zkClient.getData().forPath(itemPath);
        } catch (Exception e) {
            // 1. free the lock if there is any issue reading the item.
            // 2. it also might be raised because the item has been handled by others quickly.
            _log.warn("Problem seen while processing queue item which might be already handled by other workers. ", e);
            final String lockPath = ZKPaths.makePath(_lockPath, child);
            try {
                _log.info("delete lock {}", lockPath);
                _zkClient.delete().guaranteed().inBackground().forPath(lockPath);
            } catch (KeeperException.NoNodeException ex) {
                _log.warn("The lock {} has been removed e={}", lockPath, ex);
            } catch (Exception ex) {
                _log.warn("Problem deleting lock item: {} e={}", lockPath, ex);
            }
            data = null;
        }

        if (data != null) {
            final T item = _serializer.deserialize(data);
            _consumer.startConsumeItem(_queueName, child, item);
        }
    }

    /**
     * Process each queued item by submitting them to worker queue
     * 
     * @param children children to process
     * @throws Exception
     */
    private void processChildren(List<String> children) throws Exception {
        Collections.sort(children,
                new Comparator<String>() {
                    public int compare(String o1, String o2) {
                        return o1.compareTo(o2);
                    }
                }
                );

        for (int i = 0; i < children.size(); i++) {
            // only grab tasks when the consumer is not busy
            // we need to check it before each one is processed.
            if (_consumer.isBusy(_queueName)) {
                _log.info("The consumer {} is busy", _consumer);
                return;
            }

            final String child = children.get(i);
            final String lockPath = ZKPaths.makePath(_lockPath, child);
            try {
                _zkClient.create().withMode(CreateMode.EPHEMERAL).forPath(lockPath);
                _log.info("processChildren(): Created lock zNode {} for Queue {}", child, _queuePath);
                spawnWork(child);
            } catch (KeeperException.NodeExistsException nee) {
                _log.info("processChildren(): For Queue: {}, ZNodes already exist", _queuePath);
            } catch (KeeperException ke) {
                _log.info("processChildren(): For Queue: {}, Problem while creating ZNodes: {}",
                        new Object[] { _queuePath, lockPath }, ke);
            } catch (Exception e) {
                _log.info("processChildren(): For Queue: {}, Failed processing ZNodes: {}",
                        new Object[] { _queuePath, lockPath }, e);
            }
        }
    }

    private String getQueueName(String queuePath) {
        // Extract queue name from ZPath
        // ZPath format for queue: /queue/<queuename>/queue
        String[] tmparray = queuePath.split("/");
        if (tmparray.length != 4) {
            return null;
        }
        return tmparray[2];
    }
    
    /* (non-Javadoc)
     * @see com.emc.storageos.coordinator.client.service.DistributedQueue#getQueuedItems()
     */
    @Override
    public List<T> getQueuedItems() {
        List<T> items = new ArrayList<T>();
        try {
            synchronized (this) {
                List<String> activeItems = _zkClient.getChildren().watched().forPath(_lockPath);
                List<String> queuedItems = _zkClient.getChildren().watched().forPath(_queuePath);
                queuedItems.removeAll(activeItems);
                for (String queuedItem : queuedItems) {
                    try {
                        final String itemPath = ZKPaths.makePath(_queuePath, queuedItem);
                        byte[] data = _zkClient.getData().forPath(itemPath);
                        if (data != null) {
                            final T itemOnQueue = _serializer.deserialize(data);
                            if (itemOnQueue != null) {
                                items.add(itemOnQueue);
                            }
                        }
                    } catch (Exception e) {
                        _log.warn("Exception thrown getting queued items from queue " + _queuePath, e);
                    }
                }
            }
        } catch (Exception e) {
            _log.warn("Exception thrown getting queued items from queue " + _queuePath, e);
        }
        return items;
    }
    
    /* (non-Javadoc)
     * @see com.emc.storageos.coordinator.client.service.DistributedQueue#getActiveItems()
     */
    @Override
    public List<T> getActiveItems() {
        List<T> items = new ArrayList<T>();
        try {
            synchronized (this) {
                List<String> activeItems = _zkClient.getChildren().watched().forPath(_lockPath);
                for (String activeItem : activeItems) {
                    try {
                        final String itemPath = ZKPaths.makePath(_queuePath, activeItem);
                        byte[] data = _zkClient.getData().forPath(itemPath);
                        if (data != null) {
                            final T itemOnQueue = _serializer.deserialize(data);
                            if (itemOnQueue != null) {
                                items.add(itemOnQueue);
                            }
                        }
                    } catch (Exception e) {
                        _log.warn("Exception thrown getting active items from queue " + _queuePath, e);
                    }
                }
            }
        } catch (Exception e) {
            _log.warn("Exception thrown getting active items from queue " + _queuePath, e);
        }
        return items;
    }
}
