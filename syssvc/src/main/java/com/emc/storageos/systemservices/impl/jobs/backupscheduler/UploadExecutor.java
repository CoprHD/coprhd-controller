/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.systemservices.impl.jobs.backupscheduler;

import com.emc.storageos.management.backup.BackupFileSet;
import com.emc.storageos.security.audit.AuditLogManager;
import com.emc.storageos.services.OperationTypeEnum;

import com.emc.storageos.services.util.Strings;
import com.emc.vipr.model.sys.backup.BackupUploadStatus;
import com.emc.vipr.model.sys.backup.BackupUploadStatus.Status;
import com.emc.vipr.model.sys.backup.BackupUploadStatus.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.channels.UnsupportedAddressTypeException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * This class uploads backups to user supplied external file server.
 */
public class UploadExecutor {
    private static final int UPLOAD_RETRY_TIMES = 3;
    private static final int UPLOAD_RETRY_DELAY_MS = 5000; // 5s

    private static final Logger log = LoggerFactory.getLogger(UploadExecutor.class);

    private BackupScheduler cli;
    protected SchedulerConfig cfg;
    protected Uploader uploader;

    public UploadExecutor(SchedulerConfig cfg, BackupScheduler cli) {
        this.cfg = cfg;
        this.cli = cli;
    }

    public void setUploader(Uploader uploader) {
        this.uploader = uploader;
    }

    public void runOnce() throws Exception {
        runOnce(null);
    }

    public void runOnce(String backupTag) throws Exception {
        if (this.uploader == null) {
            setUploader(Uploader.create(cfg, cli));
            if (this.uploader == null) {
                log.info("Upload URL is empty, upload disabled");
                return;
            }
        }

        try (AutoCloseable lock = this.cfg.lock()) {
            this.cfg.reload();
            cleanupCompletedTags();
            upload(backupTag);
        } catch (Exception e) {
            log.error("Fail to run upload backup", e);
        }
    }

    /**
     * Try several times to upload a backup.
     * 
     * @param tag
     * @return null if succeeded, or error message from last retry if failed.
     * @throws InterruptedException
     */
    private String tryUpload(String tag) throws InterruptedException {
        String lastErrorMessage = null;

        setUploadStatus(tag, Status.NOT_STARTED, null, null);
        for (int i = 0; i < UPLOAD_RETRY_TIMES; i++) {
            try {
                setUploadStatus(tag, Status.IN_PROGRESS, 0, null);
                BackupFileSet files = this.cli.getDownloadFiles(tag);
                if (files.isEmpty()) {
                    setUploadStatus(null, Status.FAILED, null, ErrorCode.BACKUP_NOT_EXIST);
                    return String.format("Cannot find target backup set '%s'.", tag);
                }
                if (!files.isValid()) {
                    setUploadStatus(null, Status.FAILED, null, ErrorCode.INVALID_BACKUP);
                    return "Cannot get enough files for specified backup";
                }

                String zipName = this.cli.generateZipFileName(tag, files);

                Long existingLen = uploader.getFileSize(zipName);
                long len = existingLen == null ? 0 : existingLen;
                log.info("Uploading {} at offset {}", tag, existingLen);
                try (OutputStream uploadStream = uploader.upload(zipName, len)) {
                    this.cli.uploadTo(files, len, uploadStream);
                }

                setUploadStatus(null, Status.DONE, 100, null);
                return null;
            } catch (Exception e) {
                lastErrorMessage = e.getMessage();
                if (lastErrorMessage == null || lastErrorMessage.isEmpty()) {
                    lastErrorMessage = e.getClass().getSimpleName();
                }
                log.warn(String.format("An attempt to upload backup %s is failed", tag), e);
            }

            Thread.sleep(UPLOAD_RETRY_DELAY_MS);
        }

        setUploadStatus(null, Status.FAILED, null, ErrorCode.UPLOAD_FAILURE);

        return lastErrorMessage;
    }

    private void upload(String backupTag) throws Exception {
        log.info("Begin upload");

        List<String> toUpload = getWaitingUploads(backupTag);
        if (toUpload.isEmpty()) {
            log.info("No backups need to be uploaded");
            return;
        }

        List<String> succUploads = new ArrayList<>();
        List<String> failureUploads = new ArrayList<>();
        List<String> errMsgs = new ArrayList<>();

        for (String tag : toUpload) {
            String errMsg = tryUpload(tag);
            if (errMsg == null) {
                log.info("Upload backup {} successfully", tag);
                this.cfg.uploadedBackups.add(tag);
                succUploads.add(tag);
            } else {
                log.info("Upload backup {} failed", tag);
                failureUploads.add(tag);
                errMsgs.add(errMsg);
            }
        }

        this.cfg.persist();

        if (!succUploads.isEmpty()) {
            List<String> descParams = this.cli.getDescParams(
                    Strings.join(", ", succUploads.toArray(new String[succUploads.size()])));
            this.cli.auditBackup(OperationTypeEnum.UPLOAD_BACKUP,
                    AuditLogManager.AUDITLOG_SUCCESS, null, descParams.toArray());
        }
        if (!failureUploads.isEmpty()) {
            String failureTags = Strings.join(", ", failureUploads.toArray(new String[failureUploads.size()]));
            List<String> descParams = this.cli.getDescParams(failureTags);
            descParams.add(Strings.join(", ", errMsgs.toArray(new String[errMsgs.size()])));
            this.cli.auditBackup(OperationTypeEnum.UPLOAD_BACKUP,
                    AuditLogManager.AUDITLOG_FAILURE, null, descParams.toArray());
            log.info("Sending update failures to root user");
            this.cfg.sendUploadFailureToRoot(failureTags,
                    Strings.join("\r\n", errMsgs.toArray(new String[errMsgs.size()])));
        }
        log.info("Finish upload");
    }

