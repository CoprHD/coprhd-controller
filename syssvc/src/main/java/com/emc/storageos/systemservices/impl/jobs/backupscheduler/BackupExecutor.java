/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.systemservices.impl.jobs.backupscheduler;

import com.emc.storageos.management.backup.BackupConstants;
import com.emc.storageos.management.backup.exceptions.BackupException;
import com.emc.storageos.security.audit.AuditLogManager;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.services.util.Strings;
import com.emc.storageos.svcs.errorhandling.resources.InternalServerErrorException;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Set;

/**
 * Class to do scheduled backup.
 */
public class BackupExecutor {
    private static final Logger log = LoggerFactory.getLogger(BackupExecutor.class);

    private SchedulerConfig cfg;
    private BackupScheduler cli;

    public BackupExecutor(SchedulerConfig cfg, BackupScheduler cli) {
        this.cfg = cfg;
        this.cli = cli;
    }

    public void runOnce() throws Exception {
        if (this.cfg.schedulerEnabled) {
            try (AutoCloseable lock = this.cfg.lock()) {
                this.cfg.reload();
                
                log.info("Start to remove deleted backups");
                removeDeletedBackups();

                log.info("Start to do backup job");
                if (shouldDoBackup()) {
                    doBackup();
                }

                log.info("Start to delete expired backups");
                deleteExpiredBackups();
            } catch (Exception e) {
                log.error("Fail to run schedule backup", e);
            }
        }
    }

    private void removeDeletedBackups() {
        Set<String> clusterTags;
        try {
            clusterTags = this.cli.getClusterBackupTags(false);
        } catch (Exception e) {
            log.error("Failed to list backups from all nodes", e);
            return;
        }

        boolean modified = false;
        for (String tag : new ArrayList<>(this.cfg.retainedBackups)) {
            if (!clusterTags.contains(tag)) {
                log.info("Remove tag from retained backup {}", tag);
                this.cfg.retainedBackups.remove(tag);
                modified = true;
            }
        }

        if (modified) {
            this.cfg.persist();
        }
    }

    private boolean shouldDoBackup() throws ParseException, InterruptedException {
        Calendar now = this.cfg.now();
        ScheduleTimeRange curTimeRange = new ScheduleTimeRange(this.cfg.interval, this.cfg.intervalMultiple, now);
        Date expected = curTimeRange.minuteOffset(this.cfg.startOffsetMinutes);
        Date nowDate = now.getTime();

        log.info("Now is {} and expected run time is {}",
                ScheduledBackupTag.toTimestamp(nowDate),
                ScheduledBackupTag.toTimestamp(expected));

        // if now is before target time && this is NOT first backup
        if (nowDate.before(expected) && !this.cfg.retainedBackups.isEmpty()) {
            return false;
        }

        Date lastBackupDateTime = this.cfg.retainedBackups.isEmpty() ? null :
                ScheduledBackupTag.parseBackupTag(this.cfg.retainedBackups.last());

        log.info("Last backup is {}, expected is {}", lastBackupDateTime == null ? "N/A" :
                ScheduledBackupTag.toTimestamp(lastBackupDateTime),
                ScheduledBackupTag.toTimestamp(expected));

        if (lastBackupDateTime != null && curTimeRange.contains(lastBackupDateTime)) {
            return false;
        }

        while (!this.cfg.isAllowBackup()) {
            log.warn("Wait {} ms for the cluster is not allowed to do backup now.",
                    BackupConstants.SCHEDULER_SLEEP_TIME_FOR_UPGRADING);
            Thread.sleep(BackupConstants.SCHEDULER_SLEEP_TIME_FOR_UPGRADING);
        }

        return true;
    }

    private void doBackup() throws Exception {
        String tag = null;
        Exception lastException = null;
        int retryCount = 0;
        List<String> descParams = null;
        while (shouldDoBackup()) {
            try {
                Date backupTime = this.cfg.now().getTime();
                tag = ScheduledBackupTag.toBackupTag(backupTime,
                        this.cfg.getSoftwareVersion(), this.cfg.nodeCount);
                log.info("Starting backup using tag {} (retry #{})", tag, retryCount);
                this.cli.createBackup(tag);

                this.cfg.retainedBackups.add(tag);
                this.cfg.persist();

                return;
            } catch (InternalServerErrorException e) {
                lastException = e;
                log.error(String.format("Exception when creating backup %s (retry #%d)",
                        tag, retryCount), e);
            }

            if (retryCount == BackupConstants.BACKUP_RETRY_COUNT) {
                break;
            }
            retryCount++;
            Thread.sleep(BackupConstants.SCHEDULER_SLEEP_TIME_FOR_UPGRADING);
        }

        if (lastException != null) {
            this.cfg.sendBackupFailureToRoot(tag, lastException.getMessage());
        }
    }

    private void deleteExpiredBackups() throws Exception {
        // Remove out-of-date backup tags from master list
        if (this.cfg.retainedBackups.size() > this.cfg.copiesToKeep) {
            log.info("Found backups {} in retain list, keeping last {}",
                    StringUtils.join(this.cfg.retainedBackups, ','),
                    this.cfg.copiesToKeep);
            do {
                this.cfg.retainedBackups.remove(this.cfg.retainedBackups.first());
            } while (this.cfg.retainedBackups.size() > this.cfg.copiesToKeep);

            this.cfg.persist();
        }

        // Actually delete backups from disk that not in master list
        // NOTE: Down nodes are ignored, because once quorum nodes agree a backup is deleted, it is deleted even it still exists
        // in minority nodes.
        for (String tag : ScheduledBackupTag.pickScheduledBackupTags(this.cli.getClusterBackupTags(true))) {
            if (!this.cfg.retainedBackups.contains(tag)) {
                try {
                    this.cli.deleteBackup(tag);
                } catch (InternalServerErrorException e) {
                    log.error("Failed to delete scheduled backup from cluster", e);
                }
            }
        }
    }
}
