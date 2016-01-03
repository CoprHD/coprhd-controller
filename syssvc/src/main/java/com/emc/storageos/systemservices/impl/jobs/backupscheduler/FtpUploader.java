/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.systemservices.impl.jobs.backupscheduler;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

import com.emc.storageos.management.backup.util.FtpClient;
import com.emc.storageos.management.backup.util.ProcessInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implements upload protocol using FTPS
 */
public class FtpUploader extends Uploader {
    private static final Logger log = LoggerFactory.getLogger(FtpUploader.class);

    private final FtpClient ftpClient;

    public FtpUploader(SchedulerConfig cfg, BackupScheduler cli) {
        super(cfg, cli);
        ftpClient = new FtpClient(cfg.uploadUrl, cfg.uploadUserName, cfg.getUploadPassword());
    }

    public static boolean isSupported(String url) {
        return FtpClient.isSupported(url);
    }

    @Override
    public Long getFileSize(String fileName) throws Exception {
        return ftpClient.getFileSize(fileName);
    }

    @Override
    public OutputStream upload(String fileName, long offset) throws Exception {
        return ftpClient.upload(fileName, offset);
    }

    @Override
    public List<String> listFiles(String prefix) throws Exception {
        return ftpClient.listFiles(prefix);
    }

    @Override
    public void rename(String sourceFileName, String destFileName) throws Exception {
        ftpClient.rename(sourceFileName, destFileName);
    }
}
