/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.server.impl;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.coordinator.client.service.InterProcessLockHolder;
import com.emc.storageos.services.util.JmxServerWrapper;

import org.apache.cassandra.service.StorageService;
import org.apache.commons.lang.StringUtils;
import org.apache.curator.framework.recipes.locks.InterProcessLock;
import org.apache.curator.framework.recipes.locks.InterProcessMutex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.management.ListenerNotFoundException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;

public class DbRepairRunnable implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(DbRepairRunnable.class);

    private static final String DB_REPAIR_ZPATH = "dbrepair";
    private static final String GEODB_REPAIR_ZPATH = "geodbrepair";
    // we use DB_REPAIR_LOCK for local/geo db repair both
    private static final String DB_REPAIR_LOCK = "dbrepair";

    public static final int INTERVAL_TIME_IN_MINUTES = 1 * 3 * 60; // 3 hours

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
    private boolean noNewRepair;
    private JmxServerWrapper jmxServer;


    // Status reporting to caller that scheduled this thread to run.
    private Exception threadException;
    private StartStatus status;

    public DbRepairRunnable(JmxServerWrapper jmxServer, ScheduledExecutorService executor, CoordinatorClient coordinator, String keySpaceName, boolean isGeoDbsvc,
            int maxRetryTimes, boolean noNewRepair) {
        this.jmxServer  = jmxServer;
        this.executor = executor;
        this.coordinator = coordinator;
        this.keySpaceName = keySpaceName;
        this.isGeoDbsvc = isGeoDbsvc;
        this.maxRetryTimes = maxRetryTimes;
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

        return StringUtils.join(nodeIds, ',');
    }

    public static String getStateKey(String keySpaceName, boolean isGeoDbsvc) {
        return String.format("%s-%s", isGeoDbsvc ? GEODB_REPAIR_ZPATH : DB_REPAIR_ZPATH, keySpaceName);
    }

    public static DbRepairJobState queryRepairState(CoordinatorClient coordinator, String keySpaceName, boolean isGeoDbsvc) {
        String stateKey = getStateKey(keySpaceName, isGeoDbsvc);

        DbRepairJobState state = coordinator.queryRuntimeState(stateKey, DbRepairJobState.class);

        return state != null ? state : new DbRepairJobState();
    }

    public static String getSelfLockNodeId(InterProcessLock lock) throws Exception {
        if (lock instanceof InterProcessMutex) {
            Collection<String> nodes = ((InterProcessMutex) lock).getParticipantNodes();
            if (nodes == null || nodes.isEmpty()) {
                return null;
            }

            String nodeId = nodes.iterator().next();
            int lastSlash = nodeId.lastIndexOf('/');
            if (lastSlash != -1) {
                nodeId = nodeId.substring(lastSlash + 1);
            }
            return nodeId;
        }

        return null;
    }

    private void saveStates() {
        String stateKey = getStateKey(this.keySpaceName, this.isGeoDbsvc);
        this.coordinator.persistRuntimeState(stateKey, this.state);
    }

    private StartStatus getRepairStatus(InterProcessLock lock) throws Exception {
        this.state = queryRepairState(this.coordinator, this.keySpaceName, this.isGeoDbsvc);

        log.info("Previous repair state: {}", this.state);

        StartStatus status = getRepairStatus(getClusterStateDigest(), this.maxRetryTimes);
        if (status == StartStatus.STARTED) {
            log.info("Starting repair with state: {}", this.state);
            String workerId = getSelfLockNodeId(lock);
            if (workerId != null) {
                this.state.setCurrentWorker(workerId);
            }
        }
        saveStates();
        return status;
    }

    private RepairJobRunner createJobRunner(JmxServerWrapper jmxServer) throws IOException {
        RepairJobRunner.ProgressNotificationListener listener = new RepairJobRunner.ProgressNotificationListener() {
            @Override
            public void onStartToken(String token, int progress) {
                try {
                    state.updateProgress(progress, token);
                    saveStates();
                } catch (Exception e) {
                    log.error("Exception when updating repair progress", e);
                }
            }
        };

        RepairJobRunner runner = new RepairJobRunner(jmxServer, StorageService.instance, this.keySpaceName, this.executor,
                listener, this.state.getCurrentToken(), this.state.getCurrentDigest());

        jmxServer.addConnectionNotificiationListener(runner, null, null);
        StorageService.instance.addNotificationListener(runner, null, null);

        return runner;
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

    public static String getLockName() {
        return DB_REPAIR_LOCK;
    }

    /*
     * it's trick to set new state here, but we need to use IN_PROGRESS when get db repair status
     * in RecoveryManager, it's only used by db repair triggered by scheduler, start() will skip
     * db repair job if already did db repair.
     */
    public void preConfig() {
        this.state = queryRepairState(this.coordinator, this.keySpaceName, this.isGeoDbsvc);
        this.state.inProgress(this.getClusterStateDigest());
        log.info("preConfig db repair state:{}", this.state.toString());
        this.saveStates();
    }

    @Override
    public void run() {
        try (ScopeNotifier notifier = new ScopeNotifier(this)) {
            log.info("prepare db repair");
            // use same lock:DB_REPAIR_LOCK for both local/geo db to ensure db repair sequentially
            try (InterProcessLockHolder holder = new InterProcessLockHolder(this.coordinator, DB_REPAIR_LOCK, log)) {
                log.info("get {} lock, start to do db repair", DB_REPAIR_LOCK);
                this.status = getRepairStatus(holder.getLock());
                if (this.status != StartStatus.STARTED) {
                    return;
                }

                try (RepairJobRunner runner = createJobRunner(jmxServer)) {
                    saveStates(); // Save state to ZK before notify caller to return so it will see the result of call.
                    log.info("Repair started, notifying the triggering thread to return.");
                    notifier.close(); // Notify the thread triggering the repair before we finish

                    while (true) {
                        if (runner.runRepair()) {
                            log.info("Repair keyspace {} at cluster state {} completed successfully", keySpaceName,
                                    this.state.getCurrentDigest());

                            // Repair succeeded, update state info in ZK
                            if (!this.state.success(getClusterStateDigest())) {
                                this.state.fail(this.maxRetryTimes);
                            }

                            saveStates();

                            break;
                        }

                        if (!this.state.retry(this.maxRetryTimes)) {
                            log.error("Repair job {} for keyspace {} failed due to reach max retry times.", this.state.getCurrentDigest(),
                                    keySpaceName);
                            saveStates();
                            break;
                        }
                        saveStates();

                        log.error("Repair run failed for #{} times. Will retry after {} minutes",
                                this.state.getCurrentRetry(), this.repairRetryMin);
                        Thread.sleep(this.repairRetryMin * 60 * 1000L);
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
        } catch (Exception ex) {
            this.threadException = ex;
            log.error("Exception starting", ex);
        }
    }

    public StartStatus getRepairStatus(String clusterDigest, int maxRetryTimes) {
        log.info(String.format("Trying to start repair with digest: %s, max retries: %d, noNewRepair: %s",
                clusterDigest, maxRetryTimes, this.noNewRepair));

        if (this.state.canResume(clusterDigest, maxRetryTimes)) {
            log.info("Resuming previous repair");
            this.state.increaseRetry();
            return StartStatus.STARTED;
        }

        if (this.state.notSuitableForNewRepair(clusterDigest)) {
            log.info(String.format("It's not time to repair %s, last successful repair ended at %d, interval is %d minutes, now is %d",
                    this.keySpaceName, state.getLastSuccessEndTime(), INTERVAL_TIME_IN_MINUTES, System.currentTimeMillis()));
            this.state.cleanCurrentFields();
            return StartStatus.NOT_THE_TIME;
        }

        if (this.noNewRepair) {
            log.info("No matching repair to resume, and we're not allowed to start a new repair.");
            this.state.cleanCurrentFields();
            return StartStatus.NOTHING_TO_RESUME;
        }

        log.info("Starting new repair");
        this.state = new DbRepairJobState(clusterDigest);

        return StartStatus.STARTED;
    }
}
