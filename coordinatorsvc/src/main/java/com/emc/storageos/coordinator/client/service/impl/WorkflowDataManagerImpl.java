/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.coordinator.client.service.impl;

import java.util.List;

import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.data.Stat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.client.service.DistributedDataManager;
import com.emc.storageos.coordinator.common.impl.ZkConnection;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.api.CuratorListener;
import org.apache.curator.framework.state.ConnectionStateListener;

/**
 * DistributedDataManager implementation which allows unrestricted access to the
 * ZK tree.
 * 
 * This implementation is only approved for use by the Controller workflow processing.
 * By using it, that processing takes responsibility for limiting the consumption of
 * ZK resources in order to prevent an OOM condition.
 * 
 */
public class WorkflowDataManagerImpl implements DistributedDataManager {
    private static final Logger _log = LoggerFactory.getLogger(DistributedDataManagerImpl.class);
    private final CuratorFramework _zkClient;
    private CuratorListener _listener;
    private ConnectionStateListener _connectionStateListener;

    public WorkflowDataManagerImpl(ZkConnection conn) {
        _zkClient = conn.curator();
        _log.info("Unlimited Manager constructed by {}", getCaller());
    }

    @Override
    public Stat checkExists(String path) {
        try {
            Stat stat = _zkClient.checkExists().forPath(path);
            return stat;
        } catch (Exception ex) {
            return null;
        }
    }

    @Override
    public void createNode(String path, boolean watch) throws Exception {
        Stat stat = checkExists(path);
        if (stat == null) {
            _zkClient.create().creatingParentsIfNeeded().withMode(CreateMode.PERSISTENT).forPath(path);
        }
        if (_listener != null && watch) {
            stat = _zkClient.checkExists().watched().forPath(path);
        }
    }

    @Override
    public void removeNode(String path) throws Exception {
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
        Stat stat = checkExists(path);
        byte[] data = GenericSerializer.serialize(object, path, true);
        if (stat == null) {
            _zkClient.create().creatingParentsIfNeeded().withMode(CreateMode.PERSISTENT).forPath(path, data);
        } else {
            _zkClient.setData().forPath(path, data);
        }
    }

    @Override
    public Object getData(String path, boolean watch) throws Exception {
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
                _log.error("Fail to close workflowDataManager, " +
                        "due to remove listener/connectionStateListener failed with exception: {}", ex.getMessage());
            }
        }
        _log.info("WorkflowDataManager closed.");
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
     * Identify the class outside of the coordinator package which instantiated this
     * 
     * @return caller class
     */
    private String getCaller() {
        String myPackage = "com.emc.storageos.coordinator";
        String myClassName = this.getClass().getName();
        boolean myClassFound = false;
        String caller = "unknown";
        for (StackTraceElement element : Thread.currentThread().getStackTrace()) {
            if (!myClassFound && element.getClassName().equals(myClassName)) {
                myClassFound = true;
            } else if (myClassFound) {
                if (!element.getClassName().startsWith(myPackage)) {
                    caller = element.toString();
                    break;
                }
            }
        }
        return caller;
    }
}
