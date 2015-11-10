/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.model.sys.backup;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * BackupUploadStatus is a class used by REST API to represent the backupset upload status
 */
@XmlRootElement(name = "backup_upload_status")
public class BackupUploadStatus implements Serializable {
    private static final Logger log = LoggerFactory.getLogger(BackupUploadStatus.class);
    private static final long serialVersionUID = -1322322139926395160L;
    private static final String KEY_BACKUP_NAME = "backupName";
    private static final String KEY_STATUS = "status";
    private static final String KEY_PROGRESS = "progress";
    private static final String KEY_ERROR_CODE = "errorCode";

    private String backupName;
    private Status status;
    private Integer progress;
    private ErrorCode errorCode;

    public BackupUploadStatus() {
    }

    public BackupUploadStatus(String backupName, Status status, Integer progress, ErrorCode errorCode) {
         update(backupName, status, progress, errorCode);
    }

    public BackupUploadStatus(Map<String, String> configs) {
        update(configs);
    }

    @XmlElement(name = "backup_name")
    public String getBackupName() {
        return this.backupName;
    }

    public void setBackupName(String backupName) {
        this.backupName = backupName;
    }

    @XmlElement(name = "status")
    public Status getStatus() {
        return this.status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    @XmlElement(name = "progress")
    public Integer getProgress() {
        return this.progress;
    }

    public void setProgress(int progress) {
        this.progress = progress;
    }

    @XmlElement(name = "error_code")
    public ErrorCode getErrorCode() {
        return this.errorCode;
    }

    public void setErrorCode(ErrorCode errorCode) {
        this.errorCode = errorCode;
    }

    public Map<String, String> getAllItems() {
        Map<String, String> uploadStatus = new HashMap<String, String>();
        if (this.backupName != null) {
            uploadStatus.put(KEY_BACKUP_NAME, this.backupName);
        }
        if (this.getStatus() != null) {
            uploadStatus.put(KEY_STATUS, this.getStatus().toString());
        }
        if (this.progress != null) {
            uploadStatus.put(KEY_PROGRESS, String.valueOf(this.getProgress()));
        }
        if (this.errorCode != null) {
            uploadStatus.put(KEY_ERROR_CODE, this.getErrorCode().toString());
        }
        return uploadStatus;
    }

    private void update(Map<String, String> configs) {
        String backupName = configs.get(KEY_BACKUP_NAME);
        Status status = (configs.get(KEY_STATUS) != null) ? Status.valueOf(configs.get(KEY_STATUS)) : null;
        Integer progress = (configs.get(KEY_PROGRESS) != null) ? Integer.parseInt(configs.get(KEY_PROGRESS)) : null;
        ErrorCode errCode = (configs.get(KEY_ERROR_CODE) != null) ? ErrorCode.valueOf(configs.get(KEY_ERROR_CODE)) : null;
        update(backupName, status, progress, errCode);
    }

    public void update(String backupName, Status status, Integer progress, ErrorCode errorCode) {
        log.info("Backup upload status before updating: {}", this);
        this.backupName = (backupName != null) ? backupName: this.backupName;
        this.progress = (progress != null) ? progress : this.progress;
        this.errorCode = (errorCode != null) ? errorCode : this.errorCode;
        this.status = (status != null) ? status : this.status;
        updatePostCheck();
        log.info("Backup upload status after updating: {}", this);
    }

    private void updatePostCheck() {
        log.info("Backup upload status before the check: {}", this);
        if (this.status == null) {
            return;
        }
        switch (this.status) {
            case NOT_STARTED:
                this.progress = null;
                this.errorCode = null;
                break;
            case DONE:
                if (this.progress != 100) {
                    log.warn("Upload progress should be 100 percents, while it's " + this.progress);
                    this.progress = 100;
                }
                this.errorCode = null;
                break;
        }
        if (this.backupName == null) {
            log.warn("Backup name should not be null");
            this.status = null;
            this.progress = null;
            this.errorCode = null;
        }
    }

    /**
     * The status of uploading backup set
     */
    public enum Status {
        NOT_STARTED,  // have not started yet
        IN_PROGRESS,  // in progress
        FAILED,       // failed
        DONE,         // success
        CANCELLED     // upload was cancelled
    }

    /**
     * The error code of upload failure
     */
    public enum ErrorCode {
        FTP_NOT_CONFIGURED,      // FTP server has not been configured
        BACKUP_NOT_EXIST,        // Can not find the target backup files on disk
        INVALID_BACKUP,          // Target backup is invalid
        UPLOAD_FAILURE           // internal failures during the upload
    }


    @Override
    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append("BackupName:");
        sb.append(getBackupName());
        sb.append(", Status:");
        sb.append(getStatus());
        sb.append(", Progress:");
        sb.append(getProgress());
        sb.append(", ErrorCode:");
        sb.append(getErrorCode());
        return sb.toString();
    }
}
