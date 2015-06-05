/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 *  Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.db.server.impl;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.coordinator.client.service.InterProcessLockHolder;
import com.emc.storageos.services.util.Strings;
import org.apache.cassandra.service.StorageService;
import org.apache.curator.framework.recipes.locks.InterProcessLock;
import org.apache.curator.framework.recipes.locks.InterProcessMutex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.management.ListenerNotFoundException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;

public class DbRepairRunnable implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(DbRepairRunnable.class);

    private static final String DB_REPAIR_LOCK = "dbrepair";
    private static final String GEODB_REPAIR_LOCK = "geodbrepair";

    public static enum StartStatus {
        STARTED, ALREADY_RUNNING, NOT_THE_TIME, NOTHING_TO_RESUME
    }

    // Minutes to sleep for next retry after db repair failure
    private int repairRetryMin = 3;

    private ScheduledExecutorService executor;
    private CoordinatorClient coordinator;
    private String keySpaceName;
    private boolean isGeoDbsvc;
    private DbRepairJobState state;
    private int maxRetryTimes;
    private boolean crossVdc;
    private boolean noNewRepair;

    // Status reporting to caller that scheduled this thread to run.
    private Exception threadException;
    private StartStatus status;

    public DbRepairRunnable(ScheduledExecutorService executor, CoordinatorClient coordinator, String keySpaceName, boolean isGeoDbsvc,
                            int maxRetryTimes, boolean crossVdc, boolean noNewRepair) {
        this.executor = executor;
        this.coordinator = coordinator;
        this.keySpaceName = keySpaceName;
        this.isGeoDbsvc = isGeoDbsvc;
        this.maxRetryTimes = maxRetryTimes;
        this.crossVdc = crossVdc;
        this.noNewRepair = noNewRepair;
    }

    public StartStatus getStatus() throws Exception {
        if (this.threadException != null) {
            throw this.threadException;
        }

        return this.status;
    }

    public static String getClusterStateDigest() {
        ArrayList<String> nodeIds = new ArrayList<>();
        for (Map.Entry<String, String> entry : StorageService.instance.getHostIdMap().entrySet()) {
            nodeIds.add(String.format("%s=%s", entry.getKey(), entry.getValue()));
        }

        Collections.sort(nodeIds);

        return Strings.join(",", nodeIds);
    }

    public static String getStateKey(String keySpaceName, boolean isGeoDbsvc) {
        return String.format("%s-%s", isGeoDbsvc ? GEODB_REPAIR_LOCK : DB_REPAIR_LOCK, keySpaceName);
    }

    public static DbRepairJobState queryRepairState(CoordinatorClient coordinator, String keySpaceName, boolean isGeoDbsvc) {
        String stateKey = getStateKey(keySpaceName, isGeoDbsvc);

        DbRepairJobState state = coordinator.queryRuntimeState(stateKey, DbRepairJobState.class);

        return state != null ? state : new DbRepairJobState();
    }

    public static String getSelfLockNodeName(InterProcessLock lock) throws Exception {
        if (lock instanceof InterProcessMutex) {
            Collection<String> nodes = ((InterProcessMutex)lock).getParticipantNodes();
            if (nodes == null || nodes.size() == 0) {
                return null;
            }

            String nodeName = nodes.iterator().next();
            int lastSlash = nodeName.lastIndexOf('/');
            if (lastSlash != -1) {
                nodeName = nodeName.substring(lastSlash + 1);
            }
            return nodeName;
        }

        return null;
    }

    private void saveStates() {
        String stateKey = getStateKey(this.keySpaceName, this.isGeoDbsvc);
        this.coordinator.persistRuntimeState(stateKey, this.state);
    }

    private StartStatus start(InterProcessLock lock) throws Exception {
        this.state = queryRepairState(this.coordinator, this.keySpaceName, this.isGeoDbsvc);

        log.info("Previous repair state: {}", this.state.toString());

        StartStatus status = start(getClusterStateDigest(), this.maxRetryTimes, this.crossVdc);
        if (status == StartStatus.STARTED) {
            String workerId = getSelfLockNodeName(lock);
            if (workerId != null) {
                this.state.setCurrentWorker(workerId);
            }

            log.info("Starting repair with state: {}", this.state.toString());

            saveStates();
        }

        return status;
    }

    private RepairJobRunner createJobRunner() {
        RepairJobRunner.ProgressNotificationListener listener = new RepairJobRunner.ProgressNotificationListener(){
            @Override
            public void onStartToken(String token, int progress) {
                try {
                    updateProgress(progress, token);
                    saveStates();
                } catch (Exception e) {
                    log.error("Exception when updating repair progress", e);
                }
            }
        };

        return new RepairJobRunner(StorageService.instance, this.keySpaceName, this.executor,
                !this.crossVdc, listener, this.state.getCurrentToken(), this.state.getCurrentDigest());
    }

    private static class ScopeNotifier implements AutoCloseable {
        private Object syncObj;

        public ScopeNotifier(Object syncObj) {
            this.syncObj = syncObj;
        }

        @Override
        public void close() {
            if (this.syncObj != null) {
                synchronized (this.syncObj) {
                    this.syncObj.notify();
                }
                this.syncObj = null;
            }
        }
    }

    public static String getLockName(boolean isGeoDbsvc, String keySpaceName) {
        return String.format("%s-%s", isGeoDbsvc ? GEODB_REPAIR_LOCK : DB_REPAIR_LOCK, keySpaceName);
    }

    @Override
    public void run() {
        final String lockName = getLockName(this.isGeoDbsvc, keySpaceName);
        try (ScopeNotifier notifier = new ScopeNotifier(this)) {
            try (InterProcessLockHolder holder = InterProcessLockHolder.acquire(this.coordinator, lockName, log, 20 * 1000)) {
                if (holder == null) { // Cannot obtain lock, an repair is already running by someone else
                    log.info("Cannot obtain repair lock, maybe some other thread is doing repair now.");
                    this.status = StartStatus.ALREADY_RUNNING;
                    return;
                }

                this.status = start(holder.getLock());
                if (this.status != StartStatus.STARTED) {
                    return;
                }

                try (RepairJobRunner runner = createJobRunner()) {
                    saveStates(); // Save state to ZK before notify caller to return so it will see the result of call.
                    log.info("Repair started, notifying the triggering thread to return.");
                    notifier.close(); // Notify the thread triggering the repair before we finish

                    while (true) {
                        if (runner.runRepair()) {
                            log.info("Repair keyspace {} at cluster state {} completed successfully", keySpaceName, this.state.getCurrentDigest());

                            // Repair succeeded, update state info in ZK
                            if (!success(getClusterStateDigest())) {
                                fail(this.maxRetryTimes);
                            }

                            saveStates();

                            break;
                        }

                        if (!retry(this.maxRetryTimes)) {
                            log.error("Repair job {} for keyspace {} failed due to reach max retry times.", this.state.getCurrentDigest(), keySpaceName);
                            saveStates();
                            break;
                        }
                        saveStates();

                        log.error("Repair run failed for #{} times. Will retry after {} minutes",
                                this.state.getCurrentRetry(), this.repairRetryMin);
                        Thread.sleep(this.repairRetryMin * 60 * 1000);
                    }

                    return;
                } catch (ListenerNotFoundException ex) {
                    this.threadException = ex;
                    log.error("Failed to remove notification listener", ex);
                }
            } catch (Exception ex) {
                this.threadException = ex;
                log.error("Exception starting", ex);
            }
        }
    }

    public StartStatus start(String clusterDigest, int maxRetryTimes, boolean crossVdc) {
        log.info(String.format("Trying to start repair with digest: %s, max retries: %d, cross VDC: %s",
                clusterDigest, maxRetryTimes, Boolean.toString(crossVdc)));
        // See if we can resume: there's is something to resume, and the retry time is not reaching limit, and is in compatible config
        if (this.state.getCurrentToken() != null && this.state.getCurrentRetry() < maxRetryTimes
                && this.state.getCurrentDigest().equals(clusterDigest) && (this.state.getCurrentCrossVdc() | !crossVdc) ) {
            log.info("Resuming previous repair");
            this.state.setCurrentRetry(this.state.getCurrentRetry() + 1);
        } else if (this.noNewRepair) {
            log.info("No matching reapir to resume, and we're not allowed to start a new repair.");
            return StartStatus.NOTHING_TO_RESUME;
        } else {
            log.info("Starting new repair");
            this.state.setCurrentRetry(0);
            this.state.setCurrentToken("");
            this.state.setCurrentProgress(0);
            this.state.setCurrentDigest(clusterDigest);
            long curTime = System.currentTimeMillis();
            this.state.setCurrentStartTime(curTime);
            this.state.setCurrentUpdateTime(curTime);
            this.state.setCurrentCrossVdc(crossVdc);
        }

        return StartStatus.STARTED;
    }

    public void updateProgress(int progress, String currentToken) {
        this.state.setCurrentProgress(progress);
        this.state.setCurrentToken(currentToken);
    }

    public boolean success(String clusterDigest) {
        if (!clusterDigest.equals(this.state.getCurrentDigest())) {
            return false;
        }

        this.state.setLastSuccessDigest(this.state.getCurrentDigest());
        this.state.setLastSuccessStartTime(this.state.getCurrentStartTime());
        this.state.setLastSuccessEndTime(System.currentTimeMillis());
        this.state.setLastCrossVdc(this.state.getCurrentCrossVdc());

        this.state.setCurrentToken(null);
        this.state.setCurrentProgress(null);
        this.state.setCurrentRetry(null);
        this.state.setCurrentDigest(null);
        this.state.setCurrentWorker(null);
        this.state.setCurrentStartTime(null);
        this.state.setCurrentCrossVdc(null);

        return true;
    }

    public boolean retry(int maxRetryTimes) {
        this.state.setCurrentRetry(this.state.getCurrentRetry() + 1);

        if (this.state.getCurrentRetry() > maxRetryTimes) {
            log.error("Current db repair retry number({}) exceed the limit({})",
                    this.state.getCurrentRetry(), maxRetryTimes);
            return false;
        }
        return true;
    }

    public void fail(int maxRetryTimes) {
        if (this.state.getCurrentRetry() <= maxRetryTimes) {
            this.state.setCurrentRetry(maxRetryTimes + 1);
        }
    }
}
