package com.emc.storageos.systemservices.impl.jobs.consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.storageos.coordinator.client.model.Constants;
import com.emc.storageos.coordinator.client.model.DbConsistencyStatus;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.coordinator.client.service.DistributedQueueItemProcessedCallback;
import com.emc.storageos.coordinator.client.service.impl.DistributedQueueConsumer;
import com.emc.storageos.db.client.impl.DbChecker;
import com.emc.storageos.model.db.DbConsistencyStatusRestRep.Status;
import com.emc.storageos.systemservices.impl.jobs.DbConsistencyJob;

public class DbConsistencyJobConsumer extends DistributedQueueConsumer<DbConsistencyJob> {
    private static final Logger log = LoggerFactory.getLogger(DbConsistencyJobConsumer.class);
    @Autowired
    private CoordinatorClient coordinator;
    @Autowired
    private DbChecker dbCheker;
    
    @Override
    public void consumeItem(DbConsistencyJob job, DistributedQueueItemProcessedCallback callback) throws Exception {
        DbConsistencyStatus status = dbCheker.getStatusFromZk();
        if (isFreshStart(status)) {
            log.info("it's first time to run db consistency check, init status in zk");
            createStatusInZk();
        } else if (status.isFinished()) {
            log.info("there is finished state, move it to previous");
            status.moveToPrevious();
        } else if (status.isCancelled()) {
            log.info("it's in cancel state, return");
            return;
        }
        
        try {
            dbCheker.checkDataObjects(false);
            dbCheker.checkIndexingCFs(false);
            status = markResult();
            callback.itemProcessed();
        } catch(Exception e) {
            log.error("failed to check db consistency {}", e);
            status = markFailure();
        } finally {
            log.info("db consistency check done, persist final result {} in zk", status.getStatus());
            this.dbCheker.persistStatus(status);
        }
    }

    private DbConsistencyStatus markFailure() {
        DbConsistencyStatus status = this.dbCheker.getStatusFromZk();
        status.setStatus(Status.FAILED);
        return status;
    }
    
    private DbConsistencyStatus markResult() {
        DbConsistencyStatus status = dbCheker.getStatusFromZk();
        if (status.getInconsistencyCount() > 0) {
            log.info("there are {} inconsistency found, mark result as fail", status.getInconsistencyCount());
            status.setStatus(Status.FAILED);
        } else {
            log.info("no inconsistency record found, mark result as successful");
            status.setStatus(Status.SUCCESS);
        }
        return status;
    }

    private void createStatusInZk() {
        DbConsistencyStatus status = new DbConsistencyStatus();
        status.init();
        this.coordinator.persistRuntimeState(Constants.DB_CONSISTENCY_STATUS, status);
    }
    
    private boolean isFreshStart(DbConsistencyStatus status) {
        return status==null;
    }
}