    private List<String> getWaitingUploads(String backupTag) {
        List<String> toUpload = new ArrayList<String>();

        List<String> incompleteUploads = getIncompleteUploads();
        if (backupTag == null) {
            toUpload.addAll(incompleteUploads);
        } else {
            if(incompleteUploads.contains(backupTag)) {
                toUpload.add(backupTag);
            } else {
                log.info("Backup({}) has already been uploaded", backupTag);
            }
        }
        return toUpload;
    }

    private List<String> getIncompleteUploads() {
        List<String> toUpload = new ArrayList<>(this.cfg.retainedBackups.size());
        Set<String> allBackups = this.cli.getClusterBackupTags(true);
        allBackups.removeAll(ScheduledBackupTag.pickScheduledBackupTags(allBackups));
        allBackups.addAll(this.cfg.retainedBackups);
        for (String tagName : allBackups) {
            if (!this.cfg.uploadedBackups.contains(tagName)) {
                toUpload.add(tagName);
            }
        }

        log.info("Tags in retain list: {}, incomplete ones are: {}",
                this.cfg.retainedBackups.toArray(new String[this.cfg.retainedBackups.size()]),
                toUpload.toArray(new String[toUpload.size()]));

        return toUpload;
    }

    public void setUploadStatus(String backupTag, Status status, Integer progress, ErrorCode errorCode) {
        BackupUploadStatus uploadStatus = this.cfg.queryBackupUploadStatus();
        uploadStatus.update(backupTag, status, progress, errorCode);
        this.cfg.persistBackupUploadStatus(uploadStatus);
    }

    public BackupUploadStatus getUploadStatus(String backupTag) throws Exception {
        if (backupTag == null) {
            log.error("Query parameter of backupTag is null");
            throw new IllegalStateException("Invalid query parameter");
        }
        this.cfg.reload();
        log.info("Current uploaded backup list: {}",
                this.cfg.uploadedBackups.toArray(new String[this.cfg.uploadedBackups.size()]));
        if (this.cfg.uploadedBackups.contains(backupTag)) {
            log.info("{} is in the uploaded backup list", backupTag);
            return new BackupUploadStatus(backupTag, Status.DONE, 100, null);
        }
        if (!getIncompleteUploads().contains(backupTag)) {
            return new BackupUploadStatus(backupTag, Status.FAILED, 0, ErrorCode.BACKUP_NOT_EXIST);
        }
        if (cfg.uploadUrl == null) {
            return new BackupUploadStatus(backupTag, Status.FAILED, 0, ErrorCode.FTP_NOT_CONFIGURED);
        }
        BackupUploadStatus uploadStatus = this.cfg.queryBackupUploadStatus();
        if (backupTag.equals(uploadStatus.getBackupName())) {
            return uploadStatus;
        }
        return new BackupUploadStatus(backupTag, Status.NOT_STARTED, null, null);
    }

    /**
     * Some tags in completeTags may not exist on disk anymore, need to remove them from the list
     * to free up space in ZK.
     * 
     * @throws Exception
     */
    private void cleanupCompletedTags() throws Exception {
        boolean modified = false;
        // Get all tags by ignoring down nodes - tag is returned even found in only one node
        // This guarantees if quorum nodes say no such tag, the tag is invalid even exist in remaining nodes.
        Set<String> manualBackups = this.cli.getClusterBackupTags(true);
        manualBackups.removeAll(ScheduledBackupTag.pickScheduledBackupTags(manualBackups));

        // Auto and manual backups need be checked separately because for auto backups, it could be invalid
        // even it present in cluster, only those recorded in .retainedBackups are valid auto backups.
        for (String tag : new ArrayList<>(this.cfg.uploadedBackups)) {
            if (!this.cfg.retainedBackups.contains(tag) && !manualBackups.contains(tag)) {
                this.cfg.uploadedBackups.remove(tag);
                modified = true;
            }
        }

        if (modified) {
            this.cfg.persist();
        }
    }
}
