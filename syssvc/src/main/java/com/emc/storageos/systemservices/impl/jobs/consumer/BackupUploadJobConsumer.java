/*
 * Copyright (c) 2008-2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.systemservices.impl.jobs.consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.client.service.DistributedQueueItemProcessedCallback;
import com.emc.storageos.coordinator.client.service.impl.DistributedQueueConsumer;
import com.emc.storageos.systemservices.impl.jobs.backupscheduler.BackupScheduler;
import com.emc.vipr.model.sys.backup.BackupUploadStatus;

public class BackupUploadJobConsumer extends DistributedQueueConsumer<BackupUploadStatus> {
    private static final Logger log = LoggerFactory.getLogger(BackupUploadJobConsumer.class);
    private BackupScheduler backupScheduler;

    /**
     * Sets backup scheduler client
     *
     * @param backupScheduler the backup scheduler client instance
     */
    public void setBackupScheduler(BackupScheduler backupScheduler) {
        this.backupScheduler = backupScheduler;
    }

    @Override
    public void consumeItem(BackupUploadStatus job, DistributedQueueItemProcessedCallback callback) throws Exception {
        try {
            log.info("Upload backup({}) begin", job.getBackupName());
            backupScheduler.getUploadExecutor().runOnce(job.getBackupName());
            log.info("Upload backup({}) finish", job.getBackupName());
        } catch (Exception e) {
            log.error("Upload backup({}) failed", job.getBackupName(), e);
        } finally {
            callback.itemProcessed();
            log.info("Upload backup job done");
        }
    }
}
