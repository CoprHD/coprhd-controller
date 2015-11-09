/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.systemservices.impl.jobs.backupscheduler;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.management.backup.BackupConstants;
import com.emc.storageos.management.backup.BackupFileSet;
import com.emc.storageos.security.audit.AuditLogManager;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.services.util.NamedScheduledThreadPoolExecutor;
import com.emc.storageos.systemservices.impl.jobs.common.JobConstants;
import com.emc.storageos.systemservices.impl.property.Notifier;
import com.emc.storageos.systemservices.impl.resource.BackupService;
import com.emc.storageos.systemservices.impl.util.SkipOutputStream;

import org.apache.curator.framework.recipes.leader.LeaderSelector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.coordinator.client.service.impl.LeaderSelectorListenerImpl;
import com.emc.storageos.coordinator.common.Service;
import com.emc.storageos.db.client.model.EncryptionProvider;
import com.emc.storageos.management.backup.BackupOps;

public class BackupScheduler extends Notifier implements Runnable, Callable<Object>, JobConstants {

    private static final Logger log = LoggerFactory.getLogger(BackupScheduler.class);
    private static final long SCHEDULE_BACKUP_RETRY_OFFSITE = 5 * 60 * 1000L;
    private volatile boolean isLeader = false;

    private static volatile BackupScheduler singletonInstance;

    @Autowired
    private CoordinatorClient coordinatorClient;

    @Autowired
    private DbClient dbClient;

    @Autowired
    @Qualifier("encryptionProvider")
    private EncryptionProvider encryptionProvider;

    @Autowired
    private BackupOps backupOps;

    @Autowired
    private BackupService backupService;

    @Autowired
    @Qualifier("backupFolder")
    private File backupFolder;

    @Autowired
    private AuditLogManager auditMgr;

    @Autowired
    private Service serviceinfo;

    private SchedulerConfig cfg;
    private BackupExecutor backupExec;
    private UploadExecutor uploadExec;

    private ScheduledExecutorService service;
    private ScheduledFuture<?> scheduledTask;

    public BackupScheduler() {
    }

    public static BackupScheduler getSingletonInstance() {
        return singletonInstance;
    }

    public UploadExecutor getUploadExecutor() {
        if (this.uploadExec == null) {
            this.uploadExec = new UploadExecutor(this.cfg, this);
        }
        return this.uploadExec;
    }

    private void cancelScheduledTask() {
        this.scheduledTask.cancel(false);
        log.info("Previous scheduled task cancelled");
        this.scheduledTask = null;
    }

    /**
     * All scheduling adjustment should be done by scheduling this to scheduler thread pool,
     * so this is synchronized with scheduled executions.
     */
    @Override
    public Object call() throws Exception {
        log.info("Starting to configure scheduler");

        if (this.scheduledTask != null) {
            cancelScheduledTask();
        }

        try {
            this.cfg.reload();
        } catch (ParseException e) {
            log.error("Failed to initialize", e);
            return null;
        }

        if (this.cfg.uploadUrl == null && !this.cfg.schedulerEnabled) {
            log.info("External upload server is not configured and scheduler is disabled, nothing to do, quiting...");
            return null;
        }

        log.info("Enabling scheduler");

        this.backupExec = new BackupExecutor(this.cfg, this);
        this.uploadExec = new UploadExecutor(this.cfg, this);

        // Run once immediately in case we're crashed previously
        run();

        return null;
    }

    private void scheduleNextRun() {
        Calendar now = this.cfg.now();
        ScheduleTimeRange cur = new ScheduleTimeRange(this.cfg.interval, this.cfg.intervalMultiple, now);
        Date coming = cur.minuteOffset(this.cfg.startOffsetMinutes);
        if (coming.before(now.getTime())) {
            coming = cur.next().minuteOffset(this.cfg.startOffsetMinutes);
        }

        long millisToSleep = coming.getTime() - System.currentTimeMillis();
        log.info("schedule next backup run at {}", coming);
        this.scheduledTask = this.service.schedule((Runnable) this, millisToSleep, TimeUnit.MILLISECONDS);
    }

    @Override
    public void run() {
        try {
            log.info("Backup scheduler thread goes live");

            this.cfg.reload();

            // If we made any new backup, notify uploader thread to perform upload
            this.backupExec.runOnce();
            this.uploadExec.runOnce();

        } catch (Exception e) {
            log.error("Exception occurred in scheduler", e);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
                return;
            }
        }

