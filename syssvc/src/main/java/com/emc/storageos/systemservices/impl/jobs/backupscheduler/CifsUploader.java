/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.systemservices.impl.jobs.backupscheduler;

import java.io.OutputStream;
import java.util.List;

import com.emc.storageos.management.backup.util.CifsClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implements upload protocol using CIFS
 */
public class CifsUploader extends Uploader {
    private static final Logger log = LoggerFactory.getLogger(CifsUploader.class);

    private final CifsClient cifsClient;

    public CifsUploader(SchedulerConfig cfg, BackupScheduler cli) {
        super(cfg, cli);
        cifsClient = new CifsClient(cfg.uploadUrl,cfg.getUploadDomain(), cfg.uploadUserName, cfg.getExternalServerPassword());
    }

    public static boolean isSupported(String url) {
        return CifsClient.isSupported(url);
    }

    @Override
    public Long getFileSize(String fileName) throws Exception {
        return cifsClient.getFileSize(fileName);
    }

    @Override
    public OutputStream upload(String fileName, long offset) throws Exception {
        return cifsClient.upload(fileName, offset);
    }

    @Override
    public List<String> listFiles(String prefix) throws Exception {
        return cifsClient.listFiles(prefix);
    }

    @Override
    public void rename(String sourceFileName, String destFileName) throws Exception {
        cifsClient.rename(sourceFileName, destFileName);
    }
}

