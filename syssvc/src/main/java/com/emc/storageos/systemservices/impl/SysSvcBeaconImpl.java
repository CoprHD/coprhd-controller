/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.systemservices.impl;

import com.emc.storageos.coordinator.client.beacon.impl.ServiceBeaconImpl;
import org.apache.zookeeper.data.Stat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class implements ServiceBeacon for SysSvc
 * provides on-demand publish for state information
 */
public class SysSvcBeaconImpl extends ServiceBeaconImpl {
    private static final Logger _log = LoggerFactory.getLogger(SysSvcBeaconImpl.class);

    /**
     * Re-publish changed service information
     */
    public synchronized void publish() throws Exception {
        Stat stat = _zkConnection.curator().checkExists().forPath(_servicePath);
        /*
         * There is a bug in curator:https://github.com/Netflix/curator/issues/48
         * which cause ConnectionStateListener may not be invoked for unknown reason,
         * we try to call register if path doesn't exist.
         */
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
