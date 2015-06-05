/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 *  Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.coordinator.client.service;

import org.apache.curator.framework.recipes.cache.NodeCacheListener;

/**
 * Client should implement this interface if it has interest with zk node change,
 * and then add the listener to CoordinatorClient
 *
 * 3 methods need to know:
 * - getPath(): return the path client wants to listen.
 * - nodeChanged(): called when any change on the specified node occurs
 * - connectionStateChanged(): called when the state of sessions with zk server changed.
 *       like from CONNECTED to DISCONNECTED or vice versa.
 */
public interface NodeListener extends ConnectionStateListener, NodeCacheListener {

    /**
     *
     * @return the path of node client wants to listen
     */
    public String getPath();
}
