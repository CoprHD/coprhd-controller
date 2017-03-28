/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.coordinator.client.service.impl;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.data.Stat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.client.service.DataManagerFullException;
import com.emc.storageos.coordinator.client.service.DistributedDataManager;
import com.emc.storageos.coordinator.common.impl.ZkConnection;
import com.emc.storageos.coordinator.exceptions.CoordinatorException;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.api.CuratorListener;
import org.apache.curator.framework.recipes.cache.ChildData;
import org.apache.curator.framework.recipes.cache.PathChildrenCache;
import org.apache.curator.framework.state.ConnectionStateListener;

/**
 * DistributedDataManager implementation which imposes certain constraints
 * upon access to the zookeeper tree:
 * - Access must be rooted to a specific base path in the zk tree, specified during construction
 * - Only one level of children may be created below the base path
 * - The total number of child nodes is subject to a limitiation specified at construction
 */
public class DistributedDataManagerImpl implements DistributedDataManager {
    private static final Logger _log = LoggerFactory.getLogger(DistributedDataManagerImpl.class);
    private static final long DEFAULT_MAX_NODES = 100;
    private final CuratorFramework _zkClient;
    private CuratorListener _listener;
    private ConnectionStateListener _connectionStateListener;
    private String _basePath;
    private volatile PathChildrenCache _basePathCache = null;
    private long _maxNodes;

    /**
     * Construct a data manager for the specified path, using the DEFAULT_MAX_NODES limit
     * 
     * @param conn the zk connection
     * @param basePath the base path to manage
     */
    public DistributedDataManagerImpl(ZkConnection conn, String basePath) {
        this(conn, basePath, DEFAULT_MAX_NODES);
    }

    /**
     * Construct a data manager for the specified path, using the specified maxNodes limit
     * 
     * @param conn the zk connection
     * @param basePath the base path to manage
     * @param maxNodes the max number of child nodes allowed
     */
    public DistributedDataManagerImpl(ZkConnection conn, String basePath, long maxNodes) {
        _zkClient = conn.curator();
        if (StringUtils.isEmpty(basePath) || !basePath.startsWith("/") ||
                (basePath.length() < 2) || basePath.endsWith("/")) {
            throw new IllegalArgumentException("basePath must be at least 2 characters long and start with (but not end with) /");
        }
        _basePath = basePath;
        _maxNodes = maxNodes;
        _log.info("{}: Manager constructed with node limit of {}", _basePath, _maxNodes);
        ensureCacheStarted();
    }

    @Override
    public Stat checkExists(String path) {
        checkPath(path);
        try {
            Stat stat = _zkClient.checkExists().forPath(path);
            return stat;
        } catch (KeeperException e) {
            _log.error("Problem while creating ZNodes {}: {}", path, e);
            return null;
        } catch (Exception ex) {
            _log.error("unexpected exception encountered: {}", ex);
            return null;
        }
    }

    @Override
    public void createNode(String path, boolean watch) throws Exception {
        checkPath(path);
        Stat stat = checkExists(path);
        if (stat == null) {
            checkLimit();
            _zkClient.create().creatingParentsIfNeeded().withMode(CreateMode.PERSISTENT).forPath(path);
        }
        if (_listener != null && watch) {
            stat = _zkClient.checkExists().watched().forPath(path);
        }
    }

    @Override
    public void removeNode(String path) throws Exception {
        checkPath(path);
        Stat stat = checkExists(path);
        if (stat != null) {
            List<String> children = getChildren(path);
            for (String child : children) {
                _zkClient.delete().guaranteed().forPath(path + "/" + child);
            }
            _zkClient.delete().guaranteed().forPath(path);
        }
    }

    @Override
    public void removeNode(String path, boolean recursive) throws Exception {
        if (recursive) {
            Stat stat = checkExists(path);
            if (stat != null) {
                _zkClient.delete().deletingChildrenIfNeeded().forPath(path);
            }
        } else {
            removeNode(path);
        }
    }

    @Override
    public void putData(String path, Object object) throws Exception {
        checkPath(path);
        Stat stat = checkExists(path);
        byte[] data = GenericSerializer.serialize(object, path, true);
        if (stat == null) {
            checkLimit();
            _zkClient.create().creatingParentsIfNeeded().withMode(CreateMode.PERSISTENT).forPath(path, data);
        } else {
            _zkClient.setData().forPath(path, data);
        }
    }

    @Override
    public Object getData(String path, boolean watch) throws Exception {
        checkPath(path);
        Stat stat = checkExists(path);
        if (stat == null) {
            return null;
        }
        byte[] bytes = null;
        if (watch) {
            bytes = _zkClient.getData().watched().forPath(path);
        } else {
            bytes = _zkClient.getData().forPath(path);
        }
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        Object obj = GenericSerializer.deserialize(bytes);
        return obj;
    }

