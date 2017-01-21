/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */

package com.emc.vipr.model.sys.backup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.bind.annotation.*;

@XmlRootElement(name = "backup_status")
public class BackupStatus {
    private static final Logger log = LoggerFactory.getLogger(BackupSets.class);
    private BackupOperationStatus lastManualBackup;
    private BackupOperationStatus lastScheduledBackup;
    private BackupOperationStatus lastSuccessfulBackup;
    private BackupOperationStatus lastUploadStatus;
    private long nextScheduledBackup;

    @XmlElement(name = "last_manual_backup")
    public BackupOperationStatus getLastManualBackup() {
        return lastManualBackup;
    }

    public void setLastManualBackup(BackupOperationStatus lastManualBackup) {
        this.lastManualBackup = lastManualBackup;
    }

    public void setLastManualBackup(String backupName, long opTime, String opStatus) {
        this.lastManualBackup.backupName = backupName;
        this.lastManualBackup.opTime = opTime;
        this.lastManualBackup.opStatus = opStatus;
    }

    @XmlElement(name = "last_scheduled_backup")
    public BackupOperationStatus getLastScheduledBackup() {
        return lastScheduledBackup;
    }

    public void setLastScheduledBackup(BackupOperationStatus lastScheduledBackup) {
        this.lastScheduledBackup = lastScheduledBackup;
    }

    public void setLastScheduledBackup(String backupName, long opTime, String opStatus) {
        this.lastScheduledBackup.backupName = backupName;
        this.lastScheduledBackup.opTime = opTime;
        this.lastScheduledBackup.opStatus = opStatus;
    }

    @XmlElement(name = "next_scheduled_backup")
    public long getNextScheduledBackup() {
        return nextScheduledBackup;
    }

    public void setNextScheduledBackup(long nextScheduledBackup) {
        this.nextScheduledBackup = nextScheduledBackup;
    }

    @XmlElement(name = "last_successful_backup")
    public BackupOperationStatus getLastSuccessfulBackup() {
        return lastSuccessfulBackup;
    }

    public void setLastSuccessfulBackup(BackupOperationStatus lastSuccessfulBackup) {
        this.lastSuccessfulBackup = lastSuccessfulBackup;
    }

    public void setLastSuccessfulBackup(String backupName, long opTime, String backupType) {
        this.lastSuccessfulBackup.backupName = backupName;
        this.lastSuccessfulBackup.opTime = opTime;
        this.lastSuccessfulBackup.opStatus = backupType;
    }

    @XmlElement(name = "backup_upload_status")
    public BackupOperationStatus getLastUploadStatus() {
        return lastUploadStatus;
    }

    public void setLastUploadStatus(BackupOperationStatus lastUploadStatus) {
        this.lastUploadStatus = lastUploadStatus;
    }

    public void setLastUploadStatus(String backupName, long opTime, String backupType) {
        this.lastUploadStatus.backupName = backupName;
        this.lastUploadStatus.opTime = opTime;
        this.lastUploadStatus.opStatus = backupType;
    }

    /**
     * Class to abstract the status of an operation
     */
    @XmlRootElement(name = "backup_operation_status")
    @XmlAccessorType(XmlAccessType.NONE)
    public static class BackupOperationStatus {
        private String backupName;
        private long opTime = 0;
        private String opStatus;

        public BackupOperationStatus() {
        }

        public BackupOperationStatus(String backupName, long opTime, String opStatus) {
            this.backupName = backupName;
            this.opTime = opTime;
            this.opStatus = opStatus;
        }

        @XmlElement(name = "name")
        public String getBackupName() {
            return this.backupName;
        }

        public void setBackupName(String backupName) {
            this.backupName = backupName;
        }

        @XmlElement(name = "operation_time")
        public long getOperationTime() {
            return this.opTime;
        }

        public void setOperationTime(long operationTime) {
            this.opTime = operationTime;
        }

        @XmlElement(name = "operation_status")
        public String getOperationStatus() {
            return this.opStatus;
        }

        public void setOperationStatus(String operationStatus) {
            this.opStatus = operationStatus;
        }
    }
}
