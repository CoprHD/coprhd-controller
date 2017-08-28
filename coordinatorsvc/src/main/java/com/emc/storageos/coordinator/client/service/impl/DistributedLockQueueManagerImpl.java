package com.emc.storageos.coordinator.client.service.impl;

import com.emc.storageos.coordinator.client.service.DistributedLockQueueEventListener;
import com.emc.storageos.coordinator.client.service.DistributedLockQueueManager;
import com.emc.storageos.coordinator.common.impl.ZkConnection;
import com.emc.storageos.coordinator.exceptions.CoordinatorException;
import com.emc.storageos.services.util.NamedThreadPoolExecutor;
import com.google.common.collect.ImmutableSet;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.cache.ChildData;
import org.apache.curator.framework.recipes.cache.TreeCache;
import org.apache.curator.framework.recipes.cache.TreeCacheEvent;
import org.apache.curator.framework.recipes.cache.TreeCacheListener;
import org.apache.curator.utils.CloseableUtils;
import org.apache.curator.utils.EnsurePath;
import org.apache.curator.utils.ZKPaths;
import org.apache.zookeeper.CreateMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Default {@link DistributedLockQueueManager} implementation, backed by Zookeeper.
 *
 * @author Ian Bibby
 */
public class DistributedLockQueueManagerImpl<T> implements DistributedLockQueueManager<T> {

    private static final Logger log = LoggerFactory.getLogger(DistributedLockQueueManagerImpl.class);
    private static final int DEFAULT_MAX_THREADS = 10;

    private String rootPath;
    private DistributedLockQueueTaskConsumer<T> consumer;
    private DistributedLockQueueItemNameGenerator<T> nameGenerator;
    private CuratorFramework zkClient;
    private TreeCache treeCache;
    private ThreadPoolExecutor workers;
    private List<DistributedLockQueueEventListener<T>> listeners;

    public enum Event {
        ADDED, REMOVED
    }

    private class DefaultNameGenerator implements DistributedLockQueueItemNameGenerator<T> {

        @Override
        public String generate(Object item) {
            return item.toString();
        }
    }

    public DistributedLockQueueManagerImpl(ZkConnection zkConnection, String rootPath,
                                           DistributedLockQueueTaskConsumer<T> consumer) {
        zkClient = zkConnection.curator();
        this.rootPath = rootPath;
        this.consumer = consumer;
    }

    @Override
    public void setNameGenerator(DistributedLockQueueItemNameGenerator<T> nameGenerator) {
        this.nameGenerator = nameGenerator;
    }

    public DistributedLockQueueItemNameGenerator<T> getNameGenerator() {
        if (nameGenerator == null) {
            nameGenerator = new DefaultNameGenerator();
        }
        return nameGenerator;
    }

    @Override
    public void start() {
        log.info("DistributedLockQueueManager is starting up");
        try {
            workers = new NamedThreadPoolExecutor("LockQueueWorkers", DEFAULT_MAX_THREADS);
            log.info("Created worker pool with {} threads", DEFAULT_MAX_THREADS);

            EnsurePath path = new EnsurePath(rootPath);
            path.ensure(zkClient.getZookeeperClient());
            log.info("ZK path {} created.", rootPath);

            treeCache = TreeCache.newBuilder(zkClient, rootPath).setCacheData(true).build();
            treeCache.start();
            log.info("Curator TreeCache has started");

            if (log.isDebugEnabled()) {
                addLoggingCacheEventListener();
                log.info("Curator TreeCache event listener for logging added");
            }
        } catch (Exception e) {
            throw CoordinatorException.fatals.failedToStartDistributedQueue(e);
        }
    }

    @Override
    public void stop() {
        CloseableUtils.closeQuietly(treeCache);
        workers.shutdownNow();
    }

