package com.emc.vipr.model.sys.backup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by brian on 16-1-3.
 */
public class BackupRestoreStatus {
    private static final Logger log = LoggerFactory.getLogger(BackupRestoreStatus.class);
    private static final String KEY_BACKUP_NAME = "backupName";
    private static final String KEY_STATUS = "status";
    private static final String KEY_ERROR_CODE = "errorCode";

    private String backupName;
    private Status status = Status.NOT_STARTED;
    private ErrorCode errorCode = ErrorCode.NO_ERROR;

    private long downloadSize = 0;
    private Map<String, Float> progresses = new HashMap(); //key=node name, value=progress

    public BackupRestoreStatus(String backupName, Status status, String nodeName, float progress, ErrorCode errorCode) {
        this.backupName = backupName;
        this.status= status;
        this.errorCode = errorCode;
        updateProgress(nodeName, progress);
    }

    public BackupRestoreStatus(Map<String, String> configs) {
        log.info("lbymt0 configs={}", configs);
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
    public Map<String, Float> getProgress() {
        return progresses;
    }

    public void setDownloadSize(long downloadSize) {
        this.downloadSize = downloadSize;
    }

    // public void setProgress(int progress) {
    //    this.progress = progress;
    // }

    public void updateProgress(String nodeName, float progress) {
        progresses.put(nodeName, progress);
    }

    @XmlElement(name = "error_code")
    public ErrorCode getErrorCode() {
        return this.errorCode;
    }

    public void setErrorCode(ErrorCode errorCode) {
        this.errorCode = errorCode;
    }

    public Map<String, String> getAllItems() {
        Map<String, String> restoreStatus = new HashMap<String, String>();
        if (this.backupName != null) {
            restoreStatus.put(KEY_BACKUP_NAME, this.backupName);
        }
        if (this.getStatus() != null) {
            restoreStatus.put(KEY_STATUS, this.getStatus().toString());
        }

        //restoreStatus.put(KEY_PROGRESS, String.valueOf(this.getProgress()));

        if (this.errorCode != null) {
            restoreStatus.put(KEY_ERROR_CODE, this.getErrorCode().toString());
        }

        for (Map.Entry<String, Float> progress : progresses.entrySet()) {
            restoreStatus.put(progress.getKey(), Float.toString(progress.getValue()));
        }

        return restoreStatus;
    }

    private void update(Map<String, String> configs) {
        log.info("lbymt");
        for (Map.Entry<String, String> config: configs.entrySet()) {
            String key = config.getKey();
            switch (key) {
                case KEY_BACKUP_NAME:
                    backupName = config.getValue();
                    log.info("lbymt0");
                    break;
                case KEY_STATUS:
                    status = Status.valueOf(config.getValue());
                    log.info("lbymt1");
                    break;
                case KEY_ERROR_CODE:
                    log.info("lbymt2");
                    errorCode = ErrorCode.valueOf(config.getValue());
                    break;
                default:
                    progresses.put(key, Float.parseFloat(config.getValue()));
            }

        }
        log.info("lbymt done");
    }

    /*
    public void update(String backupName, Status status, String nodeName, float progress, ErrorCode errorCode) {
        log.info("lbym Backup restore status before updating: {}", this);
        this.backupName = (backupName != null) ? backupName: this.backupName;
        // this.progress = (progress != 0 ) ? progress : this.progress;
        updateProgress(nodeName, progress);
        this.errorCode = (errorCode != null) ? errorCode : this.errorCode;
        this.status = (status != null) ? status : this.status;
        updatePostCheck();
        log.info("lbym Backup restore status after updating: {}", this);
    }
    */

    /*
    private void updatePostCheck() {
        log.info("lbym Backup restore status before the check: {}", this);
        switch (this.status) {
            case NOT_STARTED:
                this.errorCode = null;
                break;
            case DONE:
                if (this.progress != 100) {
                    log.warn("Restore progress should be 100 percents, while it's " + this.progress);
                    this.progress = 100;
                }
                this.errorCode = null;
                break;
        }
        if (this.backupName == null) {
            log.warn("Backup name should not be null");
            this.status = null;
            this.progress = 0;
            this.errorCode = null;
        }
    }
    */

    /**
     * The status of uploading backup set
     */
    @XmlType(name = "backupRestoreStatus_Status")
    public enum Status {
        NOT_STARTED,  // have not started yet
        DOWNLOADING,  // downloading backup
        IN_PROGRESS,  // in progress
        DOWNLOAD_FAILED, // download failed
        RESTORE_FAILED,  // restore failed
        DONE,         // success
        CANCELLED     // upload was cancelled
   }

    /**
     * The error code of upload failure
     */
    @XmlType(name = "backupRestoreStatus_ErrorCode")
    public enum ErrorCode {
        NO_ERROR,                // no error
        FTP_NOT_CONFIGURED,      // FTP server has not been configured
        BACKUP_NOT_EXIST,        // Can not find the target backup files on disk
        INVALID_BACKUP,          // Target backup is invalid
        RESTORE_FAILED           // internal failures during the upload
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
        sb.append(", progress");
        sb.append(progresses);

        return sb.toString();
    }
}