    @Override
    public void setListener(CuratorListener listener) throws Exception {
        if (_listener != null) {
            _zkClient.getCuratorListenable().removeListener(_listener);
        }
        if (listener != null) {
            _zkClient.getCuratorListenable().addListener(listener);
        }
        _listener = listener;
    }

    @Override
    public void setConnectionStateListener(ConnectionStateListener listener) throws Exception {
        if (_connectionStateListener != null) {
            _zkClient.getConnectionStateListenable().removeListener(_connectionStateListener);
        }
        if (listener != null) {
            _zkClient.getConnectionStateListenable().addListener(listener);
        }
        _connectionStateListener = listener;
    }

    @Override
    public List<String> getChildren(String path) throws Exception {
        checkPath(path);
        List<String> children = _zkClient.getChildren().forPath(path);
        return children;
    }

    @Override
    public void close() {
        if (_zkClient != null) {
            try {
                removeListener();
                removeConnectionStateListener();
            } catch (Exception ex) {
                _log.error("Fail to close distributedDataManager, " +
                        "due to remove listener/connectionStateListener failed with exception: {}", ex.getMessage());
            }
        }
        if (_basePathCache != null) {
            try {
                _basePathCache.close();
                _basePathCache = null;
                _log.info("BasePathCache closed successfully.");
            } catch (IOException ex) {
                _log.error("Fail to close distributedDataManager. " +
                        "due to cannot close basePathCache with exception: {}", ex.getMessage());
            }
        }
        _log.info("DistributedDataManager closed.");
    }

    private void removeListener() throws Exception {
        if (_listener != null) {
            _zkClient.getCuratorListenable().removeListener(_listener);
            _listener = null;
            _log.info("Listener removed successfully.");
        }
    }

    private void removeConnectionStateListener() throws Exception {
        if (_connectionStateListener != null) {
            _zkClient.getConnectionStateListenable().removeListener(_connectionStateListener);
            _connectionStateListener = null;
            _log.info("ConnectionStateListener removed successfully.");
        }
    }

    /**
     * Check that the requested path is acceptable for this data manager
     * 
     * @param path the requested path
     */
    private void checkPath(String path) {
        if (!StringUtils.equals(path, _basePath)) {
            // disallow paths outside of the base, or which are more than one level deeper than the base
            String root = _basePath + "/";
            if (!StringUtils.startsWith(path, root)) {
                _log.debug("path '{}' is not within base path '{}'", path, _basePath);
                throw CoordinatorException.fatals.dataManagerPathOutOfBounds(path, _basePath);
            } else if (StringUtils.countMatches(StringUtils.remove(path, root), "/") > 0) {
                _log.debug("path '{}' is more than one level deep below base path '{}'", path, _basePath);
                throw CoordinatorException.fatals.dataManagerPathOutOfBounds(path, _basePath);
            }
        }
    }

    /**
     * Check that adding a new node to this data manager would not exceed the max node limit
     * 
     * @throws Exception if the limit has been reached
     */
    private void checkLimit() throws Exception {
        // in order to speed up writes, check limits against the cache
        // instead of doing live checkExists if possible

        ensureCacheStarted();

        Integer children = null;
        if (_basePathCache != null) {
            List<ChildData> childData = _basePathCache.getCurrentData();
            if (childData != null) {
                children = childData.size();
            }
        }
        if (children == null) {
            _log.warn("{}: cached child node data is not available; falling back to checkExists", _basePath);
            Stat stat = _zkClient.checkExists().forPath(_basePath);
            if (stat != null) {
                children = stat.getNumChildren();
            }
        }
        if (children != null) {
            _log.debug("{}: current nodes = {}; maxNodes = {}",
                    Arrays.asList(_basePath, children.toString(), Long.toString(_maxNodes)).toArray());
            if (children >= _maxNodes) {
                _log.warn("{}: rejecting create because limit of {} has been reached", _basePath, _maxNodes);
                throw new DataManagerFullException();
            }
        }
    }

    /**
     * Use the double-check algorithm to initialize the child path cache for use in limit checking
     */
    private void ensureCacheStarted() {
        if (_basePathCache == null) {
            synchronized (this) {
                if (_basePathCache == null) {
                    try {
                        _basePathCache = new PathChildrenCache(_zkClient, _basePath, false);
                        _basePathCache.start();
                    } catch (Exception ex) {
                        _basePathCache = null;
                        _log.error(String.format("%s: error initializing cache; will re-attempt", _basePath), ex);
                    }
                }
            }
        }
    }
}
