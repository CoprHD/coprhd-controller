/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.systemservices.impl.jobs.backupscheduler;

import java.io.OutputStream;
import java.nio.channels.UnsupportedAddressTypeException;
import java.util.List;

/**
 * This class uploads backups to user supplied external file server.
 */
public abstract class Uploader {
    private BackupScheduler cli;
    protected SchedulerConfig cfg;

    public static Uploader create(SchedulerConfig cfg, BackupScheduler cli) throws Exception {
        cfg.reload();
        if (cfg.uploadUrl == null) {
            return null;
        }

        if (FtpsUploader.isSupported(cfg.uploadUrl)) {
            return new FtpsUploader(cfg, cli);
        }

        throw new UnsupportedAddressTypeException();
    }

    protected Uploader(SchedulerConfig cfg, BackupScheduler cli) {
        this.cfg = cfg;
        this.cli = cli;
    }

    /**
     * Get size of a file on server.
     *
     * @param fileName the name of the file for which to get size info.
     * @return file size in bytes, or null if file is not exist.
     * @throws Exception
     */
    public abstract Long getFileSize(String fileName) throws Exception;

    /**
     * Upload file with resuming.
     *
     * @param fileName the file on server to be uploaded to.
     * @param offset   from which offset on server to resume upload.
     * @return The OutputStream instance to which upload data can be written.
     * @throws Exception
     */
    public abstract OutputStream upload(String fileName, long offset) throws Exception;

    /**
     * List files on server.
     *
     * @return List contain filenames.
     * @throws Exception
     */
    public abstract List<String> listFiles() throws Exception;

    /**
     * Delete specific file on server.
     *
     * @param fromFileName to be renamed filename on server
     * @param toFileName   rename to filename on server
     * @return null.
     * @throws Exception
     */
    public abstract void rename(String fromFileName, String toFileName) throws Exception;

    /**
     * clean up stable incompleted backup file on server based on the input filename.
     *
     * @param toUploadedFileName the filename about to upload,
     * @return null.
     */
    public abstract void markInvalidZipFile(String toUploadedFileName);
}
