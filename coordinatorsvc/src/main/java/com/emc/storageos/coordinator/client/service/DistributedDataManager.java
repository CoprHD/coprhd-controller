/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package com.emc.storageos.coordinator.client.service;

import java.util.List;

import org.apache.zookeeper.data.Stat;
import org.apache.curator.framework.api.CuratorListener;
import org.apache.curator.framework.state.ConnectionStateListener;

public interface DistributedDataManager {
    /**
     * Sets or removes a Curator Listener. If listener argument is non null,
     * a listener is added. (If there was a previous listener, it is removed).
     * If the listener argument is a null, the listener that
     * was previously set (if any) will be removed. 
     * (Each instance of DistributedDataManager keeps track of one listener.)
     * @param listener -- CuratorListener
     * @throws Exception
     */
    public void setListener(CuratorListener listener) throws Exception;
    /**
     * Sets or removes a ConnectionStateListener (curator).
     * @param listener org.apache.curator.framework.state.ConnectionStateListener
     * @throws Exception
     */
    public void setConnectionStateListener(ConnectionStateListener listener) throws Exception;
    /**
     * Returns a Stat structure if the node given by path exists,
     * otherwise returns a null.
     * @param path -- String zookeeper path
     * @return Stat
     * @throws Exception
     */
    public Stat checkExists(String path) throws Exception;
    /**
     * Creates an empty node at the given path if one is not already present.
     * The creation mode is PERSISTENT, meaning it survives Bourne node restarts.
     * If watch is set, the watched() flag is set which will cause state updates
     * to be delievered to listeners.
     * @param path -- String zookeeper path
     * @param watch -- If true, sets the watched() attribute, if false, watched() is not specified.
     * @throws Exception
     */
    public void createNode(String path, boolean watch) throws Exception;
    /**
     * If present, removes the node (and any direct children it may have)
     * given by the path. Uses "guaranteed" mode.
     * @param path -- String zookeeper path.
     * @throws Exception
     */
    public void removeNode(String path) throws Exception;
    /**
     * Stores the Java object given by data (which must be Serializable) as data of the
     * zookeeper node give by path (will create the node if necessary).
     * @param path -- String zookeeper path.
     * @param data -- Any arbitrary Java object as long as it is Serializable
     * @throws Exception
     */
    public void putData(String path, Object data) throws Exception;
    /**
     * Returns the data in the zookeeper node given by path. If the node does not exist,
     * or there is no data present in the node, null is returned. The data is returned
     * as a deserialized Java object. Intended to return data persisted with putData().
     * @param path -- String zookeeper path.
     * @param watch -- If true, will set the watched() attribute so that change 
     * notifications will be delivered to listeners.
     * @return a Java object (must be Serializable)
     * @throws Exception
     */
    public Object getData(String path, boolean watch) throws Exception;
    /**
     * Returns a list of the child node names. For example if called on
     * a path /a/b that has children c1 and c2, returns { c1, c2 }.
     * @param path -- String zookeeper path.
     * @return -- List of node names for children of the given path.
     * The list can be empty if there are no children.
     * @throws Exception
     */
    public List<String> getChildren(String path) throws Exception;
    /**
     * Provide a method that caller can close DistributedDataManager
     * will release all the listener and the thread of PathChildrenCache
     */
    public void close();
    
}
