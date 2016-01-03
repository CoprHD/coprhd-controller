/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.systemservices.impl.jobs.backupscheduler;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.common.Service;
import com.emc.storageos.systemservices.impl.client.SysClientFactory;
import com.emc.storageos.management.backup.BackupConstants;
import com.emc.storageos.management.backup.util.FtpClient;

public class DownloadExecutor implements  Runnable {
    private static final Logger log = LoggerFactory.getLogger(DownloadExecutor.class);

    private FtpClient client;
    private String remoteBackupFileName;
    private List<Service> sysSvcs;

    public DownloadExecutor(SchedulerConfig cfg, String backupZipFileName, List<Service> svcs) {
        if (cfg.uploadUrl == null) {
            try {
                cfg.reload();
            }catch (Exception e) {
                log.error("Failed to reload cfg e=", e);
                throw new RuntimeException(e);
            }
        }

        client = new FtpClient(cfg.uploadUrl, cfg.uploadUserName, cfg.getUploadPassword());
        remoteBackupFileName = backupZipFileName;
        sysSvcs = svcs;
    }

    @Override
    public void run() {
        try {
            download();
            notifyOtherNodes();
        }catch (Exception e) {
            log.error("Failed to download e=", e);
        }
    }

    private void download() throws IOException, InterruptedException {
        log.info("download start");
        ZipInputStream zin = getDownloadStream();

        try {
            long size = client.getFileSize(remoteBackupFileName);
            log.info("lby size={}", size);
        }catch (IOException | InterruptedException e) {
            log.error("Failed to get zip file size e=",e);
            throw e;
        }

        int dotIndex=remoteBackupFileName.lastIndexOf(".");
        String backupFolder=remoteBackupFileName.substring(0,dotIndex);
        log.info("lbyx backupFolder={}", backupFolder);

        String localHostName = InetAddress.getLocalHost().getHostName();
        log.info("lby local hostname={}", localHostName);

        ZipEntry zentry = zin.getNextEntry();
        while (zentry != null) {
            log.info("lbyy file {}", zentry.getName());
            if (belongsTo(zentry.getName(), localHostName)) {
                log.info("lbyy to download {}", backupFolder+"/"+zentry.getName());
                downloadMyBackupFile(backupFolder+"/"+zentry.getName(), zin);
            }
            zentry = zin.getNextEntry();
        }

        try {
            zin.closeEntry();
            zin.close();
        }catch (IOException e) {
            log.info("lbyu exception will close ignored e=",e);
        }
    }

    private boolean belongsTo(String filename, String hostname) {
        log.info("lbyuu filename={} zk suffix={}", filename, BackupConstants.BACKUP_ZK_FILE_SUFFIX);
        return filename.contains(hostname) ||
                filename.contains(BackupConstants.BACKUP_INFO_SUFFIX) ||
                filename.contains(BackupConstants.BACKUP_ZK_FILE_SUFFIX);
    }

    private void downloadMyBackupFile(String backupFileName, ZipInputStream zin) throws IOException {
        File file = new File("/data/backup", backupFileName);
        log.info("lbyu file={}", file.getAbsoluteFile());
        if (!file.exists()) {
            file.getParentFile().mkdirs();
            file.createNewFile();
        }

        byte[] buf = new byte[BackupConstants.DOWNLOAD_BUFFER_SIZE];
        int length;

        try (FileOutputStream out = new FileOutputStream(file)) {
            while ((length = zin.read(buf)) > 0) {
                out.write(buf, 0, length);
            }
        }
    }

    private void notifyOtherNodes() {
        log.info("lbyu notify other nodes");
        if (sysSvcs == null) {
            return;
        }

        URI pushUri = SysClientFactory.URI_NODE_BACKUPS_PUSH;
        log.info("lbyx pushUrl={}", pushUri);

        for (Service svc : sysSvcs) {
            URI endpoint = svc.getEndpoint();
            log.info("lbyy notify {}", endpoint);
            SysClientFactory.SysClient sysClient = SysClientFactory.getSysClient(endpoint);
            sysClient.post(pushUri, null, remoteBackupFileName);
        }
    }

    private ZipInputStream getDownloadStream() throws IOException {
        log.info("lbyu get download stream backupfile={}", remoteBackupFileName);

        InputStream in = client.download(remoteBackupFileName);
        ZipInputStream zin = new ZipInputStream(in);
        log.info("lby in={} zin={}", in ,zin);

        return zin;
    }
}
