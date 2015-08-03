/*
 * Copyright (c) 2008-2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.monitoring;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.client.service.DistributedQueueItemProcessedCallback;
import com.emc.storageos.db.client.DbClient;

public class XIVMonitoringImpl implements IMonitoringStorageSystem {
    private final Logger logger = LoggerFactory.getLogger(XIVMonitoringImpl.class);
    private DbClient dbClient;

    public void setDbClient(DbClient dbClient) {
        this.dbClient = dbClient;
    }

    @Override
    public void startMonitoring(MonitoringJob monitoringJob,
            DistributedQueueItemProcessedCallback callback) {
        logger.debug("Entering {}", Thread.currentThread().getStackTrace()[1].getMethodName());
        // TODO Auto-generated method stub
        logger.debug("Exiting {}", Thread.currentThread().getStackTrace()[1].getMethodName());
    }

    @Override
    public void scheduledMonitoring() {
        logger.debug("Entering {}", Thread.currentThread().getStackTrace()[1].getMethodName());
        // TODO Auto-generated method stub
        logger.debug("Exiting {}", Thread.currentThread().getStackTrace()[1].getMethodName());
    }

    @Override
    public void stopMonitoringStaleSystem() {
        logger.debug("Entering {}", Thread.currentThread().getStackTrace()[1].getMethodName());
        // TODO Auto-generated method stub
        logger.debug("Exiting {}", Thread.currentThread().getStackTrace()[1].getMethodName());
    }

    @Override
    public void clearCache() {
        logger.debug("Entering {}", Thread.currentThread().getStackTrace()[1].getMethodName());
        // TODO Auto-generated method stub
        logger.debug("Exiting {}", Thread.currentThread().getStackTrace()[1].getMethodName());
    }
}