    @Override
    public boolean queue(String lockKey, T task) {
        String lockPath = ZKPaths.makePath(rootPath, lockKey);
        String taskPath = ZKPaths.makePath(lockPath, getNameGenerator().generate(task));

        log.info("Queueing task: {}", taskPath);

        try {
            byte[] data = GenericSerializer.serialize(task, taskPath, true);
            zkClient.create().creatingParentsIfNeeded()
                    .withMode(CreateMode.PERSISTENT_SEQUENTIAL)
                    .forPath(taskPath, data);

            notifyListeners(task, Event.ADDED);

            return true;
        } catch (Exception e) {
            log.error("Failed to add task to lock queue", e);
        }
        return false;
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean dequeue(String lockKey) {
        String lockPath = ZKPaths.makePath(rootPath, lockKey);

        log.info("Attempting to de-queue from {}", lockPath);
        Map<String, ChildData> children = treeCache.getCurrentChildren(lockPath);

        if (children == null || children.isEmpty()) {
            log.info("Nothing to de-queue");
            return false;
        }

        String first = getFirstItem(children);
        log.info("Dequeueing {}", first);
        final String fullPath = ZKPaths.makePath(lockPath, first);
        ChildData childData = treeCache.getCurrentData(fullPath);

        final T task = (T) GenericSerializer.deserialize(childData.getData());
        log.info("Deserialized {}", task.toString());

        consumer.startConsumeTask(task, new DistributedLockQueueTaskConsumerCallback() {

            @Override
            public void taskConsumed() {
                if (deleteTask(fullPath)) {
                    notifyListeners(task, Event.REMOVED);
                }
            }
        });

        return true;
    }

    @Override
    public List<DistributedLockQueueEventListener<T>> getListeners() {
        if (listeners == null) {
            listeners = new ArrayList<>();
        }
        return listeners;
    }

    @Override
    public Set<String> getLockKeys() {
        log.info("Getting Lock keys at path {}", rootPath);
        /*
         * This call to TreeCache#getCurrentChildren with the root path aims to
         * return only the immediate znode children, i.e. the lock keys.
         * Since a Map is returned (child-name -> child-data), we need only return
         * the keys.
         */
        Map<String, ChildData> children = treeCache.getCurrentChildren(rootPath);
        if (children == null) {
            log.info("Lock children was null");
            return ImmutableSet.of();
        }
        return children.keySet();
    }

    @Override
    public void removeLockKey(String lockKey) {
        String lockPath = ZKPaths.makePath(rootPath, lockKey);
        try {
            log.info("Deleting empty lock key path: {}", lockPath);
            zkClient.delete().guaranteed().forPath(lockPath);
        } catch (Exception e) {
            log.error("Error removing lock path: {}", lockPath, e);
        }
    }

    /**
     * Given a Map of znode pathnames to their respective data, sort them using their natural lexicographical order and
     * return the first pathname.
     *
     * The assumption here is that each pathname has the format:
     *
     * "&lt;timestamp&gt;&lt;sequence-number&gt;"
     * E.g. /lockqueue/000198700420-rdfg-1/14393928728520000000000
     *
     * - the timestamp is a unix timestamp representing when the item was first created.
     * - the sequence number is automatically generated by Zookeeper and used to prevent duplicate entries (likely
     * impossible) but will also represent the number of items added to the lock group since it was last created.
     *
     * @param children
     * @return the key containing the oldest timestamp in its name.
     */
    protected String getFirstItem(Map<String, ChildData> children) {
        SortedSet<String> sortedChildren = new TreeSet<>();
        sortedChildren.addAll(children.keySet());

        return sortedChildren.first();
    }

    private boolean deleteTask(String fullPath) {
        try {
            log.info("Deleting task from lock queue: {}", fullPath);
            zkClient.delete().guaranteed().forPath(fullPath);
            return true;
        } catch (Exception e) {
            log.error("Failed to delete task from lock queue: {}", fullPath, e);
        }
        return false;
    }

    private void notifyListeners(final T task, final Event event) {
        if (listeners != null && !listeners.isEmpty()) {
            workers.submit(new Runnable() {
                @Override
                public void run() {
                    callListeners(task, event);
                }
            });
        }
    }

    private void callListeners(T task, Event event) {
        for (DistributedLockQueueEventListener<T> listener : listeners) {
            try {
                listener.lockQueueEvent(task, event);
            } catch (Exception e) {
                log.error("Error occurred whilst executing a lock queue listener", e);
            }
        }
    }

    /**
     * For logging purposes only, this will cause all nodes to log received TreeCache events.
     */
    private void addLoggingCacheEventListener() {
        TreeCacheListener listener = new TreeCacheListener() {
            @Override
            public void childEvent(CuratorFramework client, TreeCacheEvent event) throws Exception {
                switch (event.getType()) {
                    case NODE_ADDED:
                        log.debug("Node added: " + ZKPaths.getNodeFromPath(event.getData().getPath()));
                        break;
                    case NODE_UPDATED:
                        log.debug("Node changed: " + ZKPaths.getNodeFromPath(event.getData().getPath()));
                        break;
                    case NODE_REMOVED:
                        log.debug("Node removed: " + ZKPaths.getNodeFromPath(event.getData().getPath()));
                        break;
                }
            }
        };
        treeCache.getListenable().addListener(listener);
    }

}