/*
 * Copyright (c) 2008-2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.systemservices.impl.jobs.consumer;

import java.util.Date;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.lang.time.DurationFormatUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.client.model.DbConsistencyStatus;
import com.emc.storageos.coordinator.client.service.DistributedQueueItemProcessedCallback;
import com.emc.storageos.coordinator.client.service.impl.DistributedQueueConsumer;
import com.emc.storageos.db.client.impl.DbCheckerFileWriter;
import com.emc.storageos.db.client.impl.DbConsistencyChecker;
import com.emc.storageos.db.common.DbSchemaChecker;
import com.emc.storageos.model.db.DbConsistencyStatusRestRep.Status;
import com.emc.storageos.systemservices.impl.jobs.DbConsistencyJob;
import com.emc.storageos.systemservices.impl.upgrade.CoordinatorClientExt;
import com.netflix.astyanax.connectionpool.exceptions.ConnectionException;

public class DbConsistencyJobConsumer extends DistributedQueueConsumer<DbConsistencyJob> {
    private static final Logger log = LoggerFactory.getLogger(DbConsistencyJobConsumer.class);
    private CoordinatorClientExt coordinator;
    private DbConsistencyChecker dbChecker;
    private static final String[] MODEL_PACKAGES = new String[] { "com.emc.storageos.db.client.model" };
    private static final AtomicBoolean schemaInitialized = new AtomicBoolean(false);

    @Override
    public void consumeItem(DbConsistencyJob job, DistributedQueueItemProcessedCallback callback) throws Exception {
        DbConsistencyStatus status = dbChecker.getStatusFromZk();
        log.info("start db consistency check, current status:{}", status);

        long beginMillis = new Date().getTime();

        if (isFreshStart(status)) {
            log.info("it's first time to run db consistency check, init status in zk");
            status = createStatusInZk();
        } else if (status.isFinished()) {
            log.info("there is finished state, move it to previous");
            status.moveToPrevious();
        } else if (status.isCancelled()) {
            log.info("current status is cancel, restart db consistency check");
            status.init();
        }
        status.setWorkingNodeID(coordinator.getMyNodeId());
        status.setWorkingNodeName(coordinator.getMyNodeName());
        
        try {
            dbChecker.persistStatus(status);
            initSchemaIfNot();
            dbChecker.check();
            status = markResult();
        } catch (CancellationException ce) {
            log.warn("cancellation:{}", ce.getMessage());
            status.markResult(Status.CANCEL);
        } catch (ConnectionException e) {
            log.warn("ConnectionException:{}", e);
            status.markResult(Status.FAILED);
        } catch (Exception e) {
            log.error("failed to check db consistency {}", e);
            status = markFailure();
        } finally {
            log.info("db consistency check done, persist final result {} in zk", status.getStatus());
            this.dbChecker.persistStatus(status);

            log.info("db consistency check consumed: {}",
                    DurationFormatUtils.formatDurationHMS(System.currentTimeMillis() - beginMillis));

            callback.itemProcessed();
            DbCheckerFileWriter.close();
        }
    }

    private void initSchemaIfNot() throws Exception {
        if (!schemaInitialized.get()) {
            log.info("init Data Object Type");
            DbSchemaChecker.checkSourceSchema(MODEL_PACKAGES);
            schemaInitialized.getAndSet(true);
        }
    }

    private DbConsistencyStatus markFailure() {
        DbConsistencyStatus status = this.dbChecker.getStatusFromZk();
        status.markResult(Status.FAILED);
        return status;
    }

    private DbConsistencyStatus markResult() {
        DbConsistencyStatus status = dbChecker.getStatusFromZk();
        if (status.getInconsistencyCount() > 0) {
            log.info("there are {} inconsistency found, mark result as fail", status.getInconsistencyCount());
            status.markResult(Status.FAILED);

            log.info(String.format(
                    "Inconsistent data found. Clean up files [%s] are created. please read into them for further operations.",
                    DbCheckerFileWriter.getGeneratedFileNames()));
            status.setCleanupFiles(DbCheckerFileWriter.getGeneratedFileNames());
        } else {
            log.info("no inconsistency record found, mark result as successful");
            status.markResult(Status.SUCCESS);
        }
        return status;
    }

    private DbConsistencyStatus createStatusInZk() {
        DbConsistencyStatus status = new DbConsistencyStatus();
        status.init();
        return status;
    }

    private boolean isFreshStart(DbConsistencyStatus status) {
        return status == null;
    }

    public CoordinatorClientExt getCoordinator() {
        return coordinator;
    }

    public void setCoordinator(CoordinatorClientExt coordinator) {
        this.coordinator = coordinator;
    }

    public DbConsistencyChecker getDbChecker() {
        return dbChecker;
    }

    public void setDbChecker(DbConsistencyChecker dbChecker) {
        this.dbChecker = dbChecker;
    }
}
