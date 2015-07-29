/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.monitoring;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.client.service.ConnectionStateListener;
import com.emc.storageos.db.client.model.StorageSystem;

/**
 * Listener class releases monitoring related local CACHE from the local memory while ZK connection RECONNECT/CONNECT state.
 */
public class ZkConnectionStateListenerForMonitoring implements ConnectionStateListener {

    private Map<StorageSystem.Type, IMonitoringStorageSystem> _monitoringImplMap;
    private final Logger _logger = LoggerFactory.getLogger(ZkConnectionStateListenerForMonitoring.class);

    /**
     * Setter method for the monitoringImpl instances based on the StorageSystem.Type
     * 
     * @param monitoringImplMap
     */
    public void setMonitoringImplMap(
            Map<StorageSystem.Type, IMonitoringStorageSystem> monitoringImplMap) {
        this._monitoringImplMap = monitoringImplMap;
    }

    @Override
    public void connectionStateChanged(State newState) {
        _logger.debug("Entering {}", Thread.currentThread().getStackTrace()[1].getMethodName());
        _logger.info("newState :{}", newState);
        /**
         * If connection state is LOST or SUSPENDED we should clear internal cache(Monitoring).
         */
        if (newState == ConnectionStateListener.State.DISCONNECTED) {
            for (Map.Entry<StorageSystem.Type, IMonitoringStorageSystem> entry : _monitoringImplMap.entrySet()) {
                entry.getValue().clearCache();
            }
        }
        _logger.debug("Exiting {}", Thread.currentThread().getStackTrace()[1].getMethodName());
    }

}
