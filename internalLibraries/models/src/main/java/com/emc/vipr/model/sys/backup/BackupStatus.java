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
    private BackupOperationStatus manualBackupCreationStatus;
    private BackupOperationStatus scheduledBackupCreationStatus;
    private BackupOperationStatus backupUploadStatus;

    @XmlElement(name = "manual_backup_creation")
    public BackupOperationStatus getManualBackupCreationStatus() {
        return manualBackupCreationStatus;
    }

    public void setManualBackupCreationStatus(BackupOperationStatus manualBackupCreationStatus) {
        this.manualBackupCreationStatus = manualBackupCreationStatus;
    }

    @XmlElement(name = "scheduled_backup_creation")
    public BackupOperationStatus getScheduledBackupCreationStatus() {
        return scheduledBackupCreationStatus;
    }

    public void setScheduledBackupCreationStatus(BackupOperationStatus scheduledBackupCreationStatus) {
        this.scheduledBackupCreationStatus = scheduledBackupCreationStatus;
    }

    @XmlElement(name = "backup_upload_status")
    public BackupOperationStatus getBackupUploadStatus() {
        return backupUploadStatus;
    }

    public void setBackupUploadStatus(BackupOperationStatus backupUploadStatus) {
        this.backupUploadStatus = backupUploadStatus;
    }

    /**
     * Class to abstract the status of an operation
     */
    @XmlRootElement(name = "backup_operation_status")
    @XmlAccessorType(XmlAccessType.NONE)
    public static class BackupOperationStatus {
        private String backupName;
        private long opTime = 0;
        private boolean opStatus = false;

        public BackupOperationStatus() {
        }

        public BackupOperationStatus(String backupName, long opTime, boolean opStatus) {
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
        public boolean getOperationStatus() {
            return this.opStatus;
        }

        public void setOperationStatus(boolean operationStatus) {
            this.opStatus = operationStatus;
        }
    }
}
