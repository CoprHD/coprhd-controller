/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.model.sys.backup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * BackupUploadStatus is a class used by REST API to represent the backupset upload status
 */
@XmlRootElement(name = "backup_upload_status")
public class BackupUploadStatus {

    private Status status = Status.NOT_STARTED;
    private int progress;
    private ErrorCode errorCode;

    public BackupUploadStatus() {
    }

    public BackupUploadStatus(Status status, int progress, ErrorCode errorCode) {
        this.status = status;
        this.progress = progress;
        this.errorCode = errorCode;
    }

    @XmlElement(name = "status")
    public Status getStatus() {
        return this.status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    @XmlElement(name = "progress")
    public int getProgress() {
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
        BACKUP_NOT_EXIST,        // Can’t find the target backup files on disk
        INVALID_BACKUP,          // Target backup is invalid
        UPLOAD_FAILURE           // internal failures during the upload
    }


    @Override
    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append("Status:");
        sb.append(getStatus());
        sb.append(", Progress:");
        sb.append(getProgress());
        sb.append(", ErrorCode:");
        sb.append(getErrorCode());
        return sb.toString();
    }
}
