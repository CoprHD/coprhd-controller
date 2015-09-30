/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.auth;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.apache.zookeeper.data.Stat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.client.service.DistributedDataManager;
import com.emc.storageos.coordinator.common.impl.ZkConnection;
import org.apache.curator.framework.api.CuratorListener;
import org.apache.curator.framework.state.ConnectionStateListener;

/**
 * Stub DistributedDataManager class for use with unit tests
 */
public class TestDistributedDataManager implements DistributedDataManager {

    private static final Logger _log = LoggerFactory.getLogger(TestDistributedDataManager.class);
    private ZkConnection _zkConnection = null;
    private String _basePath;
    private long _maxNodes;
    private Map<String, Object> _dataMap = new HashMap<String, Object>();

    public TestDistributedDataManager(ZkConnection zkConnection, String basePath, long maxNodes) {
        _zkConnection = zkConnection;
        if (StringUtils.isEmpty(basePath) || !basePath.startsWith("/") ||
                (basePath.length() < 2) || basePath.endsWith("/")) {
            throw new IllegalArgumentException("basePath must be at least 2 characters long and start with (but not end with) /");
        }
        _basePath = basePath;
        _maxNodes = maxNodes;
        _log.info("{}: Manager constructed with node limit of {}", _basePath, _maxNodes);

    }

    @Override
    public void setListener(CuratorListener listener) throws Exception {
        // TODO Auto-generated method stub

    }

    @Override
    public void setConnectionStateListener(ConnectionStateListener listener)
            throws Exception {
        // TODO Auto-generated method stub

    }

    @Override
    public Stat checkExists(String path) throws Exception {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void createNode(String path, boolean watch) throws Exception {
        // Nothing to do here
    }

    @Override
    public void close() {
        // Nothing to do here
    }

    @Override
    public void removeNode(String path) throws Exception {
        _dataMap.remove(path);
    }

    @Override
    public void removeNode(String path, boolean recursive) throws Exception {
        _dataMap.remove(path);
    }
    
    @Override
    public void putData(String path, Object data) throws Exception {
        _dataMap.put(path, data);
    }

    @Override
    public Object getData(String path, boolean watch) throws Exception {
        return _dataMap.get(path);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.coordinator.client.service.DistributedDataManager#getChildren(java.lang.String)
     */
    @Override
    public List<String> getChildren(String path) throws Exception {
        // We need to return only ClientIP portion of the entries excluding the _basePath
        Set<String> entries = _dataMap.keySet();
        List<String> list = new ArrayList<String>();
        for (String str : entries) {
            list.add(str.replaceFirst(_basePath + "/", ""));
        }
        return list;
    }
    
    
    
}
