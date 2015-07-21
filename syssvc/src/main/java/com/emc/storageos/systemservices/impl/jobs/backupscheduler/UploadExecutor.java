/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.systemservices.impl.jobs.backupscheduler;

import com.emc.storageos.management.backup.BackupFileSet;
import com.emc.storageos.security.audit.AuditLogManager;
import com.emc.storageos.services.OperationTypeEnum;

import com.emc.storageos.services.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.OutputStream;
import java.nio.channels.UnsupportedAddressTypeException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * This class uploads backups to user supplied external file server.
 */
public abstract class UploadExecutor {
    private static final int UPLOAD_RETRY_TIMES = 3;
    private static final int UPLOAD_RETRY_DELAY_MS = 5000; // 5s

    private static final Logger log = LoggerFactory.getLogger(UploadExecutor.class);

    private BackupScheduler cli;
    protected SchedulerConfig cfg;

    public static UploadExecutor create(SchedulerConfig cfg, BackupScheduler cli) {
        if (cfg.uploadUrl == null) {
            return null;
        }

        if (FtpsUploader.isSupported(cfg.uploadUrl)) {
            return new FtpsUploader(cfg, cli);
        }

        throw new UnsupportedAddressTypeException();
    }

    protected UploadExecutor(SchedulerConfig cfg, BackupScheduler cli) {
        this.cfg = cfg;
        this.cli = cli;
    }

    public void runOnce() throws Exception {
        if (this.cfg.uploadUrl == null) {
            log.info("Upload URL is empty, upload disabled");
            return;
        }

        try (AutoCloseable lock = this.cfg.lock()) {
            this.cfg.reload();
            cleanupCompletedTags();
            upload();
        }
    }

    /**
     * Try several times to upload a backup.
     * @param tag
     * @return null if succeeded, or error message from last retry if failed.
     * @throws InterruptedException
     */
    private String tryUpload(String tag) throws InterruptedException {
        String lastErrorMessage = null;
        for (int i = 0; i < UPLOAD_RETRY_TIMES; i++) {
            try {
                BackupFileSet files = this.cli.getDownloadFiles(tag);
                if (files.isEmpty()) {
                    return String.format("Cannot find target backup set '%s'.", tag);
                }
                if (!files.isValid()) {
                    return "Cannot get enough files for specified backup";
                }

                String zipName = this.cli.generateZipFileName(tag, files);

                Long existingLen = getFileSize(zipName);
                long len = existingLen == null ? 0 : existingLen;
                log.info("Uploading {} at offset {}", tag, existingLen);
                try (OutputStream uploadStream = upload(zipName, len)) {
                    this.cli.uploadTo(files, len, uploadStream);
                }

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

        return lastErrorMessage;
    }

    private void upload() throws Exception {
        log.info("Begin upload");

        List<String> toUpload = getIncompleteUploads();
        if (toUpload.isEmpty()) {
            return;
        }

        List<String> succUploads = new ArrayList<>();
        List<String> failureUploads = new ArrayList<>();
        List<String> errMsgs = new ArrayList<>();

        for (String tag : toUpload) {
            String errMsg = tryUpload(tag);
            if (errMsg == null) {
                log.info("Backup {} is successfully uploaded", tag);
                this.cfg.uploadedBackups.add(tag);
                succUploads.add(tag);
            } else {
                failureUploads.add(tag);
                errMsgs.add(errMsg);
            }
        }

        this.cfg.persist();

        if (!succUploads.isEmpty()) {
            List<String> descParams = this.cli.getDescParams(Strings.join(", ", succUploads.toArray(new String[succUploads.size()])));
            this.cli.auditBackup(OperationTypeEnum.UPLOAD_BACKUP, AuditLogManager.AUDITLOG_SUCCESS, null, descParams.toArray());
        }
        if (!failureUploads.isEmpty()) {
            String failureTags = Strings.join(", ", failureUploads.toArray(new String[failureUploads.size()]));
            List<String> descParams = this.cli.getDescParams(failureTags);
            descParams.add(Strings.join(", ", errMsgs.toArray(new String[errMsgs.size()])));
            this.cli.auditBackup(OperationTypeEnum.UPLOAD_BACKUP, AuditLogManager.AUDITLOG_FAILURE, null, descParams.toArray());
            this.cfg.sendUploadFailureToRoot(failureTags, Strings.join("\r\n", errMsgs.toArray(new String[errMsgs.size()])));
        }
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

    /**
     * Some tags in completeTags may not exist on disk anymore, need to remove them from the list
     * to free up space in ZK.
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

    /**
     * Get size of a file on server.
     * @param fileName the name of the file for which to get size info.
     * @return file size in bytes, or null if file is not exist.
     * @throws Exception
     */
    public abstract Long getFileSize(String fileName) throws Exception;

    /**
     * Upload file with resuming.
     * @param fileName the file on server to be uploaded to.
     * @param offset from which offset on server to resume upload.
     * @return The OutputStream instance to which upload data can be written.
     * @throws Exception
     */
    public abstract OutputStream upload(String fileName, long offset) throws Exception;
}
