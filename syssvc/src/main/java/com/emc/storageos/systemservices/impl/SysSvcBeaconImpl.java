/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.systemservices.impl;

import com.emc.storageos.coordinator.client.beacon.impl.ServiceBeaconImpl;
import com.emc.storageos.coordinator.common.Service;
import com.emc.storageos.coordinator.common.impl.ServiceImpl;
import com.emc.storageos.coordinator.common.impl.ZkConnection;
import com.emc.storageos.coordinator.common.impl.ZkPath;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.state.ConnectionState;
import org.apache.curator.framework.state.ConnectionStateListener;
import org.apache.curator.utils.EnsurePath;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.data.Stat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.concurrent.*;

/**
 * Class implements ServiceBeacon for SysSvc
 * provides on-demand publish for state information
 */
public class SysSvcBeaconImpl extends ServiceBeaconImpl {
    private static final Logger _log = LoggerFactory.getLogger(SysSvcBeaconImpl.class);

    /**
     * Re-publish changed service information
     */
    public synchronized void publish () throws Exception {
        Stat stat = _zkConnection.curator().checkExists().forPath(_servicePath);
        /*
         * There is a bug in curator:https://github.com/Netflix/curator/issues/48 
         * which cause ConnectionStateListener may not be invoked for unknown reason,
         * we try to call register if path doesn't exist.
         * */
        if (stat == null) {
        	 boolean result = this.register();
        	 if (!result) {
             	_log.error("servicePath:{} doesn't exist and register fail ", this._servicePath);
            	throw new IllegalStateException("servicePath doesn't exist and register fail");
        	 }
        }
        _zkConnection.curator().setData().forPath(_servicePath, _service.serialize());
        _log.info("Service info updated @ {}", _servicePath);
        return;
    }
}