        // Will retry every 5 min if schedule next run fail
        while (isLeader && !service.isShutdown()) {
            try {
                scheduleNextRun();
                break;
            } catch (Exception e) {
                log.error("Exception occurred when schedule next run", e);
                try {
                    Thread.sleep(SCHEDULE_BACKUP_RETRY_OFFSITE);
                } catch (InterruptedException ex) {
                    log.debug("Interrupt exception, ignoring...");
                }
            }
        }
    }

    public void auditBackup(OperationTypeEnum auditType,
            String operationalStatus,
            String description,
            Object... descparams) {
        this.auditMgr.recordAuditLog(null, null,
                BackupConstants.EVENT_SERVICE_TYPE,
                auditType,
                System.currentTimeMillis(),
                operationalStatus,
                description,
                descparams);
    }

    public void createBackup(String tag) {
        this.backupOps.createBackup(tag, true);
    }

    public void deleteBackup(String tag) {
        this.backupOps.deleteBackup(tag);
    }

    /**
     * This method will scan this node's local /data/backup folder to get a rough list of
     * available backups
     * 
     * @return
     */
    public Set<String> getNodeBackupTags() {
        File[] files = this.backupFolder.listFiles();

        Set<String> tags = new HashSet<>(files.length);

        for (File file : files) {
            if (file.isDirectory()) {
                tags.add(file.getName());
            }
        }

        return tags;
    }

    /**
     * Get a list of backup tags. A tag could represent an ongoing backup that's not fully completed.
     * 
     * @param ignoreDownNodes
     * @return
     */
    public Set<String> getClusterBackupTags(boolean ignoreDownNodes) {
        return this.backupOps.listRawBackup(ignoreDownNodes).uniqueTags();
    }

    public BackupFileSet getDownloadFiles(String tag) {
        return this.backupService.getDownloadList(tag);
    }

    public void uploadTo(BackupFileSet files, long offset, OutputStream uploadStream) throws IOException {
        this.backupService.collectData(files, new SkipOutputStream(uploadStream, offset));
    }

    public String generateZipFileName(String tag, BackupFileSet files) {
        Set<String> availableNodes = files.uniqueNodes();
        Set<String> nodeIds = this.coordinatorClient.getInetAddessLookupMap().getControllerNodeIPLookupMap().keySet();
        String[] allNodes = nodeIds.toArray(new String[nodeIds.size()]);
        Arrays.sort(allNodes);
        int backupNodeCount = 0;
        for (int i = 0; i < allNodes.length; i++) {
            if (availableNodes.contains(allNodes[i])) {
                backupNodeCount++;
            }
        }

        return ScheduledBackupTag.toZipFileName(tag, nodeIds.size(), backupNodeCount);
    }

    public List<String> getDescParams(final String tag) {
        final String nodeId = this.serviceinfo.getNodeId();
        return new ArrayList<String>() {
            {
                add(tag);
                add(nodeId);
            }
        };
    }

    /**
     * Called when related system properties are changed, and we need to reschedule
     */
    @Override
    public void doNotify() throws Exception {
        log.info("Received notification that related system properties are changed");

        ScheduledExecutorService svc = service;
        if (svc != null) {
            try {
                svc.schedule((Callable<Object>) this, 0L, TimeUnit.MICROSECONDS);
            } catch (RejectedExecutionException ex) {
                if (svc.isShutdown()) {
                    log.info("Property change notification ignored because this node is no longer backup leader.");
                } else {
                    throw ex;
                }
            }
        } else {
            log.info("Property change notification ignored because this node is no longer backup leader.");
        }
    }

    /**
     * Called when initializing Spring bean, make sure only one node(leader node) performs backup job
     * */
    public void startLeaderSelector() throws InterruptedException {
        while (!this.coordinatorClient.isConnected()) {
            log.info("waiting for connecting to zookeeper");
            try {
                Thread.sleep(BackupConstants.BACKUP_WAINT_BEFORE_RETRY_ZK_CONN);
            } catch (InterruptedException e) {
                log.warn("Exception while sleeping,ignore", e);
                throw e;
            }
        }

        singletonInstance = this;
        this.cfg = new SchedulerConfig(this.coordinatorClient, this.encryptionProvider, this.dbClient);

        LeaderSelector leaderSelector = this.coordinatorClient.getLeaderSelector(BackupConstants.BACKUP_LEADER_PATH,
                new BackupLeaderSelectorListener());
        leaderSelector.autoRequeue();
        leaderSelector.start();
    }

    private class BackupLeaderSelectorListener extends LeaderSelectorListenerImpl {

        @Override
        protected void startLeadership() throws Exception {
            log.info("This node is selected as backup leader, starting Backup Scheduler");

            isLeader = true;

            service = new NamedScheduledThreadPoolExecutor("BackupScheduler", 1);
            ((NamedScheduledThreadPoolExecutor) service).setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
            service.schedule((Callable<Object>) BackupScheduler.this, 0L, TimeUnit.MICROSECONDS);
        }

        @Override
        protected void stopLeadership() {
            log.info("give up leader, stop backup scheduler");

            isLeader = false;

            // Stop scheduler thread
            service.shutdown();
            try {
                while (!service.awaitTermination(30, TimeUnit.SECONDS)) {
                    log.info("Waiting scheduler thread pool to shutdown for another 30s");
                }
            } catch (InterruptedException e) {
                log.error("Interrupted while waiting to shutdown scheduler thread pool.", e);
                Thread.currentThread().interrupt();
                return;
            }
        }
    }
}
