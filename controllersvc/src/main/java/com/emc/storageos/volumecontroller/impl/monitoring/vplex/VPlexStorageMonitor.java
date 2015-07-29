/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.volumecontroller.impl.monitoring.vplex;

import com.emc.storageos.coordinator.client.service.WorkPool;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.volumecontroller.StorageMonitor;
import com.emc.storageos.volumecontroller.StorageMonitorException;
import com.emc.storageos.volumecontroller.impl.monitoring.RecordableEventManager;
import com.emc.storageos.vplex.api.VPlexApiFactory;

/**
 * Storage monitor for VPlex storage systems.
 */
public class VPlexStorageMonitor implements StorageMonitor {

    @SuppressWarnings("unused")
    private DbClient _dbClient;

    @SuppressWarnings("unused")
    private VPlexApiFactory _apiFactory;

    @SuppressWarnings("unused")
    private RecordableEventManager _recordableEventManager = null;

    @SuppressWarnings("unused")
    private long _intervalSeconds = 120;

    /**
     * Setter for the VPlex API factory for Spring bean configuration.
     * 
     * @param apiFactory
     */
    public void setVPlexApiFactory(VPlexApiFactory apiFactory) {
        _apiFactory = apiFactory;
    }

    /**
     * Setter for the DB client for Spring bean configuration.
     * 
     * @param dbClient
     */
    public void setDbClient(DbClient dbClient) {
        _dbClient = dbClient;
    }

    /**
     * Setter for the recordable event manager for Spring bean configuration.
     * 
     * @param eventManager
     */
    public void setRecordableEventManager(RecordableEventManager eventManager) {
        _recordableEventManager = eventManager;
    }

    /**
     * Sets poll interval for events for Spring bean configuration.
     * 
     * @param interval
     */
    public void setIntervalSeconds(long interval) {
        _intervalSeconds = interval;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void startMonitoring(StorageSystem storageDevice, WorkPool.Work work)
            throws StorageMonitorException {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void stopMonitoring(StorageSystem storageDevice)
            throws StorageMonitorException {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void shutdown() {
    }
}
